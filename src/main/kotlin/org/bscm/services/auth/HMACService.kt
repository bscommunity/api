package org.bscm.services.auth

import java.util.*
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

class HMACService(
    private val secret: String,
) {
    fun calculateSignature(payload: String): String {
        val secretKeySpec = SecretKeySpec(secret.toByteArray(), "HmacSHA256")
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(secretKeySpec)
        val hashBytes = mac.doFinal(payload.toByteArray())
        return Base64.getEncoder().encodeToString(hashBytes)
    }

    fun verifySignature(providedSignature: String, expectedSignature: String): Boolean {
        // Use constant-time comparison to prevent timing attacks
        if (providedSignature.length != expectedSignature.length) {
            // println("Signature lengths do not match")
            return false
        }

        var result = 0
        for (i in providedSignature.indices) {
            result = result or (providedSignature[i].code xor expectedSignature[i].code)
        }

        // println("Signature result: $result")

        return result == 0
    }
}