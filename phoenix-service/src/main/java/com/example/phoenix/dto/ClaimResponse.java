package com.example.phoenix.dto;

import java.time.LocalDateTime;

public record ClaimResponse(
        Long id,
        String description,
        String summary,
        String status,
        String aiProvider,
        Double aiTemperature,
        Integer fraudScore,
        String fraudAnalysis,
        String fraudRationale,
        String fraudThought,
        LocalDateTime createdAt) {
}
