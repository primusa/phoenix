package com.example.phoenix.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.ApplicationContext;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE) // <--- Runs before all other runners
public class EarlyBeanPrinter implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(EarlyBeanPrinter.class);

    private final ApplicationContext ctx;

    public EarlyBeanPrinter(ApplicationContext ctx) {
        this.ctx = ctx;
    }

    @Override
    public void run(String... args) {
        log.info("Starting Bean Verification...");
        String[] modelNames = ctx.getBeanNamesForType(EmbeddingModel.class);

        if (modelNames.length == 0) {
            log.warn("No EmbeddingModel beans found!");
        } else {
            for (String name : modelNames) {
                log.info("Found Embedding Bean: {}", name);
            }
        }
    }
}