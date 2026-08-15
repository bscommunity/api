package org.bscm.utils

import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.sql.Connection
import java.text.Normalizer

object QueryUtils {
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
                            t.title AS track,
                            t.artist AS artist,
                            a.name AS album,
                            GREATEST(
                                similarity(t.normalized_title, ?),  -- param 1: dbQuery (for score)
                                similarity(t.normalized_artist, ?), -- param 2: dbQuery (for score)
                                similarity(a.normalized_name, ?)    -- param 3: dbQuery (for score)
                            ) AS match_score,
                            CASE
                                WHEN similarity(t.normalized_title, ?) >= similarity(t.normalized_artist, ?) -- param 4 & 5
                                  AND similarity(t.normalized_title, ?) >= similarity(a.normalized_name, ?)  -- param 6 & 7
                                THEN t.title
                                WHEN similarity(t.normalized_artist, ?) >= similarity(a.normalized_name, ?) -- param 8 & 9
                                THEN t.artist
                                ELSE a.name
                            END AS best_match_text
                        FROM charts c
                        INNER JOIN catalog_items ci ON c.catalog_item_id = ci.id
                        INNER JOIN tracks t ON c.track_id = t.id
                        LEFT JOIN albums a ON t.album_id = a.id
                        WHERE ci.is_public = true AND (
                            -- Condition 1: Trigram fuzzy match
                            (t.normalized_title % ? OR      -- param 10
                             t.normalized_artist % ? OR     -- param 11
                             a.normalized_name % ?)         -- param 12
                            OR
                            -- Condition 2: Exact substring match
                            (STRPOS(t.normalized_title, ?) > 0 OR  -- param 13
                             STRPOS(t.normalized_artist, ?) > 0 OR -- param 14
                             STRPOS(a.normalized_name, ?) > 0)     -- param 15
                            OR
                            -- Condition 3: minimal similarity
                            (GREATEST(
                                similarity(t.normalized_title, ?),  -- param 16
                                similarity(t.normalized_artist, ?), -- param 17
                                similarity(a.normalized_name, ?)    -- param 18
                             ) > $minimalSimilarityThreshold)
                        )
                    ) ranked_matches
                    ORDER BY LOWER(best_match_text), match_score DESC
                    LIMIT ?
                """.trimIndent()
            // Note: If minimalSimilarityThreshold were a parameter, there would be 19 '?' for dbQuery/threshold
            // and the limit would be the 20th.
            // As it's embedded, there are 18 '?' for dbQuery and limit is the 19th.

            // println("Executing SQL with dbQuery='$dbQuery' (limit $limit), threshold $minimalSimilarityThreshold:\n$sql")

            val results = mutableListOf<String>()
            val rawJdbcConnection = (connection.connection as Connection)

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

    fun similarity(s1: String, s2: String): Double {
        // Think of this like comparing two ropes - we need to identify which is longer
        val longer: String
        val shorter: String

        if (s1.length < s2.length) {
            longer = s2
            shorter = s1
        } else {
            longer = s1
            shorter = s2
        }

        val longerLength = longer.length

        // If both strings are empty, they're identical (100% similar)
        if (longerLength == 0) {
            return 1.0
        }

        // Calculate similarity as: (total length - differences) / total length
        // Like measuring how much of the longer rope matches the shorter one
        return (longerLength - editDistance(longer, shorter)).toDouble() / longerLength
    }

    fun editDistance(s1: String, s2: String): Int {
        // Convert to lowercase for case-insensitive comparison
        // Like standardizing the "format" before comparing
        val str1 = s1.lowercase()
        val str2 = s2.lowercase()

        // Create a "cost matrix" - think of it as a spreadsheet tracking
        // the minimum operations needed at each step
        val costs = IntArray(str2.length + 1)

        // Initialize the first row: cost of converting empty string to prefixes of str2
        // Like setting up the "baseline" costs
        for (j in costs.indices) {
            costs[j] = j
        }

        // Fill the matrix row by row
        // Each row represents adding one more character from str1
        for (i in 1..str1.length) {
            var lastValue = i  // Cost of converting str1[0..i-1] to empty string

            for (j in 1..str2.length) {
                // Get the cost from the cell above (insertion)
                val newValue = costs[j - 1]

                // If characters don't match, we need an operation
                val finalValue = if (str1[i - 1] != str2[j - 1]) {
                    // Take minimum of three operations: insert, delete, substitute
                    // Like choosing the cheapest way to fix the difference
                    minOf(
                        newValue,      // substitute
                        lastValue,     // insert
                        costs[j]       // delete
                    ) + 1
                } else {
                    // Characters match, no additional cost
                    newValue
                }

                // Slide the values: previous becomes current
                costs[j - 1] = lastValue
                lastValue = finalValue
            }

            // Store the final value for this row
            costs[str2.length] = lastValue
        }

        // Return the bottom-right cell: minimum operations to transform str1 to str2
        return costs[str2.length]
    }
}