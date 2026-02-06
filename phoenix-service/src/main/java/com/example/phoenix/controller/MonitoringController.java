package com.example.phoenix.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

@RestController
@RequestMapping("/api/monitoring")
public class MonitoringController {

    private static final Logger log = LoggerFactory.getLogger(MonitoringController.class);

    @Value("${grafana.internal.url:http://lgtm:3000}")
    private String grafanaUrl;

    @GetMapping("/dashboards")
    public ResponseEntity<String> getDashboards() {
        RestTemplate restTemplate = new RestTemplate();
        try {
            // The backend talks to Grafana over the INTERNAL docker network
            return restTemplate.getForEntity(grafanaUrl + "/api/search", String.class);
        } catch (RestClientException e) {
            log.warn("Failed to reach Grafana at {}: {}", grafanaUrl, e.getMessage());
            return ResponseEntity.status(502).body("Failed to fetch dashboards");
        }
    }
}