package com.example.phoenix.config;

import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.pgvector.PgVectorStore;
import org.springframework.ai.vectorstore.weaviate.WeaviateVectorStore;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

import io.weaviate.client.Config;
import io.weaviate.client.WeaviateClient;

@Configuration
public class VectorStoreConfig {

    private static final Logger log = LoggerFactory.getLogger(VectorStoreConfig.class);

    // --- Weaviate Client ---

    @Bean
    @ConditionalOnProperty(name = "phoenix.vector-store.mode", havingValue = "weaviate", matchIfMissing = true)
    public WeaviateClient weaviateClient(@Value("${spring.ai.vectorstore.weaviate.host:localhost:8080}") String host) {
        log.info("Creating manual WeaviateClient for host: {}", host);
        Config config = new Config("http", host);
        return new WeaviateClient(config);
    }

    // --- Weaviate Implementation ---

    @Bean
    @Qualifier("ollamaVectorStore")
    @ConditionalOnProperty(name = "phoenix.vector-store.mode", havingValue = "weaviate", matchIfMissing = true)
    public VectorStore ollamaWeaviateStore(
            WeaviateClient weaviateClient,
            @Qualifier("ollamaEmbeddingModel") EmbeddingModel embeddingModel) {
        log.info("Configuring Ollama VectorStore with Weaviate");
        return WeaviateVectorStore.builder(weaviateClient, embeddingModel).build();
    }

    @Bean
    @Qualifier("geminiVectorStore")
    @ConditionalOnProperty(name = "phoenix.vector-store.mode", havingValue = "weaviate", matchIfMissing = true)
    public VectorStore geminiWeaviateStore(
            WeaviateClient weaviateClient,
            @Qualifier("textEmbedding") EmbeddingModel embeddingModel) {
        log.info("Configuring Gemini VectorStore with Weaviate");
        return WeaviateVectorStore.builder(weaviateClient, embeddingModel).build();
    }

    @Bean
    @Qualifier("openaiVectorStore")
    @ConditionalOnProperty(name = "phoenix.vector-store.mode", havingValue = "weaviate", matchIfMissing = true)
    public VectorStore openaiWeaviateStore(
            WeaviateClient weaviateClient,
            @Qualifier("openAiEmbeddingModel") EmbeddingModel embeddingModel) {
        log.info("Configuring OpenAI VectorStore with Weaviate");
        return WeaviateVectorStore.builder(weaviateClient, embeddingModel).build();
    }

    // --- PGVector Implementation ---

    @Bean
    @Qualifier("ollamaVectorStore")
    @ConditionalOnProperty(name = "phoenix.vector-store.mode", havingValue = "pgvector")
    public VectorStore ollamaPgVectorStore(
            JdbcTemplate jdbcTemplate,
            @Qualifier("ollamaEmbeddingModel") EmbeddingModel embeddingModel) {
        log.info("Configuring Ollama VectorStore with PGVector (768 dim)");
        return PgVectorStore.builder(jdbcTemplate, embeddingModel)
                .dimensions(768)
                .initializeSchema(true)
                .build();
    }

    @Bean
    @Qualifier("geminiVectorStore")
    @ConditionalOnProperty(name = "phoenix.vector-store.mode", havingValue = "pgvector")
    public VectorStore geminiPgVectorStore(
            JdbcTemplate jdbcTemplate,
            @Qualifier("textEmbedding") EmbeddingModel embeddingModel) {
        log.info("Configuring Gemini VectorStore with PGVector (768 dim)");
        return PgVectorStore.builder(jdbcTemplate, embeddingModel)
                .dimensions(768)
                .initializeSchema(true)
                .build();
    }

    @Bean
    @Qualifier("openaiVectorStore")
    @ConditionalOnProperty(name = "phoenix.vector-store.mode", havingValue = "pgvector")
    public VectorStore openaiPgVectorStore(
            JdbcTemplate jdbcTemplate,
            @Qualifier("openAiEmbeddingModel") EmbeddingModel embeddingModel) {
        log.info("Configuring OpenAI VectorStore with PGVector (1536 dim)");
        return PgVectorStore.builder(jdbcTemplate, embeddingModel)
                .dimensions(1536)
                .initializeSchema(true)
                .build();
    }

    @Bean
    @ConditionalOnProperty(name = "phoenix.vector-store.mode", havingValue = "weaviate", matchIfMissing = true)
    public CommandLineRunner initializeWeaviateSchema(
            @Qualifier("ollamaVectorStore") VectorStore ollamaStore,
            @Qualifier("geminiVectorStore") VectorStore geminiStore,
            @Qualifier("openaiVectorStore") VectorStore openaiStore) {
        return args -> {
            log.info("Initializing Weaviate Schemas (Retry logic enabled)...");
            List<VectorStore> stores = List.of(ollamaStore, geminiStore, openaiStore);

            for (int i = 0; i < 3; i++) {
                try {
                    for (VectorStore store : stores) {
                        if (store instanceof org.springframework.beans.factory.InitializingBean ib) {
                            ib.afterPropertiesSet();
                        }
                    }
                    log.info("Weaviate Schemas initialized successfully on attempt {}", i + 1);
                    return; // Success
                } catch (Exception e) {
                    log.warn("Attempt {} to initialize Weaviate failed: {}. Retrying in 5s...", i + 1, e.getMessage());
                    try {
                        Thread.sleep(5000);
                    } catch (InterruptedException ignored) {
                    }
                }
            }
            log.error("Failed to initialize Weaviate Schema after all retries.");
        };
    }

    @Bean
    public CommandLineRunner printEmbeddingModels(ApplicationContext ctx) {
        return args -> {
            log.info("====================================================");
            log.info("  VERIFYING LOADED EMBEDDING MODELS (2026)");
            log.info("====================================================");

            // Fetch all beans that implement the EmbeddingModel interface
            Map<String, EmbeddingModel> models = ctx.getBeansOfType(EmbeddingModel.class);

            if (models.isEmpty()) {
                log.warn(" NO EMBEDDING MODELS FOUND!");
                log.warn("Check your pom.xml starters and application.yml properties.");
            } else {
                models.forEach((name, bean) -> {
                    log.info(" Found Model: [{}]", name);
                    log.info("   Implementation: {}", bean.getClass().getSimpleName());
                });
            }
            log.info("====================================================");
        };
    }
}
