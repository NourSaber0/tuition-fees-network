package com.tuitionnetwork.ai.dto;

public record ChatResponse(

        String answer,

        String source,

        Double confidence,

        String navPage,

        String navLabel

) {
}