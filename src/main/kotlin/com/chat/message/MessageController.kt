package com.chat.message

import org.springframework.messaging.handler.annotation.MessageMapping
import org.springframework.messaging.handler.annotation.SendTo
import org.springframework.messaging.simp.SimpMessageHeaderAccessor
import org.springframework.stereotype.Controller

@Controller
class MessageController(private val service: MessageService) {

    @MessageMapping("/chat.sendMessage")
    @SendTo("/topic/public")
    fun sendMessage(message: Message): Message {
        return service.save(message)
    }

    @MessageMapping("/chat.addUser")
    @SendTo("/topic/public")
    fun addUser(message: Message,headerAccessor: SimpMessageHeaderAccessor): Message {
        message.sender?.let { sender ->
            headerAccessor.sessionAttributes?.put("username", sender)
        }
        return service.save(message)
    }
}
