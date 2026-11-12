package com.chat.message

import org.junit.jupiter.api.Test

import org.assertj.core.api.Assertions.assertThat

class MessageTest {

    @Test
    fun constructorSetsAllFields() {
        val message = Message(MessageType.CHAT, "hello", "alice", "2026-06-21T15:20:00Z")

        assertThat(message.type).isEqualTo(MessageType.CHAT)
        assertThat(message.content).isEqualTo("hello")
        assertThat(message.sender).isEqualTo("alice")
        assertThat(message.timestamp).isEqualTo("2026-06-21T15:20:00Z")
    }

    @Test
    fun noArgConstructorAllowsPropertyMutation() {
        val message = Message()
        message.type = MessageType.JOIN
        message.content = "joined"
        message.sender = "bob"
        message.timestamp = "2026-06-21T15:21:00Z"

        assertThat(message.type).isEqualTo(MessageType.JOIN)
        assertThat(message.content).isEqualTo("joined")
        assertThat(message.sender).isEqualTo("bob")
        assertThat(message.timestamp).isEqualTo("2026-06-21T15:21:00Z")
    }

    @Test
    fun toStringContainsImportantFields() {
        val message = Message(MessageType.LEAVE, "bye", "carol", "2026-06-21T15:22:00Z")

        assertThat(message.toString())
                .contains("type='LEAVE'")
                .contains("content='bye'")
            .contains("sender='carol'")
            .contains("timestamp='2026-06-21T15:22:00Z'")
    }
}
