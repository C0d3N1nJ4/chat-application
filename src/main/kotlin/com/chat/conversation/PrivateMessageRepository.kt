package com.chat.conversation

import java.time.Instant
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository

@Repository
class PrivateMessageRepository(private val jdbc: JdbcTemplate) {
    fun save(senderId: String, request: PrivateMessageRequest): PrivateMessage {
        val id = jdbc.queryForObject(
            "INSERT INTO private_messages (sender_id, recipient_id, content, timestamp) VALUES (?, ?, ?, ?) RETURNING id",
            Long::class.java, senderId, request.recipientId, request.content, Instant.now().toString()
        )!!
        return query("WHERE pm.id = ?", id).single()
    }

    fun recent(userId: String, otherId: String): List<PrivateMessage> =
        query(
            """WHERE (pm.sender_id = ? AND pm.recipient_id = ?)
                OR (pm.sender_id = ? AND pm.recipient_id = ?)
                ORDER BY pm.id DESC LIMIT 100""",
            userId, otherId, otherId, userId
        ).reversed()

    private fun query(suffix: String, vararg arguments: Any): List<PrivateMessage> =
        jdbc.query(
            """SELECT pm.*, sender.username AS sender, recipient.username AS recipient
                FROM private_messages pm
                JOIN users sender ON sender.id = pm.sender_id
                JOIN users recipient ON recipient.id = pm.recipient_id $suffix""",
            { row, _ ->
                PrivateMessage(
                    row.getLong("id"), row.getString("sender_id"), row.getString("sender"),
                    row.getString("recipient_id"), row.getString("recipient"),
                    row.getString("content"), row.getString("timestamp")
                )
            },
            *arguments
        )
}
