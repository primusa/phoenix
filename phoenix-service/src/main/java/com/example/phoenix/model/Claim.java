package com.example.phoenix.model;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "claims")
public class Claim {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(columnDefinition = "TEXT")
    private String summary;

    private String status = "OPEN";

    @Column(name = "ai_provider")
    private String aiProvider;

    @Column(name = "ai_temperature")
    private Double aiTemperature;

    @Column(name = "fraud_score")
    private Integer fraudScore;

    @Column(name = "fraud_analysis", columnDefinition = "TEXT")
    private String fraudAnalysis;

    @Column(name = "fraud_rationale", columnDefinition = "TEXT")
    private String fraudRationale;

    @Column(name = "fraud_thought", columnDefinition = "TEXT")
    private String fraudThought;

    @Column(name = "created_at")
    private LocalDateTime createdAt = LocalDateTime.now();

    public Claim() {
    }

    public Claim(Long id, String description, String summary, String status, String aiProvider, Double aiTemperature,
            Integer fraudScore, String fraudAnalysis, String fraudRationale, String fraudThought,
            LocalDateTime createdAt) {
        this.id = id;
        this.description = description;
        this.summary = summary;
        this.status = status;
        this.aiProvider = aiProvider;
        this.aiTemperature = aiTemperature;
        this.fraudScore = fraudScore;
        this.fraudAnalysis = fraudAnalysis;
        this.fraudRationale = fraudRationale;
        this.fraudThought = fraudThought;
        this.createdAt = createdAt;
    }

    public static ClaimBuilder builder() {
        return new ClaimBuilder();
    }

    // Getters
    public Long getId() {
        return id;
    }

    public String getDescription() {
        return description;
    }

    public String getSummary() {
        return summary;
    }

    public String getStatus() {
        return status;
    }

    public String getAiProvider() {
        return aiProvider;
    }

    public Double getAiTemperature() {
        return aiTemperature;
    }

    public Integer getFraudScore() {
        return fraudScore;
    }

    public String getFraudAnalysis() {
        return fraudAnalysis;
    }

    public String getFraudRationale() {
        return fraudRationale;
    }

    public String getFraudThought() {
        return fraudThought;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    // Setters
    public void setId(Long id) {
        this.id = id;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public void setSummary(String summary) {
        this.summary = summary;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public void setAiProvider(String aiProvider) {
        this.aiProvider = aiProvider;
    }

    public void setAiTemperature(Double aiTemperature) {
        this.aiTemperature = aiTemperature;
    }

    public void setFraudScore(Integer fraudScore) {
        this.fraudScore = fraudScore;
    }

    public void setFraudAnalysis(String fraudAnalysis) {
        this.fraudAnalysis = fraudAnalysis;
    }

    public void setFraudRationale(String fraudRationale) {
        this.fraudRationale = fraudRationale;
    }

    public void setFraudThought(String fraudThought) {
        this.fraudThought = fraudThought;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public static class ClaimBuilder {
        private Long id;
        private String description;
        private String summary;
        private String status = "OPEN";
        private String aiProvider;
        private Double aiTemperature;
        private Integer fraudScore;
        private String fraudAnalysis;
        private String fraudRationale;
        private String fraudThought;
        private LocalDateTime createdAt = LocalDateTime.now();

        public ClaimBuilder id(Long id) {
            this.id = id;
            return this;
        }

        public ClaimBuilder description(String description) {
            this.description = description;
            return this;
        }

        public ClaimBuilder summary(String summary) {
            this.summary = summary;
            return this;
        }

        public ClaimBuilder status(String status) {
            this.status = status;
            return this;
        }

        public ClaimBuilder aiProvider(String aiProvider) {
            this.aiProvider = aiProvider;
            return this;
        }

        public ClaimBuilder aiTemperature(Double aiTemperature) {
            this.aiTemperature = aiTemperature;
            return this;
        }

        public ClaimBuilder fraudScore(Integer fraudScore) {
            this.fraudScore = fraudScore;
            return this;
        }

        public ClaimBuilder fraudAnalysis(String fraudAnalysis) {
            this.fraudAnalysis = fraudAnalysis;
            return this;
        }

        public ClaimBuilder fraudRationale(String fraudRationale) {
            this.fraudRationale = fraudRationale;
            return this;
        }

        public ClaimBuilder fraudThought(String fraudThought) {
            this.fraudThought = fraudThought;
            return this;
        }

        public ClaimBuilder createdAt(LocalDateTime createdAt) {
            this.createdAt = createdAt;
            return this;
        }

        public Claim build() {
            return new Claim(id, description, summary, status, aiProvider, aiTemperature, fraudScore, fraudAnalysis,
                    fraudRationale, fraudThought, createdAt);
        }
    }
}
