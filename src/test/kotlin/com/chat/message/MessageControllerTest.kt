package com.chat.message

import org.junit.jupiter.api.Test
import org.springframework.messaging.simp.SimpMessageHeaderAccessor

import java.util.HashMap

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.springframework.dao.DataAccessResourceFailureException
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import org.mockito.Mockito.verify

class MessageControllerTest {

    private val repository = mock(MessageRepository::class.java)
    private val controller = MessageController(repository)

    @Test
    fun sendMessageReturnsSameMessage() {
        val message = Message(MessageType.CHAT, "hi", "alice")
        `when`(repository.save(message)).thenReturn(message)

        val result = controller.sendMessage(message)

        assertThat(result).isSameAs(message)
        assertThat(result.timestamp).isNotBlank()
        verify(repository).save(message)
    }

    @Test
    fun sendMessageKeepsProvidedTimestamp() {
        val message = Message(MessageType.CHAT, "hi", "alice", "2026-06-21T15:23:00Z")
        `when`(repository.save(message)).thenReturn(message)

        val result = controller.sendMessage(message)

        assertThat(result.timestamp).isEqualTo("2026-06-21T15:23:00Z")
    }

    @Test
    fun failedPersistenceDoesNotReturnMessageForBroadcast() {
        val message = Message(MessageType.CHAT, "hi", "alice")
        `when`(repository.save(message)).thenThrow(DataAccessResourceFailureException("Disk unavailable"))

        assertThatThrownBy { controller.sendMessage(message) }
            .isInstanceOf(DataAccessResourceFailureException::class.java)
    }

    @Test
    fun addUserStoresUsernameWhenSenderIsPresent() {
        val message = Message(MessageType.JOIN, "", "dave")
        `when`(repository.save(message)).thenReturn(message)
        val headerAccessor = mock(SimpMessageHeaderAccessor::class.java)
        val sessionAttributes: MutableMap<String, Any> = HashMap()
        `when`(headerAccessor.sessionAttributes).thenReturn(sessionAttributes)

        val result = controller.addUser(message, headerAccessor)

        assertThat(result).isSameAs(message)
        assertThat(result.timestamp).isNotBlank()
        assertThat(sessionAttributes).containsEntry("username", "dave")
        verify(repository).save(message)
    }

    @Test
    fun addUserDoesNotStoreUsernameWhenSenderIsNull() {
        val message = Message(MessageType.JOIN, "", null)
        `when`(repository.save(message)).thenReturn(message)
        val headerAccessor = mock(SimpMessageHeaderAccessor::class.java)
        val sessionAttributes: MutableMap<String, Any> = HashMap()
        `when`(headerAccessor.sessionAttributes).thenReturn(sessionAttributes)

        val result = controller.addUser(message, headerAccessor)

        assertThat(result).isSameAs(message)
        assertThat(sessionAttributes).doesNotContainKey("username")
    }
}
