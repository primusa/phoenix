package com.example.phoenix.service;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.document.Document;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.ai.ollama.api.OllamaChatOptions;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vertexai.gemini.VertexAiGeminiChatModel;
import org.springframework.context.ApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Service;

import com.example.phoenix.config.constant.AiProvider;
import com.example.phoenix.model.FraudResult;
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

    public ClaimModernizationService(ApplicationContext applicationContext,
            VectorStoreManager vectorStoreManager,
            JdbcTemplate jdbcTemplate,
            ObservationRegistry observationRegistry,
            GovernanceService governanceService) {
        this.applicationContext = applicationContext;
        this.vectorStoreManager = vectorStoreManager;
        this.jdbcTemplate = jdbcTemplate;
        this.observationRegistry = observationRegistry;
        this.governanceService = governanceService;
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
                            .user("Summarize this insurance claim in 1 sentence: " + sanitizedDescription)
                            .options(OllamaChatOptions.builder().temperature(claimTemperature).build())
                            .call()
                            .content();

                    // Update DB with summary immediately
                    this.jdbcTemplate.update("UPDATE claims SET summary = ? WHERE id = ?", summary, claimId);
                    log.info("Summary updated for claim ID: {}. Starting Fraud Analysis...", claimId);

                    // 2. STAGE 2: RAG Context + Fraud Analysis
                    List<Document> fetchedClaims = List.of();
                    try {
                        fetchedClaims = vectorStoreManager.getStore(claimProvider)
                                .similaritySearch(SearchRequest.builder().query(sanitizedDescription).topK(3).build());
                    } catch (Exception e) {
                        log.warn("Vector search skipped for claim {} (likely empty store or schema pending): {}",
                                claimId, e.getMessage());
                    }

                    final List<Document> similarClaims = fetchedClaims;

                    String historicalContext = similarClaims.isEmpty()
                            ? "No prior overlapping claims found."
                            : IntStream.range(0, similarClaims.size())
                                    .mapToObj(i -> "Historical Claim " + (i + 1) + ": "
                                            + similarClaims.get(i).getText())
                                    .collect(Collectors.joining("\n"));

                    FraudResult fraudResult = analyzeClaim(sanitizedDescription, historicalContext, chatClient);

                    int fraudScore = fraudResult.getScore();
                    String fraudAnalysis = fraudResult.getAnalysis();
                    String fraudRationale = fraudResult.getRationale();

                    log.info("Final Parsed Insights for {}: Score={}, Analysis={}, Rationale={}",
                            claimId, fraudScore, fraudAnalysis, fraudRationale);

                    // 3. Update DB with Fraud insights
                    this.jdbcTemplate.update(
                            "UPDATE claims SET fraud_score = ?, fraud_analysis = ?, fraud_rationale = ? WHERE id = ?",
                            fraudScore, fraudAnalysis, fraudRationale, claimId);
                    log.info("Claim {} modernized with Fraud Score: {} and Rationale", claimId, fraudScore);

                    // 4. Vector Sync
                    try {
                        List<Document> docs = List.of(
                                new Document(summary, Map.of("source", "legacy_db", "claim_id", claimId)));
                        this.vectorStoreManager.getStore(claimProvider).add(docs);
                        enrichmentObs.event(Observation.Event.of("vector.store.sync"));
                    } catch (Exception ve) {
                        log.error("Vector Store Error: {}", ve.getMessage());
                    }

                    enrichmentObs.event(Observation.Event.of("pipeline.complete"));

                } catch (Exception e) {
                    enrichmentObs.error(e);
                    log.error("Critical error processing claim {}: {}", message, e.getMessage());
                }
            });
        });
    }

    public FraudResult analyzeClaim(String claimText, String historicalText, ChatClient chatClient) {

        String systemPrompt = """
                You are an insurance fraud detection engine.

                You MUST follow the output format exactly.
                You are NOT allowed to add extra text.
                You are NOT allowed to use markdown.
                If you fail to follow the format exactly, the response is invalid.
                """;

        String userPrompt = """
                Analyze the insurance claim for fraud risk.

                Current Claim:
                """ + claimText + """

                Historical Context:
                """ + historicalText + """

                You must respond EXACTLY in this format:

                SCORE: <integer between 0 and 100>
                ANALYSIS: <one concise paragraph>
                RATIONALE: <detailed explanation>

                Rules:
                - Start directly with SCORE:
                - SCORE must be an integer only
                - No text before SCORE
                - No text after RATIONALE

                Example:

                SCORE: 78
                ANALYSIS: The claim shows moderate fraud risk due to timing inconsistencies.
                RATIONALE: The claimant delayed reporting by 45 days and previously filed two similar claims.

                Now analyze the provided claim.
                """;

        String response = chatClient.prompt()
                .system(systemPrompt)
                .user(userPrompt)
                .options(OllamaChatOptions.builder()
                        // .model("tinyllama")
                        .temperature(0.0)
                        .repeatPenalty(1.1)
                        .build())
                .call()
                .content();

        return parseAndValidate(response, claimText, historicalText, chatClient, 1);
    }

    private FraudResult parseAndValidate(String response,
            String claimText,
            String historicalText,
            ChatClient chatClient,
            int attempt) {

        log.info("Raw response: " + response);

        // 1. Try strict pattern first
        Pattern strictPattern = Pattern.compile(
                "SCORE:\\s*(\\d{1,3})\\s*ANALYSIS:\\s*(.*?)\\s*RATIONALE:\\s*(.*)",
                Pattern.DOTALL);

        Matcher strictMatcher = strictPattern.matcher(response);

        if (strictMatcher.find()) {
            try {
                int score = Integer.parseInt(strictMatcher.group(1));
                String analysis = strictMatcher.group(2).trim();
                String rationale = strictMatcher.group(3).trim();

                if (score < 0 || score > 100)
                    throw new NumberFormatException();

                return new FraudResult(score, analysis, rationale);
            } catch (Exception e) {
                // fallback to flexible parsing below
            }
        }
        // 1.2 Try strict pattern first
        Pattern strictPattern2 = Pattern.compile(
                "Scoring:\\s*(\\d{1,3})\\s*Analysis:\\s*(.*?)\\s*Rational analysis:\\s*(.*)",
                Pattern.DOTALL);

        Matcher strictMatcher2 = strictPattern2.matcher(response);

        if (strictMatcher2.find()) {
            try {
                int score = Integer.parseInt(strictMatcher2.group(1));
                String analysis = strictMatcher2.group(2).trim();
                String rationale = strictMatcher2.group(3).trim();

                if (score < 0 || score > 100)
                    throw new NumberFormatException();

                return new FraudResult(score, analysis, rationale);
            } catch (Exception e) {
                // fallback to flexible parsing below
            }
        }

        // 2. Flexible extraction for messy labels like "RAISONALE", "SCORING", etc.
        Integer extractedScore = null;
        String extractedRationale = "";
        String extractedAnalysis = "";

        // Extract score from any sentence containing "SCORE is <number>"
        Pattern scorePattern = Pattern.compile("SCORE\\s*(is)?\\s*(\\d{1,3})", Pattern.CASE_INSENSITIVE);
        Matcher scoreMatcher = scorePattern.matcher(response);
        if (scoreMatcher.find()) {
            extractedScore = Integer.parseInt(scoreMatcher.group(2));
        }

        // Extract rationale from any sentence containing "RATIONALE" or "RAISONALE"
        Pattern rationalePattern = Pattern.compile("(RATIONALE|RAISONALE)[^:\\n]*:\\s*(.*?)((\\n\\d+\\.)|$)",
                Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
        Matcher rationaleMatcher = rationalePattern.matcher(response);
        if (rationaleMatcher.find()) {
            extractedRationale = rationaleMatcher.group(2).trim();
        }

        // If rationale exists, use it as analysis too (can be refined further)
        if (!extractedRationale.isEmpty()) {
            extractedAnalysis = extractedRationale.split("\\.")[0] + "."; // first sentence as summary
        }

        // 3. Validation
        if (extractedScore != null && extractedScore >= 0 && extractedScore <= 100) {
            return new FraudResult(extractedScore, extractedAnalysis, extractedRationale);
        }

        // 4. Retry if still fails
        if (attempt < 2) {
            return retryWithCorrection(claimText, historicalText, response, chatClient, attempt + 1);
        }

        // 5. Fallback
        return new FraudResult(0,
                "AI Analysis unavailable",
                "The model failed to follow format. Raw response: " + response);
    }

    private FraudResult retryWithCorrection(
            String claimText,
            String historicalText,
            String previousResponse,
            ChatClient chatClient,
            int attempt) {

        String systemPrompt = """
                You are an insurance fraud detection engine.

                You MUST follow the output format exactly.
                You are NOT allowed to add extra text.
                You are NOT allowed to use markdown.
                If you fail to follow the format exactly, the response is invalid.
                """;

        String correctionPrompt = """
                Analyze the insurance claim for fraud risk.

                Current Claim:
                """ + claimText + """

                Historical Context:
                """ + historicalText
                + """

                        You must respond EXACTLY in this format:

                        SCORE: <integer between 0 and 100>
                        ANALYSIS: <one concise paragraph>
                        RATIONALE: <detailed explanation>

                        STRICT RULES:
                        - Start directly with SCORE:
                        - SCORE must be an integer only
                        - No text before SCORE
                        - No text after RATIONALE
                        - Do not repeat instructions
                        - Do not use markdown
                        - Do not add explanations outside the template

                        Example:

                        SCORE: 65
                        ANALYSIS: The claim presents moderate fraud indicators due to prior similar filings.
                        RATIONALE: The claimant filed two comparable damage claims within the past year and delayed reporting the current incident.

                        Now analyze the provided claim.
                        """;

        String retryResponse = chatClient.prompt()
                .system(systemPrompt)
                .user(correctionPrompt)
                .options(OllamaChatOptions.builder()
                        // .model("tinyllama")
                        .temperature(0.0)
                        .repeatPenalty(1.1)
                        .build())
                .call()
                .content();

        return parseAndValidate(retryResponse, claimText, historicalText, chatClient, attempt);
    }

}
