package org.bscm.models.dto.version

/**
 * Minimal internal DTO passed to [org.bscm.models.interfaces.IVersionRepository.addVersion].
 *
 * Contains only the fields that are persisted in the version table.
 * This decouples the repository layer from client-facing request models
 * and prevents the same DTO from serving dual purposes (request + internal transfer).
 */
data class VersionBundleData(
    val id: ULong? = null,
    val fileSizeBytes: Long = 0,
    val changelog: String = "",
)
