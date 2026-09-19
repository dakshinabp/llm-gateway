package com.dakshina.llmgateway;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

@Service
public class AnthropicClient {

    private static final Logger log = LoggerFactory.getLogger(AnthropicClient.class);

    private static final int MAX_TOKENS = 1024;
    private static final int MAX_ATTEMPTS = 3;
    private static final long BASE_BACKOFF_MS = 500;
    private final String model;
    private final RestClient restClient;

    public AnthropicClient(@Value("${anthropic.api-key}") String apiKey,
                           @Value("${anthropic.base-url}") String baseUrl,
                           @Value("${anthropic.model}") String model) {

        this.model = model;

        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();

        JdkClientHttpRequestFactory requestFactory =
                new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(Duration.ofSeconds(30));

        this.restClient = RestClient.builder()
                .requestFactory(requestFactory)
                .baseUrl(baseUrl)
                .defaultHeader("x-api-key", apiKey)
                .defaultHeader("anthropic-version", "2023-06-01")
                .defaultHeader("content-type", "application/json")
                .build();
    }

    public CompletionResult complete(String prompt) {
        AnthropicRequest body = new AnthropicRequest(
                model,
                MAX_TOKENS,
                List.of(new AnthropicRequest.Message("user", prompt)));

        for (int attempt = 1; ; attempt++) {
            try {
                return callOnce(body);
            } catch (DownstreamException e) {
                if (!e.retryable() || attempt == MAX_ATTEMPTS) {
                    log.warn("giving up after {} attempt(s): {}", attempt, e.getMessage());
                    throw e;
                }
                long delay = backoffMillis(attempt);
                log.warn("attempt {} failed ({}), retrying in {}ms",
                        attempt, e.getMessage(), delay);
                sleep(delay);
            }
        }
    }

    private CompletionResult callOnce(AnthropicRequest body) {
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
            throw new DownstreamException(HttpStatus.GATEWAY_TIMEOUT,
                    "model provider did not respond in time", true);
        }

        if (response == null || response.content() == null || response.content().isEmpty()) {
            throw new DownstreamException(HttpStatus.BAD_GATEWAY,
                    "model provider returned an empty response", false);
        }

        AnthropicResponse.Usage usage = response.usage();

        return new CompletionResult(
                response.content().get(0).text(),
                usage == null ? 0 : usage.inputTokens(),
                usage == null ? 0 : usage.outputTokens());
    }

    private DownstreamException translate(RestClientResponseException e) {
        return switch (e.getStatusCode().value()) {
            case 400 -> new DownstreamException(HttpStatus.BAD_REQUEST,
                    "model provider rejected the request", false);
            case 401, 403 -> new DownstreamException(HttpStatus.BAD_GATEWAY,
                    "gateway is not authorized with the model provider", false);
            case 429 -> new DownstreamException(HttpStatus.TOO_MANY_REQUESTS,
                    "model provider rate limit reached", true);
            default -> new DownstreamException(HttpStatus.BAD_GATEWAY,
                    "model provider error", e.getStatusCode().is5xxServerError());
        };
    }

    private long backoffMillis(int attempt) {
        long exponential = BASE_BACKOFF_MS * (1L << (attempt - 1));
        long jitter = ThreadLocalRandom.current().nextLong(BASE_BACKOFF_MS / 2);
        return exponential + jitter;
    }

    private void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new DownstreamException(HttpStatus.SERVICE_UNAVAILABLE,
                    "request interrupted", false);
        }
    }
}