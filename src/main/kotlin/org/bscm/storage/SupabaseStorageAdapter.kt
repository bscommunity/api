package org.bscm.storage

import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*

class SupabaseStorageAdapter(
    private val client: HttpClient,
    private val baseUrl: String,
    private val serviceKey: String,
    private val publicBaseUrl: String? = null,
) : StorageAdapter {
    override suspend fun putObject(
        bucket: String,
        path: String,
        bytes: ByteArray,
        contentType: String,
        upsert: Boolean,
    ) {
        val response: HttpResponse = client.put("${baseUrl.trimEnd('/')}/storage/v1/object/$bucket/$path") {
            header("Authorization", "Bearer $serviceKey")
            header("apikey", serviceKey)
            header("x-upsert", upsert.toString())
            contentType(ContentType.parse(contentType))
            setBody(bytes)
        }

        if (!response.status.isSuccess()) {
            val body = response.bodyAsText()
            throw IllegalStateException("Supabase storage upload failed: ${response.status} - $body")
        }
    }

    override suspend fun getObject(bucket: String, path: String): ByteArray {
        val response: HttpResponse = client.get("${baseUrl.trimEnd('/')}/storage/v1/object/$bucket/$path") {
            header("Authorization", "Bearer $serviceKey")
            header("apikey", serviceKey)
        }

        if (!response.status.isSuccess()) {
            val body = response.bodyAsText()
            throw IllegalStateException("Supabase storage download failed: ${response.status} - $body")
        }

        return response.readRawBytes()
    }

    override suspend fun deleteObject(bucket: String, path: String) {
        val response: HttpResponse = client.delete("${baseUrl.trimEnd('/')}/storage/v1/object/$bucket/$path") {
            header("Authorization", "Bearer $serviceKey")
            header("apikey", serviceKey)
        }

        if (!response.status.isSuccess()) {
            val body = response.bodyAsText()
            throw IllegalStateException("Supabase storage delete failed: ${response.status} - $body")
        }
    }

    override fun publicUrl(bucket: String, path: String): String {
        val base = publicBaseUrl?.trimEnd('/') ?: baseUrl.trimEnd('/')
        return "$base/storage/v1/object/public/$bucket/$path"
    }
}
