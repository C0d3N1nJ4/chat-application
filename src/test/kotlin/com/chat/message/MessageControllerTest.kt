package com.chat.message

import org.junit.jupiter.api.Test
import org.springframework.messaging.simp.SimpMessageHeaderAccessor

import java.util.HashMap

import org.assertj.core.api.Assertions.assertThat
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`

class MessageControllerTest {

    private val controller = MessageController()

    @Test
    fun sendMessageReturnsSameMessage() {
        val message = Message(MessageType.CHAT, "hi", "alice")

        val result = controller.sendMessage(message)

        assertThat(result).isSameAs(message)
        assertThat(result.timestamp).isNotBlank()
    }

    @Test
    fun sendMessageKeepsProvidedTimestamp() {
        val message = Message(MessageType.CHAT, "hi", "alice", "2026-06-21T15:23:00Z")

        val result = controller.sendMessage(message)

        assertThat(result.timestamp).isEqualTo("2026-06-21T15:23:00Z")
    }

    @Test
    fun addUserStoresUsernameWhenSenderIsPresent() {
        val message = Message(MessageType.JOIN, "", "dave")
        val headerAccessor = mock(SimpMessageHeaderAccessor::class.java)
        val sessionAttributes: MutableMap<String, Any> = HashMap()
        `when`(headerAccessor.sessionAttributes).thenReturn(sessionAttributes)

        val result = controller.addUser(message, headerAccessor)

        assertThat(result).isSameAs(message)
        assertThat(result.timestamp).isNotBlank()
        assertThat(sessionAttributes).containsEntry("username", "dave")
    }

    @Test
    fun addUserDoesNotStoreUsernameWhenSenderIsNull() {
        val message = Message(MessageType.JOIN, "", null)
        val headerAccessor = mock(SimpMessageHeaderAccessor::class.java)
        val sessionAttributes: MutableMap<String, Any> = HashMap()
        `when`(headerAccessor.sessionAttributes).thenReturn(sessionAttributes)

        val result = controller.addUser(message, headerAccessor)

        assertThat(result).isSameAs(message)
        assertThat(sessionAttributes).doesNotContainKey("username")
    }
}
