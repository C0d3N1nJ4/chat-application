package com.chat.message

import org.springframework.messaging.handler.annotation.MessageMapping
import org.springframework.messaging.handler.annotation.SendTo
import org.springframework.messaging.simp.SimpMessageHeaderAccessor
import org.springframework.stereotype.Controller

@Controller
class MessageController {

    @MessageMapping("/chat.sendMessage")
    @SendTo("/topic/public")
    fun sendMessage(message: Message): Message {
        return message
    }

    @MessageMapping("/chat.addUser")
    @SendTo("/topic/public")
    fun addUser(message: Message,headerAccessor: SimpMessageHeaderAccessor): Message {
        headerAccessor.sessionAttributes?.put("username", message.sender)
        return message
    }
}