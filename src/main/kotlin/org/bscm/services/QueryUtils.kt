package org.bscm.services

import org.bscm.models.tables.ChartTable
import org.jetbrains.exposed.sql.lowerCase
import org.jetbrains.exposed.sql.or
import java.util.*

class QueryUtils() {
    companion object {
        fun getNormalizedQuery(query: String): String {
            return query.trim().lowercase()
                .replace(Regex("[^a-z0-9 ]"), "") // remove punctuation
                .replace(Regex("\\s+"), " ") // normalize spaces
        }

        fun getSearchMatches(query: String, limit: Int): List<String> {
            if (query.isBlank()) return emptyList()

            val normalizedQuery = getNormalizedQuery(query)
            val queryWithoutVowels = normalizedQuery.replace(Regex("[aeiou]"), "")
            val exactSearchTerm = "%$normalizedQuery%"
            val noVowelsSearchPattern = "%${queryWithoutVowels.map { "$it%?" }.joinToString("")}%"

            val matchedStrings = findMatches(normalizedQuery, exactSearchTerm, limit)

            return if (matchedStrings.size < limit) {
                val fuzzyMatches = findMatchesWithoutVowels(
                    normalizedQuery,
                    queryWithoutVowels,
                    noVowelsSearchPattern,
                    limit - matchedStrings.size
                )
                (matchedStrings + fuzzyMatches).distinct().take(limit)
            } else {
                matchedStrings
            }
        }

        /**
         * Find exact matches in the database
         */
        fun findMatches(
            normalizedQuery: String,
            searchTerm: String,
            limit: Int
        ): List<String> {
            return ChartTable
                .select(
                    listOf(
                        ChartTable.artist,
                        ChartTable.track,
                        ChartTable.album
                    )
                )
                .where {
                    (ChartTable.artist.lowerCase() like searchTerm) or
                            (ChartTable.track.lowerCase() like searchTerm) or
                            (ChartTable.album.lowerCase() like searchTerm)
                }
                .limit(limit)
                .map { row ->
                    // Prioritize the field that matched the query
                    listOf(row[ChartTable.artist], row[ChartTable.track], row[ChartTable.album]).firstOrNull {
                        it?.lowercase()?.contains(normalizedQuery) ?: false
                    } ?: row[ChartTable.track]
                }
                .distinct()
        }

        /**
         * Find matches using a vowel-insensitive approach
         */
        fun findMatchesWithoutVowels(
            normalizedQuery: String,
            queryWithoutVowels: String,
            noVowelsSearchPattern: String,
            limit: Int
        ): List<String> {
            // Get all potential candidates
            val candidates = ChartTable
                .select(
                    listOf(
                        ChartTable.id,
                        ChartTable.artist,
                        ChartTable.track,
                        ChartTable.album
                    )
                )
                .limit(limit * 3) // Get more candidates than needed to filter with better ranking
                .map { row ->
                    val id = row[ChartTable.id]
                    val artist = row[ChartTable.artist]
                    val track = row[ChartTable.track]
                    val album = row[ChartTable.album] ?: ""

                    SearchCandidate(id.value, artist, track, album)
                }

            // Calculate similarity scores and filter results
            return candidates.mapNotNull { candidate ->
                // Calculate similarity scores for each field
                val artistNoVowels = candidate.artist.lowercase().replace(Regex("[aeiou]"), "")
                val trackNoVowels = candidate.track.lowercase().replace(Regex("[aeiou]"), "")
                val albumNoVowels = candidate.album.lowercase().replace(Regex("[aeiou]"), "")

                // Calculate Levenshtein distance for vowel-less versions
                val artistScore = calculateSimilarity(queryWithoutVowels, artistNoVowels)
                val trackScore = calculateSimilarity(queryWithoutVowels, trackNoVowels)
                val albumScore = calculateSimilarity(queryWithoutVowels, albumNoVowels)

                // Choose the best field based on similarity score
                val bestScore = maxOf(artistScore, trackScore, albumScore)

                // If the score is too low, this is probably not a match
                if (bestScore < 0.5) {
                    null
                } else {
                    // Choose the most relevant field with the highest score
                    when {
                        artistScore == bestScore -> Pair(candidate.artist, bestScore)
                        trackScore == bestScore -> Pair(candidate.track, bestScore)
                        else -> Pair(candidate.album, bestScore)
                    }
                }
            }
                .sortedByDescending { it.second } // Sort by similarity score (highest first)
                .take(limit)
                .map { it.first } // Take only the text, not the score
        }

        /**
         * Calculate similarity between two strings (0-1 where 1 is identical)
         */
        fun calculateSimilarity(s1: String, s2: String): Double {
            if (s1.isEmpty() || s2.isEmpty()) return 0.0
            if (s1 == s2) return 1.0

            // If one string contains the other, it's a decent match
            if (s2.contains(s1)) return 0.9
            if (s1.contains(s2)) return 0.8

            // Calculate similarity using Levenshtein distance
            val distance = levenshteinDistance(s1, s2)
            val maxLength = maxOf(s1.length, s2.length)

            return 1.0 - (distance.toDouble() / maxLength)
        }

        /**
         * Calculate Levenshtein distance between two strings
         */
        private fun levenshteinDistance(s1: String, s2: String): Int {
            val m = s1.length
            val n = s2.length

            // Create a matrix of size (m+1) x (n+1)
            val dp = Array(m + 1) { IntArray(n + 1) }

            // Initialize the first row and column
            for (i in 0..m) dp[i][0] = i
            for (j in 0..n) dp[0][j] = j

            // Fill the matrix
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
         * Data class to hold search candidates with their fields
         */
        private data class SearchCandidate(
            val id: UUID,
            val artist: String,
            val track: String,
            val album: String
        )
    }
}