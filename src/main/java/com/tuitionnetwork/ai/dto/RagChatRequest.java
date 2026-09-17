package com.tuitionnetwork.ai.dto;

import java.util.List;

public record RagChatRequest(

        String message,

        String userRole,

        List<ConversationMessage> conversationHistory

) {
}