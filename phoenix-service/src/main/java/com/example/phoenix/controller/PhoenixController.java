package com.example.phoenix.controller;

import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;

import com.example.phoenix.service.ClaimModernizationService;

import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;

@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "*") // Allow React app
public class PhoenixController {

    private static final Logger log = LoggerFactory.getLogger(PhoenixController.class);

    @Value("${grafana.internal.url:http://lgtm:3000}")
    private String grafanaUrl;

    private final JdbcTemplate jdbcTemplate;
    private final ClaimModernizationService modernizationService;
    private final ObservationRegistry observationRegistry;

    public PhoenixController(JdbcTemplate jdbcTemplate,
            ClaimModernizationService modernizationService,
            ObservationRegistry observationRegistry) {
        this.jdbcTemplate = jdbcTemplate;
        this.modernizationService = modernizationService;
        this.observationRegistry = observationRegistry;
    }

    @GetMapping("/claims")
    public List<Map<String, Object>> getClaims() {
        log.trace("GET /api/claims called");
        return jdbcTemplate.queryForList("SELECT * FROM claims ORDER BY id DESC");
    }

    @PostMapping("/claims")
    public Map<String, Object> createClaim(@RequestBody Map<String, Object> payload) {
        return Observation.createNotStarted("claim.creation", observationRegistry)
                .observe(() -> {
                    String description = (String) payload.get("description");
                    String aiProvider = (String) payload.get("aiProvider");
                    Object tempObj = payload.get("aiTemperature");
                    Double aiTemperature = null;

                    if (tempObj != null) {
                        aiTemperature = Double.valueOf(tempObj.toString());
                    }

                    log.info("POST /api/claims called with description: {}, provider: {}, temp: {}",
                            description, aiProvider, aiTemperature);

                    jdbcTemplate.update(
                            "INSERT INTO claims (description, status, ai_provider, ai_temperature) VALUES (?, 'OPEN', ?, ?)",
                            description, aiProvider, aiTemperature);
                    return Map.of("status", "success");
                });
    }

    @GetMapping("/config/ai-provider")
    public Map<String, String> getAiProvider() {
        String provider = modernizationService.getAiProvider();
        Double temperature = modernizationService.getTemperature();
        log.trace("GET /api/config/ai-provider returned: {}", provider, temperature);
        return Map.of("provider", provider, "temperature", temperature.toString());
    }

    @PostMapping("/config/ai-provider")
    public Map<String, String> setAiProvider(@RequestBody Map<String, String> payload) {
        String provider = payload.get("provider");
        String temperatureString = payload.get("temperature");

        Double temperature = validateAndGetTemperature(temperatureString);
        log.info("POST /api/config/ai-provider called to set provider to: {} and temperature to: {}", provider,
                temperature);
        modernizationService.setAiProvider(provider, temperature);
        return Map.of("status", "success", "provider", provider);
    }

    private Double validateAndGetTemperature(String temperatureString) {
        try {
            if (temperatureString == null) {
                temperatureString = "0.3";
            }
            Double temperature = Double.parseDouble(temperatureString);
            if (temperature < 0 || temperature > 1) {
                throw new IllegalArgumentException("Temperature must be between 0 and 1");
            }
            return temperature;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Temperature must be a valid double value");
        }
    }

    @GetMapping("/monitoring/dashboards")
    public ResponseEntity<String> getDashboards() {
        RestTemplate restTemplate = new RestTemplate();
        // The backend talks to Grafana over the INTERNAL docker network
        return restTemplate.getForEntity(grafanaUrl + "/api/search", String.class);
    }
}
