package org.bscm.storage

interface StorageAdapter {
    suspend fun putObject(
        bucket: String,
        path: String,
        bytes: ByteArray,
        contentType: String,
        upsert: Boolean = true,
    )

    suspend fun getObject(
        bucket: String,
        path: String,
    ): ByteArray

    suspend fun deleteObject(
        bucket: String,
        path: String,
    )

    fun publicUrl(bucket: String, path: String): String
}
