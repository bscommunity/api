package org.bscm.routes

import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.utils.io.*

/** Shared uploaded file payload for multipart route handlers. */
internal class UploadedFile(
    val bytes: ByteArray,
    val filename: String,
    val contentType: ContentType,
)

internal typealias UploadedImage = UploadedFile

internal data class ParsedMultipartPayload(
    val fields: Map<String, String>,
    val files: Map<String, UploadedFile>,
)

internal suspend fun ApplicationCall.parseMultipartPayload(
    acceptedFormFields: Set<String>,
    fileAliases: Map<String, String>,
    defaultFilenames: Map<String, String> = emptyMap(),
): ParsedMultipartPayload? {
    if (!request.contentType().match(ContentType.MultiPart.FormData)) {
        return null
    }

    val fields = mutableMapOf<String, String>()
    val files = mutableMapOf<String, UploadedFile>()

    receiveMultipart().forEachPart { part ->
        when (part) {
            is PartData.FormItem -> if (part.name in acceptedFormFields) {
                fields[part.name!!] = part.value
            }

            is PartData.FileItem -> {
                val canonicalName = part.name?.let { fileAliases[it] }
                if (canonicalName != null) {
                    val bytes = part.provider().toByteArray()
                    if (bytes.isNotEmpty()) {
                        files[canonicalName] = UploadedFile(
                            bytes = bytes,
                            filename = part.originalFileName
                                ?: defaultFilenames[canonicalName]
                                ?: "upload.bin",
                            contentType = part.contentType ?: ContentType.Application.OctetStream,
                        )
                    }
                }
            }

            else -> {}
        }
        part.dispose()
    }

    return ParsedMultipartPayload(
        fields = fields,
        files = files,
    )
}

