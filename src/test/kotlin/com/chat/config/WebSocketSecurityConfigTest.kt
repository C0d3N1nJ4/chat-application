package com.chat.config

import java.util.function.Supplier
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.springframework.messaging.MessageChannel
import org.springframework.messaging.simp.SimpMessageHeaderAccessor
import org.springframework.messaging.simp.SimpMessageType
import org.springframework.messaging.support.MessageBuilder
import org.springframework.security.authentication.AnonymousAuthenticationToken
import org.springframework.security.authentication.TestingAuthenticationToken
import org.springframework.security.core.Authentication
import org.springframework.security.core.authority.AuthorityUtils
import org.springframework.security.messaging.access.intercept.MessageMatcherDelegatingAuthorizationManager
import org.springframework.security.messaging.web.csrf.CsrfChannelInterceptor
import org.springframework.security.web.csrf.CsrfToken
import org.springframework.security.web.csrf.DefaultCsrfToken
import org.springframework.security.web.csrf.InvalidCsrfTokenException

class WebSocketSecurityConfigTest {
    private val authorization = WebSocketSecurityConfig().messageAuthorizationManager(
        MessageMatcherDelegatingAuthorizationManager.builder()
    )
    private val authenticated = TestingAuthenticationToken("1", "", "ROLE_USER")

    private fun allowed(type: SimpMessageType, destination: String, user: Authentication = authenticated): Boolean {
        val headers = SimpMessageHeaderAccessor.create(type)
        headers.destination = destination
        val message = MessageBuilder.createMessage(ByteArray(0), headers.messageHeaders)
        return authorization.authorize(Supplier { user }, message)?.isGranted == true
    }

    @Test
    fun usersMaySubscribeOnlyToPublicAndTheirOwnLogicalQueues() {
        assertThat(allowed(SimpMessageType.SUBSCRIBE, "/topic/public")).isTrue()
        assertThat(allowed(SimpMessageType.SUBSCRIBE, "/user/queue/private")).isTrue()
        assertThat(allowed(SimpMessageType.SUBSCRIBE, "/user/queue/errors")).isTrue()
        assertThat(allowed(SimpMessageType.SUBSCRIBE, "/user/2/queue/private")).isFalse()
        assertThat(allowed(SimpMessageType.SUBSCRIBE, "/queue/private-userOtherSession")).isFalse()
        assertThat(allowed(SimpMessageType.SUBSCRIBE, "/app/chat.sendPrivateMessage")).isFalse()
    }

    @Test
    fun clientsCannotSendDirectlyToBrokerOrUseAnonymousSessions() {
        assertThat(allowed(SimpMessageType.MESSAGE, "/app/chat.sendPrivateMessage")).isTrue()
        assertThat(allowed(SimpMessageType.MESSAGE, "/topic/public")).isFalse()
        assertThat(allowed(SimpMessageType.MESSAGE, "/user/2/queue/private")).isFalse()
        val anonymous = AnonymousAuthenticationToken(
            "test", "anonymous", AuthorityUtils.createAuthorityList("ROLE_ANONYMOUS")
        )
        assertThat(allowed(SimpMessageType.SUBSCRIBE, "/user/queue/private", anonymous)).isFalse()
        assertThat(allowed(SimpMessageType.MESSAGE, "/app/chat.sendPrivateMessage", anonymous)).isFalse()
    }

    @Test
    fun connectRequiresSessionCsrfToken() {
        val csrf = DefaultCsrfToken("X-CSRF-TOKEN", "_csrf", "session-token")
        val headers = SimpMessageHeaderAccessor.create(SimpMessageType.CONNECT)
        headers.sessionAttributes = mutableMapOf(CsrfToken::class.java.name to csrf)
        val channel = mock(MessageChannel::class.java)
        val interceptor = CsrfChannelInterceptor()
        assertThatThrownBy {
            interceptor.preSend(MessageBuilder.createMessage(ByteArray(0), headers.messageHeaders), channel)
        }.isInstanceOf(InvalidCsrfTokenException::class.java)

        val validHeaders = SimpMessageHeaderAccessor.create(SimpMessageType.CONNECT)
        validHeaders.sessionAttributes = mutableMapOf(CsrfToken::class.java.name to csrf)
        validHeaders.setNativeHeader(csrf.headerName, csrf.token)
        val valid = MessageBuilder.createMessage(ByteArray(0), validHeaders.messageHeaders)
        assertThat(interceptor.preSend(valid, channel)).isSameAs(valid)
    }
}
