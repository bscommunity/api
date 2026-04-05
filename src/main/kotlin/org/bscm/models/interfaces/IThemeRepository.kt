package org.bscm.models.interfaces

import org.bscm.models.Theme
import java.util.*

interface IThemeRepository {
    suspend fun getThemes(
        userId: UUID? = null,
        contentIds: List<String>? = null,
        search: String?,
        limit: Int? = null,
        offset: Int? = null,
    ): List<Theme>

    suspend fun getThemeById(id: ULong, userId: UUID? = null): Theme?
    suspend fun getAppThemeById(contentId: String, userId: UUID? = null): Theme?
    suspend fun createTheme(
        userId: UUID,
        name: String,
        replaces: String,
        coverUrl: String,
        displayArtUrl: String,
        previewUrl: String,
        id: ULong? = null,
    ): Theme
    suspend fun updateTheme(
        id: ULong,
        userId: UUID,
        name: String?,
        replaces: String?,
        coverUrl: String?,
        displayArtUrl: String?,
        previewUrl: String?,
    ): Theme
    suspend fun deleteTheme(id: ULong, userId: UUID): Boolean
}
