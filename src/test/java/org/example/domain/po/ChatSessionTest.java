package org.example.domain.po;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ChatSessionTest {

    @Test
    void keepsOnlyConfiguredMessagePairs() {
        ChatSession session = new ChatSession("session-1", 6);

        for (int i = 0; i < 7; i++) {
            session.addMessage("question-" + i, "answer-" + i);
        }

        List<ChatMessage> history = session.getHistory();
        assertThat(session.getMessagePairCount()).isEqualTo(6);
        assertThat(history).hasSize(12);
        assertThat(history.get(0).getRole()).isEqualTo("user");
        assertThat(history.get(0).getContent()).isEqualTo("question-1");
    }

    @Test
    void clearsHistory() {
        ChatSession session = new ChatSession("session-1", 6);
        session.addMessage("question", "answer");

        session.clearHistory();

        assertThat(session.getMessagePairCount()).isZero();
        assertThat(session.getHistory()).isEmpty();
    }

    @Test
    void returnsDefensiveHistoryCopy() {
        ChatSession session = new ChatSession("session-1", 6);
        session.addMessage("question", "answer");

        List<ChatMessage> history = session.getHistory();
        history.clear();

        assertThat(session.getMessagePairCount()).isEqualTo(1);
        assertThat(session.getHistory()).hasSize(2);
    }
}
