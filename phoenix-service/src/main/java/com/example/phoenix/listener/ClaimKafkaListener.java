package com.example.phoenix.listener;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import com.example.phoenix.service.ClaimProcessor;

@Component
public class ClaimKafkaListener {

    private static final Logger log = LoggerFactory.getLogger(ClaimKafkaListener.class);
    private final ClaimProcessor claimProcessor;

    public ClaimKafkaListener(ClaimProcessor claimProcessor) {
        this.claimProcessor = claimProcessor;
    }

    @KafkaListener(topics = "legacy.public.claims", groupId = "phoenix-modernizer")
    public void onClaimUpdate(String message) {
        log.info("Received Kafka message: {}", message);
        claimProcessor.processClaimUpdate(message);
    }
}
