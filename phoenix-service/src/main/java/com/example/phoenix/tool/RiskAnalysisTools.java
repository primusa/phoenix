package com.example.phoenix.tool;

import com.example.phoenix.service.VectorStoreManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

/**
 * AI Tool definitions for Agentic RAG.
 * These functions are exposed to the AI Model as tools it can call
 * autonomously.
 */
@Component
public class RiskAnalysisTools {

    private static final Logger log = LoggerFactory.getLogger(RiskAnalysisTools.class);
    private final VectorStoreManager vectorStoreManager;

    public RiskAnalysisTools(VectorStoreManager vectorStoreManager) {
        this.vectorStoreManager = vectorStoreManager;
    }

    @Tool(description = "Search the historical insurance claims database for similar cases to provide context for fraud detection.")
    public String historicalClaimSearch(String query, String provider) {
        log.info("Agent Tool triggered: Searching for similar claims with query: '{}' using provider: '{}'",
                query, provider);

        try {
            List<Document> docs = vectorStoreManager.getStore(provider)
                    .similaritySearch(SearchRequest.builder()
                            .query(query)
                            .topK(3)
                            .build());

            if (docs.isEmpty()) {
                return "No similar historical claims found.";
            }

            return docs.stream()
                    .map(Document::getText)
                    .collect(Collectors.joining("\n---\n"));

        } catch (Exception e) {
            log.error("Tool execution failed: {}", e.getMessage());
            return "Error retrieving historical context: " + e.getMessage();
        }
    }
}
