package com.chat.message

import java.nio.file.Path
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.springframework.core.io.ClassPathResource
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.datasource.DriverManagerDataSource
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator

class MessageRepositoryTest {

    @TempDir
    lateinit var directory: Path

    private fun repository(): MessageRepository {
        val source = DriverManagerDataSource("jdbc:sqlite:${directory.resolve("messages.db")}")
        ResourceDatabasePopulator(ClassPathResource("schema.sql")).execute(source)
        return MessageRepository(JdbcTemplate(source))
    }

    @Test
    fun messagesSurviveReopeningDatabase() {
        val original = Message(MessageType.CHAT, "Alice's hello", "alice", "2026-11-12T20:00:00Z")
        val first = repository()
        first.save(original)

        val reopened = repository()
        val restored = reopened.recent(reopened.latestId()).single()

        assertThat(restored).usingRecursiveComparison().isEqualTo(original)
        assertThat(restored.id).isPositive()
        assertThat(reopened.cacheNamespace()).isEqualTo(first.cacheNamespace())
    }

    @Test
    fun recentHistoryIsBoundedChronologicalAndUsesRequestedSnapshot() {
        val repository = repository()
        assertThat(repository.recent(repository.latestId())).isEmpty()
        repeat(105) { index ->
            repository.save(Message(MessageType.CHAT, "$index", "alice", "2026-11-12T20:00:00Z"))
        }
        val snapshot = repository.latestId()
        repository.save(Message(MessageType.JOIN, "", "bob", "2026-11-12T20:01:00Z"))

        val messages = repository.recent(snapshot)

        assertThat(messages).hasSize(100)
        assertThat(messages.map { it.content }).containsExactlyElementsOf((5..104).map { "$it" })
        assertThat(repository.recent(repository.latestId()).last().type).isEqualTo(MessageType.JOIN)
    }
}
