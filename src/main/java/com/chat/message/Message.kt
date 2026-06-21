package com.chat.message

class Message {

    var type: MessageType? = null
    var content: String? = null
    var sender: String? = null
    var timestamp: String? = null

    constructor() {}

    constructor(type: MessageType?, content: String?, sender: String?) {
        this.type = type
        this.content = content
        this.sender = sender
    }

    constructor(type: MessageType?, content: String?, sender: String?, timestamp: String?) {
        this.type = type
        this.content = content
        this.sender = sender
        this.timestamp = timestamp
    }

    override fun toString(): String {
        return "Message{" +
                "type='" + type + '\'' +
                ", content='" + content + '\'' +
                ", sender='" + sender + '\'' +
            ", timestamp='" + timestamp + '\'' +
                '}'
    }
}