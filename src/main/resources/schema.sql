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
