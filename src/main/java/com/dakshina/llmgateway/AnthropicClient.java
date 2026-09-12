package com.dakshina.llmgateway;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

@Service
public class AnthropicClient {

    private static final String MODEL = "claude-sonnet-4-5";
    private static final int MAX_TOKENS = 1024;

    private final RestClient restClient;

    public AnthropicClient(@Value("${anthropic.api-key}") String apiKey) {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();

        JdkClientHttpRequestFactory requestFactory =
                new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(Duration.ofSeconds(30));

        this.restClient = RestClient.builder()
                .requestFactory(requestFactory)
                .baseUrl("https://api.anthropic.com")
                .defaultHeader("x-api-key", apiKey)
                .defaultHeader("anthropic-version", "2023-06-01")
                .defaultHeader("content-type", "application/json")
                .build();
    }

    public String complete(String prompt) {
        AnthropicRequest body = new AnthropicRequest(
                MODEL,
                MAX_TOKENS,
                List.of(new AnthropicRequest.Message("user", prompt)));

        AnthropicResponse response = restClient.post()
                .uri("/v1/messages")
                .body(body)
                .retrieve()
                .body(AnthropicResponse.class);

        return response.content().get(0).text();
    }
}