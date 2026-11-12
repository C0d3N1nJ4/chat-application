package com.chat.message

import com.chat.user.ChatUser
import com.chat.user.UserRepository
import java.security.Principal
import org.junit.jupiter.api.Test
import org.springframework.messaging.simp.SimpMessageHeaderAccessor

import java.util.HashMap

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.springframework.dao.DataAccessResourceFailureException
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoInteractions
import org.springframework.security.access.AccessDeniedException

class MessageControllerTest {

    private val service = mock(MessageService::class.java)
    private val users = mock(UserRepository::class.java).also {
        `when`(it.requireUser("1")).thenReturn(ChatUser("1", "dave"))
    }
    private val principal = Principal { "1" }
    private val controller = MessageController(service, users)

    @Test
    fun sendMessageReturnsSameMessage() {
        val message = Message(MessageType.CHAT, "hi", "alice")
        `when`(service.save(message)).thenReturn(message)

        val result = controller.sendMessage(message, principal)

        assertThat(result).isSameAs(message)
        assertThat(result.sender).isEqualTo("dave")
        assertThat(result.type).isEqualTo(MessageType.CHAT)
        verify(service).save(message)
    }

    @Test
    fun failedPersistenceDoesNotReturnMessageForBroadcast() {
        val message = Message(MessageType.CHAT, "hi", "alice")
        `when`(service.save(message)).thenThrow(DataAccessResourceFailureException("Disk unavailable"))

        assertThatThrownBy { controller.sendMessage(message, principal) }
            .isInstanceOf(DataAccessResourceFailureException::class.java)
    }

    @Test
    fun addUserStoresUsernameWhenSenderIsPresent() {
        val message = Message(MessageType.JOIN, "", "dave")
        `when`(service.save(message)).thenReturn(message)
        val headerAccessor = mock(SimpMessageHeaderAccessor::class.java)
        val sessionAttributes: MutableMap<String, Any> = HashMap()
        `when`(headerAccessor.sessionAttributes).thenReturn(sessionAttributes)

        val result = controller.addUser(message, headerAccessor, principal)

        assertThat(result).isSameAs(message)
        assertThat(sessionAttributes).containsEntry("username", "dave")
        verify(service).save(message)
    }

    @Test
    fun addUserUsesAuthenticatedUsernameEvenWhenPayloadSenderIsNull() {
        val message = Message(MessageType.JOIN, "", null)
        `when`(service.save(message)).thenReturn(message)
        val headerAccessor = mock(SimpMessageHeaderAccessor::class.java)
        val sessionAttributes: MutableMap<String, Any> = HashMap()
        `when`(headerAccessor.sessionAttributes).thenReturn(sessionAttributes)

        val result = controller.addUser(message, headerAccessor, principal)

        assertThat(result).isSameAs(message)
        assertThat(sessionAttributes).containsEntry("username", "dave")
    }

    @Test
    fun unknownAuthenticatedUserCannotPublish() {
        `when`(users.requireUser("unknown")).thenThrow(AccessDeniedException("Please sign in again."))

        assertThatThrownBy {
            controller.sendMessage(Message(MessageType.CHAT, "hi", "dave"), Principal { "unknown" })
        }.isInstanceOf(AccessDeniedException::class.java)
        verifyNoInteractions(service)
    }
}
