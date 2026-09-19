package com.dakshina.llmgateway;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class UsageTracker {

    private final double inputPricePerMillion;
    private final double outputPricePerMillion;
    private final Map<String, Totals> byKey = new ConcurrentHashMap<>();

    public UsageTracker(@Value("${pricing.input-per-million}") double inputPricePerMillion,
                        @Value("${pricing.output-per-million}") double outputPricePerMillion) {
        this.inputPricePerMillion = inputPricePerMillion;
        this.outputPricePerMillion = outputPricePerMillion;
    }

    public void record(String key, int inputTokens, int outputTokens) {
        byKey.merge(key, new Totals(1, inputTokens, outputTokens), Totals::plus);
    }

    public Summary summaryFor(String key) {
        Totals t = byKey.getOrDefault(key, new Totals(0, 0, 0));

        double cost = (t.inputTokens() / 1_000_000.0) * inputPricePerMillion
                    + (t.outputTokens() / 1_000_000.0) * outputPricePerMillion;

        return new Summary(t.requests(), t.inputTokens(), t.outputTokens(),
                Math.round(cost * 1_000_000.0) / 1_000_000.0);
    }

    public record Summary(long requests, long inputTokens, long outputTokens,
                          double estimatedCostUsd) { }

    record Totals(long requests, long inputTokens, long outputTokens) {
        Totals plus(Totals other) {
            return new Totals(requests + other.requests,
                    inputTokens + other.inputTokens,
                    outputTokens + other.outputTokens);
        }
    }
}