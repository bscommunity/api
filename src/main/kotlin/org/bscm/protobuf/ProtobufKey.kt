package org.bscm.protobuf

/**
 * Represents a Protobuf key with wire type and field number.
 */
data class ProtobufKey(
    val wire: Int,
    val field: Int,
    val actual: Int,
    val length: Int = 0
) {
    val value: String = toBinaryString(actual)

    private fun toBinaryString(b: Int): String {
        return pad(b.toString(2))
    }

    private fun pad(b: String): String {
        return b.padStart(8, '0')
    }
}
