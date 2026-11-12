package com.chat.conversation

import com.chat.message.Message
import com.chat.message.MessageRepository
import com.chat.message.MessageType
import com.chat.user.ChatUser
import com.chat.user.UserRepository
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.messaging.MessageHeaders
import org.springframework.messaging.converter.CompositeMessageConverter
import org.springframework.messaging.support.MessageBuilder
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.oauth2.core.user.DefaultOAuth2User
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oauth2Login
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl
import org.springframework.test.web.servlet.setup.DefaultMockMvcBuilder
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.context.WebApplicationContext
import tools.jackson.databind.json.JsonMapper
import org.springframework.util.MimeTypeUtils

@SpringBootTest(properties = ["spring.datasource.url=jdbc:sqlite::memory:"])
@ActiveProfiles("test")
class PrivateMessagingTests {
    @Autowired lateinit var context: WebApplicationContext
    @Autowired lateinit var users: UserRepository
    @Autowired lateinit var service: PrivateMessageService
    @Autowired lateinit var publicMessages: MessageRepository
    @Autowired lateinit var jdbc: JdbcTemplate
    @Autowired lateinit var json: JsonMapper
    @Autowired lateinit var brokerMessageConverter: CompositeMessageConverter
    private lateinit var mvc: MockMvc

    @BeforeEach
    fun setup() {
        jdbc.update("DELETE FROM private_messages")
        jdbc.update("DELETE FROM users")
        jdbc.update("DELETE FROM messages")
        users.save(ChatUser("1", "alice"))
        users.save(ChatUser("2", "bob"))
        users.save(ChatUser("3", "eve"))
        mvc = MockMvcBuilders.webAppContextSetup(context)
            .apply<DefaultMockMvcBuilder>(springSecurity()).build()
    }

    private fun login(id: String) = oauth2Login().oauth2User(
        DefaultOAuth2User(
            setOf(SimpleGrantedAuthority("ROLE_USER")),
            mapOf("id" to id, "login" to users.requireUser(id).username), "id"
        )
    )

    @Test
    fun privateHistoryIsLimitedToTheAuthenticatedParticipants() {
        val sent = service.send("1", PrivateMessageRequest("2", "Only Alice and Bob"))
        service.send("2", PrivateMessageRequest("1", "Reply"))
        service.send("3", PrivateMessageRequest("2", "Separate conversation"))

        val aliceResponse = mvc.perform(get("/api/conversations/2/messages").with(login("1")))
            .andExpect(status().isOk).andReturn().response.contentAsString
        val bobResponse = mvc.perform(get("/api/conversations/1/messages").with(login("2")))
            .andExpect(status().isOk).andReturn().response.contentAsString
        val eveResponse = mvc.perform(get("/api/conversations/1/messages").with(login("3")))
            .andExpect(status().isOk).andReturn().response.contentAsString

        assertThat(json.readValue(aliceResponse, Array<PrivateMessage>::class.java).map { it.content })
            .containsExactly("Only Alice and Bob", "Reply")
        assertThat(bobResponse).isEqualTo(aliceResponse)
        assertThat(json.readValue(eveResponse, Array<PrivateMessage>::class.java)).isEmpty()
        assertThat(sent.senderId).isEqualTo("1")
        assertThat(sent.sender).isEqualTo("alice")
        assertThat(sent.timestamp).isNotBlank()
        assertThat(publicMessages.recent(publicMessages.latestId())).isEmpty()
    }

    @Test
    fun anonymousUsersCannotReadHistoryOrOpenWebSocket() {
        mvc.perform(get("/api/conversations/2/messages")).andExpect(status().is3xxRedirection)
        mvc.perform(get("/api/messages")).andExpect(status().is3xxRedirection)
        mvc.perform(get("/chat-websocket/info")).andExpect(status().is3xxRedirection)
    }

    @Test
    fun anonymousUsersSeeLoginPageAndCanStartGithubSignIn() {
        mvc.perform(get("/api/messages")).andExpect(redirectedUrl("/login"))
        val page = mvc.perform(get("/login")).andExpect(status().isOk)
            .andReturn().response.contentAsString

        assertThat(page).contains("Sign in with GitHub", "href=\"/oauth2/authorization/github\"")
            .doesNotContain("GitHub sign-in failed.", "You have been signed out.", "/main.js")
        mvc.perform(get("/styles.css")).andExpect(status().isOk)
        val authorization = mvc.perform(get("/oauth2/authorization/github"))
            .andExpect(status().is3xxRedirection).andReturn().response.redirectedUrl
        assertThat(authorization).startsWith("https://github.com/login/oauth/authorize?")
    }

    @Test
    fun loginPageShowsOnlyTheRelevantStatusMessage() {
        val failed = mvc.perform(get("/login").param("error", ""))
            .andExpect(status().isOk).andReturn().response.contentAsString
        assertThat(failed).contains("GitHub sign-in failed.").doesNotContain("You have been signed out.")

        val signedOut = mvc.perform(get("/login").param("logout", ""))
            .andExpect(status().isOk).andReturn().response.contentAsString
        assertThat(signedOut).contains("You have been signed out.").doesNotContain("GitHub sign-in failed.")
    }

    @Test
    fun privateHistoryIsBoundedChronologicalAndSurvivesRepositoryRecreation() {
        repeat(105) { service.send("1", PrivateMessageRequest("2", "$it")) }
        val recreated = PrivateMessageRepository(jdbc)
        assertThat(recreated.recent("2", "1").map { it.content })
            .containsExactlyElementsOf((5..104).map { "$it" })
    }

    @Test
    fun invalidRecipientAndInvalidContentAreRejectedWithoutSaving() {
        listOf(
            PrivateMessageRequest("missing", "hello"),
            PrivateMessageRequest("1", "hello"),
            PrivateMessageRequest("2", " "),
            PrivateMessageRequest("2", "x".repeat(4001))
        ).forEach { request ->
            assertThatThrownBy { service.send("1", request) }.isInstanceOf(IllegalArgumentException::class.java)
        }
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM private_messages", Int::class.java)).isZero()
    }

    @Test
    fun websocketJsonPayloadCanBeConvertedToPrivateMessageRequest() {
        val payload = MessageBuilder.withPayload("""{"recipientId":"2","content":"hello"}""".toByteArray())
            .setHeader(MessageHeaders.CONTENT_TYPE, MimeTypeUtils.APPLICATION_JSON).build()

        assertThat(brokerMessageConverter.fromMessage(payload, PrivateMessageRequest::class.java))
            .isEqualTo(PrivateMessageRequest("2", "hello"))
    }

    @Test
    fun existingPublicHistoryRemainsPublicOnlyAndUsersUseStableGithubIds() {
        publicMessages.save(Message(MessageType.CHAT, "Public", "legacy", "2026-11-12T20:00:00Z"))
        service.send("1", PrivateMessageRequest("2", "Private"))
        users.save(ChatUser("1", "renamed-alice"))

        assertThat(service.recent("1", "2").single().sender).isEqualTo("renamed-alice")
        assertThat(publicMessages.recent(publicMessages.latestId()).map { it.content }).containsExactly("Public")
        assertThat(users.others("1").map { it.id }).containsExactly("2", "3")
        mvc.perform(get("/")).andExpect(status().is3xxRedirection)
        mvc.perform(get("/").with(login("1"))).andExpect(status().isOk)
        val session = mvc.perform(get("/api/session").with(login("1")))
            .andExpect(status().isOk).andReturn().response.contentAsString
        assertThat(json.readTree(session).get("csrfToken").asString()).isNotBlank()
    }
}
