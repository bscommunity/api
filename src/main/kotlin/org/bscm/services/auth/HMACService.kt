package org.bscm.services.auth

import java.util.*
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

class HMACService {
    /**
     * Calculates HMAC-SHA256 signature using the provided secret key.
     * @param payload The data to sign
     * @param secret The secret key (typically the app signature)
     * @return Base64-encoded signature
     */
    fun calculateSignature(payload: String, secret: String): String {
        val secretKeySpec = SecretKeySpec(secret.toByteArray(), "HmacSHA256")
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(secretKeySpec)
        val hashBytes = mac.doFinal(payload.toByteArray())
        return Base64.getEncoder().encodeToString(hashBytes)
    }

    /**
     * Verifies the HMAC signature sent by the mobile app.
     * The app sends: X-App-Signature (cert hash), X-Timestamp, and X-HMAC (signature)
     * The payload format is: "timestamp:appSignature"
     *
     * @param appSignature The SHA-256 hash of the app's signing certificate (from X-App-Signature header)
     * @param timestamp The timestamp from the request (from X-Timestamp header)
     * @param providedHmac The HMAC signature to verify (from X-HMAC header)
     * @param maxAgeMillis Maximum age of the timestamp in milliseconds (default 5 minutes)
     * @return true if the signature is valid and timestamp is recent, false otherwise
     */
    fun verifyAppSignature(
        appSignature: String,
        timestamp: String,
        providedHmac: String,
        maxAgeMillis: Long = 300_000 // 5 minutes
    ): Boolean {
        try {
            // Validate timestamp to prevent replay attacks
            val requestTime = timestamp.toLongOrNull() ?: return false
            val currentTime = System.currentTimeMillis()
            val timeDiff = currentTime - requestTime

            // Allow for clock skew: accept timestamps slightly in the future (up to 5 seconds)
            // and timestamps in the past (up to maxAgeMillis)
            val clockSkewTolerance = 5_000L // 5 seconds
            if (timeDiff < -clockSkewTolerance || timeDiff > maxAgeMillis) {
                println("Timestamp validation failed: timeDiff=$timeDiff, max=$maxAgeMillis, tolerance=$clockSkewTolerance")
                return false
            }

            // Reconstruct the payload that was signed by the app
            val payload = "$timestamp:$appSignature"

            // Calculate expected HMAC using the app signature as the secret
            val expectedHmac = calculateSignature(payload, appSignature)

            // Use constant-time comparison
            return constantTimeEquals(providedHmac, expectedHmac)
        } catch (e: Exception) {
            println("Error verifying app signature: ${e.message}")
            return false
        }
    }

    /**
     * Verifies that the app signature matches the expected certificate and the HMAC is valid.
     * This is the recommended method when you have a known certificate signature.
     *
     * @param appSignature The SHA-256 hash of the app's signing certificate (from X-App-Signature header)
     * @param timestamp The timestamp from the request (from X-Timestamp header)
     * @param providedHmac The HMAC signature to verify (from X-HMAC header)
     * @param expectedCertificate The expected app certificate signature (from server config)
     * @param maxAgeMillis Maximum age of the timestamp in milliseconds (default 5 minutes)
     * @return true if the certificate matches and signature is valid, false otherwise
     */
    fun verifyAppSignatureWithCertificate(
        appSignature: String,
        timestamp: String,
        providedHmac: String,
        expectedCertificate: String,
        maxAgeMillis: Long = 300_000 // 5 minutes
    ): Boolean {
        // First verify that the app signature matches the expected certificate
        if (!constantTimeEquals(appSignature, expectedCertificate)) {
            println("App certificate does not match expected value")
            return false
        }

        // Then verify the HMAC signature
        return verifyAppSignature(appSignature, timestamp, providedHmac, maxAgeMillis)
    }

    /**
     * Constant-time string comparison to prevent timing attacks.
     */
    private fun constantTimeEquals(a: String, b: String): Boolean {
        if (a.length != b.length) {
            return false
        }

        var result = 0
        for (i in a.indices) {
            result = result or (a[i].code xor b[i].code)
        }

        return result == 0
    }

    /**
     * Legacy method for backward compatibility.
     * Use verifyAppSignature for the new certificate-based verification.
     */
    @Deprecated("Use verifyAppSignature instead", ReplaceWith("constantTimeEquals(providedSignature, expectedSignature)"))
    fun verifySignature(providedSignature: String, expectedSignature: String): Boolean {
        return constantTimeEquals(providedSignature, expectedSignature)
    }
}