package com.tuitionnetwork.ai.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;

import java.util.List;

public record ChatRequest(

        @NotBlank(message = "Message cannot be empty")
        String message,

        @Valid
        List<ConversationMessage> conversationHistory

) {
}