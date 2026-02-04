package org.bscm.protobuf

/**
 * Represents a Protobuf message during the parsing process.
 */
class ProtobufMessage {
    private val data = mutableMapOf<String, Any?>()
    private var unknownIndex = 0

    fun addField(name: String, value: Any?) {
        val fieldName = if (name == "_") {
            "unknown${unknownIndex++}"
        } else {
            name
        }
        data[fieldName] = value
    }

    fun finalize(): Map<String, Any?> {
        return data.toMap()
    }

    companion object {
        fun from(message: Map<String, Any?>): ProtobufMessage {
            val newMessage = ProtobufMessage()
            message.forEach { (key, value) ->
                newMessage.addField(key, value)
            }
            return newMessage
        }
    }
}
