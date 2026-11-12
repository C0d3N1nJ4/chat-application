package com.chat.message

import com.chat.user.UserRepository
import java.security.Principal
import org.springframework.messaging.handler.annotation.MessageMapping
import org.springframework.messaging.handler.annotation.SendTo
import org.springframework.messaging.simp.SimpMessageHeaderAccessor
import org.springframework.stereotype.Controller

@Controller
class MessageController(private val service: MessageService, private val users: UserRepository) {

    @MessageMapping("/chat.sendMessage")
    @SendTo("/topic/public")
    fun sendMessage(message: Message, principal: Principal): Message {
        message.sender = users.requireUser(principal.name).username
        message.type = MessageType.CHAT
        return service.save(message)
    }

    @MessageMapping("/chat.addUser")
    @SendTo("/topic/public")
    fun addUser(message: Message, headerAccessor: SimpMessageHeaderAccessor, principal: Principal): Message {
        message.sender = users.requireUser(principal.name).username
        message.type = MessageType.JOIN
        message.sender?.let { sender ->
            headerAccessor.sessionAttributes?.put("username", sender)
        }
        return service.save(message)
    }
}
