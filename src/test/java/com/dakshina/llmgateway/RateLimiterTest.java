package com.dakshina.llmgateway;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class RateLimiterTest {

    /** A clock we control, so the test doesn't have to wait a real minute. */
    static class TestClock extends Clock {
        private Instant now = Instant.parse("2026-01-01T00:00:00Z");

        void advanceMinutes(long minutes) {
            now = now.plusSeconds(minutes * 60);
        }

        @Override public Instant instant() { return now; }
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
    }

    @Test
    void allowsUpToTheLimitThenRefuses() {
        RateLimiter limiter = new RateLimiter(3, new TestClock());

        assertTrue(limiter.allow("key-a"));
        assertTrue(limiter.allow("key-a"));
        assertTrue(limiter.allow("key-a"));
        assertFalse(limiter.allow("key-a"));
    }

    @Test
    void countsEachKeySeparately() {
        RateLimiter limiter = new RateLimiter(1, new TestClock());

        assertTrue(limiter.allow("key-a"));
        assertFalse(limiter.allow("key-a"));
        assertTrue(limiter.allow("key-b"));
    }

    @Test
    void resetsWhenTheMinuteChanges() {
        TestClock clock = new TestClock();
        RateLimiter limiter = new RateLimiter(2, clock);

        assertTrue(limiter.allow("key-a"));
        assertTrue(limiter.allow("key-a"));
        assertFalse(limiter.allow("key-a"));

        clock.advanceMinutes(1);

        assertTrue(limiter.allow("key-a"));
    }
}