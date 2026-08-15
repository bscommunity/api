package org.bscm.storage

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider
import software.amazon.awssdk.core.sync.RequestBody
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest
import software.amazon.awssdk.services.s3.model.GetObjectRequest
import software.amazon.awssdk.services.s3.model.PutObjectRequest
import java.net.URI

class S3StorageAdapter(
    private val endpoint: String,
    accessKey: String,
    secretKey: String,
    region: String = "us-east-1",
    private val publicBaseUrl: String,
) : StorageAdapter {

    private val s3Client: S3Client = S3Client.builder()
        .endpointOverride(URI.create(endpoint))
        .credentialsProvider(
            StaticCredentialsProvider.create(AwsBasicCredentials.create(accessKey, secretKey))
        )
        .region(Region.of(region))
        .forcePathStyle(true)
        .build()

    override suspend fun putObject(
        bucket: String,
        path: String,
        bytes: ByteArray,
        contentType: String,
        upsert: Boolean,
    ) {
        withContext(Dispatchers.IO) {
            val request = PutObjectRequest.builder()
                .bucket(bucket)
                .key(path)
                .contentType(contentType)
                .build()

            s3Client.putObject(request, RequestBody.fromBytes(bytes))
        }
    }

    override suspend fun getObject(bucket: String, path: String): ByteArray =
        withContext(Dispatchers.IO) {
            val request = GetObjectRequest.builder()
                .bucket(bucket)
                .key(path)
                .build()

            s3Client.getObjectAsBytes(request).asByteArray()
        }

    override suspend fun deleteObject(bucket: String, path: String) {
        withContext(Dispatchers.IO) {
            val request = DeleteObjectRequest.builder()
                .bucket(bucket)
                .key(path)
                .build()

            s3Client.deleteObject(request)
        }
    }

    override fun publicUrl(bucket: String, path: String): String {
        val base = publicBaseUrl.trimEnd('/')
        return "$base/$bucket/$path"
    }
}
