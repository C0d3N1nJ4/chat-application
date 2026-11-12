package com.chat.message

import java.time.Instant
import org.springframework.stereotype.Service

@Service
class MessageService(
    private val repository: MessageRepository,
    private val cache: MessageHistoryCache
) {
    fun save(message: Message): Message {
        if (message.timestamp.isNullOrBlank()) {
            message.timestamp = Instant.now().toString()
        }
        return repository.save(message)
    }

    fun recent(): List<Message> {
        // Versioned keys prevent concurrent readers from caching stale history after a write.
        val latestId = repository.latestId()
        return cache.getOrLoad(repository.cacheNamespace(), latestId) {
            repository.recent(latestId)
        }
    }
}
