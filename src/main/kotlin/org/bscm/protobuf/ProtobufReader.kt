package org.bscm.protobuf

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.zip.GZIPInputStream

/**
 * Leitor de dados Protobuf customizado para o formato Beatstar.
 */
class ProtobufReader(
    private val data: ByteArray
) {
    var index = 0
    var parsed: MutableMap<Int, Any?> = mutableMapOf()

    fun process() {
        val blocks = processBlocks()
        this.parsed = blocks
    }

    fun <T> parseProto(proto: Map<Int, ProtoField>): Map<String, Any?> {
        val finalMessage = mutableMapOf<String, Any?>()

        proto.forEach { (key, field) ->
            val buffer = parsed[key]
            if (buffer != null) {
                val value = field.read(buffer, field, proto)
                val name = field.name ?: key.toString()
                finalMessage[name] = value
            }
        }

        return finalMessage
    }

    fun hasNext(): Boolean {
        return index < data.size
    }

    fun readByte(): Int {
        if (index >= data.size) {
            throw IndexOutOfBoundsException("readByte: no bytes available (index=$index, size=${data.size})")
        }
        return data[index++].toInt() and 0xFF
    }

    fun read32Bit(): Int {
        if (index + 4 > data.size) {
            throw IndexOutOfBoundsException("read32Bit: need 4 bytes but only ${data.size - index} available")
        }
        val buffer = ByteBuffer.wrap(data, index, 4)
        buffer.order(ByteOrder.LITTLE_ENDIAN)
        index += 4
        return buffer.int
    }

    fun readBig32(): Int {
        if (index + 4 > data.size) {
            throw IndexOutOfBoundsException("readBig32: need 4 bytes but only ${data.size - index} available")
        }
        val buffer = ByteBuffer.wrap(data, index, 4)
        buffer.order(ByteOrder.BIG_ENDIAN)
        index += 4
        return buffer.int
    }

    fun readFloat(): Float {
        if (index + 4 > data.size) {
            throw IndexOutOfBoundsException("readFloat: need 4 bytes but only ${data.size - index} available")
        }
        val buffer = ByteBuffer.wrap(data, index, 4)
        buffer.order(ByteOrder.LITTLE_ENDIAN)
        index += 4
        return buffer.float
    }

    fun read64Bit(): Long {
        if (index + 8 > data.size) {
            throw IndexOutOfBoundsException("read64Bit: need 8 bytes but only ${data.size - index} available")
        }
        val buffer = ByteBuffer.wrap(data, index, 8)
        buffer.order(ByteOrder.LITTLE_ENDIAN)
        index += 8
        return buffer.long
    }

    fun slice(start: Int, length: Int): ProtobufReader {
        if (start < 0) {
            throw IndexOutOfBoundsException("Slice start < 0: $start")
        }

        if (start > data.size) {
            throw IndexOutOfBoundsException("Slice start=$start beyond data size=${data.size}")
        }

        if (start + length > data.size) {
            val available = data.size - start
            throw IndexOutOfBoundsException(
                "Reading outside the bounds of the buffer: " +
                "requested $length bytes at offset $start, but only $available bytes available " +
                "(total size=${data.size})"
            )
        }

        val slicedData = data.copyOfRange(start, start + length)
        index = start + length
        return ProtobufReader(slicedData)
    }

    fun readVarint(peek: Boolean = false, signed: Boolean = false): ProtobufKey {
        val startIndex = index
        val arr = mutableListOf<String>()

        while (true) {
            if (!hasNext()) {
                // No more bytes available; treat as truncated varint
                System.err.println("readVarint: truncated varint at index=$index (no more bytes)")
                break
            }
            val by = readByte()
            val binary = (by and 0xFF).toString(2).padStart(8, '0')
            arr.add(binary.substring(1))
            
            if (binary[0] == '0') {
                break
            }
        }

        val binary = arr.reversed().joinToString("")
        val value = if (signed) {
            // Handle signed integers (zigzag encoding)
            val unsigned = binary.toLongOrNull(2) ?: 0L
            ((unsigned shr 1) xor -(unsigned and 1)).toInt()
        } else {
            binary.toLongOrNull(2)?.toInt() ?: 0
        }

        val actualByte = if (startIndex < data.size) data[startIndex].toInt() and 0xFF else 0
        val key = ProtobufKey(
            wire = actualByte and 0x07,
            field = actualByte shr 3,
            actual = value,
            length = arr.size
        )

        if (peek) {
            index = startIndex
        }

        return key
    }

    fun readKey(peek: Boolean = false): ProtobufKey {
        val key = readVarint(peek)
        return key
    }

    fun parseUnknown(key: ProtobufKey): Any? {
        return when (key.wire) {
            0 -> readVarint().actual
            1 -> read64Bit()
            2 -> {
                val length = readVarint().actual
                slice(index, length)
            }
            5 -> read32Bit()
            else -> null
        }
    }

    private fun processBlocks(): MutableMap<Int, Any?> {
        val info = mutableMapOf<Int, Any?>()
        
        while (hasNext()) {
            val key = readVarint()
            val value: Any? = when (key.wire) {
                0 -> key.actual
                1 -> read64Bit()
                2 -> {
                    // Peek at length to get its size in bytes
                    val lengthVarint = readVarint(peek = true)
                    val lengthActual = lengthVarint.actual
                    
                    // Calculate the start position (before the key)
                    val segmentStart = index - key.length
                    
                    // Consume the length varint for real
                    readVarint()
                    
                    // Total segment size: key.length + lengthVarint.length + lengthActual
                    val segmentLength = key.length + lengthVarint.length + lengthActual
                    
                    // Slice from the key position, including key, length, and payload
                    slice(segmentStart, segmentLength)
                }
                5 -> slice(index, 4)
                else -> null
            }
            
            if (info.containsKey(key.field)) {
                val existing = info[key.field]
                info[key.field] = if (existing is MutableList<*>) {
                    (existing as MutableList<Any?>).apply { add(value) }
                } else {
                    mutableListOf(existing, value)
                }
            } else {
                info[key.field] = value
            }
        }
        
        return info
    }

    fun processHeaderPacket(): List<ProtobufReader> {
        val fullSize = readBig32()
        val packetSize = readBig32()
        val header = slice(index, packetSize)
        header.process()

        var packet = slice(index, data.size - index)
        
        // Check if it's GZIP compressed
        if (packet.data.size >= 2 && 
            packet.data[0].toInt() == 0x1f && 
            packet.data[1].toInt() and 0xFF == 0x8b) {
            val decompressed = GZIPInputStream(packet.data.inputStream()).readBytes()
            packet = ProtobufReader(decompressed)
        }
        
        packet.process()

        return listOf(header, packet)
    }

    override fun toString(): String {
        return data.toString(Charsets.UTF_8)
    }

    /**
     * Retorna quantos bytes restam a partir do index atual.
     */
    fun remaining(): Int {
        return data.size - index
    }
}
