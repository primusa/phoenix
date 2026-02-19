package com.example.phoenix.service;

import java.util.concurrent.atomic.AtomicReference;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import com.example.phoenix.config.constant.AiProvider;

@Service
public class VectorStoreManager {

    private static final Logger log = LoggerFactory.getLogger(VectorStoreManager.class);

    public record ActiveStore(AiProvider provider, Double temperature, VectorStore store) {
    }

    private final AtomicReference<ActiveStore> currentActiveStore = new AtomicReference<>();

    private final VectorStore ollamaStore;
    private final VectorStore geminiStore;
    private final VectorStore openaiStore;

    public VectorStoreManager(
            @Qualifier("ollamaVectorStore") VectorStore ollamaStore,
            @Qualifier("geminiVectorStore") VectorStore geminiStore,
            @Qualifier("openaiVectorStore") VectorStore openaiStore) {
        this.ollamaStore = ollamaStore;
        this.geminiStore = geminiStore;
        this.openaiStore = openaiStore;
        // Set default
        log.info("Initializing VectorStoreManager with Ollama as default provider.");
        this.currentActiveStore.set(new ActiveStore(AiProvider.OLLAMA, 0.3, ollamaStore));
    }

    public void switchToProvider(String providerName, Double temperature) {
        log.info("Attempting to switch active AI provider to: {} and temperature to: {}", providerName,
                temperature);
        // This is the atomic transformation
        currentActiveStore.updateAndGet(current -> {
            try {
                AiProvider nextProvider = AiProvider.valueOf(providerName.toUpperCase());

                // If it's already set to this, do nothing
                if (current.provider() == nextProvider) {
                    log.debug("Already using provider: {} and temperature: {}", nextProvider, temperature);
                    return current;
                }

                VectorStore nextStore = lookupStore(nextProvider);
                log.info("Successfully switched strategy to: {} and temperature to: {}", nextProvider, temperature);
                return new ActiveStore(nextProvider, temperature, nextStore);
            } catch (IllegalArgumentException e) {
                log.warn("Invalid AI provider requested: {}. Keeping current provider: {} and temperature: {}",
                        providerName, current.provider(), current.temperature());
                return current;
            }
        });
    }

    public VectorStore lookupStore(AiProvider provider) {
        return switch (provider) {
            case OLLAMA -> ollamaStore;
            case GEMINI -> geminiStore;
            case OPENAI -> openaiStore;
        };
    }

    public VectorStore getStore(String providerName) {
        try {
            AiProvider provider = AiProvider.valueOf(providerName.toUpperCase());
            return lookupStore(provider);
        } catch (Exception e) {
            log.warn("Invalid provider: {}. Using fallback/current default.", providerName);
            return currentActiveStore.get().store();
        }
    }

    public ActiveStore get() {
        return currentActiveStore.get();
    }
}