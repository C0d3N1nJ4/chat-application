const assert = require('node:assert/strict');
const { readFileSync } = require('node:fs');
const { test } = require('node:test');
const vm = require('node:vm');

function client(response) {
    function element() {
        return {
            children: [],
            classList: { add() {}, remove() {} },
            style: {},
            appendChild(child) { this.children.push(child); },
            addEventListener() {}
        };
    }
    const elements = new Map();
    const errors = [];
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
        Stomp: { over: () => ({ connect() {}, subscribe() {}, send() {} }) },
        fetch: () => response,
        console: { error: (...args) => errors.push(args) }
    });
    vm.runInContext(readFileSync('src/main/resources/static/main.js', 'utf8'), context);
    context.onConnected();
    return { context, elements, errors };
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

test('failed history request displays an error and does not block live messages', async () => {
    const { context, elements, errors } = client(Promise.resolve({ ok: false, status: 500 }));
    context.onMessageReceived({ body: JSON.stringify({ id: 1, type: 'CHAT', content: 'hello' }) });
    await new Promise(setImmediate);

    assert.equal(context.messageHistory.length, 1);
    assert.match(elements.get('.connecting').textContent, /Could not load message history/);
    assert.equal(errors.length, 1);
});
