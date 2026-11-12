# Chat Application

A WebSocket chat application built with **Spring Boot 4.0** and **Kotlin**.

## Features

- ✅ Real-time messaging via WebSocket
- ✅ Built with Kotlin for type safety and conciseness
- ✅ Spring Boot with reactive capabilities
- ✅ Clean separation of concerns (Controller, Config, Message models)
- ✅ H2 in-memory database
- ✅ Responsive web UI with separate CSS styling

## Architecture

- **Backend**: Spring Boot 4.0 + Kotlin
- **Frontend**: Thymeleaf templates + HTML/CSS/JavaScript
- **WebSocket**: SockJS + STOMP protocol
- **Database**: H2 in-memory
- **Build**: Gradle

## Project Structure

```
src/
├── main/
│   ├── kotlin/com/chat/
│   │   ├── ChatApplication.kt       # Main Spring Boot application
│   │   ├── config/
│   │   │   └── WebSocketConfig.kt   # WebSocket configuration
│   │   ├── controller/
│   │   │   ├── MessageController.kt # WebSocket message handler
│   │   │   └── HomeController.kt    # Index page routing
│   │   └── message/
│   │       └── Message.kt           # Message model
│   └── resources/
│       ├── templates/
│       │   └── index.html           # Web UI
│       ├── static/
│       │   ├── styles.css           # Stylesheet
│       │   └── main.js              # Client-side logic
│       └── application.properties    # Configuration
└── test/
    └── kotlin/                      # Unit and integration tests
```

## Pre-conditions

Before running this project, ensure you have:

- **JDK 25** (required by the Gradle Java toolchain)
- **Gradle 9.x** installed globally
- Linux/macOS or WSL on Windows

### Recommended Setup

```bash
export JAVA_HOME=/path/to/java-25
export PATH=$JAVA_HOME/bin:$PATH
```

## Running the Application

### Run with Gradle

```bash
gradle bootRun
```

The application will start on `http://localhost:8080`

### Run Tests

```bash
gradle test
```

## Configuration

Application settings can be modified in `src/main/resources/application.properties`:

```properties
server.port=8080
spring.datasource.url=jdbc:h2:mem:testdb
spring.h2.console.enabled=true
```

## WebSocket Configuration

WebSocket is configured in `WebSocketConfig.kt` with:
- CORS support for cross-origin requests
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

- [ ] User authentication and authorization
- [ ] Message persistence
- [ ] User profiles
- [ ] Private messaging
- [ ] Message history

## Technologies

| Technology | Version | Purpose |
|-----------|---------|---------|
| Spring Boot | 4.0.0 | Web framework |
| Kotlin | 2.2.0 | Language |
| Gradle | 9.x | Build tool |
| H2 Database | Latest | In-memory DB |
| SockJS | 1.3.0 | WebSocket fallback |
| STOMP | 2.3.3 | Message protocol |

## License

This project is open source and available under the MIT License.