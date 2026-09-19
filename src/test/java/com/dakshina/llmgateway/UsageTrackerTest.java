package com.dakshina.llmgateway;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class UsageTrackerTest {

    @Test
    void addsUpTokensAndCost() {
        UsageTracker tracker = new UsageTracker(2.00, 10.00);

        tracker.record("key-a", 1_000_000, 0);
        tracker.record("key-a", 0, 1_000_000);

        UsageTracker.Summary summary = tracker.summaryFor("key-a");

        assertEquals(2, summary.requests());
        assertEquals(1_000_000, summary.inputTokens());
        assertEquals(1_000_000, summary.outputTokens());
        assertEquals(12.00, summary.estimatedCostUsd(), 0.000001);
    }

    @Test
    void keepsKeysSeparate() {
        UsageTracker tracker = new UsageTracker(2.00, 10.00);
        tracker.record("key-a", 100, 100);

        UsageTracker.Summary other = tracker.summaryFor("key-b");

        assertEquals(0, other.requests());
        assertEquals(0.0, other.estimatedCostUsd(), 0.000001);
    }
}