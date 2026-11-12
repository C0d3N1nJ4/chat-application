package com.chat.config

import org.slf4j.LoggerFactory
import org.springframework.dao.DataAccessException
import org.springframework.messaging.handler.annotation.MessageExceptionHandler
import org.springframework.messaging.simp.annotation.SendToUser
import org.springframework.security.access.AccessDeniedException
import org.springframework.web.bind.annotation.ControllerAdvice

data class ChatError(val error: String)

@ControllerAdvice
class MessageErrorHandler {
    private val logger = LoggerFactory.getLogger(javaClass)

    @MessageExceptionHandler(IllegalArgumentException::class, AccessDeniedException::class)
    @SendToUser("/queue/errors", broadcast = false)
    fun rejected(exception: RuntimeException): ChatError {
        logger.warn("Chat message rejected: {}", exception.message)
        return ChatError(exception.message ?: "Message rejected.")
    }

    @MessageExceptionHandler(DataAccessException::class)
    @SendToUser("/queue/errors", broadcast = false)
    fun persistenceFailure(exception: DataAccessException): ChatError {
        logger.error("Could not persist chat message", exception)
        return ChatError("Message could not be saved. Please try again.")
    }
}
