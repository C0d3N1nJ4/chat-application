package com.chat.message;

import org.junit.jupiter.api.Test;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MessageControllerTest {

    private final MessageController controller = new MessageController();

    @Test
    void sendMessageReturnsSameMessage() {
        Message message = new Message(MessageType.CHAT, "hi", "alice");

        Message result = controller.sendMessage(message);

        assertThat(result).isSameAs(message);
        assertThat(result.getTimestamp()).isNotBlank();
    }

    @Test
    void sendMessageKeepsProvidedTimestamp() {
        Message message = new Message(MessageType.CHAT, "hi", "alice", "2026-06-21T15:23:00Z");

        Message result = controller.sendMessage(message);

        assertThat(result.getTimestamp()).isEqualTo("2026-06-21T15:23:00Z");
    }

    @Test
    void addUserStoresUsernameWhenSenderIsPresent() {
        Message message = new Message(MessageType.JOIN, "", "dave");
        SimpMessageHeaderAccessor headerAccessor = mock(SimpMessageHeaderAccessor.class);
        Map<String, Object> sessionAttributes = new HashMap<>();
        when(headerAccessor.getSessionAttributes()).thenReturn(sessionAttributes);

        Message result = controller.addUser(message, headerAccessor);

        assertThat(result).isSameAs(message);
        assertThat(result.getTimestamp()).isNotBlank();
        assertThat(sessionAttributes).containsEntry("username", "dave");
    }

    @Test
    void addUserDoesNotStoreUsernameWhenSenderIsNull() {
        Message message = new Message(MessageType.JOIN, "", null);
        SimpMessageHeaderAccessor headerAccessor = mock(SimpMessageHeaderAccessor.class);
        Map<String, Object> sessionAttributes = new HashMap<>();
        when(headerAccessor.getSessionAttributes()).thenReturn(sessionAttributes);

        Message result = controller.addUser(message, headerAccessor);

        assertThat(result).isSameAs(message);
        assertThat(sessionAttributes).doesNotContainKey("username");
    }
}
