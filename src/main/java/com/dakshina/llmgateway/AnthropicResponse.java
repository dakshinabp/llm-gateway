package com.dakshina.llmgateway;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public record AnthropicResponse(List<ContentBlock> content, Usage usage) {

    public record ContentBlock(String type, String text) { }

    public record Usage(
            @JsonProperty("input_tokens") int inputTokens,
            @JsonProperty("output_tokens") int outputTokens) { }
}