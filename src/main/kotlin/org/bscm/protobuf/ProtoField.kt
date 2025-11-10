package org.bscm.protobuf

/**
 * Classe base para todos os tipos de campos Protobuf.
 */
abstract class ProtoField(
    val name: String?,
    val field: Int,
    val options: Map<String, Any?> = emptyMap()
) {
    abstract fun read(reader: Any?, proto: ProtoField, fullProto: Map<Int, ProtoField>? = null): Any?

    protected fun parseKey(
        buffer: ProtobufReader,
        key: ProtobufKey,
        message: ProtobufMessage,
        proto: ProtoField,
        fields: Map<Int, ProtoField>
    ) {
        val subField = fields[key.field]
        if (subField != null) {
            val value = subField.read(buffer, subField, fields)
            message.addField(subField.name ?: key.field.toString(), value)
        } else {
            val value = buffer.parseUnknown(key)
            message.addField(key.field.toString(), value)
        }
    }
}

/**
 * Campo Varint (inteiros variáveis).
 */
class VarintField(
    name: String?,
    field: Int,
    options: Map<String, Any?> = emptyMap()
) : ProtoField(name, field, options) {
    
    override fun read(reader: Any?, proto: ProtoField, fullProto: Map<Int, ProtoField>?): Any? {
        if (reader !is ProtobufReader) return reader

        val repeating = options["repeating"] as? Boolean ?: false
        val packed = options["packed"] as? Boolean ?: false
        val signed = options["signed"] as? Boolean ?: false

        if (repeating && packed) {
            return readPackedRepeating(reader, signed)
        }

        val key = reader.readVarint()

        if (repeating) {
            val varints = mutableListOf<Int>()
            while (reader.hasNext()) {
                varints.add(reader.readVarint().actual)
                if (!reader.hasNext()) return varints
                
                val subKey = reader.readVarint(peek = true)
                if (subKey.field != key.field) return varints
                reader.readVarint() // consume the key
            }
            return varints
        }

        return reader.readVarint(signed = signed).actual
    }

    private fun readPackedRepeating(reader: ProtobufReader, signed: Boolean): List<Int> {
        val key = reader.readKey()
        val length = reader.readVarint().actual
        val subBuffer = reader.slice(reader.index, length)
        
        val varints = mutableListOf<Int>()
        while (subBuffer.hasNext()) {
            varints.add(subBuffer.readVarint(signed = signed).actual)
        }
        
        return varints
    }
}

/**
 * Campo String.
 */
class StringField(
    name: String?,
    field: Int,
    options: Map<String, Any?> = emptyMap()
) : ProtoField(name, field, options) {
    
    override fun read(reader: Any?, proto: ProtoField, fullProto: Map<Int, ProtoField>?): Any? {
        if (reader !is ProtobufReader) return reader

        val repeating = options["repeating"] as? Boolean ?: false
        val packed = options["packed"] as? Boolean ?: false

        if (repeating) {
            return if (packed) {
                readRepeatingWithKey(reader)
            } else {
                readRepeatingWithLength(reader)
            }
        }

        val key = reader.readVarint()
        val length = reader.readVarint().actual
        if (length < 0) {
            throw IllegalStateException("StringField.read: negative length=$length")
        }
        return reader.slice(reader.index, length).toString()
    }

    private fun readRepeatingWithKey(reader: ProtobufReader): List<String> {
        val strings = mutableListOf<String>()
        val key = reader.readVarint()
        val totalLength = reader.readVarint().actual
        val buffer = reader.slice(reader.index, totalLength)

        while (buffer.hasNext()) {
            val length = buffer.readVarint().actual
            strings.add(buffer.slice(buffer.index, length).toString())
        }

        return strings
    }

    private fun readRepeatingWithLength(reader: ProtobufReader): List<String> {
        val strings = mutableListOf<String>()
        
        while (reader.hasNext()) {
            val key = reader.readVarint(peek = true)
            if (key.field != field) break
            
            reader.readVarint() // consume key
            val length = reader.readVarint().actual
            strings.add(reader.slice(reader.index, length).toString())
        }

        return strings
    }
}

/**
 * Campo Float.
 */
class FloatField(
    name: String?,
    field: Int
) : ProtoField(name, field) {
    
    override fun read(reader: Any?, proto: ProtoField, fullProto: Map<Int, ProtoField>?): Any? {
        if (reader !is ProtobufReader) return reader
        
        reader.readVarint() // read key
        return reader.readFloat()
    }
}

/**
 * Campo Group (mensagem aninhada).
 */
class GroupField(
    name: String?,
    field: Int,
    val fields: Map<Int, ProtoField>,
    options: Map<String, Any?> = emptyMap()
) : ProtoField(name, field, options) {
    
    override fun read(reader: Any?, proto: ProtoField, fullProto: Map<Int, ProtoField>?): Any? {
        if (reader !is ProtobufReader) return reader

        val repeating = options["repeating"] as? Boolean ?: false

        return if (repeating) {
            readRepeating(reader)
        } else {
            readNormal(reader)
        }
    }

    private fun readRepeating(reader: ProtobufReader): List<Map<String, Any?>> {
        val messages = mutableListOf<Map<String, Any?>>()

        while (reader.hasNext()) {
            val message = ProtobufMessage()
            val key = reader.readVarint(peek = true)
            
            if (key.field != field) {
                return messages
            }
            
            reader.readVarint() // consume key
            val length = reader.readVarint().actual
            val buffer = reader.slice(reader.index, length)
            
            while (buffer.hasNext()) {
                val subKey = buffer.readVarint(peek = true)
                parseKey(buffer, subKey, message, this, fields)
            }
            
            messages.add(message.finalize())
        }

        return messages
    }

    private fun readNormal(reader: ProtobufReader): Map<String, Any?> {
        reader.readKey() // consume key
        val length = reader.readVarint().actual
        val buffer = reader.slice(reader.index, length)
        val message = ProtobufMessage()

        while (buffer.hasNext()) {
            val key = buffer.readKey(peek = true)
            parseKey(buffer, key, message, this, fields)
        }

        return message.finalize()
    }
}

/**
 * Campo PackedMessage (lista de mensagens empacotadas).
 */
class PackedMessageField(
    name: String?,
    field: Int,
    val fields: Map<Int, ProtoField>,
    options: Map<String, Any?> = emptyMap()
) : ProtoField(name, field, options) {
    
    override fun read(reader: Any?, proto: ProtoField, fullProto: Map<Int, ProtoField>?): Any? {
        if (reader !is ProtobufReader) return reader

        val messages = mutableListOf<Map<String, Any?>>()
        val packedKey = reader.readVarint()
        val packedLength = reader.readVarint().actual
        val packedBuffer = reader.slice(reader.index, packedLength)

        while (packedBuffer.hasNext()) {
            val message = ProtobufMessage()
            val messageLength = packedBuffer.readVarint().actual
            val messageBuffer = packedBuffer.slice(packedBuffer.index, messageLength)

            while (messageBuffer.hasNext()) {
                val key = messageBuffer.readVarint(peek = true)
                parseKey(messageBuffer, key, message, this, fields)
            }
            
            messages.add(message.finalize())
        }

        return messages
    }
}
