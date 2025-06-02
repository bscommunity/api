package org.bscm.services

import org.bscm.models.tables.ChartTable
import org.jetbrains.exposed.sql.Op
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.lowerCase
import org.jetbrains.exposed.sql.or
import java.text.Normalizer
import java.util.*

// import kotlin.math.minOf // For minOf in levenshteinDistance, if not automatically available

class QueryUtils() {
    companion object {
        /**
         * Normalizes a query string by trimming, converting to lowercase, removing diacritics (accents),
         * and removing all characters that are not alphanumeric or spaces.
         * Multiple spaces are condensed to a single space.
         * Example: "I Didn't KNOW cafés?!" -> "i didnt know cafes"
         */
        fun getNormalizedQuery(query: String): String {
            // 1. Trim whitespace
            val trimmedQuery = query.trim()
            // 2. Normalize to NFD form to separate diacritics
            val nfdNormalizedString = Normalizer.normalize(trimmedQuery, Normalizer.Form.NFD)
            // 3. Remove diacritics (combining marks)
            val noAccents = Regex("\\p{InCombiningDiacriticalMarks}+").replace(nfdNormalizedString, "")
            // 4. Convert to lowercase
            // 5. Remove all characters that are not lowercase letters (a-z), numbers (0-9), or spaces.
            //    This handles general punctuation, including apostrophes (e.g., "didn't" -> "didnt").
            val alphaNumericOnly = noAccents.lowercase().replace(Regex("[^a-z0-9 ]"), "")
            // 6. Normalize multiple spaces to a single space and trim again.
            return alphaNumericOnly.replace(Regex("\\s+"), " ").trim()
        }

        fun getSearchMatches(query: String, limit: Int): List<String> {
            if (query.isBlank()) return emptyList()

            val normalizedQuery = getNormalizedQuery(query)
            // Ensure vowel removal and space normalization are clean for the vowel-less query
            val queryWithoutVowels = normalizedQuery.replace(Regex("[aeiou]"), "").replace(Regex("\\s+"), " ").trim()

            val matchedStrings = findMatches(normalizedQuery, limit)

            return if (matchedStrings.size < limit) {
                val fuzzyMatches = findMatchesWithoutVowels(
                    normalizedQuery,
                    queryWithoutVowels,
                    limit - matchedStrings.size
                )
                // Combine, remove duplicates (case-insensitive), and limit results
                (matchedStrings + fuzzyMatches).distinctBy { it.lowercase() }.take(limit)
            } else {
                matchedStrings
            }
        }

        /**
         * Finds matches in the database where fields (after normalization including apostrophe removal)
         * contain all terms from the normalized query.
         */
        fun findMatches(
            normalizedQuery: String,
            limit: Int
        ): List<String> {
            if (normalizedQuery.isBlank()) return emptyList()

            val terms = normalizedQuery.split(" ").filter { it.isNotBlank() }
            if (terms.isEmpty()) return emptyList()

            return ChartTable
                .select(
                    listOf(
                        ChartTable.artist,
                        ChartTable.track,
                        ChartTable.album
                    )
                )
                .where {
                    // For each term, it must be present in at least one field (artist, track, or album).
                    // The database field is lowercased and has apostrophes removed for comparison.
                    val allTermConditions = terms.map { term ->
                        val pattern = "%$term%" // term is already normalized by getNormalizedQuery

                        val artistCondition = ChartTable.artist.lowerCase() like pattern
                        val trackCondition = ChartTable.track.lowerCase() like pattern
                        val albumCondition = ChartTable.album.lowerCase().like(pattern) // Handle nullable album

                        artistCondition or trackCondition or albumCondition
                    }
                    // All terms must be found (AND logic across terms)
                    allTermConditions.reduce { acc, expr -> acc and expr }
                }
                .limit(limit)
                .mapNotNull { row ->
                    val artist = row[ChartTable.artist]
                    val track = row[ChartTable.track]
                    val album = row[ChartTable.album]

                    // Attempt to return the field that contains the full normalizedQuery.
                    // The 'contains' check here uses the original lowercase field value,
                    // as the WHERE clause already ensured the match considering normalization.
                    val queryLc = normalizedQuery // normalizedQuery is already lowercase.

                    when {
                        track.lowercase().replace("'", "").contains(queryLc) -> track
                        artist.lowercase().replace("'", "").contains(queryLc) -> artist
                        album?.lowercase()?.replace("'", "")?.contains(queryLc) == true -> album
                        // Fallback: if the full query (after apostrophe removal) isn't contained as a substring,
                        // but terms matched due to the WHERE clause, prioritize track.
                        else -> track
                    }
                }
                .distinctBy { it.lowercase() } // Ensure unique results (case-insensitive)
        }


        /**
         * Finds matches using a vowel-insensitive approach and Levenshtein distance.
         * Candidates are pre-filtered based on the original normalized query terms.
         */
        fun findMatchesWithoutVowels(
            normalizedQuery: String,    // The original query, normalized (e.g., "i didnt")
            queryWithoutVowels: String, // Vowel-less version of normalizedQuery (e.g., "ddnt")
            limit: Int
        ): List<String> {
            if (limit <= 0) return emptyList()
            // If queryWithoutVowels is blank (e.g. query was only vowels/punctuation),
            // this specific fuzzy match method might not be effective.
            if (queryWithoutVowels.isBlank() && normalizedQuery.isBlank()) return emptyList()

            // Use terms from the original normalized query to fetch initial candidates more broadly.
            val termsForCandidateSelection = normalizedQuery.split(" ").filter { it.isNotBlank() }

            val candidates = ChartTable
                .select(
                    listOf(
                        ChartTable.id,
                        ChartTable.artist,
                        ChartTable.track,
                        ChartTable.album
                    )
                )
                .where {
                    if (termsForCandidateSelection.isEmpty()) {
                        // If no terms (original query was only spaces/symbols not normalizable),
                        // do not filter here, or return emptyList earlier.
                        // Opting not to filter here; Op.TRUE means it will fetch up to the limit without pre-filtering.
                        // This could be wide; consider returning emptyList() earlier if this case is problematic.
                        Op.TRUE
                    } else {
                        // At least ONE of the original normalized terms must be present (OR logic).
                        // Here, we use simple lowerCase() like pattern. The more aggressive normalization
                        // (like apostrophe removal) is primarily for findMatches.
                        val termOrConditions = termsForCandidateSelection.map { term ->
                            val pattern = "%$term%" // term is already normalized
                            (ChartTable.artist.lowerCase() like pattern) or
                                    (ChartTable.track.lowerCase() like pattern) or
                                    (ChartTable.album.lowerCase() like pattern) // Handle nullable album
                        }
                        termOrConditions.reduce { acc, expr -> acc or expr }
                    }
                }
                // Increase limit to get a larger pool for in-memory filtering.
                // The ideal multiplier (*5, *10) may depend on the increase of our dataset size and performance.
                .limit(limit * 10)
                .map { row ->
                    val id = row[ChartTable.id]
                    val artist = row[ChartTable.artist]
                    val track = row[ChartTable.track]
                    val album = row[ChartTable.album] ?: "" // Ensure album is not null for SearchCandidate

                    SearchCandidate(id.value, artist, track, album)
                }

            // Calculate similarity scores and filter results
            return candidates.mapNotNull { candidate ->
                // Normalize candidate fields (including accent removal via getNormalizedQuery)
                // then remove vowels for Levenshtein comparison.
                val artistNormalizedFull = getNormalizedQuery(candidate.artist)
                val trackNormalizedFull = getNormalizedQuery(candidate.track)
                val albumNormalizedFull = getNormalizedQuery(candidate.album)

                val artistNoVowels = artistNormalizedFull.replace(Regex("[aeiou]"), "").replace(Regex("\\s+"), " ").trim()
                val trackNoVowels = trackNormalizedFull.replace(Regex("[aeiou]"), "").replace(Regex("\\s+"), " ").trim()
                val albumNoVowels = albumNormalizedFull.replace(Regex("[aeiou]"), "").replace(Regex("\\s+"), " ").trim()

                // Calculate Levenshtein distance for vowel-less versions
                // using queryWithoutVowels which is already processed.
                val artistScore = calculateSimilarity(queryWithoutVowels, artistNoVowels)
                val trackScore = calculateSimilarity(queryWithoutVowels, trackNoVowels)
                val albumScore = calculateSimilarity(queryWithoutVowels, albumNoVowels)

                val bestScore = maxOf(artistScore, trackScore, albumScore)

                if (bestScore < 0.5) { // Similarity threshold
                    null
                } else {
                    // Choose the most relevant field with the highest score.
                    // In case of a tie, preference is track, then artist, then album.
                    when {
                        trackScore == bestScore -> Pair(candidate.track, bestScore)
                        artistScore == bestScore -> Pair(candidate.artist, bestScore)
                        albumScore == bestScore -> Pair(candidate.album, bestScore)
                        else -> null // Should not happen with maxOf if at least one score is >= 0.5
                    }
                }
            }
                .filterNot { it.first.isBlank() && it.second < 0.5 } // Avoid blank results unless they have a decent score (unlikely)
                .sortedByDescending { it.second } // Sort by similarity score (highest first)
                .map { it.first } // Take only the text string
                .distinctBy { it.lowercase() } // Remove duplicates based on lowercase text
                .take(limit) // Limit to the required number
        }

        /**
         * Calculates similarity between two strings (0-1, where 1 is identical).
         * s1 is typically the query (vowel-less), s2 is the DB field (vowel-less).
         */
        fun calculateSimilarity(s1: String, s2: String): Double {
            if (s1.isEmpty() && s2.isEmpty()) return 1.0 // Both empty are identical
            if (s1.isEmpty() || s2.isEmpty()) return 0.0 // One empty, one not: totally dissimilar
            if (s1 == s2) return 1.0

            // If one string (vowel-less) contains the other, it's a good match.
            // s1 is the query, s2 is the database field.
            if (s2.contains(s1)) return 0.9
            if (s1.contains(s2)) return 0.8 // Less likely if s1 (query) is shorter than s2 (field)

            val distance = levenshteinDistance(s1, s2)
            val maxLength = maxOf(s1.length, s2.length)
            if (maxLength == 0) return 1.0 // Both were effectively empty, already handled, but for safety.

            return 1.0 - (distance.toDouble() / maxLength)
        }

        /**
         * Calculates Levenshtein distance between two strings.
         */
        private fun levenshteinDistance(s1: String, s2: String): Int {
            val m = s1.length
            val n = s2.length

            val dp = Array(m + 1) { IntArray(n + 1) }

            for (i in 0..m) dp[i][0] = i
            for (j in 0..n) dp[0][j] = j

            for (i in 1..m) {
                for (j in 1..n) {
                    val cost = if (s1[i - 1] == s2[j - 1]) 0 else 1
                    dp[i][j] = minOf(
                        dp[i - 1][j] + 1,      // Deletion
                        dp[i][j - 1] + 1,      // Insertion
                        dp[i - 1][j - 1] + cost // Substitution
                    )
                }
            }
            return dp[m][n]
        }

        /**
         * Data class to hold search candidates with their fields.
         */
        private data class SearchCandidate(
            val id: UUID,
            val artist: String,
            val track: String,
            val album: String
        )
    }
}