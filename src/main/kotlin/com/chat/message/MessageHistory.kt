package com.chat.message

import java.time.Duration
import org.slf4j.LoggerFactory
import org.springframework.dao.DataAccessException
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Service
import tools.jackson.core.JacksonException
import tools.jackson.databind.json.JsonMapper

@Service
class MessageHistory(
    private val repository: MessageRepository,
    private val redis: StringRedisTemplate,
    private val json: JsonMapper
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    fun recent(): List<Message> {
        // Versioned keys prevent concurrent readers from caching stale history after a write.
        val latestId = repository.latestId()
        val key = "chat:history:v1:${repository.cacheNamespace()}:$latestId"
        try {
            redis.opsForValue().get(key)?.let {
                try {
                    return json.readValue(it, Array<Message>::class.java).toList()
                } catch (exception: JacksonException) {
                    logger.warn("Invalid cached message history; reloading from SQLite", exception)
                }
            }
        } catch (exception: DataAccessException) {
            logger.warn("Redis history read failed; using SQLite", exception)
            return repository.recent(latestId)
        }

        val messages = repository.recent(latestId)
        try {
            redis.opsForValue().set(key, json.writeValueAsString(messages), Duration.ofSeconds(60))
        } catch (exception: DataAccessException) {
            logger.warn("Redis history write failed; history remains available in SQLite", exception)
        }
        return messages
    }
}
