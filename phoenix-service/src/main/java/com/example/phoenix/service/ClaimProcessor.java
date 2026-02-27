package com.example.phoenix.service;

import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.document.Document;
import org.springframework.ai.ollama.api.OllamaChatOptions;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.phoenix.model.Claim;
import com.example.phoenix.model.FraudResult;
import com.example.phoenix.repository.ClaimRepository;
import com.example.phoenix.tool.RiskAnalysisTools;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;

@Service
public class ClaimProcessor {

    private static final Logger log = LoggerFactory.getLogger(ClaimProcessor.class);

    private final ClaimRepository claimRepository;
    private final AiService aiService;
    private final GovernanceService governanceService;
    private final VectorStoreManager vectorStoreManager;
    private final RiskAnalysisTools riskAnalysisTools;
    private final ObservationRegistry observationRegistry;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public ClaimProcessor(ClaimRepository claimRepository, AiService aiService, GovernanceService governanceService,
            VectorStoreManager vectorStoreManager, RiskAnalysisTools riskAnalysisTools,
            ObservationRegistry observationRegistry) {
        this.claimRepository = claimRepository;
        this.aiService = aiService;
        this.governanceService = governanceService;
        this.vectorStoreManager = vectorStoreManager;
        this.riskAnalysisTools = riskAnalysisTools;
        this.observationRegistry = observationRegistry;
    }

    @Transactional
    public void processClaimUpdate(String message) {
        try {
            JsonNode rootNode = objectMapper.readTree(message);
            JsonNode payloadNode = rootNode.has("payload") ? rootNode.get("payload") : rootNode;

            if (!payloadNode.has("after") || payloadNode.get("after").isNull()) {
                return;
            }

            JsonNode after = payloadNode.get("after");
            Long claimId = after.get("id").asLong();

            Claim claim = claimRepository.findById(claimId)
                    .orElseThrow(() -> new RuntimeException("Claim not found: " + claimId));

            if (claim.getSummary() != null && !claim.getSummary().isEmpty()) {
                return;
            }

            runEnrichmentPipeline(claim);

        } catch (Exception e) {
            log.error("Failed to process claim update: {}", e.getMessage(), e);
        }
    }

    private void runEnrichmentPipeline(Claim claim) {
        Observation observation = Observation.start("claim.enrichment.pipeline", observationRegistry);
        observation.lowCardinalityKeyValue("claim.id", String.valueOf(claim.getId()));

        try (Observation.Scope scope = observation.openScope()) {
            log.info("Starting enrichment pipeline for claim: {}", claim.getId());

            // 1. Governance/Sanitization
            String sanitizedDescription = governanceService.redactSensitiveData(claim.getDescription());
            observation.event(Observation.Event.of("governance.sanitization.complete"));

            // 2. Stage 1: Summarization
            ChatClient chatClient = aiService.getChatClient(claim.getAiProvider());
            String summary = summarizeClaim(sanitizedDescription, claim.getAiTemperature(), chatClient);
            claim.setSummary(summary);
            claimRepository.save(claim);
            observation.event(Observation.Event.of("summarization.complete"));

            // 3. Stage 2: Agentic Fraud Analysis
            log.info("Starting Agentic Fraud Analysis for claim: {}", claim.getId());
            observation.event(Observation.Event.of("agentic.rag.started"));
            FraudResult fraudResult = agenticAnalyzeClaim(sanitizedDescription, chatClient, claim.getAiProvider());

            claim.setFraudScore(fraudResult.score());
            claim.setFraudAnalysis(fraudResult.analysis());
            claim.setFraudRationale(fraudResult.rationale());
            claim.setFraudThought(fraudResult.thought());
            claimRepository.save(claim);
            observation.event(Observation.Event.of("agentic.rag.complete"));

            // 4. Vector Sync
            syncToVectorStore(claim);
            observation.event(Observation.Event.of("vector.sync.complete"));

            log.info("Enrichment pipeline completed for claim: {}", claim.getId());
        } catch (Exception e) {
            observation.error(e);
            log.error("Error in enrichment pipeline for claim {}: {}", claim.getId(), e.getMessage());
        } finally {
            observation.stop();
        }
    }

    private String summarizeClaim(String description, Double temperature, ChatClient chatClient) {
        return chatClient.prompt()
                .user("Summarize this insurance claim for a technical adjuster in 1 sentence: " + description)
                .options(OllamaChatOptions.builder().temperature(temperature).build())
                .call()
                .content();
    }

    private FraudResult agenticAnalyzeClaim(String claimText, ChatClient chatClient, String provider) {
        ChatClient agenticClient = chatClient.mutate()
                .defaultTools(riskAnalysisTools)
                .defaultAdvisors(new SimpleLoggerAdvisor())
                .build();

        String systemPrompt = """
                You are a Senior Insurance Fraud Analyst Agent.
                MISSION:
                1. First, REASON about the claim. Look for names, amounts, and specific incident types.
                2. EVALUATE if 'historicalClaimSearch' is needed.
                3. EXECUTE the tool call using provider: %s.
                4. ANALYZE the results from the tool alongside the current claim.

                OUTPUT FORMAT (STRICT):
                THOUGHT: <your reasoning>
                SCORE: <0-100>
                ANALYSIS: <concise summary>
                RATIONALE: <detailed logic>
                """;

        String response = agenticClient.prompt()
                .system(String.format(systemPrompt, provider))
                .user("Task: Analyze this claim for potential fraud or anomalies: " + claimText)
                .call()
                .content();

        return parseAndValidate(response, claimText, chatClient, 1);
    }

    private FraudResult parseAndValidate(String response, String claimText, ChatClient chatClient, int attempt) {
        Pattern pattern = Pattern.compile(
                "(?:THOUGHT:\\s*(.*?)\\s*)?SCORE:\\s*(\\d{1,3})\\s*ANALYSIS:\\s*(.*?)\\s*RATIONALE:\\s*(.*)",
                Pattern.DOTALL | Pattern.CASE_INSENSITIVE);

        Matcher matcher = pattern.matcher(response);
        if (matcher.find()) {
            try {
                String thought = matcher.group(1) != null ? matcher.group(1).trim()
                        : "Agent skipped explicit reasoning.";
                int score = Integer.parseInt(matcher.group(2));
                String analysis = matcher.group(3).trim();
                String rationale = matcher.group(4).trim();

                if (score >= 0 && score <= 100) {
                    return new FraudResult(score, analysis, rationale, thought);
                }
            } catch (Exception e) {
                log.warn("Regex parsing failed, attempting flexible extraction.");
            }
        }

        return extractFlexibly(response, claimText, chatClient, attempt);
    }

    private FraudResult extractFlexibly(String response, String claimText, ChatClient chatClient, int attempt) {
        Integer score = extractValue(response, "SCORE\\s*[:\\-]?\\s*(\\d{1,3})").map(Integer::parseInt).orElse(null);
        String thought = extractValue(response, "THOUGHT:\\s*(.*?)(?=SCORE:|ANALYSIS:|RATIONALE:|$)").orElse("N/A");
        String analysis = extractValue(response, "ANALYSIS:\\s*(.*?)(?=RATIONALE:|SCORE:|THOUGHT:|$)").orElse("N/A");
        String rationale = extractValue(response, "RATIONALE:\\s*(.*)").orElse("N/A");

        if (score != null && score >= 0 && score <= 100) {
            return new FraudResult(score, analysis, rationale, thought);
        }

        if (attempt < 2) {
            return retryWithCorrection(claimText, response, chatClient, attempt + 1);
        }

        return new FraudResult(0, "Error", "Failed to parse AI response format.", "Parsing failure.");
    }

    private java.util.Optional<String> extractValue(String text, String regex) {
        Matcher m = Pattern.compile(regex, Pattern.DOTALL | Pattern.CASE_INSENSITIVE).matcher(text);
        return m.find() ? java.util.Optional.of(m.group(1).trim()) : java.util.Optional.empty();
    }

    private FraudResult retryWithCorrection(String claimText, String previousResponse, ChatClient chatClient,
            int attempt) {
        String correctionPrompt = String.format("""
                REASONING ERROR IN PREVIOUS TASK:
                The previous output was:
                ---
                %s
                ---
                FIX THIS NOW. Analyze this claim using the STRICT format:
                THOUGHT: <reasoning>
                SCORE: <0-100>
                ANALYSIS: <summary>
                RATIONALE: <detail>
                """, previousResponse);

        String retryResponse = chatClient.prompt()
                .system("You are a Senior Fraud Auditor. Follow STRICT format.")
                .user(correctionPrompt)
                .options(ChatOptions.builder().temperature(0.0).build())
                .call()
                .content();

        return parseAndValidate(retryResponse, claimText, chatClient, attempt);
    }

    private void syncToVectorStore(Claim claim) {
        try {
            List<Document> docs = List.of(
                    new Document(claim.getSummary(), Map.of("source", "legacy_db", "claim_id", claim.getId())));
            vectorStoreManager.getStore(claim.getAiProvider()).add(docs);
        } catch (Exception e) {
            log.error("Vector Store Sync Error: {}", e.getMessage());
        }
    }
}
