package com.dakshina.llmgateway;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/v1")
public class ChatController {

    private final AnthropicClient anthropicClient;
    private final ResponseCache cache;
    private final UsageTracker usageTracker;

    public ChatController(AnthropicClient anthropicClient,
                          ResponseCache cache,
                          UsageTracker usageTracker) {
        this.anthropicClient = anthropicClient;
        this.cache = cache;
        this.usageTracker = usageTracker;
    }

    @PostMapping("/chat")
    public ChatResponse chat(@RequestBody ChatRequest request,
                             @RequestHeader("X-Gateway-Key") String key) {

        if (request.prompt() == null || request.prompt().isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "prompt must not be empty");
        }

        CompletionResult cached = cache.get(request.prompt());
        if (cached != null) {
            return new ChatResponse(cached.text());
        }

        CompletionResult result = anthropicClient.complete(request.prompt());
        cache.put(request.prompt(), result);
        usageTracker.record(key, result.inputTokens(), result.outputTokens());

        return new ChatResponse(result.text());
    }

    @GetMapping("/usage")
    public UsageTracker.Summary usage(@RequestHeader("X-Gateway-Key") String key) {
        return usageTracker.summaryFor(key);
    }
}