package com.example.phoenix.service;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
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

    public ClaimModernizationService(ApplicationContext applicationContext,
            VectorStoreManager vectorStoreManager,
            JdbcTemplate jdbcTemplate,
            ObservationRegistry observationRegistry) {
        this.applicationContext = applicationContext;
        this.vectorStoreManager = vectorStoreManager;
        this.jdbcTemplate = jdbcTemplate;
        this.observationRegistry = observationRegistry;
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

    private ChatClient getChatClient() {
        ChatModel chatModel;
        AiProvider provider = this.vectorStoreManager.get().provider();

        try {
            if (provider == AiProvider.OPENAI) {
                chatModel = applicationContext.getBean(OpenAiChatModel.class);
            } else if (provider == AiProvider.GEMINI) {
                chatModel = applicationContext.getBean(VertexAiGeminiChatModel.class);
            } else {
                // Default to Ollama
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

        // 1. Capture the parent observation from the Kafka consumer thread
        // Micrometer Tracing automatically populates this from Kafka headers
        Observation listenerObs = observationRegistry.getCurrentObservation();

        CompletableFuture.runAsync(() -> {
            // 2. Open the scope within the async task using the parent reference
            Observation enrichmentObs = Observation.createNotStarted("claim.enrichment", observationRegistry)
                    .parentObservation(listenerObs) // Explicitly link to the Kafka trace
                    .lowCardinalityKeyValue("ai.provider", getAiProvider())
                    .lowCardinalityKeyValue("thread.type", "async");

            enrichmentObs.observe(() -> {
                try {
                    log.debug("Received Kafka message length: {}", message != null ? message.length() : 0);
                    JsonNode rootNode = objectMapper.readTree(message);
                    JsonNode payloadNode = rootNode.has("payload") ? rootNode.get("payload") : rootNode;

                    if (!payloadNode.has("after") || payloadNode.get("after").isNull()) {
                        return;
                    }

                    JsonNode after = payloadNode.get("after");
                    int claimId = after.get("id").asInt();
                    String rawDescription = after.get("description").asText();

                    // Add metadata to the trace span
                    enrichmentObs.lowCardinalityKeyValue("claim.id", String.valueOf(claimId));

                    if (after.has("summary") && !after.get("summary").isNull()
                            && !after.get("summary").asText().isEmpty()) {
                        log.debug("Claim {} already has a summary. Skipping.", claimId);
                        return;
                    }

                    log.info("Processing enrichment for claim ID: {}. Model: {}", claimId, getAiProvider());

                    // AI Enrichment with Trace Context
                    String summary = getChatClient().prompt()
                            .user("Summarize this insurance claim in 1 sentence: " + rawDescription)
                            .options(OllamaChatOptions.builder()
                                    .temperature(getTemperature())
                                    .build())
                            .call()
                            .content();

                    // 1. Update Legacy DB
                    this.jdbcTemplate.update("UPDATE claims SET summary = ? WHERE id = ?", summary, claimId);
                    log.info("Legacy DB updated for claim ID: {}", claimId);

                    // 2. Save to Vector DB
                    try {
                        List<Document> docs = List.of(
                                new Document(summary, Map.of("source", "legacy_db", "claim_id", claimId)));
                        this.vectorStoreManager.get().store().add(docs);

                        // Explicitly log the Vector Sync event for Jaeger
                        enrichmentObs.event(Observation.Event.of("vector.store.sync"));
                        log.info("Saved vector for claim ID: {} via {}", claimId, getAiProvider());
                    } catch (Exception ve) {
                        log.error("Vector Store Error: {}", ve.getMessage());
                    }

                    // Final Success Event
                    enrichmentObs.event(Observation.Event.of("pipeline.complete"));

                } catch (Exception e) {
                    enrichmentObs.error(e); // Attach error to Jaeger trace
                    log.error("Critical error processing claim {}: {}", message, e.getMessage());
                }
            });
        });
    }
}
