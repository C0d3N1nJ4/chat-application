# chat-application

This is a simple Kotlin chat application that uses websockets to send messages between clients. 
The application is built using Spring Boot and Thymeleaf.

## Database

The application uses an in-memory H2 database. 
The database is initialized with some data when the application starts.
Configuration properties can be found in the `application.properties` file.

## Websockets

The application uses websockets to send messages between clients.

## Planned features

- Security (future implementation)

## Pre-conditions

Before running this project, make sure the following are available:

- Linux/macOS shell (or WSL on Windows)
- Java 21+ available on PATH (Gradle must run on Java 17+)
- Gradle 9.x installed globally (this repository does not include a Gradle wrapper)
- Internet access for dependency/toolchain resolution on first build

Recommended environment for this repository:

```bash
export JAVA_HOME=/usr/local/sdkman/candidates/java/21.0.10-ms
export PATH=$JAVA_HOME/bin:$PATH
```

## Running the application

To run the application, use:

```bash
gradle bootRun
```

To run tests:

```bash
gradle test
```