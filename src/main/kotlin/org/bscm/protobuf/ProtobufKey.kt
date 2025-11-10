package org.bscm.protobuf

/**
 * Representa uma chave Protobuf com tipo de wire e número de field.
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
