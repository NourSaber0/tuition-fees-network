package com.tuitionnetwork.ai.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tuitionnetwork.ai.dto.ChatRequest;
import com.tuitionnetwork.ai.dto.ChatResponse;
import com.tuitionnetwork.ai.dto.RagChatRequest;
import com.tuitionnetwork.ai.dto.RagChatResponse;

import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

@Service
public class ChatService {

    private final RestClient ragRestClient;
    private final CurrentUserService currentUserService;
    private final ObjectMapper objectMapper;

    public ChatService(
            RestClient ragRestClient,
            CurrentUserService currentUserService,
            ObjectMapper objectMapper) {

        this.ragRestClient = ragRestClient;
        this.currentUserService = currentUserService;
        this.objectMapper = objectMapper;
    }

    public ChatResponse chat(ChatRequest request) {

        String role = currentUserService.getCurrentRole();

        RagChatRequest ragRequest = new RagChatRequest(
                request.message(),
                role,
                request.conversationHistory()
        );

        try {
            String jsonBody = objectMapper.writeValueAsString(ragRequest);

            RagChatResponse ragResponse = ragRestClient
                    .post()
                    .uri("/rag/chat")
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .body(jsonBody)
                    .retrieve()
                    .body(RagChatResponse.class);

            if (ragResponse == null) {
                throw new IllegalStateException(
                        "Empty response from RAG service"
                );
            }

            return new ChatResponse(
                    ragResponse.answer(),
                    ragResponse.source(),
                    ragResponse.confidence(),
                    ragResponse.navPage(),
                    ragResponse.navLabel()
            );

        } catch (JsonProcessingException e) {
            throw new IllegalStateException(
                    "Failed to serialize RAG request",
                    e
            );
        }
    }
}