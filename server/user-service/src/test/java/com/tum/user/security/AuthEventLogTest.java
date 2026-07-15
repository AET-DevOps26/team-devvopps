package com.tum.user.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for AuthEventLog.
 *
 * This test class verifies the behavior of the in-memory authentication
 * event log that is used by the admin panel. The tests cover:
 * - Recording new authentication events.
 * - Preserving the event information (type, email, result).
 * - Returning events in reverse chronological (newest-first) order.
 * - Enforcing the maximum buffer size by evicting the oldest events.
 * - Correct behavior when the log is empty.
 *
 * Since AuthEventLog is a simple in-memory data structure without
 * external dependencies, these tests instantiate the class directly instead
 * of loading the Spring application context.
 */
class AuthEventLogTest {

    private AuthEventLog authEventLog;

    @BeforeEach
    void setUp() {
        authEventLog = new AuthEventLog();
    }

    /**
     * Verifies that an empty log returns an empty list instead of null.
     * This ensures callers can safely iterate over the returned collection.
     */
    @Test
    @DisplayName("Should return an empty list when no events have been recorded")
    void shouldReturnEmptyListWhenNoEventsExist() {
        List<AuthEventLog.AuthEvent> events = authEventLog.recent();

        assertNotNull(events);
        assertTrue(events.isEmpty());
    }

    /**
     * Verifies that recording a single authentication event stores all
     * relevant information correctly.
     */
    @Test
    @DisplayName("Should record a single authentication event")
    void shouldRecordSingleEvent() {
        authEventLog.record("LOGIN", "alice@example.com", "SUCCESS");

        List<AuthEventLog.AuthEvent> events = authEventLog.recent();

        assertEquals(1, events.size());

        AuthEventLog.AuthEvent event = events.get(0);

        assertEquals("LOGIN", event.type());
        assertEquals("alice@example.com", event.email());
        assertEquals("SUCCESS", event.result());

        // Timestamp should be generated automatically.
        assertNotNull(event.timestamp());
        assertFalse(event.timestamp().isBlank());
    }

    /**
     * Verifies that events are returned in reverse chronological order
     * (newest event first), matching the behavior expected by the admin UI.
     */
    @Test
    @DisplayName("Should return events in newest-first order")
    void shouldReturnNewestEventsFirst() {
        authEventLog.record("LOGIN", "first@example.com", "SUCCESS");
        authEventLog.record("REGISTER", "second@example.com", "SUCCESS");
        authEventLog.record("LOGIN", "third@example.com", "FAILED");

        List<AuthEventLog.AuthEvent> events = authEventLog.recent();

        assertEquals(3, events.size());

        assertEquals("third@example.com", events.get(0).email());
        assertEquals("second@example.com", events.get(1).email());
        assertEquals("first@example.com", events.get(2).email());
    }

    /**
     * Verifies that the log behaves as a bounded ring buffer.
     * Once the maximum capacity is exceeded, the oldest event should
     * be removed while newer events remain available.
     */
    @Test
    @DisplayName("Should evict the oldest event when the maximum capacity is exceeded")
    void shouldEvictOldestEventWhenCapacityExceeded() {

        // Insert one more event than the configured capacity.
        for (int i = 0; i < 201; i++) {
            authEventLog.record(
                    "LOGIN",
                    "user" + i + "@example.com",
                    "SUCCESS"
            );
        }

        List<AuthEventLog.AuthEvent> events = authEventLog.recent();

        // Buffer should never exceed its configured size.
        assertEquals(200, events.size());

        // Newest event should be the last inserted.
        assertEquals("user200@example.com", events.get(0).email());

        // Oldest retained event should be user1 since user0 was evicted.
        assertEquals("user1@example.com", events.get(events.size() - 1).email());
    }

    /**
     * Verifies that the timestamp is automatically assigned when an
     * authentication event is recorded.
     */
    @Test
    @DisplayName("Should assign a timestamp to every recorded event")
    void shouldAssignTimestampToRecordedEvent() {
        authEventLog.record("LOGIN", "alice@example.com", "SUCCESS");

        AuthEventLog.AuthEvent event = authEventLog.recent().get(0);

        assertNotNull(event.timestamp());
        assertFalse(event.timestamp().isBlank());
    }
}