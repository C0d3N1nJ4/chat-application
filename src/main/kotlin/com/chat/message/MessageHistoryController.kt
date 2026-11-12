package com.chat.message

import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController

@RestController
class MessageHistoryController(private val service: MessageService) {

    @GetMapping("/api/messages")
    fun recent(): List<Message> = service.recent()
}
