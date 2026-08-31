package org.bscm.models.theme

import kotlinx.serialization.Serializable

@Serializable
data class BeatstarThemeCatalog(
    val schemaVersion: Int,
    val assetTypes: List<String>,
    val genres: Map<ThemeGenre, List<BeatstarTheme>>,
    val unmappedThemes: List<UnmappedTheme>
)

@Serializable
data class BeatstarTheme(
    val id: String,
    val name: String,
    val tier: ThemeTier? = null,
    val sourceName: String? = null,
    val assets: ThemeAssets,
    val unmappedAssets: List<String> = emptyList()
)

@Serializable
data class ThemeAssets(
    val icon: String? = null,
    val track: String? = null,
    val top: String? = null,
    val bottom: String? = null,
    val circle: String? = null,
    val perfectBar: String? = null,
    val perfectLine: String? = null
)

@Serializable
data class UnmappedTheme(
    val sourceName: String,
    val tier: ThemeTier? = null,
    val assets: List<String>
)

@Serializable
enum class ThemeTier { A, B, S, SSS }

@Serializable
enum class ThemeGenre { rock, pop, alternative, hipHop, universal, rnb, dance, country }
