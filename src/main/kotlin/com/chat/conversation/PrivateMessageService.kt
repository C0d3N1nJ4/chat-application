package com.chat.conversation

import com.chat.user.UserRepository
import org.springframework.stereotype.Service

@Service
class PrivateMessageService(
    private val repository: PrivateMessageRepository,
    private val users: UserRepository
) {
    fun send(senderId: String, request: PrivateMessageRequest): PrivateMessage {
        users.requireUser(senderId)
        require(request.recipientId != senderId) { "Choose another user for a private message." }
        require(users.find(request.recipientId) != null) { "Recipient not found. Refresh the user list." }
        require(request.content.isNotBlank() && request.content.length <= 4000) {
            "Private messages must contain between 1 and 4000 characters."
        }
        return repository.save(senderId, request)
    }

    fun recent(userId: String, otherId: String): List<PrivateMessage> {
        users.requireUser(userId)
        return repository.recent(userId, otherId)
    }
}
