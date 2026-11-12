# Chat Application

A WebSocket chat application built with **Spring Boot 4.0** and **Kotlin**.

## Features

- ✅ Real-time messaging via WebSocket
- ✅ Built with Kotlin for type safety and conciseness
- ✅ Spring Boot with reactive capabilities
- ✅ Clean separation of concerns (Controller, Config, Message models)
- ✅ Persistent message history in SQLite with a Redis cache
- ✅ GitHub sign-in and durable private conversations
- ✅ Responsive web UI with separate CSS styling

## Architecture

- **Backend**: Spring Boot 4.0 + Kotlin
- **Frontend**: Thymeleaf templates + HTML/CSS/JavaScript
- **WebSocket**: SockJS + STOMP protocol
- **Database**: SQLite (durable storage), Redis (recent-history cache)
- **Build**: Gradle

The project is organized by feature with simple layers:

- Controllers handle HTTP and WebSocket requests.
- `MessageService` coordinates message persistence, timestamps, and history reads.
- `MessageRepository` contains SQLite queries.
- `MessageHistoryCache` handles Redis caching and cache-failure fallback.

The dependency flow is **Controller → Service → Repository / Cache**. Public
messaging, private conversations, and user accounts live in the `message`,
`conversation`, and `user` packages. Page routing lives in `web` and shared
security and WebSocket configuration lives in `config`.

## Project Structure

```
src/
├── main/
│   ├── kotlin/com/chat/
│   │   ├── ChatApplication.kt       # Main Spring Boot application
│   │   ├── web/
│   │   │   └── HomeController.kt    # Index page routing
│   │   ├── config/
│   │   │   ├── SecurityConfig.kt    # GitHub OAuth login
│   │   │   ├── WebSocketSecurityConfig.kt # STOMP authorization and CSRF
│   │   │   ├── WebSocketSessionLifecycle.kt # Close sockets on logout/session expiry
│   │   │   ├── MessageErrorHandler.kt # Per-session messaging errors
│   │   │   └── WebSocketConfig.kt   # WebSocket configuration
│   │   ├── user/                   # GitHub account directory and session API
│   │   ├── conversation/           # Private messages, service, repository, endpoints
│   │   └── message/
│   │       ├── Message.kt           # Message model
│   │       ├── MessageType.kt       # Chat and presence event types
│   │       ├── MessageController.kt # WebSocket endpoints
│   │       ├── MessageService.kt    # Persistence and history coordination
│   │       ├── MessageRepository.kt # SQLite persistence
│   │       ├── MessageHistoryCache.kt # Redis history caching
│   │       └── MessageHistoryController.kt # History HTTP endpoint
│   └── resources/
│       ├── templates/
│       │   ├── index.html           # Web UI
│       │   └── login.html           # GitHub sign-in page
│       ├── static/
│       │   ├── styles.css           # Stylesheet
│       │   └── main.js              # Client-side logic
│       ├── schema.sql               # SQLite schema initialization
│       └── application.properties   # Configuration
└── test/
    ├── kotlin/                     # Unit and integration tests
    └── javascript/                 # Client history tests
```

## Pre-conditions

Before running this project, ensure you have:

- **JDK 25** (required by the Gradle Java toolchain)
- **Gradle 9.x** installed globally
- **Docker Compose** for the optional local Redis cache, or an existing Redis server
- Linux/macOS or WSL on Windows

### Recommended Setup

```bash
export JAVA_HOME=/path/to/java-25
export PATH=$JAVA_HOME/bin:$PATH
```

## Running the Application

### Configure GitHub sign-in

Chat pages, history endpoints, and WebSocket connections require login.
Unauthenticated visitors are redirected to `/login`, where they can select
**Sign in with GitHub**. This page also displays sign-in failures and sign-out
confirmation; the login page and its stylesheet are publicly accessible.
Create a GitHub OAuth App under **GitHub Settings > Developer settings > OAuth Apps**:

- Homepage URL: `http://localhost:8080`
- Authorization callback URL: `http://localhost:8080/login/oauth2/code/github`

Set these environment variables before starting the application:

```bash
export GH_OAUTH_CLIENT_ID='your-client-id'
export GH_OAUTH_CLIENT_SECRET='your-client-secret'
```

Use Codespaces secrets named `GH_OAUTH_CLIENT_ID` and `GH_OAUTH_CLIENT_SECRET`
for credentials in a Codespace; never commit them. GitHub reserves the `GITHUB_`
prefix, so secret names must not start with it.
For Codespaces, register the forwarded HTTPS URL in your OAuth App and explicitly
set the matching callback URL:

```bash
export GH_OAUTH_REDIRECT_URI='https://YOUR-CODESPACE-8080.app.github.dev/login/oauth2/code/github'
```

The WebSocket endpoint also permits the exact origin of `GH_OAUTH_REDIRECT_URI`,
so the forwarded page can connect while other origins remain blocked. Without
this setting, WebSocket connections are limited to the application's own origin.

Without this override, the callback is derived from the incoming request's base URL.
Internal login and logout redirects are relative so they remain on the forwarded
browser URL rather than redirecting to the Codespace's localhost.
Only the `read:user` scope is requested. SQLite stores GitHub IDs and usernames,
not passwords or GitHub access tokens. Missing OAuth credentials prevent startup.
Tests use dummy credentials and mocked GitHub identities; they do not contact GitHub.

### Run with Gradle

Start the local Redis cache, then run the application:

```bash
docker compose up -d redis
gradle bootRun
```

The application will start on `http://localhost:8080`

Sign in with GitHub. Another user must sign in once before they appear in the
conversation selector. Select **Private: username** to exchange private messages,
or **Public chat** to return to the shared room. Use **Refresh users** to discover
new accounts. Recipients can be offline and read messages after their next login.

### Run Tests

```bash
gradle test
```

The client history-loading tests use Node.js's built-in test runner (Node.js 18+):

```bash
node --test src/test/javascript/main.test.js
```

## Configuration

Application settings can be modified in `src/main/resources/application.properties`:

```properties
server.port=8080
spring.datasource.url=${CHAT_DATABASE_URL:jdbc:sqlite:chat.db}
spring.data.redis.host=${REDIS_HOST:localhost}
spring.data.redis.port=${REDIS_PORT:6379}
```

### Message persistence

Chat messages and join events are stored in `chat.db` in the working directory
before being broadcast. This file is ignored by Git; keep it to preserve history
across application restarts. To move it, set `CHAT_DATABASE_URL` to a SQLite JDBC
URL such as `jdbc:sqlite:/absolute/path/chat.db` (the parent directory must exist).

On connection, the browser loads the latest 100 messages from `GET /api/messages`
in chronological order and merges them with live messages without duplicates.
SQLite retains all messages; the 100-message limit only applies to history reads.

Redis caches history for 60 seconds. Cache keys include a database-specific
namespace and the latest persisted message ID so separate databases do not share
history and new writes do not reuse older history. Redis is not the source of
truth: cache failures are logged and history falls back to SQLite. Database
failures are not hidden, and messages that fail to persist are not broadcast.
The cache does not need a persistent volume.

Stop the local cache with `docker compose down`. If using an external Redis
server, configure `REDIS_HOST`, `REDIS_PORT`, and, if needed, Spring's
`spring.data.redis.password` property through your environment.

### Private conversations

Private messages are stored separately from public history in SQLite. They are
never placed in the public Redis history cache or broadcast to `/topic/public`.
The server derives the sender from the authenticated GitHub identity, persists
the message, then delivers it to the sender's and recipient's `/user/queue/private`
subscriptions (including their other connected sessions).

`GET /api/conversations/{recipientId}/messages` returns the latest 100 messages
between the authenticated user and that recipient, oldest first. Callers cannot
supply another sender ID to read someone else's conversation. GitHub numeric IDs
are used as identity keys so username changes do not lose conversation history.

Messages are limited to 4000 characters; blank messages and unknown recipients are
rejected through `/user/queue/errors`. The browser merges saved and live history
without duplicates. Sign out using the **Sign out** button.
Signing out or ending an HTTP session also closes its WebSocket connections.

Private messages are access-controlled, not end-to-end encrypted. The database
contains plaintext message content; protect the database file and its backups,
and use HTTPS when exposing the app outside localhost.

## WebSocket Configuration

WebSocket is configured in `WebSocketConfig.kt` with:
- Same-origin WebSocket connections (plus the configured callback origin) and authenticated STOMP destinations
- CSRF protection on STOMP CONNECT and HTTP logout
- SockJS fallback for browser compatibility
- STOMP protocol for message routing

## Development

### Spring Boot DevTools

DevTools is enabled for `gradle bootRun` and excluded from the packaged application.
It restarts the application when compiled classes change. After editing Kotlin
sources, compile them from another terminal:

```bash
gradle classes
```

DevTools also disables template caching during development and provides a
LiveReload server for browsers with a LiveReload extension.

### Code Quality

The project uses:
- Kotlin language for type safety
- Spring Boot conventions
- Error Prone compiler for bug detection
- JUnit 5 for testing

### IDE Setup

For IntelliJ IDEA:
1. Ensure Kotlin plugin is installed
2. Mark `src/main/kotlin` as Sources Root
3. Mark `src/test/kotlin` as Test Sources Root
4. Enable Gradle delegation in Build Tools settings

## Planned Features

- [x] GitHub authentication and message authorization
- [x] Message persistence
- [ ] User profiles
- [x] Private messaging
- [x] Recent message history

## Technologies

| Technology | Version | Purpose |
|-----------|---------|---------|
| Spring Boot | 4.0.0 | Web framework |
| Kotlin | 2.2.0 | Language |
| Gradle | 9.x | Build tool |
| SQLite JDBC | 3.50.3.0 | Persistent message storage |
| Redis | 7.4 (local Compose) | Recent-history cache |
| Spring Security OAuth2 Client | Spring Boot managed | GitHub sign-in |
| SockJS | 1.3.0 | WebSocket fallback |
| STOMP | 2.3.3 | Message protocol |

## License

This project is open source and available under the MIT License.