package com.dakshina.llmgateway;

import java.time.Clock;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class RateLimiter {

    private final int maxPerMinute;
    private final Clock clock;
    private final Map<String, Window> windows = new ConcurrentHashMap<>();

    @Autowired
    public RateLimiter(@Value("${gateway.rate-limit-per-minute}") int maxPerMinute) {
        this(maxPerMinute, Clock.systemUTC());
    }

    RateLimiter(int maxPerMinute, Clock clock) {
        this.maxPerMinute = maxPerMinute;
        this.clock = clock;
    }

    public boolean allow(String key) {
        long minute = clock.millis() / 60_000;

        Window updated = windows.compute(key, (k, existing) ->
                (existing == null || existing.minute() != minute)
                        ? new Window(minute, 1)
                        : new Window(minute, existing.count() + 1));

        return updated.count() <= maxPerMinute;
    }

    private record Window(long minute, int count) { }
}