package com.dakshina.llmgateway;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.server.ResponseStatusException;

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

        AnthropicResponse response;
        try {
            response = restClient.post()
                    .uri("/v1/messages")
                    .body(body)
                    .retrieve()
                    .body(AnthropicResponse.class);
        } catch (RestClientResponseException e) {
            throw translate(e);
        } catch (ResourceAccessException e) {
            throw new ResponseStatusException(HttpStatus.GATEWAY_TIMEOUT,
                    "model provider did not respond in time");
        }

        if (response == null || response.content() == null || response.content().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "model provider returned an empty response");
        }

        return response.content().get(0).text();
    }

    private ResponseStatusException translate(RestClientResponseException e) {
        return switch (e.getStatusCode().value()) {
            case 400 -> new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "model provider rejected the request");
            case 401, 403 -> new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "gateway is not authorized with the model provider");
            case 429 -> new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
                    "model provider rate limit reached");
            default -> new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "model provider error");
        };
    }
}