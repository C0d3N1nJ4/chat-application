'use strict';

var messageForm = document.querySelector('#messageForm');
var messageInput = document.querySelector('#message');
var messageArea = document.querySelector('#messageArea');
var connectingElement = document.querySelector('.connecting');

var stompClient = null;
var username = 'guest-' + Math.floor(Math.random() * 10000);
var messageHistory = [];

function connect() {
    var socket = new SockJS('/chat-websocket');
    stompClient = Stomp.over(socket);

    stompClient.connect({}, onConnected, onError);
}

function onConnected() {
    // Subscribe to the Public Topic
    stompClient.subscribe('/topic/public', onMessageReceived);

    // Tell your username to the server
    stompClient.send("/app/chat.addUser",
        {},
        JSON.stringify({sender: username, type: 'JOIN', timestamp: new Date().toISOString()})
    );

    connectingElement.classList.add('hidden');
}

function onError(error) {
    connectingElement.textContent = 'Could not connect to WebSocket server. Please refresh this page to try again!';
    connectingElement.style.color = 'red';
}

function sendMessage(event) {
    var messageContent = messageInput.value.trim();

    if(messageContent && stompClient) {
        var chatMessage = {
            sender: username,
            content: messageContent,
            type: 'CHAT',
            timestamp: new Date().toISOString()
        };

        stompClient.send("/app/chat.sendMessage", {}, JSON.stringify(chatMessage));
        messageInput.value = '';
    }
    event.preventDefault();
}

function onMessageReceived(payload) {
    var message = JSON.parse(payload.body);
    messageHistory.push(message);
    renderMessage(message);
    messageArea.scrollTop = messageArea.scrollHeight;
}

function renderMessage(message) {
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

    messageArea.appendChild(messageElement);
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
connect();
