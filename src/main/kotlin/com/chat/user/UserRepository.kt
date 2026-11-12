package com.chat.user

import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.security.access.AccessDeniedException
import org.springframework.stereotype.Repository

@Repository
class UserRepository(private val jdbc: JdbcTemplate) {
    fun save(user: ChatUser) {
        jdbc.update(
            "INSERT INTO users (id, username) VALUES (?, ?) ON CONFLICT(id) DO UPDATE SET username = excluded.username",
            user.id, user.username
        )
    }

    fun find(id: String): ChatUser? =
        jdbc.query("SELECT id, username FROM users WHERE id = ?", { row, _ ->
            ChatUser(row.getString("id"), row.getString("username"))
        }, id).singleOrNull()

    fun requireUser(id: String): ChatUser =
        find(id) ?: throw AccessDeniedException("Please sign in again.")

    fun others(id: String): List<ChatUser> =
        jdbc.query("SELECT id, username FROM users WHERE id <> ? ORDER BY username", { row, _ ->
            ChatUser(row.getString("id"), row.getString("username"))
        }, id)
}
