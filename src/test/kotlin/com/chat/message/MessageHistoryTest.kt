package com.chat.message

import java.time.Duration
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.Mockito.RETURNS_DEEP_STUBS
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.mockito.Mockito.doThrow
import org.springframework.data.redis.RedisConnectionFailureException
import org.springframework.data.redis.core.StringRedisTemplate
import tools.jackson.databind.json.JsonMapper

class MessageHistoryTest {
    private val repository = mock(MessageRepository::class.java).also {
        `when`(it.cacheNamespace()).thenReturn("test")
    }
    private val redis = mock(StringRedisTemplate::class.java, RETURNS_DEEP_STUBS)
    private val json = JsonMapper.builder().build()
    private val history = MessageHistory(repository, redis, json)
    private val message = Message(MessageType.CHAT, "hello", "alice", "2026-11-12T20:00:00Z")
        .apply { id = 1 }

    @Test
    fun cacheMissPopulatesCacheAndNextReadUsesIt() {
        `when`(repository.latestId()).thenReturn(1)
        `when`(repository.recent(1)).thenReturn(listOf(message))
        val encoded = json.writeValueAsString(listOf(message))

        assertThat(history.recent()).containsExactly(message)
        verify(redis.opsForValue()).set("chat:history:v1:test:1", encoded, Duration.ofSeconds(60))

        `when`(redis.opsForValue().get("chat:history:v1:test:1")).thenReturn(encoded)
        assertThat(history.recent().single()).usingRecursiveComparison().isEqualTo(message)
        verify(repository).recent(1)
    }

    @Test
    fun newDatabaseVersionDoesNotUsePreviousCacheEntry() {
        `when`(repository.latestId()).thenReturn(2)
        `when`(repository.recent(2)).thenReturn(listOf(message))

        assertThat(history.recent()).containsExactly(message)

        verify(redis.opsForValue()).get("chat:history:v1:test:2")
        verify(redis.opsForValue(), never()).get("chat:history:v1:test:1")
    }

    @Test
    fun unavailableRedisFallsBackToSqlite() {
        `when`(repository.latestId()).thenReturn(1)
        `when`(repository.recent(1)).thenReturn(listOf(message))
        `when`(redis.opsForValue().get("chat:history:v1:test:1"))
            .thenThrow(RedisConnectionFailureException("Redis unavailable"))

        assertThat(history.recent()).containsExactly(message)
    }

    @Test
    fun malformedCacheEntryIsReplacedFromSqlite() {
        `when`(repository.latestId()).thenReturn(1)
        `when`(repository.recent(1)).thenReturn(listOf(message))
        `when`(redis.opsForValue().get("chat:history:v1:test:1")).thenReturn("not json")

        assertThat(history.recent()).containsExactly(message)
        verify(redis.opsForValue()).set(
            "chat:history:v1:test:1", json.writeValueAsString(listOf(message)), Duration.ofSeconds(60)
        )
    }

    @Test
    fun cacheWriteFailureDoesNotHidePersistedHistory() {
        `when`(repository.latestId()).thenReturn(1)
        `when`(repository.recent(1)).thenReturn(listOf(message))
        val values = redis.opsForValue()
        doThrow(RedisConnectionFailureException("Redis unavailable"))
            .`when`(values).set(
                "chat:history:v1:test:1", json.writeValueAsString(listOf(message)), Duration.ofSeconds(60)
            )

        assertThat(history.recent()).containsExactly(message)
    }
}
