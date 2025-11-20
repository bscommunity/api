package org.bscm.utils

import org.bscm.models.enums.Genre

object GenresUtils {
    private val genreMap: Map<Genre, List<String>> = mapOf(
        Genre.HIP_HOP to listOf(
            "hip hop", "rap", "trap", "urban", "crunk", "dirty south", "gangsta rap", "drill", "grime", "breakbeat", "beats", "boom bap", "rhyme"
        ),
        Genre.POP to listOf(
            "pop", "teen pop", "k-pop", "j-pop", "dance pop", "electropop", "europop", "synthpop", "power pop", "pop rock", "disco", "contemporary", "mainstream", "commercial"
        ),
        Genre.RNB to listOf(
            "r&b", "rnb", "soul", "funk", "motown", "neo soul", "jazz", "blues", "gospel", "smooth jazz", "rhythm and blues", "swing", "reggae", "ska", "tropical"
        ),
        Genre.ROCK to listOf(
            "rock", "metal", "punk", "grunge", "hard rock", "indie rock", "alternative rock", "progressive rock", "post-rock", "garage rock", "psychedelic", "rockabilly", "hardcore", "heavy", "death", "thrash"
        ),
        Genre.DANCE to listOf(
            "dance", "electronic", "edm", "house", "techno", "trance", "dubstep", "drum and bass", "breakcore", "jungle", "hardstyle", "garage", "ambient", "synthwave", "electronica", "bass music", "future bass", "bass", "glitch", "8bit", "chiptune"
        ),
        Genre.ALTERNATIVE to listOf(
            "alternative", "indie", "folk", "singer-songwriter", "experimental", "art rock", "post-punk", "new wave", "shoegaze", "dreampop", "lo-fi", "acoustic", "americana", "world", "fusion", "avant", "progressive"
        ),
        Genre.CLASSICAL to listOf(
            "classical", "orchestra", "symphony", "opera", "chamber music", "baroque", "contemporary classical", "instrumental", "soundtrack", "score", "ost", "film score", "neoclassical", "orchestral", "cinematic", "piano"
        )
    )

    private val DANCE_KEYWORDS = listOf("mix", "club", "dj")
    private val POP_KEYWORDS = listOf("vocal", "song", "hit", "idol")
    private val HIP_HOP_KEYWORDS = listOf("beat", "rhythm", "rap", "flow")
    private val ROCK_KEYWORDS = listOf("band", "guitar", "riff")
    private val RNB_KEYWORDS = listOf("groove", "soul", "vibe")
    private val ALTERNATIVE_KEYWORDS = listOf("experimental", "underground", "indie")
    private val CLASSICAL_KEYWORDS = listOf("orchestra", "ensemble", "concerto")

    fun normalizeGenre(genre: String?): Genre? {
        if (genre.isNullOrBlank()) return null
        val normalizedInput = genre.lowercase()
        // Direct match
        for ((key, values) in genreMap) {
            if (values.any { normalizedInput.contains(it) }) {
                return key
            }
        }
        // Fallbacks
        if (DANCE_KEYWORDS.any { normalizedInput.contains(it) }) return Genre.DANCE
        if (POP_KEYWORDS.any { normalizedInput.contains(it) }) return Genre.POP
        if (HIP_HOP_KEYWORDS.any { normalizedInput.contains(it) }) return Genre.HIP_HOP
        if (ROCK_KEYWORDS.any { normalizedInput.contains(it) }) return Genre.ROCK
        if (RNB_KEYWORDS.any { normalizedInput.contains(it) }) return Genre.RNB
        if (ALTERNATIVE_KEYWORDS.any { normalizedInput.contains(it) }) return Genre.ALTERNATIVE
        if (CLASSICAL_KEYWORDS.any { normalizedInput.contains(it) }) return Genre.CLASSICAL
        // Default fallback
        return Genre.POP
    }
}

