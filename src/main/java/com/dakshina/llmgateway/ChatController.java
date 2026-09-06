package com.dakshina.llmgateway;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1")
public class ChatController {

    private final AnthropicClient anthropicClient;

    public ChatController(AnthropicClient anthropicClient) {
        this.anthropicClient = anthropicClient;
    }

    @PostMapping("/chat")
    public ChatResponse chat(@RequestBody ChatRequest request) {
        String text = anthropicClient.complete(request.prompt());
        return new ChatResponse(text);
    }
}