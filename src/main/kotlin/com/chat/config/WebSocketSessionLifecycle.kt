package com.chat.config

import jakarta.servlet.http.HttpSession
import jakarta.servlet.http.HttpSessionEvent
import jakarta.servlet.http.HttpSessionIdListener
import jakarta.servlet.http.HttpSessionListener
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import org.slf4j.LoggerFactory
import org.springframework.http.server.ServerHttpRequest
import org.springframework.http.server.ServerHttpResponse
import org.springframework.http.server.ServletServerHttpRequest
import org.springframework.security.core.context.SecurityContext
import org.springframework.security.web.context.HttpSessionSecurityContextRepository
import org.springframework.stereotype.Component
import org.springframework.web.socket.CloseStatus
import org.springframework.web.socket.WebSocketHandler
import org.springframework.web.socket.WebSocketSession
import org.springframework.web.socket.handler.WebSocketHandlerDecorator
import org.springframework.web.socket.handler.WebSocketHandlerDecoratorFactory
import org.springframework.web.socket.server.HandshakeInterceptor

@Component
class WebSocketSessionLifecycle : HandshakeInterceptor, WebSocketHandlerDecoratorFactory,
    HttpSessionListener, HttpSessionIdListener {
    private val sockets = ConcurrentHashMap<String, MutableSet<WebSocketSession>>()
    private val logger = LoggerFactory.getLogger(javaClass)

    override fun beforeHandshake(
        request: ServerHttpRequest, response: ServerHttpResponse,
        handler: WebSocketHandler, attributes: MutableMap<String, Any>
    ): Boolean {
        val session = (request as ServletServerHttpRequest).servletRequest.getSession(false) ?: return false
        attributes[SESSION_ATTRIBUTE] = session
        return true
    }

    override fun afterHandshake(
        request: ServerHttpRequest, response: ServerHttpResponse,
        handler: WebSocketHandler, exception: Exception?
    ) {}

    override fun decorate(handler: WebSocketHandler): WebSocketHandler =
        object : WebSocketHandlerDecorator(handler) {
            override fun afterConnectionEstablished(socket: WebSocketSession) {
                val session = socket.attributes[SESSION_ATTRIBUTE] as HttpSession
                sockets.compute(session.id) { _, connected ->
                    (connected ?: ConcurrentHashMap.newKeySet()).apply { add(socket) }
                }
                // Invalidation can race the handshake; register first, then verify the session.
                val authenticated = try {
                    val context = session.getAttribute(
                        HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY
                    ) as? SecurityContext
                    val authentication = context?.authentication
                    authentication != null && authentication.isAuthenticated &&
                        authentication.name == socket.principal?.name
                } catch (exception: IllegalStateException) {
                    logger.debug("HTTP session expired during WebSocket handshake", exception)
                    false
                }
                if (!authenticated) {
                    socket.close(CloseStatus.POLICY_VIOLATION)
                    return
                }
                super.afterConnectionEstablished(socket)
            }

            override fun afterConnectionClosed(socket: WebSocketSession, status: CloseStatus) {
                val session = socket.attributes[SESSION_ATTRIBUTE] as HttpSession
                sockets.computeIfPresent(session.id) { _, connected ->
                    connected.remove(socket)
                    connected.takeIf { it.isNotEmpty() }
                }
                super.afterConnectionClosed(socket, status)
            }
        }

    override fun sessionDestroyed(event: HttpSessionEvent) {
        sockets.remove(event.session.id)?.forEach { socket ->
            try {
                socket.close(CloseStatus.POLICY_VIOLATION.withReason("Session ended. Sign in again."))
            } catch (exception: IOException) {
                logger.warn("Could not close WebSocket for ended HTTP session", exception)
            }
        }
    }

    override fun sessionIdChanged(event: HttpSessionEvent, oldSessionId: String) {
        sockets.remove(oldSessionId)?.let { connected ->
            sockets.compute(event.session.id) { _, existing ->
                (existing ?: ConcurrentHashMap.newKeySet()).apply { addAll(connected) }
            }
        }
    }

    companion object {
        private const val SESSION_ATTRIBUTE = "chat.httpSession"
    }
}
