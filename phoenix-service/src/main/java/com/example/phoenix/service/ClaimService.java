package com.example.phoenix.service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.phoenix.dto.ClaimResponse;
import com.example.phoenix.model.Claim;
import com.example.phoenix.repository.ClaimRepository;

@Service
public class ClaimService {

    private static final Logger log = LoggerFactory.getLogger(ClaimService.class);
    private final ClaimRepository claimRepository;
    private final VectorStoreManager vectorStoreManager;

    public ClaimService(ClaimRepository claimRepository, VectorStoreManager vectorStoreManager) {
        this.claimRepository = claimRepository;
        this.vectorStoreManager = vectorStoreManager;
    }

    @Transactional(readOnly = true)
    public List<ClaimResponse> getAllClaims() {
        return claimRepository.findAllByOrderByIdDesc().stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Transactional
    public ClaimResponse createClaim(String description, String aiProvider, Double aiTemperature) {
        log.info("Creating new claim with provider: {}", aiProvider);
        Claim claim = Claim.builder()
                .description(description)
                .aiProvider(aiProvider)
                .aiTemperature(aiTemperature)
                .status("OPEN")
                .createdAt(LocalDateTime.now())
                .build();

        Claim saved = claimRepository.save(claim);
        return mapToResponse(saved);
    }

    public String getCurrentAiProvider() {
        return vectorStoreManager.get().provider().name().toLowerCase();
    }

    public Double getCurrentTemperature() {
        return vectorStoreManager.get().temperature();
    }

    public void updateAiProvider(String provider, Double temperature) {
        vectorStoreManager.switchToProvider(provider, temperature);
    }

    private ClaimResponse mapToResponse(Claim claim) {
        return new ClaimResponse(
                claim.getId(),
                claim.getDescription(),
                claim.getSummary(),
                claim.getStatus(),
                claim.getAiProvider(),
                claim.getAiTemperature(),
                claim.getFraudScore() != null ? claim.getFraudScore() : -1,
                claim.getFraudAnalysis(),
                claim.getFraudRationale(),
                claim.getFraudThought(),
                claim.getCreatedAt());
    }
}
