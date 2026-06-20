package org.bscm.models.interfaces

import org.bscm.models.Theme
import java.util.*

interface IThemeRepository {
    suspend fun getThemes(
        userId: UUID? = null,
        contentIds: List<String>? = null,
        search: String? = null,
        limit: Int? = null,
        offset: Int? = null,
    ): List<Theme>

    suspend fun getThemeById(id: String, userId: UUID? = null): Theme?
    suspend fun createTheme(
        userId: UUID,
        name: String,
        replaces: String,
        coverUrl: String,
        displayArtUrl: String,
        previewUrl: String,
        id: String? = null,
    ): Theme

    suspend fun updateTheme(
        id: String,
        userId: UUID,
        name: String?,
        replaces: String?,
        coverUrl: String?,
        displayArtUrl: String?,
        previewUrl: String?,
    ): Theme

    suspend fun deleteTheme(id: String, userId: UUID): Boolean
}
