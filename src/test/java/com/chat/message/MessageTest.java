package com.chat.message;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MessageTest {

    @Test
    void constructorSetsAllFields() {
        Message message = new Message(MessageType.CHAT, "hello", "alice", "2026-06-21T15:20:00Z");

        assertThat(message.getType()).isEqualTo(MessageType.CHAT);
        assertThat(message.getContent()).isEqualTo("hello");
        assertThat(message.getSender()).isEqualTo("alice");
        assertThat(message.getTimestamp()).isEqualTo("2026-06-21T15:20:00Z");
    }

    @Test
    void noArgConstructorAllowsPropertyMutation() {
        Message message = new Message();
        message.setType(MessageType.JOIN);
        message.setContent("joined");
        message.setSender("bob");
        message.setTimestamp("2026-06-21T15:21:00Z");

        assertThat(message.getType()).isEqualTo(MessageType.JOIN);
        assertThat(message.getContent()).isEqualTo("joined");
        assertThat(message.getSender()).isEqualTo("bob");
        assertThat(message.getTimestamp()).isEqualTo("2026-06-21T15:21:00Z");
    }

    @Test
    void toStringContainsImportantFields() {
        Message message = new Message(MessageType.LEAVE, "bye", "carol", "2026-06-21T15:22:00Z");

        assertThat(message.toString())
                .contains("type='LEAVE'")
                .contains("content='bye'")
            .contains("sender='carol'")
            .contains("timestamp='2026-06-21T15:22:00Z'");
    }
}
