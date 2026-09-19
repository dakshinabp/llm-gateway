package com.dakshina.llmgateway;

public record CompletionResult(String text, int inputTokens, int outputTokens) { }