# chat-application

This is a simple Kotlin chat application that uses websockets to send messages between clients. 
The application is built using Spring Boot and Thymeleaf.

## Database

The application uses an in-memory H2 database. 
The database is initialized with some data when the application starts.
Configuration properties can be found in the `application.properties` file.

## Websockets

The application uses websockets to send messages between clients.

## Security

The application uses Spring Security to secure the endpoints. Users need to be authenticated to access the chat.

## Running the application

To run the application, you can use the following command:

```bash 
./gradlew bootRun
```