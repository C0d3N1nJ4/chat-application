'use strict';

var messageForm = document.querySelector('#messageForm');
var messageInput = document.querySelector('#message');
var messageArea = document.querySelector('#messageArea');
var connectingElement = document.querySelector('.connecting');
var recipientSelect = document.querySelector('#recipient');
var privateMessageArea = document.querySelector('#privateMessageArea');
var chatStatus = document.querySelector('#chatStatus');

var stompClient = null;
var username = null;
var currentUserId = null;
var selectedRecipientId = '';
var privateHistories = new Map();
var messageHistory = [];
var historyLoaded = false;
var pendingMessages = [];
var displayedMessageIds = new Set();

function connect() {
    fetch('/api/session')
        .then(readResponse)
        .then(function(session) {
            currentUserId = session.user.id;
            username = session.user.username;
            document.querySelector('#currentUser').textContent = username;
            var socket = new SockJS('/chat-websocket');
            stompClient = Stomp.over(socket);
            stompClient.debug = null;
            var headers = {};
            headers[session.csrfHeader] = session.csrfToken;
            stompClient.connect(headers, onConnected, onError);
        })
        .catch(onError);
}

function onConnected() {
    // Subscribe to the Public Topic
    stompClient.subscribe('/topic/public', onMessageReceived);
    stompClient.subscribe('/user/queue/private', onPrivateMessageReceived);
    stompClient.subscribe('/user/queue/errors', function(payload) {
        chatStatus.textContent = JSON.parse(payload.body).error;
    });
    refreshUsers();

    fetch('/api/messages')
        .then(function(response) {
            if (!response.ok) {
                throw new Error('History request failed: HTTP ' + response.status);
            }
            return response.json();
        })
        .then(function(messages) {
            messages.forEach(appendMessage);
            finishHistoryLoad();
        })
        .catch(function(error) {
            console.error('Could not load message history', error);
            connectingElement.textContent = 'Could not load message history. Live messages are still available; refresh to retry.';
            connectingElement.style.color = 'red';
            connectingElement.classList.remove('hidden');
            finishHistoryLoad();
        });

    // Tell your username to the server
    stompClient.send("/app/chat.addUser",
        {},
        JSON.stringify({sender: username, type: 'JOIN', timestamp: new Date().toISOString()})
    );

    connectingElement.classList.add('hidden');
}

function onError(error) {
    console.error('Chat connection failed', error);
    connectingElement.textContent = 'Could not connect to WebSocket server. Please refresh this page to try again!';
    connectingElement.style.color = 'red';
    connectingElement.classList.remove('hidden');
}

function sendMessage(event) {
    var messageContent = messageInput.value.trim();

    if(messageContent && stompClient && stompClient.connected) {
        if (selectedRecipientId) {
            stompClient.send('/app/chat.sendPrivateMessage', {}, JSON.stringify({
                recipientId: selectedRecipientId,
                content: messageContent
            }));
            messageInput.value = '';
            event.preventDefault();
            return;
        }
        var chatMessage = {
            content: messageContent,
            type: 'CHAT',
            timestamp: new Date().toISOString()
        };

        stompClient.send("/app/chat.sendMessage", {}, JSON.stringify(chatMessage));
        messageInput.value = '';
    } else if (messageContent) {
        chatStatus.textContent = 'Not connected. Please refresh this page before sending.';
    }
    event.preventDefault();
}

function onMessageReceived(payload) {
    var message = JSON.parse(payload.body);
    if (!historyLoaded) {
        pendingMessages.push(message);
        return;
    }
    appendMessage(message);
}

function finishHistoryLoad() {
    historyLoaded = true;
    pendingMessages.sort(function(a, b) { return a.id - b.id; }).forEach(appendMessage);
    pendingMessages = [];
}

function appendMessage(message) {
    if (message.id != null) {
        if (displayedMessageIds.has(message.id)) {
            return;
        }
        displayedMessageIds.add(message.id);
    }
    messageHistory.push(message);
    renderMessage(message);
    messageArea.scrollTop = messageArea.scrollHeight;
}

function renderMessage(message, area) {
    var messageElement = document.createElement('li');
    var metaElement = document.createElement('small');
    metaElement.classList.add('message-meta');

    var sender = message.sender || 'system';
    metaElement.textContent = sender + ' • ' + formatTimestamp(message.timestamp);
    messageElement.appendChild(metaElement);

    if(message.type === 'JOIN') {
        messageElement.classList.add('event-message');
        message.content = sender + ' joined!';
    } else if (message.type === 'LEAVE') {
        messageElement.classList.add('event-message');
        message.content = sender + ' left!';
    } else {
        messageElement.classList.add('chat-message');

        var usernameElement = document.createElement('strong');
        usernameElement.classList.add('nickname');
        var usernameText = document.createTextNode(sender);
        usernameElement.appendChild(usernameText);
        messageElement.appendChild(usernameElement);
    }

    var textElement = document.createElement('span');
    var messageText = document.createTextNode(message.content || '');
    textElement.appendChild(messageText);

    messageElement.appendChild(textElement);

    (area || messageArea).appendChild(messageElement);
}

function readResponse(response) {
    if (!response.ok) {
        throw new Error('Request failed: HTTP ' + response.status);
    }
    return response.json();
}

function refreshUsers() {
    fetch('/api/users')
        .then(readResponse)
        .then(function(users) {
            recipientSelect.replaceChildren();
            var publicOption = document.createElement('option');
            publicOption.value = '';
            publicOption.textContent = 'Public chat';
            recipientSelect.appendChild(publicOption);
            users.forEach(function(user) {
                var option = document.createElement('option');
                option.value = user.id;
                option.textContent = 'Private: ' + user.username;
                recipientSelect.appendChild(option);
            });
            recipientSelect.value = selectedRecipientId;
        })
        .catch(function(error) {
            console.error('Could not load users', error);
            chatStatus.textContent = 'Could not load users. Use Refresh users to retry.';
        });
}

function selectConversation() {
    selectedRecipientId = recipientSelect.value;
    chatStatus.textContent = '';
    if (!selectedRecipientId) {
        messageArea.classList.remove('hidden');
        privateMessageArea.classList.add('hidden');
        return;
    }
    messageArea.classList.add('hidden');
    privateMessageArea.classList.remove('hidden');
    renderPrivateHistory();
    var recipientId = selectedRecipientId;
    fetch('/api/conversations/' + encodeURIComponent(recipientId) + '/messages')
        .then(readResponse)
        .then(function(messages) {
            mergePrivateHistory(recipientId, messages);
            if (selectedRecipientId === recipientId) {
                renderPrivateHistory();
            }
        })
        .catch(function(error) {
            console.error('Could not load private history', error);
            if (selectedRecipientId === recipientId) {
                chatStatus.textContent = 'Could not load private history. Select the conversation again to retry.';
            }
        });
}

function mergePrivateHistory(recipientId, messages) {
    var byId = new Map();
    (privateHistories.get(recipientId) || []).concat(messages).forEach(function(message) {
        byId.set(message.id, message);
    });
    privateHistories.set(recipientId, Array.from(byId.values()).sort(function(a, b) { return a.id - b.id; }));
}

function onPrivateMessageReceived(payload) {
    var message = JSON.parse(payload.body);
    var recipientId = message.senderId === currentUserId ? message.recipientId : message.senderId;
    mergePrivateHistory(recipientId, [message]);
    if (selectedRecipientId === recipientId) {
        renderPrivateHistory();
    } else if (message.senderId !== currentUserId) {
        chatStatus.textContent = 'New private message from ' + message.sender + '. Select their conversation to read it.';
    }
}

function renderPrivateHistory() {
    privateMessageArea.replaceChildren();
    (privateHistories.get(selectedRecipientId) || []).forEach(function(message) {
        renderMessage(message, privateMessageArea);
    });
    privateMessageArea.scrollTop = privateMessageArea.scrollHeight;
}

function formatTimestamp(rawTimestamp) {
    if(!rawTimestamp) {
        return 'unknown time';
    }

    var parsedDate = new Date(rawTimestamp);
    if(Number.isNaN(parsedDate.getTime())) {
        return rawTimestamp;
    }

    return parsedDate.toLocaleString();
}

messageForm.addEventListener('submit', sendMessage, true);
recipientSelect.addEventListener('change', selectConversation);
document.querySelector('#refreshUsers').addEventListener('click', refreshUsers);
connect();
