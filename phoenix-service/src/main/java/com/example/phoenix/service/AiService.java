package com.example.phoenix.service;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.vertexai.gemini.VertexAiGeminiChatModel;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Service;

@Service
public class AiService {

    private final ApplicationContext applicationContext;

    public AiService(ApplicationContext applicationContext) {
        this.applicationContext = applicationContext;
    }

    public ChatClient getChatClient(String providerName) {
        ChatModel chatModel;
        try {
            if ("openai".equalsIgnoreCase(providerName)) {
                chatModel = applicationContext.getBean(OpenAiChatModel.class);
            } else if ("gemini".equalsIgnoreCase(providerName)) {
                chatModel = applicationContext.getBean(VertexAiGeminiChatModel.class);
            } else {
                chatModel = applicationContext.getBean(OllamaChatModel.class);
            }
            return ChatClient.create(chatModel);
        } catch (Exception e) {
            throw new RuntimeException("AI Provider " + providerName + " not configured correctly.", e);
        }
    }
}
