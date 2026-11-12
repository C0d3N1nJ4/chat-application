CREATE TABLE IF NOT EXISTS message_store (
    id INTEGER PRIMARY KEY CHECK (id = 1),
    namespace TEXT NOT NULL
);
INSERT OR IGNORE INTO message_store (id, namespace) VALUES (1, lower(hex(randomblob(16))));

CREATE TABLE IF NOT EXISTS messages (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    type TEXT,
    content TEXT,
    sender TEXT,
    timestamp TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS users (
    id TEXT PRIMARY KEY,
    username TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS private_messages (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    sender_id TEXT NOT NULL REFERENCES users(id),
    recipient_id TEXT NOT NULL REFERENCES users(id),
    content TEXT NOT NULL,
    timestamp TEXT NOT NULL
);
CREATE INDEX IF NOT EXISTS private_messages_participants
    ON private_messages(sender_id, recipient_id, id);
