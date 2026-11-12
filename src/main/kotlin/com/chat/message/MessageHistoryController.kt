package com.chat.message

import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController

@RestController
class MessageHistoryController(private val history: MessageHistory) {

    @GetMapping("/api/messages")
    fun recent(): List<Message> = history.recent()
}
