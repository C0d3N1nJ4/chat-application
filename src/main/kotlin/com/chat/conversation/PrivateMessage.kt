package com.chat.conversation

data class PrivateMessageRequest(val recipientId: String, val content: String)

data class PrivateMessage(
    val id: Long,
    val senderId: String,
    val sender: String,
    val recipientId: String,
    val recipient: String,
    val content: String,
    val timestamp: String
)
