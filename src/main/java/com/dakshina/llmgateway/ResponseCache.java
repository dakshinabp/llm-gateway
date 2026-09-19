package com.dakshina.llmgateway;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class ResponseCache {

    private final Map<String, CompletionResult> entries;

    public ResponseCache(@Value("${cache.max-entries}") int maxEntries) {
        this.entries = Collections.synchronizedMap(
                new LinkedHashMap<>(16, 0.75f, true) {
                    @Override
                    protected boolean removeEldestEntry(
                            Map.Entry<String, CompletionResult> eldest) {
                        return size() > maxEntries;
                    }
                });
    }

    public CompletionResult get(String prompt) {
        return entries.get(prompt);
    }

    public void put(String prompt, CompletionResult result) {
        entries.put(prompt, result);
    }

    public int size() {
        return entries.size();
    }
}