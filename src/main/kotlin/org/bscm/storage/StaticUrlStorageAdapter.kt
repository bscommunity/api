package org.bscm.storage

class StaticUrlStorageAdapter(
    private val publicBaseUrl: String,
) : StorageAdapter {
    override suspend fun putObject(
        bucket: String,
        path: String,
        bytes: ByteArray,
        contentType: String,
        upsert: Boolean,
    ) {
        throw IllegalStateException("Storage adapter is read-only; upload is not configured")
    }

    override suspend fun getObject(bucket: String, path: String): ByteArray {
        throw IllegalStateException("Storage adapter is read-only; download is not configured")
    }

    override suspend fun deleteObject(bucket: String, path: String) {
        throw IllegalStateException("Storage adapter is read-only; delete is not configured")
    }

    override fun publicUrl(bucket: String, path: String): String {
        return "${publicBaseUrl.trimEnd('/')}/$bucket/$path"
    }
}
