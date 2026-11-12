package com.chat.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.messaging.Message
import org.springframework.messaging.simp.SimpMessageType
import org.springframework.security.authorization.AuthorizationManager
import org.springframework.security.config.annotation.web.socket.EnableWebSocketSecurity
import org.springframework.security.messaging.access.intercept.MessageMatcherDelegatingAuthorizationManager

@Configuration
@EnableWebSocketSecurity
class WebSocketSecurityConfig {
    @Bean
    fun messageAuthorizationManager(
        messages: MessageMatcherDelegatingAuthorizationManager.Builder
    ): AuthorizationManager<Message<*>> =
        messages
            .simpTypeMatchers(
                SimpMessageType.CONNECT, SimpMessageType.DISCONNECT,
                SimpMessageType.UNSUBSCRIBE, SimpMessageType.HEARTBEAT
            ).authenticated()
            .simpSubscribeDestMatchers("/topic/public", "/user/queue/private", "/user/queue/errors")
                .authenticated()
            .simpTypeMatchers(SimpMessageType.SUBSCRIBE).denyAll()
            .simpDestMatchers("/app/chat.sendMessage", "/app/chat.addUser", "/app/chat.sendPrivateMessage")
                .authenticated()
            .anyMessage().denyAll()
            .build()
}
