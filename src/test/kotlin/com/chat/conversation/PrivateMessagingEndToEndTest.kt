package com.chat.conversation

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import java.net.CookieManager
import java.net.CookiePolicy
import java.net.InetSocketAddress
import java.net.URI
import java.net.URLDecoder
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.net.http.WebSocket
import java.time.Duration
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.messaging.simp.user.SimpUserRegistry
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import tools.jackson.databind.json.JsonMapper

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = [
        "spring.datasource.url=jdbc:sqlite::memory:",
        "GH_OAUTH_REDIRECT_URI=https://chat.example.test/login/oauth2/code/github"
    ]
)
@ActiveProfiles("test")
class PrivateMessagingEndToEndTest {
    @LocalServerPort private var port: Int = 0
    @Autowired private lateinit var json: JsonMapper
    @Autowired private lateinit var registry: SimpUserRegistry

    companion object {
        private val nextUser = AtomicInteger(1)
        private val github = HttpServer.create(InetSocketAddress("localhost", 0), 0).apply {
            createContext("/authorize") { exchange ->
                val query = parameters(exchange.requestURI.rawQuery)
                val callback = query.getValue("redirect_uri")
                val state = URLEncoder.encode(query.getValue("state"), Charsets.UTF_8)
                exchange.responseHeaders.add("Location", "$callback?code=${nextUser.get()}&state=$state")
                exchange.sendResponseHeaders(302, -1)
                exchange.close()
            }
            createContext("/token") { exchange ->
                val form = parameters(exchange.requestBody.bufferedReader().readText())
                respond(exchange, """{"access_token":"${form.getValue("code")}","token_type":"bearer","scope":"read:user"}""")
            }
            createContext("/user") { exchange ->
                val id = exchange.requestHeaders.getFirst("Authorization").substringAfter("Bearer ")
                respond(exchange, """{"id":$id,"login":"user$id"}""")
            }
            start()
        }

        private fun parameters(value: String): Map<String, String> =
            value.split("&").associate {
                val pair = it.split("=", limit = 2)
                URLDecoder.decode(pair[0], Charsets.UTF_8) to URLDecoder.decode(pair[1], Charsets.UTF_8)
            }

        private fun respond(exchange: HttpExchange, body: String) {
            val bytes = body.toByteArray()
            exchange.responseHeaders.add("Content-Type", "application/json")
            exchange.sendResponseHeaders(200, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }

        @JvmStatic
        @DynamicPropertySource
        fun provider(properties: DynamicPropertyRegistry) {
            val base = "http://localhost:${github.address.port}"
            properties.add("spring.security.oauth2.client.provider.github.authorization-uri") { "$base/authorize" }
            properties.add("spring.security.oauth2.client.provider.github.token-uri") { "$base/token" }
            properties.add("spring.security.oauth2.client.provider.github.user-info-uri") { "$base/user" }
            properties.add("spring.security.oauth2.client.registration.github.redirect-uri") {
                "{baseUrl}/login/oauth2/code/{registrationId}"
            }
        }

        @JvmStatic
        @AfterAll
        fun stopProvider() {
            github.stop(0)
        }
    }

    private fun login(id: Int): HttpClient {
        nextUser.set(id)
        val client = HttpClient.newBuilder()
            .cookieHandler(CookieManager(null, CookiePolicy.ACCEPT_ALL))
            .followRedirects(HttpClient.Redirect.ALWAYS)
            .connectTimeout(Duration.ofSeconds(5)).build()
        val response = get(client, "/oauth2/authorization/github")
        assertThat(response.statusCode()).isEqualTo(200)
        assertThat(response.uri().path).isEqualTo("/")
        assertThat(json.readTree(get(client, "/api/session").body()).get("user").get("id").asString())
            .isEqualTo("$id")
        return client
    }

    private fun get(client: HttpClient, path: String): HttpResponse<String> =
        client.send(
            HttpRequest.newBuilder(URI("http://localhost:$port$path")).timeout(Duration.ofSeconds(10)).GET().build(),
            HttpResponse.BodyHandlers.ofString()
        )

    @Test
    fun loginAndLogoutRedirectsStayOnTheBrowsersOriginBehindAProxy() {
        nextUser.set(1)
        val client = HttpClient.newBuilder()
            .cookieHandler(CookieManager(null, CookiePolicy.ACCEPT_ALL))
            .followRedirects(HttpClient.Redirect.NEVER)
            .connectTimeout(Duration.ofSeconds(5)).build()

        val protected = get(client, "/")
        assertThat(protected.statusCode()).isEqualTo(302)
        assertThat(protected.headers().firstValue("location").orElseThrow()).isEqualTo("/login")

        val start = get(client, "/oauth2/authorization/github")
        val providerUri = URI(start.headers().firstValue("location").orElseThrow())
        val provider = client.send(
            HttpRequest.newBuilder(providerUri).GET().build(), HttpResponse.BodyHandlers.ofString()
        )
        val callbackUri = URI(provider.headers().firstValue("location").orElseThrow())
        val callback = client.send(
            HttpRequest.newBuilder(callbackUri).GET().build(), HttpResponse.BodyHandlers.ofString()
        )
        assertThat(callback.statusCode()).isEqualTo(302)
        assertThat(callback.headers().firstValue("location").orElseThrow()).isEqualTo("/")

        val csrf = json.readTree(get(client, "/api/session").body())
        val logout = client.send(
            HttpRequest.newBuilder(URI("http://localhost:$port/logout"))
                .header(csrf.get("csrfHeader").asString(), csrf.get("csrfToken").asString())
                .POST(HttpRequest.BodyPublishers.noBody()).build(),
            HttpResponse.BodyHandlers.ofString()
        )
        assertThat(logout.statusCode()).isEqualTo(302)
        assertThat(logout.headers().firstValue("location").orElseThrow()).isEqualTo("/login?logout")
    }

    @Test
    fun sockJsAcceptsConfiguredProxyOriginButRejectsUntrustedOrigins() {
        val client = login(1)
        fun info(origin: String): HttpResponse<String> = client.send(
            HttpRequest.newBuilder(URI("http://localhost:$port/chat-websocket/info"))
                .header("Origin", origin).GET().build(),
            HttpResponse.BodyHandlers.ofString()
        )

        assertThat(info("https://chat.example.test").statusCode()).isEqualTo(200)
        assertThat(info("https://untrusted.example.test").statusCode()).isEqualTo(403)

        fun post(path: String, body: String, origin: String = "https://chat.example.test"): HttpResponse<String> =
            client.send(
                HttpRequest.newBuilder(URI("http://localhost:$port$path"))
                    .timeout(Duration.ofSeconds(10))
                    .header("Origin", origin)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body)).build(),
                HttpResponse.BodyHandlers.ofString()
            )

        val endpoint = "/chat-websocket/123/test-session"
        assertThat(post("$endpoint/xhr", "", "https://untrusted.example.test").statusCode()).isEqualTo(403)
        val open = post("$endpoint/xhr", "")
        assertThat(open.statusCode()).isEqualTo(200)
        assertThat(open.body()).startsWith("o")
        val session = json.readTree(get(client, "/api/session").body())
        val connect = "CONNECT\naccept-version:1.2\nhost:localhost\n" +
            "${session.get("csrfHeader").asString()}:${session.get("csrfToken").asString()}\n\n\u0000"
        val sent = post("$endpoint/xhr_send", json.writeValueAsString(listOf(connect)))
        assertThat(sent.statusCode()).isEqualTo(204)
        val connected = post("$endpoint/xhr", "")
        assertThat(connected.statusCode()).isEqualTo(200)
        assertThat(connected.body()).contains("CONNECTED")

        assertThat(post("$endpoint/xhr_send", json.writeValueAsString(listOf(
            "SUBSCRIBE\nid:public\ndestination:/topic/public\n\n\u0000"
        ))).statusCode()).isEqualTo(204)
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
        while (registry.users.sumOf { user -> user.sessions.sumOf { it.subscriptions.size } } < 1 &&
            System.nanoTime() < deadline) {
            Thread.sleep(20)
        }
        assertThat(registry.users.sumOf { user -> user.sessions.sumOf { it.subscriptions.size } }).isEqualTo(1)
        val send = "SEND\ndestination:/app/chat.sendMessage\ncontent-type:application/json\n\n" +
            """{"content":"Hello through proxy"}""" + "\u0000"
        assertThat(post("$endpoint/xhr_send", json.writeValueAsString(listOf(send))).statusCode()).isEqualTo(204)
        assertThat(post("$endpoint/xhr", "").body()).contains("Hello through proxy")

        val websocket = connect(client, Frames(), "https://chat.example.test")
        websocket.sendClose(WebSocket.NORMAL_CLOSURE, "Test complete").join()
    }

    private class Frames : WebSocket.Listener {
        val frames = LinkedBlockingQueue<String>()
        val closed = LinkedBlockingQueue<Int>()
        private val text = StringBuilder()

        override fun onOpen(webSocket: WebSocket) {
            webSocket.request(1)
        }

        override fun onText(webSocket: WebSocket, data: CharSequence, last: Boolean): java.util.concurrent.CompletionStage<*>? {
            text.append(data)
            if (last) {
                frames.add(text.toString())
                text.setLength(0)
            }
            webSocket.request(1)
            return null
        }

        override fun onClose(webSocket: WebSocket, statusCode: Int, reason: String): java.util.concurrent.CompletionStage<*>? {
            closed.add(statusCode)
            return null
        }

        fun await(prefix: String): String {
            val frame = frames.poll(10, TimeUnit.SECONDS)
            assertThat(frame).describedAs("Expected STOMP $prefix frame").isNotNull().startsWith(prefix)
            return requireNotNull(frame)
        }
    }

    private fun connect(client: HttpClient, frames: Frames, origin: String = "http://localhost:$port"): WebSocket {
        val session = json.readTree(get(client, "/api/session").body())
        val socket = client.newWebSocketBuilder()
            .header("Origin", origin)
            .buildAsync(URI("ws://localhost:$port/chat-websocket/websocket"), frames).join()
        socket.sendText(
            "CONNECT\naccept-version:1.2\nhost:localhost\n${session.get("csrfHeader").asString()}:${session.get("csrfToken").asString()}\n\n\u0000",
            true
        ).join()
        frames.await("CONNECTED")
        socket.sendText("SUBSCRIBE\nid:private\ndestination:/user/queue/private\n\n\u0000", true).join()
        return socket
    }

    @Test
    fun githubLoginAndPrivateDeliveryWorkAcrossRealHttpAndWebSocketSessions() {
        val alice = login(1)
        val bob = login(2)
        val eve = login(3)
        val aliceFrames = Frames()
        val bobFrames = Frames()
        val eveFrames = Frames()
        val sockets = mutableListOf<WebSocket>()
        try {
            val aliceSocket = connect(alice, aliceFrames).also(sockets::add)
            connect(bob, bobFrames).also(sockets::add)
            connect(eve, eveFrames).also(sockets::add)
            aliceSocket.sendText("SUBSCRIBE\nid:errors\ndestination:/user/queue/errors\n\n\u0000", true).join()
            val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
            while (registry.users.sumOf { user -> user.sessions.sumOf { it.subscriptions.size } } < 4 &&
                System.nanoTime() < deadline) {
                Thread.sleep(20)
            }
            assertThat(registry.users.sumOf { user -> user.sessions.sumOf { it.subscriptions.size } }).isEqualTo(4)

            aliceSocket.sendText(
                "SEND\ndestination:/app/chat.sendPrivateMessage\ncontent-type:application/json\n\n" +
                    """{"recipientId":"2","content":" "}""" + "\u0000", true
            ).join()
            assertThat(aliceFrames.await("MESSAGE")).contains("Private messages must contain")
            assertThat(bobFrames.frames.poll(100, TimeUnit.MILLISECONDS)).isNull()

            aliceSocket.sendText(
                "SEND\ndestination:/app/chat.sendPrivateMessage\ncontent-type:application/json\n\n" +
                    """{"recipientId":"2","content":"Only Alice and Bob"}""" + "\u0000", true
            ).join()
            val delivered = bobFrames.await("MESSAGE")
            assertThat(delivered).contains("Only Alice and Bob")
            assertThat(aliceFrames.await("MESSAGE")).contains("Only Alice and Bob")
            assertThat(eveFrames.frames.poll(300, TimeUnit.MILLISECONDS)).isNull()

            val bobHistory = json.readValue(get(bob, "/api/conversations/1/messages").body(), Array<PrivateMessage>::class.java)
            assertThat(bobHistory.single().senderId).isEqualTo("1")
            assertThat(bobHistory.single().recipientId).isEqualTo("2")
            assertThat(json.readValue(get(eve, "/api/conversations/1/messages").body(), Array<PrivateMessage>::class.java))
                .isEmpty()

            val csrf = json.readTree(get(bob, "/api/session").body())
            bob.send(
                HttpRequest.newBuilder(URI("http://localhost:$port/logout"))
                    .header(csrf.get("csrfHeader").asString(), csrf.get("csrfToken").asString())
                    .POST(HttpRequest.BodyPublishers.noBody()).build(),
                HttpResponse.BodyHandlers.ofString()
            )
            assertThat(bobFrames.closed.poll(5, TimeUnit.SECONDS)).isEqualTo(1008)
            aliceSocket.sendText(
                "SEND\ndestination:/app/chat.sendPrivateMessage\ncontent-type:application/json\n\n" +
                    """{"recipientId":"2","content":"Saved while Bob is offline"}""" + "\u0000", true
            ).join()
            assertThat(aliceFrames.await("MESSAGE")).contains("Saved while Bob is offline")
            val returningBob = login(2)
            val restored = json.readValue(
                get(returningBob, "/api/conversations/1/messages").body(), Array<PrivateMessage>::class.java
            )
            assertThat(restored.map { it.content }).containsExactly("Only Alice and Bob", "Saved while Bob is offline")
        } finally {
            sockets.filter { !it.isOutputClosed }.forEach { it.sendClose(WebSocket.NORMAL_CLOSURE, "Test complete").join() }
        }
    }
}
