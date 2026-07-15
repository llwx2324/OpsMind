package org.example.session;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ChatSessionEntityTest {
    @Test
    void tracksLastAssignedSequence() {
        Instant now = Instant.parse("2026-01-01T00:00:00Z");
        ChatSessionEntity session = new ChatSessionEntity("session", "tenant", "user", now, now.plusSeconds(60));

        assertEquals(0, session.getLastAssignedSequence());
        assertEquals(1, session.nextSequence());
        assertEquals(2, session.nextSequence());
        assertEquals(2, session.getLastAssignedSequence());

        session.clear(now.plusSeconds(1), now.plusSeconds(61));

        assertEquals(0, session.getLastAssignedSequence());
    }
}
