package com.chat.conversation

import java.security.Principal
import org.springframework.messaging.handler.annotation.MessageMapping
import org.springframework.messaging.simp.SimpMessagingTemplate
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RestController

@RestController
class PrivateMessageController(
    private val service: PrivateMessageService,
    private val messaging: SimpMessagingTemplate
) {
    @MessageMapping("/chat.sendPrivateMessage")
    fun send(request: PrivateMessageRequest, principal: Principal) {
        val message = service.send(principal.name, request)
        messaging.convertAndSendToUser(message.recipientId, "/queue/private", message)
        messaging.convertAndSendToUser(message.senderId, "/queue/private", message)
    }

    @GetMapping("/api/conversations/{recipientId}/messages")
    fun recent(@PathVariable recipientId: String, principal: Principal): List<PrivateMessage> =
        service.recent(principal.name, recipientId)
}
