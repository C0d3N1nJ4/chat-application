package com.chat.config

import java.net.URI
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Configuration
import org.springframework.messaging.simp.config.MessageBrokerRegistry
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker
import org.springframework.web.socket.config.annotation.StompEndpointRegistry
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer
import org.springframework.web.socket.config.annotation.WebSocketTransportRegistration


@Configuration
@EnableWebSocketMessageBroker
class WebSocketConfig(
    private val sessions: WebSocketSessionLifecycle,
    @param:Value("\${GH_OAUTH_REDIRECT_URI:}") private val redirectUri: String
): WebSocketMessageBrokerConfigurer {

    override fun configureMessageBroker(config: MessageBrokerRegistry) {
        config.enableSimpleBroker("/topic", "/queue")
        config.setApplicationDestinationPrefixes("/app")
    }

    override fun registerStompEndpoints(registry: StompEndpointRegistry) {
        val endpoint = registry.addEndpoint("/chat-websocket")
            .addInterceptors(sessions)
        if (redirectUri.isNotBlank()) {
            val callback = URI(redirectUri)
            require(callback.scheme in listOf("http", "https") && callback.host != null && callback.userInfo == null) {
                "GH_OAUTH_REDIRECT_URI must be an absolute HTTP(S) URL without user info"
            }
            val origin = URI(callback.scheme, null, callback.host, callback.port, null, null, null)
            endpoint.setAllowedOrigins(origin.toASCIIString())
        }
        endpoint.withSockJS()
    }

    override fun configureWebSocketTransport(registration: WebSocketTransportRegistration) {
        registration.addDecoratorFactory(sessions)
    }
}
