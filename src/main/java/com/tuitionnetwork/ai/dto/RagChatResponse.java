package com.tuitionnetwork.ai.dto;

public record RagChatResponse(

        String answer,

        String source,

        Double confidence,

        String navPage,

        String navLabel

) {
}