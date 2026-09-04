package com.tradingsim.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Applies a small fixed-window limit to API traffic before expensive work runs.
 */
@Component
public class ApiRateLimitFilter extends OncePerRequestFilter {
    private static final int GENERAL_LIMIT = 120;
    private static final int REPLAY_ADVANCE_LIMIT = 600;
    private static final int SENSITIVE_LIMIT = 10;
    private static final long WINDOW_SECONDS = 60;

    private final Map<String, Window> windows = new ConcurrentHashMap<>();
    private final AtomicLong lastCleanup = new AtomicLong();
    private final ObjectMapper objectMapper;

    public ApiRateLimitFilter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return !path.startsWith("/api/") && !path.equals("/login");
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        long now = Instant.now().getEpochSecond();
        cleanupExpiredWindows(now);
        String path = request.getRequestURI();
        boolean sensitive = path.equals("/login")
                || path.equals("/api/auth/register")
                || path.equals("/api/backtests/csv");
        boolean replayAdvance = path.startsWith("/api/competitive/session/")
                && path.endsWith("/advance");
        int limit = sensitive
                ? SENSITIVE_LIMIT
                : replayAdvance ? REPLAY_ADVANCE_LIMIT : GENERAL_LIMIT;
        // Authentication endpoints always stay bound to the source IP. A new
        // session identity must not grant a fresh password-attempt bucket.
        String identity = sensitive || request.getUserPrincipal() == null
                ? request.getRemoteAddr()
                : request.getUserPrincipal().getName();
        String bucket = sensitive ? path : replayAdvance ? "replay-advance" : "general";
        String key = identity + ":" + bucket;
        Window window = windows.compute(key, (ignored, current) -> {
            if (current == null || now - current.startedAt >= WINDOW_SECONDS) {
                return new Window(now, 1);
            }
            return new Window(current.startedAt, current.requests + 1);
        });

        response.setHeader("X-RateLimit-Limit", String.valueOf(limit));
        response.setHeader("X-RateLimit-Remaining",
                String.valueOf(Math.max(0, limit - window.requests)));
        if (window.requests > limit) {
            response.setStatus(429);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setHeader(
                    "Retry-After",
                    String.valueOf(WINDOW_SECONDS - (now - window.startedAt)));
            objectMapper.writeValue(
                    response.getOutputStream(),
                    Map.of("message", "Too many requests. Please wait and try again."));
            return;
        }
        filterChain.doFilter(request, response);
    }

    private void cleanupExpiredWindows(long now) {
        long previous = lastCleanup.get();
        if (now - previous >= WINDOW_SECONDS
                && lastCleanup.compareAndSet(previous, now)) {
            windows.entrySet().removeIf(
                    entry -> now - entry.getValue().startedAt >= WINDOW_SECONDS);
        }
    }

    private record Window(long startedAt, int requests) {
    }
}
