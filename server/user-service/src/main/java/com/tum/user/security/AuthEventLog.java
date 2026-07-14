package com.tum.user.security;

import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * Bounded in-memory ring buffer of recent authentication events, surfaced to
 * the admin panel via GET /auth/logs. Mirrors the llm-service _logs pattern.
 *
 * Only non-sensitive fields are stored — never the password or the JWT.
 */
@Component
public class AuthEventLog {

    /** A single auth event shown in the admin panel. */
    public record AuthEvent(String timestamp, String type, String email, String result) {
    }

    private static final int MAX_EVENTS = 200;
    private final Deque<AuthEvent> events = new ArrayDeque<>();

    /** Records an event, evicting the oldest once the buffer is full. */
    public synchronized void record(String type, String email, String result) {
        if (events.size() >= MAX_EVENTS) {
            events.removeFirst();
        }
        events.addLast(new AuthEvent(Instant.now().toString(), type, email, result));
    }

    /** Returns events newest-first for display. */
    public synchronized List<AuthEvent> recent() {
        List<AuthEvent> list = new ArrayList<>(events);
        java.util.Collections.reverse(list);
        return list;
    }
}
