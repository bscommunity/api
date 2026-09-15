package org.bscm.models.interfaces

import org.bscm.models.Theme
import org.bscm.models.dto.contributor.SimplifiedContributor
import java.util.*

interface IThemeRepository {
    suspend fun getThemes(
        userId: UUID? = null,
        catalogIds: List<String>? = null,
        search: String? = null,
        limit: Int? = null,
        offset: Int? = null,
        includeVersions: Boolean = false,
    ): List<Theme>

    suspend fun getThemeById(id: String, userId: UUID? = null, includeVersions: Boolean = false): Theme?
    suspend fun createTheme(
        userId: UUID,
        name: String,
        replaces: String,
        originalArtwork: String?,
        id: String?,
        contributors: List<SimplifiedContributor> = emptyList(),
    ): Theme

    suspend fun updateTheme(
        id: String,
        userId: UUID,
        name: String?,
        replaces: String?,
        originalArtwork: String?,
    ): Theme

    suspend fun deleteTheme(id: String, userId: UUID): Boolean
    suspend fun updateDiscordCoordinates(catalogItemId: String, channelId: String, messageId: String)
    suspend fun countThemes(search: String? = null): Int
    suspend fun findThemeByBundleHash(hash: String): Theme?
}
