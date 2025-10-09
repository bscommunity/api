package org.bscm.repository

import org.bscm.models.Theme
import java.util.*

interface ThemeRepository {
    suspend fun getThemes(
        userId: UUID? = null,
        contentIds: List<String>? = null,
        search: String?,
        limit: Int? = null,
        offset: Int? = null,
    ): List<Theme>

    suspend fun getThemeById(id: ULong): Theme?
    suspend fun getAppThemeById(contentId: String): Theme?
    suspend fun createTheme(userId: UUID, name: String, replaces: String, coverUrl: String, previewUrl: String): Theme
    suspend fun updateTheme(id: ULong, name: String?, replaces: String?, coverUrl: String?, previewUrl: String?): Theme
    suspend fun deleteTheme(id: ULong): Boolean
}
