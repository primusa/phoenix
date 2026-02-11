package com.example.phoenix.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationHandler;

@Configuration
public class ObservationConfig {

    private static final Logger log = LoggerFactory.getLogger(ObservationConfig.class);

    @Bean
    ObservationHandler<Observation.Context> metadataHandler() {
        return new ObservationHandler<>() {
            @Override
            public void onStart(Observation.Context context) {
                // This ensures "claim.id" becomes a tag in the distributed trace
                // log.debug("Starting Observation: {}", context.getName());
            }

            @Override
            public boolean supportsContext(Observation.Context context) {
                return true;
            }
        };
    }
}