const assert = require('node:assert/strict');
const { readFileSync } = require('node:fs');
const { test } = require('node:test');
const vm = require('node:vm');

function client(response, privateResponse = response) {
    function element() {
        return {
            children: [],
            classList: { add() {}, remove() {} },
            style: {},
            appendChild(child) { this.children.push(child); },
            replaceChildren() { this.children = []; },
            addEventListener() {}
        };
    }
    const elements = new Map();
    const errors = [];
    const sent = [];
    const context = vm.createContext({
        document: {
            querySelector(selector) {
                if (!elements.has(selector)) elements.set(selector, element());
                return elements.get(selector);
            },
            createElement: element,
            createTextNode: (text) => ({ text })
        },
        SockJS: function() {},
        Stomp: { over: () => ({
            connected: true,
            connect() {},
            subscribe() {},
            send(destination, headers, body) { sent.push({ destination, body }); }
        }) },
        fetch: (url) => {
            if (url === '/api/session') return new Promise(() => {});
            if (url === '/api/users') return Promise.resolve({ ok: true, json: async () => [] });
            if (url.startsWith('/api/conversations/')) return privateResponse;
            return response;
        },
        console: { error: (...args) => errors.push(args) }
    });
    vm.runInContext(readFileSync('src/main/resources/static/main.js', 'utf8'), context);
    context.stompClient = context.Stomp.over();
    context.currentUserId = 'alice-id';
    context.onConnected();
    return { context, elements, errors, sent };
}

test('history and overlapping live delivery are merged without duplicates', async () => {
    let resolve;
    const response = new Promise((done) => { resolve = done; });
    const { context, elements } = client(response);
    const message = (id) => ({ id, type: 'CHAT', sender: 'alice', content: `${id}` });
    context.onMessageReceived({ body: JSON.stringify(message(2)) });
    context.onMessageReceived({ body: JSON.stringify(message(3)) });
    resolve({ ok: true, json: async () => [message(1), message(2)] });
    await new Promise(setImmediate);

    assert.deepEqual(Array.from(context.messageHistory, (item) => item.id), [1, 2, 3]);
    assert.equal(elements.get('#messageArea').children.length, 3);
});

test('private messages never render in public history and are deduplicated', async () => {
    const { context, elements } = client(Promise.resolve({ ok: true, json: async () => [] }));
    await new Promise(setImmediate);
    const message = {
        id: 1, senderId: 'bob-id', sender: 'bob', recipientId: 'alice-id',
        recipient: 'alice', content: 'private hello', timestamp: '2026-11-12T20:00:00Z'
    };
    const payload = { body: JSON.stringify(message) };
    context.onPrivateMessageReceived(payload);
    context.onPrivateMessageReceived(payload);
    assert.equal(context.messageHistory.length, 0);
    assert.equal(elements.get('#messageArea').children.length, 0);
    assert.equal(context.privateHistories.get('bob-id').length, 1);
    context.selectedRecipientId = 'bob-id';
    context.renderPrivateHistory();
    assert.equal(elements.get('#privateMessageArea').children.length, 1);
});

test('private messages use only the private endpoint and do not supply a sender identity', () => {
    const { context, elements, sent } = client(Promise.resolve({ ok: true, json: async () => [] }));
    context.selectedRecipientId = 'bob-id';
    elements.get('#message').value = 'secret';

    context.sendMessage({ preventDefault() {} });

    assert.equal(sent.at(-1).destination, '/app/chat.sendPrivateMessage');
    assert.deepEqual(JSON.parse(sent.at(-1).body), { recipientId: 'bob-id', content: 'secret' });
    assert.equal(sent.filter((item) => item.destination === '/app/chat.sendMessage').length, 0);
});

test('late private history response merges live messages without switching conversations', async () => {
    let resolve;
    const response = new Promise((done) => { resolve = done; });
    const { context, elements } = client(Promise.resolve({ ok: true, json: async () => [] }), response);
    elements.get('#recipient').value = 'bob-id';
    context.selectConversation();
    const message = { id: 2, senderId: 'bob-id', sender: 'bob', recipientId: 'alice-id', content: 'live' };
    context.onPrivateMessageReceived({ body: JSON.stringify(message) });
    elements.get('#recipient').value = '';
    context.selectConversation();
    resolve({ ok: true, json: async () => [{ ...message, id: 1, content: 'earlier' }, message] });
    await new Promise(setImmediate);

    assert.equal(context.selectedRecipientId, '');
    assert.deepEqual(Array.from(context.privateHistories.get('bob-id'), (item) => item.id), [1, 2]);
    assert.equal(elements.get('#messageArea').children.length, 0);
});

test('failed history request displays an error and does not block live messages', async () => {
    const { context, elements, errors } = client(Promise.resolve({ ok: false, status: 500 }));
    context.onMessageReceived({ body: JSON.stringify({ id: 1, type: 'CHAT', content: 'hello' }) });
    await new Promise(setImmediate);

    assert.equal(context.messageHistory.length, 1);
    assert.match(elements.get('.connecting').textContent, /Could not load message history/);
    assert.equal(errors.length, 1);
});
