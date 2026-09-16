package com.tuitionnetwork.ai.web;

import com.tuitionnetwork.ai.dto.ChatRequest;
import com.tuitionnetwork.ai.dto.ChatResponse;
import com.tuitionnetwork.ai.dto.HealthResponse;
import com.tuitionnetwork.ai.service.ChatService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/chat")
public class ChatController {

    private final ChatService chatService;

    public ChatController(ChatService chatService) {
        this.chatService = chatService;
    }

    @PostMapping
    public ResponseEntity<ChatResponse> chat(
            @Valid @RequestBody ChatRequest request) {

        return ResponseEntity.ok(
                chatService.chat(request)
        );
    }

    @GetMapping("/health")
    public ResponseEntity<HealthResponse> health() {

        return ResponseEntity.ok(
                new HealthResponse(
                        "ok",
                        "cib-assistant"
                )
        );
    }
}