package org.bscm.services

import org.jetbrains.exposed.sql.transactions.transaction
import java.text.Normalizer

class QueryUtils {
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
            var processedQuery = noAccents.lowercase()
            // 5. Remove non-alphanumeric characters (except space)
            processedQuery = processedQuery.replace(Regex("[^a-z0-9 ]"), "")
            // 6. Collapse multiple spaces into one and final trim
            return processedQuery.replace(Regex("\\s+"), " ").trim()
        }

        /**
         * Performs a fuzzy and substring search using pg_trgm and STRPOS.
         * Includes an additional check for minimal similarity to catch typos in substrings
         * that might not meet the default trigram threshold or exact substring match.
         * Returns up to [limit] distinct strings ordered by best trigram similarity.
         */
        fun getSearchMatches(rawQuery: String, limit: Int): List<String> {
            if (rawQuery.isBlank()) return emptyList()

            val dbQuery = getNormalizedQuery(rawQuery)
            if (dbQuery.isBlank()) return emptyList()

            // This threshold is used in the 3rd OR condition in the WHERE clause.
            // It allows matches with lower overall similarity (e.g., a typo in a short query against a long string)
            // to still be considered. Tune this value as needed.
            val minimalSimilarityThreshold = 0.09

            return transaction {
                val sql = """
                    SELECT DISTINCT ON (LOWER(best_match_text)) best_match_text
                    FROM (
                        SELECT
                            track,
                            artist,
                            album,
                            GREATEST(
                                similarity(normalized_track, ?),  -- param 1: dbQuery (for score)
                                similarity(normalized_artist, ?), -- param 2: dbQuery (for score)
                                similarity(normalized_album, ?)   -- param 3: dbQuery (for score)
                            ) AS match_score,
                            CASE
                                WHEN similarity(normalized_track, ?) >= similarity(normalized_artist, ?) -- param 4 & 5: dbQuery (for case)
                                  AND similarity(normalized_track, ?) >= similarity(normalized_album, ?)  -- param 6 & 7: dbQuery (for case)
                                THEN track
                                WHEN similarity(normalized_artist, ?) >= similarity(normalized_album, ?) -- param 8 & 9: dbQuery (for case)
                                THEN artist
                                ELSE album
                            END AS best_match_text
                        FROM chart
                        WHERE is_public = true AND (
                            -- Condition 1: Trigram fuzzy match (uses pg_trgm.similarity_threshold, default 0.3)
                            (normalized_track % ? OR      -- param 10: dbQuery (for %)
                             normalized_artist % ? OR     -- param 11: dbQuery (for %)
                             normalized_album % ?)        -- param 12: dbQuery (for %)
                            OR
                            -- Condition 2: Exact substring match on normalized fields
                            (STRPOS(normalized_track, ?) > 0 OR  -- param 13: dbQuery (for STRPOS)
                             STRPOS(normalized_artist, ?) > 0 OR -- param 14: dbQuery (for STRPOS)
                             STRPOS(normalized_album, ?) > 0)    -- param 15: dbQuery (for STRPOS)
                            OR
                            -- Condition 3: Catch-all for items with at least some minimal similarity.
                            -- This helps with typos in substrings where overall similarity is low but non-zero.
                            (GREATEST(
                                similarity(normalized_track, ?),  -- param 16: dbQuery (for WHERE GREATEST)
                                similarity(normalized_artist, ?), -- param 17: dbQuery (for WHERE GREATEST)
                                similarity(normalized_album, ?)   -- param 18: dbQuery (for WHERE GREATEST)
                             ) > $minimalSimilarityThreshold) -- Using the Kotlin variable directly in the string for clarity here.
                                                             -- For PreparedStatement, this would be a literal or another '?' if dynamic.
                                                             -- For this implementation, embedding the constant is fine.
                        )
                    ) ranked_matches
                    ORDER BY LOWER(best_match_text), match_score DESC
                    LIMIT ? -- param 19: limit (this will be the 19th '?' if minimalSimilarityThreshold is also a '?')
                """.trimIndent()
                // Note: If minimalSimilarityThreshold were a parameter, there would be 19 '?' for dbQuery/threshold
                // and the limit would be the 20th.
                // As it's embedded, there are 18 '?' for dbQuery and limit is the 19th.

                // println("Executing SQL with dbQuery='$dbQuery' (limit $limit), threshold $minimalSimilarityThreshold:\n$sql")

                val results = mutableListOf<String>()
                val rawJdbcConnection = (connection.connection as java.sql.Connection)

                rawJdbcConnection.prepareStatement(sql).use { preparedStatement ->
                    // Set all 18 dbQuery parameters
                    for (i in 1..18) {
                        preparedStatement.setString(i, dbQuery)
                    }
                    // Set the limit parameter (19th placeholder)
                    preparedStatement.setInt(19, limit)

                    preparedStatement.executeQuery().use { resultSet ->
                        while (resultSet.next()) {
                            resultSet.getString("best_match_text")?.let { match ->
                                results.add(match)
                            }
                        }
                    }
                }
                results
            }
        }
    }
}