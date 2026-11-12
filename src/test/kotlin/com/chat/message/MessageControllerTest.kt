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

    private val service = mock(MessageService::class.java)
    private val controller = MessageController(service)

    @Test
    fun sendMessageReturnsSameMessage() {
        val message = Message(MessageType.CHAT, "hi", "alice")
        `when`(service.save(message)).thenReturn(message)

        val result = controller.sendMessage(message)

        assertThat(result).isSameAs(message)
        verify(service).save(message)
    }

    @Test
    fun failedPersistenceDoesNotReturnMessageForBroadcast() {
        val message = Message(MessageType.CHAT, "hi", "alice")
        `when`(service.save(message)).thenThrow(DataAccessResourceFailureException("Disk unavailable"))

        assertThatThrownBy { controller.sendMessage(message) }
            .isInstanceOf(DataAccessResourceFailureException::class.java)
    }

    @Test
    fun addUserStoresUsernameWhenSenderIsPresent() {
        val message = Message(MessageType.JOIN, "", "dave")
        `when`(service.save(message)).thenReturn(message)
        val headerAccessor = mock(SimpMessageHeaderAccessor::class.java)
        val sessionAttributes: MutableMap<String, Any> = HashMap()
        `when`(headerAccessor.sessionAttributes).thenReturn(sessionAttributes)

        val result = controller.addUser(message, headerAccessor)

        assertThat(result).isSameAs(message)
        assertThat(sessionAttributes).containsEntry("username", "dave")
        verify(service).save(message)
    }

    @Test
    fun addUserDoesNotStoreUsernameWhenSenderIsNull() {
        val message = Message(MessageType.JOIN, "", null)
        `when`(service.save(message)).thenReturn(message)
        val headerAccessor = mock(SimpMessageHeaderAccessor::class.java)
        val sessionAttributes: MutableMap<String, Any> = HashMap()
        `when`(headerAccessor.sessionAttributes).thenReturn(sessionAttributes)

        val result = controller.addUser(message, headerAccessor)

        assertThat(result).isSameAs(message)
        assertThat(sessionAttributes).doesNotContainKey("username")
    }
}
