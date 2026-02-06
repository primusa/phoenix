package com.example.phoenix.config;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
class ModelConfig {

    // To Save memory
    @Bean
    Executor ollamaExecutor() {
        return Executors.newSingleThreadExecutor();
    }
}