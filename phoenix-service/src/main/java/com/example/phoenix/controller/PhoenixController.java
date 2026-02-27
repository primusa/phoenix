package com.example.phoenix.controller;

import com.example.phoenix.dto.ClaimResponse;
import com.example.phoenix.service.ClaimService;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "*")
public class PhoenixController {

    private static final Logger log = LoggerFactory.getLogger(PhoenixController.class);
    
    private final ClaimService claimService;
    private final ObservationRegistry observationRegistry;

    @Value("${grafana.internal.url:http://lgtm:3000}")
    private String grafanaUrl;

    public PhoenixController(ClaimService claimService, ObservationRegistry observationRegistry) {
        this.claimService = claimService;
        this.observationRegistry = observationRegistry;
    }

    @GetMapping("/claims")
    public List<ClaimResponse> getClaims() {
        log.trace("GET /api/claims called");
        return claimService.getAllClaims();
    }

    @PostMapping("/claims")
    public ClaimResponse createClaim(@RequestBody Map<String, Object> payload) {
        return Observation.createNotStarted("claim.creation", observationRegistry)
                .observe(() -> {
                    String description = (String) payload.get("description");
                    String aiProvider = (String) payload.get("aiProvider") != null ? 
                            (String) payload.get("aiProvider") : "ollama";
                    Double aiTemperature = payload.get("aiTemperature") != null ? 
                            Double.valueOf(payload.get("aiTemperature").toString()) : 0.3;

                    log.info("POST /api/claims called with description length: {}, provider: {}", 
                            description != null ? description.length() : 0, aiProvider);
                            
                    return claimService.createClaim(description, aiProvider, aiTemperature);
                });
    }

    @GetMapping("/config/ai-provider")
    public Map<String, Object> getAiProvider() {
        String provider = claimService.getCurrentAiProvider();
        Double temperature = claimService.getCurrentTemperature();
        return Map.of(
                "provider", provider,
                "temperature", temperature
        );
    }

    @PostMapping("/config/ai-provider")
    public Map<String, String> setAiProvider(@RequestBody Map<String, String> payload) {
        String provider = payload.get("provider");
        String temperatureStr = payload.getOrDefault("temperature", "0.3");
        Double temperature = Double.valueOf(temperatureStr);
        
        log.info("Setting AI provider to: {} with temperature: {}", provider, temperature);
        claimService.updateAiProvider(provider, temperature);
        return Map.of("status", "success", "provider", provider);
    }

    @GetMapping("/monitoring/dashboards")
    public ResponseEntity<String> getDashboards() {
        RestTemplate restTemplate = new RestTemplate();
        // The backend talks to Grafana over the INTERNAL docker network
        return restTemplate.getForEntity(grafanaUrl + "/api/search", String.class);
    }
}
