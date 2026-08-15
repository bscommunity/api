@file:Suppress("ArrayInDataClass")

package org.bscm.backup

import kotlinx.serialization.Serializable

@Serializable
data class WorkshopBackupEntryInfo(
    val messageId: String,
    val versionId: String,
    val bundleUrl: String,
    val coverUrl: String? = null,
    val audioUrl: String? = null,
    val bundlePath: String,
    val coverPath: String? = null,
    val bundleSizeBytes: Long,
    val coverSizeBytes: Long? = null,
    val downloadedAt: String,
)

@Serializable
data class WorkshopBackupManifest(
    val channelId: String,
    val generatedAt: String,
    val totalEntries: Int,
    val entries: List<WorkshopBackupEntryInfo>,
)

data class WorkshopBackupFile(
    val relativePath: String,
    val bytes: ByteArray,
)

data class WorkshopBackupPackage(
    val files: List<WorkshopBackupFile>,
)


