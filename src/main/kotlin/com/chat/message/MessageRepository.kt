package com.chat.message

import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository

@Repository
class MessageRepository(private val jdbc: JdbcTemplate) {

    fun cacheNamespace(): String =
        jdbc.queryForObject("SELECT namespace FROM message_store WHERE id = 1", String::class.java)!!

    fun save(message: Message): Message {
        message.id = jdbc.queryForObject(
            "INSERT INTO messages (type, content, sender, timestamp) VALUES (?, ?, ?, ?) RETURNING id",
            Long::class.java,
            message.type?.name, message.content, message.sender, message.timestamp
        )
        return message
    }

    fun latestId(): Long =
        jdbc.queryForObject("SELECT COALESCE(MAX(id), 0) FROM messages", Long::class.java)!!

    fun recent(latestId: Long): List<Message> =
        jdbc.query(
            "SELECT id, type, content, sender, timestamp FROM messages WHERE id <= ? ORDER BY id DESC LIMIT 100",
            { row, _ ->
                Message(
                    row.getString("type")?.let { MessageType.valueOf(it) },
                    row.getString("content"),
                    row.getString("sender"),
                    row.getString("timestamp")
                ).apply { id = row.getLong("id") }
            },
            latestId
        ).reversed()
}
