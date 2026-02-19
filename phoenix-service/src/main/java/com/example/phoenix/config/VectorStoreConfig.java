package com.example.phoenix.config;

import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.weaviate.WeaviateVectorStore;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.weaviate.client.WeaviateClient;

@Configuration
public class VectorStoreConfig {

    private static final Logger log = LoggerFactory.getLogger(VectorStoreConfig.class);

    @Bean
    @Qualifier("ollamaVectorStore")
    public WeaviateVectorStore ollamaVectorStore(
            WeaviateClient weaviateClient,
            @Qualifier("ollamaEmbeddingModel") EmbeddingModel embeddingModel) {

        return WeaviateVectorStore.builder(weaviateClient, embeddingModel)
                .build();
    }

    @Bean
    @Qualifier("geminiVectorStore")
    public WeaviateVectorStore geminiVectorStore(
            WeaviateClient weaviateClient,
            @Qualifier("textEmbedding") EmbeddingModel embeddingModel) {

        return WeaviateVectorStore.builder(weaviateClient, embeddingModel)
                .build();
    }

    @Bean
    @Qualifier("openaiVectorStore")
    public WeaviateVectorStore openaiVectorStore(
            WeaviateClient weaviateClient,
            @Qualifier("openAiEmbeddingModel") EmbeddingModel embeddingModel) {

        return WeaviateVectorStore.builder(weaviateClient, embeddingModel)
                .build();
    }

    @Bean
    public CommandLineRunner initializeWeaviateSchema(
            @Qualifier("ollamaVectorStore") WeaviateVectorStore ollamaStore,
            @Qualifier("geminiVectorStore") WeaviateVectorStore geminiStore,
            @Qualifier("openaiVectorStore") WeaviateVectorStore openaiStore) {
        return args -> {
            log.info("Initializing Weaviate Schemas (Retry logic enabled)...");
            List<WeaviateVectorStore> stores = List.of(ollamaStore, geminiStore, openaiStore);

            for (int i = 0; i < 3; i++) {
                try {
                    for (WeaviateVectorStore store : stores) {
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
