package com.example.phoenix.service;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.document.Document;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.ai.ollama.api.OllamaChatOptions;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.vertexai.gemini.VertexAiGeminiChatModel;
import org.springframework.context.ApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Service;

import com.example.phoenix.config.constant.AiProvider;
import com.example.phoenix.model.FraudResult;
import com.example.phoenix.tool.RiskAnalysisTools;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;

@Service
public class ClaimModernizationService {

    private static final Logger log = LoggerFactory.getLogger(ClaimModernizationService.class);

    private final ApplicationContext applicationContext;
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private final VectorStoreManager vectorStoreManager;
    private final ObservationRegistry observationRegistry;
    private final GovernanceService governanceService;
    private final RiskAnalysisTools riskAnalysisTools;

    public ClaimModernizationService(ApplicationContext applicationContext,
            VectorStoreManager vectorStoreManager,
            JdbcTemplate jdbcTemplate,
            ObservationRegistry observationRegistry,
            GovernanceService governanceService,
            RiskAnalysisTools riskAnalysisTools) {
        this.applicationContext = applicationContext;
        this.vectorStoreManager = vectorStoreManager;
        this.jdbcTemplate = jdbcTemplate;
        this.observationRegistry = observationRegistry;
        this.governanceService = governanceService;
        this.riskAnalysisTools = riskAnalysisTools;
    }

    public void setAiProvider(String provider, Double temperature) {
        log.info("Switching AI Provider to: {}", provider);
        this.vectorStoreManager.switchToProvider(provider, temperature);
    }

    public String getAiProvider() {
        return this.vectorStoreManager.get().provider().name().toLowerCase();
    }

    public Double getTemperature() {
        return this.vectorStoreManager.get().temperature();
    }

    private ChatClient getChatClient(String providerName) {
        ChatModel chatModel;
        AiProvider provider = AiProvider.OLLAMA;

        try {
            if ("openai".equalsIgnoreCase(providerName)) {
                provider = AiProvider.OPENAI;
                chatModel = applicationContext.getBean(OpenAiChatModel.class);
            } else if ("gemini".equalsIgnoreCase(providerName)) {
                provider = AiProvider.GEMINI;
                chatModel = applicationContext.getBean(VertexAiGeminiChatModel.class);
            } else {
                chatModel = applicationContext.getBean(OllamaChatModel.class);
            }
            log.debug("ChatClient initialized with provider: {}", provider);
            return ChatClient.create(chatModel);
        } catch (Exception e) {
            log.error("Failed to load AI model for provider: {}. Error: {}", provider, e.getMessage());
            throw new RuntimeException("AI Provider " + provider + " not configured correctly.", e);
        }
    }

    @KafkaListener(topics = "legacy.public.claims")
    public void onClaimUpdate(String message, @Header(KafkaHeaders.RECEIVED_KEY) String key) {
        log.info("Kafka listener triggered for topic 'legacy.public.claims'");

        Observation listenerObs = observationRegistry.getCurrentObservation();

        CompletableFuture.runAsync(() -> {
            Observation enrichmentObs = Observation.createNotStarted("claim.enrichment", observationRegistry)
                    .parentObservation(listenerObs)
                    .lowCardinalityKeyValue("ai.provider", getAiProvider())
                    .lowCardinalityKeyValue("thread.type", "async");

            enrichmentObs.observe(() -> {
                try {
                    JsonNode rootNode = objectMapper.readTree(message);
                    JsonNode payloadNode = rootNode.has("payload") ? rootNode.get("payload") : rootNode;

                    if (!payloadNode.has("after") || payloadNode.get("after").isNull()) {
                        return;
                    }

                    JsonNode after = payloadNode.get("after");
                    int claimId = after.get("id").asInt();
                    String rawDescription = after.get("description").asText();
                    String claimProvider = after.has("ai_provider") && !after.get("ai_provider").isNull()
                            ? after.get("ai_provider").asText()
                            : getAiProvider();
                    double claimTemperature = after.has("ai_temperature") && !after.get("ai_temperature").isNull()
                            ? after.get("ai_temperature").asDouble()
                            : getTemperature();

                    enrichmentObs.lowCardinalityKeyValue("claim.id", String.valueOf(claimId));
                    enrichmentObs.lowCardinalityKeyValue("ai.provider", claimProvider);

                    if (after.has("summary") && !after.get("summary").isNull()
                            && !after.get("summary").asText().isEmpty()) {
                        return;
                    }

                    log.info("Processing enrichment and fraud detection for claim ID: {}", claimId);

                    // --- GOVERNANCE GATEWAY ---
                    // Redact PII (SSN, Email) before sending to LLMs
                    String sanitizedDescription = governanceService.redactSensitiveData(rawDescription);

                    // 1. STAGE 1: Fast Summarization
                    ChatClient chatClient = getChatClient(claimProvider);
                    String summary = chatClient.prompt()
                            .user("Summarize this insurance claim for a technical adjuster in 1 sentence: "
                                    + sanitizedDescription)
                            .options(OllamaChatOptions.builder().temperature(claimTemperature).build())
                            .call()
                            .content();

                    // Update DB with summary immediately
                    this.jdbcTemplate.update("UPDATE claims SET summary = ? WHERE id = ?", summary, claimId);
                    log.info("[Stage 1/2] AI Summary persisted for claim ID: {}. Dispatching to Agentic RAG layer...",
                            claimId);

                    log.info("Starting Agentic Fraud Analysis for claim ID: {} using provider: {}", claimId,
                            claimProvider);

                    // 2. STAGE 2: Agentic RAG Analysis
                    // The AI Agent decides autonomously if it needs historical context using its
                    // tools.
                    enrichmentObs.event(Observation.Event.of("agentic.rag.started"));
                    FraudResult fraudResult = agenticAnalyzeClaim(sanitizedDescription, chatClient, claimProvider);
                    enrichmentObs.event(Observation.Event.of("agentic.rag.complete"));

                    int fraudScore = fraudResult.getScore();
                    String fraudAnalysis = fraudResult.getAnalysis();
                    String fraudRationale = fraudResult.getRationale();
                    String fraudThought = fraudResult.getThought();

                    log.info("Final Parsed Insights for {}: Score={}, Analysis={}, Rationale={}, Thought={}",
                            claimId, fraudScore, fraudAnalysis, fraudRationale, fraudThought);

                    // 3. Update DB with Fraud insights
                    this.jdbcTemplate.update(
                            "UPDATE claims SET fraud_score = ?, fraud_analysis = ?, fraud_rationale = ?, fraud_thought = ? WHERE id = ?",
                            fraudScore, fraudAnalysis, fraudRationale, fraudThought, claimId);
                    log.info("Claim {} modernized with Fraud Score: {}, Rationale, and Thought", claimId, fraudScore);

                    // 4. Vector Sync
                    try {
                        List<Document> docs = List.of(
                                new Document(summary, Map.of("source", "legacy_db", "claim_id", claimId)));
                        this.vectorStoreManager.getStore(claimProvider).add(docs);
                        enrichmentObs.event(Observation.Event.of("agentic.vector.sync.complete"));
                    } catch (Exception ve) {
                        log.error("Vector Store Error: {}", ve.getMessage());
                    }

                    enrichmentObs.event(Observation.Event.of("agentic.pipeline.complete"));
                    log.info("[Pipeline Done] Claim {} fully processed through Agentic RAG pipeline.", claimId);

                } catch (Exception e) {
                    enrichmentObs.error(e);
                    log.error("Critical error processing claim {}: {}", message, e.getMessage());
                }
            });
        });
    }

    /**
     * Agentic RAG Assessment: The AI autonomously uses tools to gather
     * intelligence.
     */
    public FraudResult agenticAnalyzeClaim(String claimText, ChatClient chatClient, String provider) {
        log.info("Agentic Intelligence Engine: Deploying Fraud Analyst Agent...");

        // In Spring AI 2.0.x, it's safer to register functions at the client level via
        // builder
        // We obtain the underlying ChatModel from the current client if possible, or
        // use a new one

        ChatClient agenticClient = chatClient.mutate()
                .defaultTools(riskAnalysisTools)
                .defaultAdvisors(new SimpleLoggerAdvisor()) // Logs the tool-calling handshake
                .build();

        String systemPrompt = """
                You are a Senior Insurance Fraud Analyst Agent.

                MISSION:
                1. First, REASON about the claim. Look for names, amounts, and specific incident types.
                2. EVALUATE if 'historicalClaimSearch' is needed. (Hint: It is almost always needed for proper due diligence).
                3. EXECUTE the tool call using provider: %s.
                4. ANALYZE the results from the tool alongside the current claim.

                OUTPUT FORMAT (STRICT):
                THOUGHT: <your internal reasoning process regarding the tool usage>
                SCORE: <0-100>
                ANALYSIS: <concise summary of your investigation>
                RATIONALE: <detailed logic, including specifically what the historical search revealed>
                """;

        String userPrompt = "Task: Analyze this claim for potential fraud or anomalies: " + claimText;

        // Execute Agentic Loop
        String response = agenticClient.prompt()
                .system(String.format(systemPrompt, provider))
                .user(userPrompt)
                .call()
                .content();

        log.info("Agent Task Complete. Parsing intelligence report...");

        // Agent handled context (historicalText) retrieval via tools.
        return parseAndValidate(response, claimText, chatClient, 1);
    }

    private FraudResult parseAndValidate(String response,
            String claimText,
            ChatClient chatClient,
            int attempt) {

        log.info("Raw agentic response: " + response);

        // 1. Strict Pattern Matching
        Pattern agenticPattern = Pattern.compile(
                "(?:THOUGHT:\\s*(.*?)\\s*)?SCORE:\\s*(\\d{1,3})\\s*ANALYSIS:\\s*(.*?)\\s*RATIONALE:\\s*(.*)",
                Pattern.DOTALL | Pattern.CASE_INSENSITIVE);

        Matcher matcher = agenticPattern.matcher(response);

        if (matcher.find()) {
            try {
                String thought = matcher.group(1) != null ? matcher.group(1).trim() : "No internal reasoning provided.";
                int score = Integer.parseInt(matcher.group(2));
                String analysis = matcher.group(3).trim();
                String rationale = matcher.group(4).trim();

                if (score >= 0 && score <= 100) {
                    return new FraudResult(score, analysis, rationale, thought);
                }
            } catch (Exception e) {
                log.warn("Strict parsing failed, falling back to flexible extraction.");
            }
        }

        // 2. Flexible Extraction
        Integer extractedScore = null;
        String extractedThought = "";
        String extractedAnalysis = "";
        String extractedRationale = "";

        // Extract Thought
        Matcher thoughtMatcher = Pattern.compile("THOUGHT:\\s*(.*?)(?=SCORE:|ANALYSIS:|RATIONALE:|$)",
                Pattern.DOTALL | Pattern.CASE_INSENSITIVE).matcher(response);
        if (thoughtMatcher.find())
            extractedThought = thoughtMatcher.group(1).trim();

        // Extract Score
        Matcher scoreMatcher = Pattern.compile("SCORE\\s*[:\\-]?\\s*(\\d{1,3})", Pattern.CASE_INSENSITIVE)
                .matcher(response);
        if (scoreMatcher.find())
            extractedScore = Integer.parseInt(scoreMatcher.group(1));

        // Extract Analysis
        Matcher analysisMatcher = Pattern.compile("ANALYSIS:\\s*(.*?)(?=RATIONALE:|SCORE:|THOUGHT:|$)",
                Pattern.DOTALL | Pattern.CASE_INSENSITIVE).matcher(response);
        if (analysisMatcher.find())
            extractedAnalysis = analysisMatcher.group(1).trim();

        // Extract Rationale
        Matcher rationaleMatcher = Pattern.compile("RATIONALE:\\s*(.*)",
                Pattern.DOTALL | Pattern.CASE_INSENSITIVE).matcher(response);
        if (rationaleMatcher.find())
            extractedRationale = rationaleMatcher.group(1).trim();

        // 3. Validation and Fallbacks
        if (extractedScore != null && extractedScore >= 0 && extractedScore <= 100) {
            if (extractedAnalysis.isEmpty())
                extractedAnalysis = "See rationale for summary.";
            if (extractedThought.isEmpty())
                extractedThought = "Agent skipped explicit reasoning.";

            return new FraudResult(extractedScore, extractedAnalysis, extractedRationale, extractedThought);
        }

        // 4. Retry
        if (attempt < 2) {
            return retryWithCorrection(claimText, response, chatClient, attempt + 1);
        }

        return new FraudResult(0, "Error", "Failed to parse format.", "Analysis failed after retries.");
    }

    private FraudResult retryWithCorrection(
            String claimText,
            String previousResponse,
            ChatClient chatClient,
            int attempt) {

        // Directing the AI to look at its own mistake
        String systemPrompt = """
                You are a Senior Fraud Auditor. Your previous response was rejected because it did not follow the STRICT output headers.

                CORRECTION RULES:
                - You MUST include the 'THOUGHT:' header first.
                - You MUST follow with 'SCORE:', 'ANALYSIS:', and 'RATIONALE:'.
                - Do not use markdown (no bold, no backticks).
                - Do not include any introductory text like "Sure, here is the report".
                """;

        String correctionPrompt = String.format("""
                REASONING ERROR IN PREVIOUS TASK:
                The previous output was:
                ---
                %s
                ---

                FIX THIS NOW.
                Analyze this claim: %s

                Format your response exactly as:
                THOUGHT: <your reasoning>
                SCORE: <0-100>
                ANALYSIS: <summary>
                RATIONALE: <detail>
                """, previousResponse, claimText);

        String retryResponse = chatClient.prompt()
                .system(systemPrompt)
                .user(correctionPrompt)
                .options(ChatOptions.builder() // Use the static builder on the interface
                        .temperature(0.0)
                        .build())
                .call()
                .content();

        // Passing an empty string for historicalText because in Agentic RAG,
        // the agent is responsible for its own context retrieval via tools.
        return parseAndValidate(retryResponse, claimText, chatClient, attempt);
    }

}
