package org.bscm.plugins

import io.ktor.server.application.*
import kotlinx.coroutines.*
import org.bscm.models.KnownIssue
import org.bscm.models.StreamingLink
import org.bscm.models.dto.chart.CreateChartRequest
import org.bscm.models.dto.contributor.SimplifiedContributor
import org.bscm.models.dto.user.CreateUserRequest
import org.bscm.models.dto.user.SimplifiedUser
import org.bscm.models.dto.version.CreateVersionRequest
import org.bscm.models.enums.ContributorRole
import org.bscm.models.enums.Difficulty
import org.bscm.models.enums.Genre
import org.bscm.models.enums.StreamingPlatform
import org.bscm.models.interfaces.*
import org.bscm.utils.NanoIdUtils
import org.koin.ktor.ext.inject
import org.slf4j.LoggerFactory
import java.util.*
import kotlin.random.Random

private val logger = LoggerFactory.getLogger("SeedScript")

fun Application.seedDatabase() {
    val chartRepository by inject<IChartRepository>()
    val userRepository by inject<IUserRepository>()
    val versionRepository by inject<IVersionRepository>()
    val contributorRepository by inject<IContributorRepository>()
    val knownIssueRepository by inject<IKnownIssueRepository>()

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
    chartRepository: IChartRepository,
    userRepository: IUserRepository,
    versionRepository: IVersionRepository,
    contributorRepository: IContributorRepository,
    knownIssueRepository: IKnownIssueRepository
) = coroutineScope {
    // Create users first (sequentially since it's a small number)
    // Create or retrieve users
    val users = mutableListOf<SimplifiedUser>()
    val usersToCreate = 5

    // Try to get existing users first
    for (i in 1..usersToCreate) {
        val user = userRepository.getUserByUsername("user$i")
        if (user != null) {
            users.add(user)
            logger.info("Found existing user: ${user.username}")
            continue
        }

        // User doesn't exist, create new one
        try {
            val newUser = userRepository.createUser(
                user = CreateUserRequest(
                    username = "user$i",
                    email = "user$i@example.com",
                    discordId = "${100000000000000000 + i}",
                    avatarUrl = "https://example.com/avatar$i.png"
                )
            )
            users.add(SimplifiedUser(
                id = newUser.id,
                username = newUser.username,
                avatarUrl = newUser.avatarUrl,
                bannerUrl = newUser.bannerUrl,
                isVerified = newUser.isVerified,
                bio = newUser.bio,
                accentColor = newUser.accentColor,
            ))
            logger.info("Created new user: ${newUser.username}")
        } catch (e: Exception) {
            logger.error("Failed to create user$i: ${e.message}", e)
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
                            contentId = NanoIdUtils.generate(),
                            artist = getRandomArtist(),
                            track = getRandomTrack(),
                            album = if (Random.nextBoolean()) getRandomAlbum() else null,
                            trackUrls = listOf(
                                StreamingLink(
                                    platform = StreamingPlatform.SPOTIFY,
                                    url = "https://open.spotify.com/track/${getRandomId()}",
                                ),
                                StreamingLink(
                                    platform = StreamingPlatform.YOUTUBE_MUSIC,
                                    url = "https://www.youtube.com/watch?v=${getRandomId()}",
                                ),
                            ),
                            trackPreviewUrl = "https://example.com/preview/${getRandomId()}.mp3",
                            coverUrl = getRandomCoverUrl(),
                            difficulty = difficulties.random(),
                            isDeluxe = Random.nextBoolean(),
                            isExplicit = Random.nextBoolean(),
                            genre = Genre.entries.toTypedArray().random(),
                            bundleUrl = "https://example.com/charts/${getRandomId()}.bscm",
                            previewUrl = "https://example.com/chartpreviews/${getRandomId()}.jpg",
                            duration = Random.nextFloat() * 4 + 2, // 2-6 minutes
                            notesAmount = Random.nextInt(100, 1000),
                            effectsAmount = Random.nextInt(10, 100),
                            bpm = Random.nextInt(80, 180),
                        ),
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
                                        chartId = chart.id.toULong(),
                                        CreateVersionRequest(
                                            track = chart.track,
                                            artist = chart.artist,
                                            duration = Random.nextFloat() * 4 + 2,
                                            notesAmount = Random.nextInt(100, 1000),
                                            effectsAmount = Random.nextInt(10, 100),
                                            bpm = Random.nextInt(80, 180),
                                            difficulty = difficulties.random(),
                                            isDeluxe = Random.nextBoolean(),
                                            isExplicit = Random.nextBoolean(),
                                            bundleUrl = "https://example.com/charts/${getRandomId()}.bscm",
                                            previewUrl = "https://example.com/chartpreviews/${getRandomId()}.jpg",
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
                                chart.id.toULong(),
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
                                        chart.id.toULong(),
                                        KnownIssue(
                                            id = UUID.randomUUID(),
                                            description = getRandomIssue(),
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
    val artists = listOf(
        "Avicii", "Coldplay", "Imagine Dragons", "The Weeknd", "Dua Lipa",
        "Linkin Park", "BTS", "Billie Eilish", "Taylor Swift", "Ed Sheeran",
        "Queen", "The Beatles", "Michael Jackson", "Madonna", "Prince",
        "David Bowie", "Elton John", "Rihanna", "Katy Perry", "Justin Bieber",
        "Drake", "Beyoncé", "Bruno Mars", "Adele", "Sia",
        "Maroon 5", "Ariana Grande", "Post Malone", "Harry Styles", "Lorde",
        "Kendrick Lamar", "Eminem", "Kanye West", "Taylor Swift", "Oasis",
        "Blur", "Red Hot Chili Peppers", "Nirvana", "Metallica", "Guns N' Roses"
    )
    return artists.random()
}

private fun getRandomTrack(): String {
    val tracks = listOf(
        "Levels", "Viva La Vida", "Believer", "Blinding Lights", "Don't Start Now",
        "In The End", "Dynamite", "Bad Guy", "Shake It Off", "Shape of You",
        "Bohemian Rhapsody", "Hey Jude", "Billie Jean", "Like a Prayer", "Purple Rain",
        "Space Oddity", "Rocket Man", "Umbrella", "Firework", "Baby",
        "God's Plan", "Single Ladies", "Uptown Funk", "Rolling in the Deep", "Chandelier",
        "Sugar", "Thank U, Next", "Rockstar", "Watermelon Sugar", "Royals",
        "Humble", "Lose Yourself", "Stronger", "Blank Space", "Wonderwall",
        "Song 2", "Californication", "Smells Like Teen Spirit", "Enter Sandman", "Sweet Child o' Mine"
    )
    return tracks.random()
}

private fun getRandomAlbum(): String {
    val albums = listOf(
        "True",
        "Viva la Vida",
        "Evolve",
        "After Hours",
        "Future Nostalgia",
        "Hybrid Theory",
        "Map of the Soul",
        "When We All Fall Asleep",
        "1989",
        "÷",
        "A Night at the Opera",
        "Abbey Road",
        "Thriller",
        "Like a Virgin",
        "Purple Rain",
        "The Rise and Fall of Ziggy Stardust",
        "Goodbye Yellow Brick Road",
        "Good Girl Gone Bad",
        "Teenage Dream",
        "My World 2.0",
        "Views",
        "Lemonade",
        "24K Magic",
        "21",
        "1000 Forms of Fear",
        "V",
        "Sweetener",
        "Beerbongs & Bentleys",
        "Fine Line",
        "Pure Heroine",
        "DAMN",
        "The Eminem Show",
        "My Beautiful Dark Twisted Fantasy",
        "Red",
        "Definitely Maybe",
        "Blur",
        "Californication",
        "Nevermind",
        "Metallica",
        "Appetite for Destruction"
    )
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

private fun getRandomCoverUrl(): String {
    val covers = listOf(
        "https://is1-ssl.mzstatic.com/image/thumb/Music211/v4/92/9f/69/929f69f1-9977-3a44-d674-11f70c852d1b/24UMGIM36186.rgb.jpg/600x600bb.jpg",
        "https://is1-ssl.mzstatic.com/image/thumb/Music125/v4/33/fd/32/33fd32b1-0e43-9b4a-8ed6-19643f23544e/21UMGIM26092.rgb.jpg/600x600bb.jpg",
        "https://is1-ssl.mzstatic.com/image/thumb/Music221/v4/1f/c9/3b/1fc93bbf-42f1-4385-0a3a-34d60f4e0451/artwork.jpg/600x600bb.jpg",
        "https://is1-ssl.mzstatic.com/image/thumb/Music126/v4/96/8b/08/968b08e4-cb2b-54c2-32bb-dba77ad1c2ca/634904912161.png/600x600bb.jpg",
        "https://is1-ssl.mzstatic.com/image/thumb/Music116/v4/f1/8f/b9/f18fb977-e326-dbc9-1416-69bb3a754d0c/5056167126287_Cover.jpg/600x600bb.jpg",
        "https://is1-ssl.mzstatic.com/image/thumb/Music128/v4/13/cd/b4/13cdb427-8b1b-6ed1-aa0d-654bdf3fd644/00602567140092.rgb.jpg/600x600bb.jpg",
        "https://is1-ssl.mzstatic.com/image/thumb/Music115/v4/15/e6/e8/15e6e8a4-4190-6a8b-86c3-ab4a51b88288/190295851286.jpg/600x600bb.jpg",
        "https://is1-ssl.mzstatic.com/image/thumb/Music69/v4/41/b5/ea/41b5ea6c-25f5-bebf-c5a0-fc42435fb301/qeZK4.png/600x600bb.jpg",
        "https://is1-ssl.mzstatic.com/image/thumb/Music115/v4/f2/0d/8b/f20d8bff-a927-ae98-6784-20a1f51cb23e/16UMGIM27642.rgb.jpg/600x600bb.jpg",
        "https://is1-ssl.mzstatic.com/image/thumb/Music115/v4/09/01/16/090116af-770e-23da-21a9-6bd30782eda5/00843930013562.rgb.jpg/600x600bb.jpg",
        "https://is1-ssl.mzstatic.com/image/thumb/Music125/v4/96/24/d4/9624d446-3b95-af38-4b8c-d3087e6f962c/886444933049.jpg/600x600bb.jpg",
        "https://is1-ssl.mzstatic.com/image/thumb/Music116/v4/a0/1a/10/a01a1080-7b1d-e475-a03c-394e5cbba66c/12UMGIM53876.rgb.jpg/600x600bb.jpg",
        "https://is1-ssl.mzstatic.com/image/thumb/Music126/v4/81/a4/dc/81a4dc50-8d7e-6ae5-71d3-f83393348248/15UMGIM59807.rgb.jpg/600x600bb.jpg",
        "https://is1-ssl.mzstatic.com/image/thumb/Music115/v4/e3/d4/de/e3d4def5-886e-da47-e1cf-568566510b53/15UMGIM62454.rgb.jpg/600x600bb.jpg",
        "https://is1-ssl.mzstatic.com/image/thumb/Music126/v4/c1/54/2d/c1542d45-c6c2-12ca-7308-6eacd762c562/190295807870.jpg/600x600bb.jpg",
        "https://example.com/error.png",
        "https://example.com/error.png",
        "https://example.com/error.png"
    )

    return covers.random()
}
