package com.example.phoenix.config;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.logback.appender.v1_0.OpenTelemetryAppender;
import org.springframework.context.annotation.Configuration;

import jakarta.annotation.PostConstruct;

@Configuration
public class OtelConfig {

    private final OpenTelemetry openTelemetry;

    public OtelConfig(OpenTelemetry openTelemetry) {
        this.openTelemetry = openTelemetry;
    }

    @PostConstruct
    public void setup() {
        OpenTelemetryAppender.install(openTelemetry);
    }
}
