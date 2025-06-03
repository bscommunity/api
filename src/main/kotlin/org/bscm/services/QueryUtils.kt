package org.bscm.services

import org.bscm.models.tables.ChartTable
import org.jetbrains.exposed.sql.Op
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.or
import java.text.Normalizer
import java.util.*

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
                    normalizedQuery, // Pass the original normalized query for candidate selection
                    queryWithoutVowels, // Pass the vowel-less query for Levenshtein
                    limit - matchedStrings.size
                )
                // Combine, remove duplicates (case-insensitive), and limit results
                (matchedStrings + fuzzyMatches).distinctBy { it.lowercase() }.take(limit)
            } else {
                matchedStrings
            }
        }

        /**
         * Finds matches in the database where normalized fields
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
                        ChartTable.artist, // Select original fields for display
                        ChartTable.track,
                        ChartTable.album,
                        // Also select normalized fields if needed for post-query logic, though not strictly necessary here
                        // ChartTable.normalizedArtist,
                        // ChartTable.normalizedTrack,
                        // ChartTable.normalizedAlbum
                    )
                )
                .where {
                    // For each term, it must be present in at least one normalized field.
                    val allTermConditions = terms.map { term ->
                        val pattern = "%$term%" // term is already normalized

                        // Use the pre-normalized fields from the table
                        val artistCondition = ChartTable.normalizedArtist like pattern
                        val trackCondition = ChartTable.normalizedTrack like pattern
                        // Handle nullable normalizedAlbum
                        val albumCondition = ChartTable.normalizedAlbum?.like(pattern) ?: Op.FALSE

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

                    // The 'normalizedQuery' is already lowercase and fully normalized.
                    // We check which original field, when normalized, contains the normalizedQuery.
                    when {
                        getNormalizedQuery(track).contains(normalizedQuery) -> track
                        getNormalizedQuery(artist).contains(normalizedQuery) -> artist
                        album?.let { getNormalizedQuery(it).contains(normalizedQuery) } == true -> album
                        // Fallback: if the full normalizedQuery isn't contained as a substring in any single normalized field,
                        // but terms matched due to the WHERE clause, prioritize track.
                        // This can happen if terms are spread across fields, though the current query structure (OR within terms, AND across terms)
                        // implies each term must be in *some* field, and all terms must be satisfied.
                        // A more robust fallback might be needed if the goal is to return the field that matched *most* terms,
                        // but the current logic prioritizes the field containing the whole query.
                        else -> track // Default to track if no single field's normalized version contains the full normalized query.
                    }
                }
                .distinctBy { it.lowercase() } // Ensure unique results (case-insensitive)
        }


        /**
         * Finds matches using a vowel-insensitive approach and Levenshtein distance.
         * Candidates are pre-filtered based on the original normalized query terms using normalized DB fields.
         */
        fun findMatchesWithoutVowels(
            originalNormalizedQuery: String, // The query normalized by getNormalizedQuery (e.g., "i didnt know cafes")
            queryWithoutVowels: String,      // Vowel-less version of originalNormalizedQuery (e.g., "ddnt knw cfs")
            limit: Int
        ): List<String> {
            if (limit <= 0) return emptyList()
            if (queryWithoutVowels.isBlank() && originalNormalizedQuery.isBlank()) return emptyList()

            // Use terms from the original normalized query to fetch initial candidates.
            val termsForCandidateSelection = originalNormalizedQuery.split(" ").filter { it.isNotBlank() }

            val candidates = ChartTable
                .select(
                    listOf(
                        ChartTable.id,
                        ChartTable.artist,         // Original for display
                        ChartTable.track,          // Original for display
                        ChartTable.album,          // Original for display
                        ChartTable.normalizedArtist, // Normalized for vowel removal & comparison logic
                        ChartTable.normalizedTrack,
                        ChartTable.normalizedAlbum
                    )
                )
                .where {
                    if (termsForCandidateSelection.isEmpty()) {
                        Op.TRUE // Fetch all if no terms (e.g. query was only symbols)
                    } else {
                        // At least ONE of the original normalized terms must be present in a normalized field.
                        val termOrConditions = termsForCandidateSelection.map { term ->
                            val pattern = "%$term%" // term is already normalized
                            (ChartTable.normalizedArtist like pattern) or
                                    (ChartTable.normalizedTrack like pattern) or
                                    (ChartTable.normalizedAlbum?.like(pattern) ?: Op.FALSE)
                        }
                        termOrConditions.reduce { acc, expr -> acc or expr }
                    }
                }
                .limit(limit * 10) // Fetch a larger pool for in-memory filtering
                .map { row ->
                    SearchCandidate(
                        id = row[ChartTable.id].value,
                        artist = row[ChartTable.artist],
                        track = row[ChartTable.track],
                        album = row[ChartTable.album] ?: "",
                        // Store the fetched normalized fields directly
                        normalizedArtist = row[ChartTable.normalizedArtist],
                        normalizedTrack = row[ChartTable.normalizedTrack],
                        normalizedAlbum = row[ChartTable.normalizedAlbum] ?: ""
                    )
                }

            // Calculate similarity scores and filter results
            return candidates.mapNotNull { candidate ->
                // Use the pre-fetched normalized fields for vowel removal
                val artistNoVowels = candidate.normalizedArtist.replace(Regex("[aeiou]"), "").replace(Regex("\\s+"), " ").trim()
                val trackNoVowels = candidate.normalizedTrack.replace(Regex("[aeiou]"), "").replace(Regex("\\s+"), " ").trim()
                val albumNoVowels = candidate.normalizedAlbum.replace(Regex("[aeiou]"), "").replace(Regex("\\s+"), " ").trim()

                // Calculate Levenshtein distance for vowel-less versions
                // using queryWithoutVowels which is already processed.
                val artistScore = calculateSimilarity(queryWithoutVowels, artistNoVowels)
                val trackScore = calculateSimilarity(queryWithoutVowels, trackNoVowels)
                val albumScore = calculateSimilarity(queryWithoutVowels, albumNoVowels)

                val bestScore = maxOf(artistScore, trackScore, albumScore)

                if (bestScore < 0.5) { // Similarity threshold
                    null
                } else {
                    // Choose the original field corresponding to the highest score.
                    when {
                        trackScore == bestScore -> Pair(candidate.track, bestScore)
                        artistScore == bestScore -> Pair(candidate.artist, bestScore)
                        albumScore == bestScore && candidate.album.isNotBlank() -> Pair(candidate.album, bestScore) // Ensure album isn't blank
                        // If album score is best but album is blank, try track or artist if their scores are also the bestScore
                        albumScore == bestScore && candidate.album.isBlank() -> {
                            if (trackScore == bestScore) Pair(candidate.track, bestScore)
                            else if (artistScore == bestScore) Pair(candidate.artist, bestScore)
                            else null // Should not happen if bestScore >= 0.5
                        }
                        else -> null
                    }
                }
            }
                .filterNot { it.first.isBlank() && it.second < 0.5 }
                .sortedByDescending { it.second }
                .map { it.first }
                .distinctBy { it.lowercase() }
                .take(limit)
        }

        /**
         * Calculates similarity between two strings (0-1, where 1 is identical).
         * s1 is typically the query (vowel-less), s2 is the DB field (vowel-less).
         */
        fun calculateSimilarity(s1: String, s2: String): Double {
            if (s1.isEmpty() && s2.isEmpty()) return 1.0
            if (s1.isEmpty() || s2.isEmpty()) return 0.0
            if (s1 == s2) return 1.0

            if (s2.contains(s1)) return 0.9
            if (s1.contains(s2)) return 0.8

            val distance = levenshteinDistance(s1, s2)
            val maxLength = maxOf(s1.length, s2.length)
            if (maxLength == 0) return 1.0

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
                        dp[i - 1][j] + 1,
                        dp[i][j - 1] + 1,
                        dp[i - 1][j - 1] + cost
                    )
                }
            }
            return dp[m][n]
        }

        /**
         * Data class to hold search candidates with their original and normalized fields.
         */
        private data class SearchCandidate(
            val id: UUID,
            val artist: String,
            val track: String,
            val album: String,
            val normalizedArtist: String, // Added normalized fields
            val normalizedTrack: String,
            val normalizedAlbum: String
        )
    }
}
