package com.dakshina.llmgateway;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class ApiKeyFilter extends OncePerRequestFilter {

    private final String expectedKey;
    private final RateLimiter rateLimiter;

    public ApiKeyFilter(@Value("${gateway.api-key}") String expectedKey,
                        RateLimiter rateLimiter) {
        this.expectedKey = expectedKey;
        this.rateLimiter = rateLimiter;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {

        if (request.getRequestURI().startsWith("/v1/")) {

            String provided = request.getHeader("X-Gateway-Key");
            if (provided == null || !provided.equals(expectedKey)) {
                reject(response, 401, "missing or invalid X-Gateway-Key");
                return;
            }

            if (!rateLimiter.allow(provided)) {
                response.setHeader("Retry-After", "60");
                reject(response, 429, "rate limit exceeded, try again shortly");
                return;
            }
        }

        filterChain.doFilter(request, response);
    }

    private void reject(HttpServletResponse response, int status, String message)
            throws IOException {
        response.setStatus(status);
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write(
                "{\"status\":" + status + ",\"message\":\"" + message + "\"}");
    }
}