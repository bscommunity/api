package org.bscm.plugins

import io.ktor.server.application.*
import kotlinx.coroutines.*
import org.bscm.models.KnownIssue
import org.bscm.models.StreamingLink
import org.bscm.models.dto.chart.CreateChartRequest
import org.bscm.models.dto.contributor.SimplifiedContributor
import org.bscm.models.dto.user.CreateUserRequest
import org.bscm.models.enums.ContributorRole
import org.bscm.models.enums.Difficulty
import org.bscm.repository.*
import org.koin.ktor.ext.inject
import org.slf4j.LoggerFactory
import java.time.LocalDate
import java.util.*
import kotlin.random.Random

private val logger = LoggerFactory.getLogger("SeedScript")

fun Application.seedDatabase() {
    val chartRepository by inject<ChartRepository>()
    val userRepository by inject<UserRepository>()
    val versionRepository by inject<VersionRepository>()
    val contributorRepository by inject<ContributorRepository>()
    val knownIssueRepository by inject<KnownIssueRepository>()

    runBlocking {
        generateRandomCharts(
            50,
            chartRepository,
            userRepository,
            versionRepository,
            contributorRepository,
            knownIssueRepository
        )
    }
}

/**
 * Generates random charts with associated data for database seeding
 * Uses coroutines for parallel processing
 */
private suspend fun generateRandomCharts(
    count: Int,
    chartRepository: ChartRepository,
    userRepository: UserRepository,
    versionRepository: VersionRepository,
    contributorRepository: ContributorRepository,
    knownIssueRepository: KnownIssueRepository
) = coroutineScope {
    // Create users first (sequentially since it's a small number)
    // Create or retrieve users
    val users = mutableListOf<org.bscm.models.User>()
    val usersToCreate = 5

    // Try to get existing users first
    for (i in 1..usersToCreate) {
        try {
            val user = userRepository.getUserByUsername("user$i")
            if (user != null) users.add(user)
            logger.info("Found existing user: ${user?.username}")
        } catch (e: Exception) {
            // User doesn't exist, create new one
            try {
                val newUser = userRepository.createUser(
                    user = CreateUserRequest(
                        username = "user$i",
                        email = "user$i@example.com",
                        discordId = "${100000000000000000 + i}",
                        imageUrl = "https://example.com/avatar$i.png"
                    )
                )
                users.add(newUser)
                logger.info("Created new user: ${newUser.username}")
            } catch (e: Exception) {
                logger.error("Failed to create user$i: ${e.message}", e)
            }
        }
    }

    // Make sure we have enough users
    if (users.isEmpty()) {
        logger.error("No users available for chart creation. Aborting.")
        return@coroutineScope
    }

    val userIds = users.map { it.id }


    val difficulties = Difficulty.entries.toTypedArray()
    val contributorRoles = ContributorRole.entries.toTypedArray()

    // Use a dispatcher optimized for IO operations
    val dispatcher = Dispatchers.IO.limitedParallelism(8) // Limit to avoid overwhelming the database

    // Process charts in batches to control parallelism
    val batchSize = 10
    (0 until count step batchSize).map { batchStart ->
        val batchEnd = minOf(batchStart + batchSize, count)

        // Launch batch processing
        async(dispatcher) {
            (batchStart until batchEnd).forEach { i ->
                try {
                    val ownerId = userIds.random()

                    // Create chart with first version
                    val chart = chartRepository.createChart(
                        userId = ownerId,
                        chart = CreateChartRequest(
                            artist = getRandomArtist(),
                            track = getRandomTrack(),
                            album = if (Random.nextBoolean()) getRandomAlbum() else null,
                            trackUrls = listOf(
                                StreamingLink("spotify", "https://open.spotify.com/track/${getRandomId()}"),
                                StreamingLink("youtube", "https://www.youtube.com/watch?v=${getRandomId()}")
                            ),
                            trackPreviewUrl = "https://example.com/preview/${getRandomId()}.mp3",
                            coverUrl = "https://example.com/covers/${getRandomId()}.jpg",
                            difficulty = difficulties.random(),
                            isDeluxe = Random.nextBoolean(),
                            isExplicit = Random.nextBoolean(),
                            chartUrl = "https://example.com/charts/${getRandomId()}.bscm",
                            chartPreviewUrls = List(Random.nextInt(1, 4)) { "https://example.com/chartpreviews/${getRandomId()}.jpg" },
                            duration = Random.nextFloat() * 4 + 2, // 2-6 minutes
                            notesAmount = Random.nextInt(100, 1000),
                            effectsAmount = Random.nextInt(10, 100),
                            bpm = Random.nextInt(80, 180),
                        )
                    )

                    logger.info("Created chart with ID: ${chart.id}")

                    // Process additional data for this chart in parallel
                    coroutineScope {
                        // Add additional versions randomly
                        val versionJob = launch {
                            if (Random.nextBoolean()) {
                                val additionalVersionsCount = Random.nextInt(1, 3)
                                repeat(additionalVersionsCount) {
                                    versionRepository.addVersion(
                                        org.bscm.models.dto.version.CreateVersionRequest(
                                            chartId = chart.id,
                                            duration = Random.nextFloat() * 4 + 2,
                                            notesAmount = Random.nextInt(100, 1000),
                                            effectsAmount = Random.nextInt(10, 100),
                                            bpm = Random.nextInt(80, 180),
                                            chartUrl = "https://example.com/charts/${getRandomId()}.bscm",
                                            chartPreviewUrls = List(Random.nextInt(1, 4)) { "https://example.com/chartpreviews/${getRandomId()}.jpg" }
                                        )
                                    )
                                }
                                logger.info("Added additional versions for chart ID: ${chart.id}")
                            }
                        }

                        // Add contributors randomly
                        val contributorJob = launch {
                            val contributorsCount = Random.nextInt(1, 4)
                            val contributors = userIds.filter { it != ownerId }.shuffled().take(contributorsCount)
                            contributorRepository.addContributors(
                                chart.id,
                                contributors.map { SimplifiedContributor(it, listOf(contributorRoles.random())) }
                            )
                            logger.info("Added contributors for chart ID: ${chart.id}")
                        }

                        // Add known issues randomly
                        val issueJob = launch {
                            if (Random.nextBoolean()) {
                                val issuesCount = Random.nextInt(1, 3)
                                repeat(issuesCount) {
                                    knownIssueRepository.addIssue(
                                        chart.id,
                                        KnownIssue(
                                            id = UUID.randomUUID(),
                                            description = "Issue ${getRandomIssue()}",
                                            createdAt = LocalDate.now(),
                                        )
                                    )
                                }
                                logger.info("Added known issues for chart ID: ${chart.id}")
                            }
                        }

                        // Wait for all data for this chart to be added
                        versionJob.join()
                        contributorJob.join()
                        issueJob.join()
                    }

                } catch (e: Exception) {
                    logger.error("Error generating chart $i: ${e.message}", e)
                }
            }
        }
    }.awaitAll() // Wait for all batches to complete

    logger.info("Database seeding completed: Generated $count charts")
}

private fun getRandomId(): String = UUID.randomUUID().toString().replace("-", "").substring(0, 10)

private fun getRandomArtist(): String {
    val artists = listOf("Avicii", "Coldplay", "Imagine Dragons", "The Weeknd", "Dua Lipa",
        "Linkin Park", "BTS", "Billie Eilish", "Taylor Swift", "Ed Sheeran")
    return artists.random()
}

private fun getRandomTrack(): String {
    val tracks = listOf("Levels", "Viva La Vida", "Believer", "Blinding Lights", "Don't Start Now",
        "In The End", "Dynamite", "Bad Guy", "Shake It Off", "Shape of You")
    return tracks.random()
}

private fun getRandomAlbum(): String {
    val albums = listOf("True", "Viva la Vida", "Evolve", "After Hours", "Future Nostalgia",
        "Hybrid Theory", "Map of the Soul", "When We All Fall Asleep", "1989", "÷")
    return albums.random()
}

private fun getRandomIssue(): String {
    val issues = listOf(
        "Notes out of sync with the beat",
        "Missing effects at the chorus",
        "Incorrect BPM in the bridge section",
        "Overlapping notes in the second verse",
        "Sound effects too loud in the mix",
        "Chart crashes at the end of the song",
        "Performance issues on older devices"
    )
    return issues.random()
}