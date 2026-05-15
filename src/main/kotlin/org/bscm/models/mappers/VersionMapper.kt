package org.bscm.models.mappers

import org.bscm.models.Version
import org.bscm.models.dao.VersionEntity

object VersionMapper {
    fun entityToVersion(entity: VersionEntity): Version = Version(
        id = entity.id.value.toString(),
        catalogItemId = entity.catalogItem.id.value,
        versionCode = entity.versionCode,
        downloadsAmount = entity.downloadsAmount,
        fileSizeBytes = entity.fileSizeBytes,
        changelog = entity.changelog,
        createdAt = entity.createdAt,
    )
}