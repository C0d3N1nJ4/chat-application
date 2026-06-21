package com.chat.message

import java.time.Instant
import org.springframework.messaging.handler.annotation.MessageMapping
import org.springframework.messaging.handler.annotation.SendTo
import org.springframework.messaging.simp.SimpMessageHeaderAccessor
import org.springframework.stereotype.Controller

@Controller
class MessageController {

    @MessageMapping("/chat.sendMessage")
    @SendTo("/topic/public")
    fun sendMessage(message: Message): Message {
        ensureTimestamp(message)
        return message
    }

    @MessageMapping("/chat.addUser")
    @SendTo("/topic/public")
    fun addUser(message: Message,headerAccessor: SimpMessageHeaderAccessor): Message {
        ensureTimestamp(message)
        message.sender?.let { sender ->
            headerAccessor.sessionAttributes?.put("username", sender)
        }
        return message
    }

    private fun ensureTimestamp(message: Message) {
        if (message.timestamp.isNullOrBlank()) {
            message.timestamp = Instant.now().toString()
        }
    }
}