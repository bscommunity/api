package org.bscm.routes

import io.ktor.http.*

/** Shared in-route payload for uploaded image files in multipart endpoints. */
internal class UploadedImage(
    val bytes: ByteArray,
    val filename: String,
    val contentType: ContentType,
)

