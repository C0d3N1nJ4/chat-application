package com.chat.conversation

import java.security.Principal
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoInteractions
import org.mockito.Mockito.verifyNoMoreInteractions
import org.mockito.Mockito.`when`
import org.springframework.dao.DataAccessResourceFailureException
import org.springframework.messaging.simp.SimpMessagingTemplate

class PrivateMessageControllerTest {
    private val service = mock(PrivateMessageService::class.java)
    private val messaging = mock(SimpMessagingTemplate::class.java)
    private val controller = PrivateMessageController(service, messaging)
    private val request = PrivateMessageRequest("2", "hello")
    private val principal = Principal { "1" }

    @Test
    fun persistedMessageIsDeliveredOnlyToSenderAndRecipient() {
        val saved = PrivateMessage(1, "1", "alice", "2", "bob", "hello", "2026-11-12T20:00:00Z")
        `when`(service.send("1", request)).thenReturn(saved)

        controller.send(request, principal)

        verify(service).send("1", request)
        verify(messaging).convertAndSendToUser("2", "/queue/private", saved)
        verify(messaging).convertAndSendToUser("1", "/queue/private", saved)
        verifyNoMoreInteractions(messaging)
    }

    @Test
    fun failedPersistenceDoesNotDeliverPrivateMessage() {
        `when`(service.send("1", request)).thenThrow(DataAccessResourceFailureException("Disk unavailable"))

        assertThatThrownBy { controller.send(request, principal) }
            .isInstanceOf(DataAccessResourceFailureException::class.java)
        verifyNoInteractions(messaging)
    }
}
