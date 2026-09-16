package com.tuitionnetwork.ai.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record ConversationMessage(

        @NotBlank
        @Pattern(
                regexp = "user|assistant",
                message = "Role must be either user or assistant"
        )
        String role,

        @NotBlank
        String content

) {
}