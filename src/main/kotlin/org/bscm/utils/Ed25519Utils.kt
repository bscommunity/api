package org.bscm.utils

import org.bouncycastle.crypto.params.Ed25519PublicKeyParameters
import org.bouncycastle.crypto.signers.Ed25519Signer

private fun String.hexToBytes(): ByteArray {
    require(length % 2 == 0) { "Hex inválido" }
    return chunked(2).map { it.toInt(16).toByte() }.toByteArray()
}

fun verifyEd25519(publicKeyHex: String, signatureHex: String, message: ByteArray): Boolean {
    return try {
        val pubKey = Ed25519PublicKeyParameters(publicKeyHex.lowercase().hexToBytes(), 0)
        val verifier = Ed25519Signer()
        verifier.init(false, pubKey)
        verifier.update(message, 0, message.size)
        verifier.verifySignature(signatureHex.lowercase().hexToBytes())
    } catch (e: Exception) {
        false
    }
}

