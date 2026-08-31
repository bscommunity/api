package org.bscm.plugins

import io.ktor.server.application.*
import io.ktor.util.logging.*
import kotlinx.coroutines.*
import org.bscm.models.StreamingRef
import org.bscm.models.dto.chart.CreateChartRequest
import org.bscm.models.dto.contributor.SimplifiedContributor
import org.bscm.models.dto.user.CreateUserRequest
import org.bscm.models.dto.user.SimplifiedUser
import org.bscm.models.dto.version.VersionBundleData
import org.bscm.models.enums.*
import org.bscm.models.interfaces.*
import org.bscm.utils.NanoIdUtils
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.suspendTransaction
import org.koin.ktor.ext.inject
import kotlin.random.Random

private val log = KtorSimpleLogger("Seed")

fun Application.seedDatabase() {
    val chartRepository by inject<IChartRepository>()
    val userRepository by inject<IUserRepository>()
    val versionRepository by inject<IVersionRepository>()
    val contributorRepository by inject<IContributorRepository>()
    val tourPassRepository by inject<ITourPassRepository>()
    val themeRepository by inject<IThemeRepository>()
    val collectionRepository by inject<ICollectionRepository>()
    val activityRepository by inject<IActivityRepository>()

    runBlocking {
        // Check if data already exists — skip seeding if so
        val existingUsers = suspendTransaction {
            org.bscm.models.tables.UserTable.selectAll().count()
        }
        if (existingUsers > 0) {
            log.info("Database already contains $existingUsers users — skipping seed")
            return@runBlocking
        }

        log.info("Seeding database with fake data...")

        val users = createUsers(userRepository)
        if (users.isEmpty()) {
            log.error("No users created. Aborting seed.")
            return@runBlocking
        }

        val chartIds = createCharts(
            count = 50,
            users = users,
            chartRepository = chartRepository,
            versionRepository = versionRepository,
            contributorRepository = contributorRepository,
        )

        coroutineScope {
            launch { createTourPasses(users, chartIds, tourPassRepository, activityRepository) }
            launch { createThemes(users, themeRepository, activityRepository) }
            launch { createCollections(users, chartIds, collectionRepository, activityRepository) }
            launch { createFollows(users, userRepository, activityRepository) }
        }

        log.info("Seed completed: ${users.size} users, ${chartIds.size} charts, 8 tour passes, 6 themes, 12 collections")
    }
}

// ---------------------------------------------------------------------------
// Users
// ---------------------------------------------------------------------------

private suspend fun createUsers(userRepository: IUserRepository): List<SimplifiedUser> {
    data class UserSeed(
        val username: String,
        val email: String,
        val discordId: String,
        val avatarUrl: String?,
        val bannerUrl: String?,
        val accentColor: Int?,
        val bio: String?,
    )

    val seeds = listOf(
        UserSeed("nova", "nova@example.com", "1000000000000000001",
            "https://i.pravatar.cc/150?u=nova", "https://images.unsplash.com/photo-1557683316-973673baf926?w=600", 0xFF6C63FF.toInt(), "Chart creator & rhythm game enthusiast"),
        UserSeed("axel", "axel@example.com", "1000000000000000002",
            "https://i.pravatar.cc/150?u=axel", "https://images.unsplash.com/photo-1557682250-33bd709cbe85?w=600", 0xFFFF6B6B.toInt(), "Audio engineer | BPM detective"),
        UserSeed("luna", "luna@example.com", "1000000000000000003",
            "https://i.pravatar.cc/150?u=luna", "https://images.unsplash.com/photo-1557682224-5b8590cd9ec5?w=600", 0xFFFFE66D.toInt(), "Making charts since 2019"),
        UserSeed("kai", "kai@example.com", "1000000000000000004",
            "https://i.pravatar.cc/150?u=kai", "https://images.unsplash.com/photo-1557682268-e3955ed5d83f?w=600", 0xFF4ECDC4.toInt(), "Hard mode only"),
        UserSeed("ember", "ember@example.com", "1000000000000000005",
            "https://i.pravatar.cc/150?u=ember", "https://images.unsplash.com/photo-1557682260-96773506d070?w=600", 0xFFFF4757.toInt(), "Tour pass curator"),
        UserSeed("sage", "sage@example.com", "1000000000000000006",
            "https://i.pravatar.cc/150?u=sage", "https://images.unsplash.com/photo-1557682254-62e0f12dfa1e?w=600", 0xFF2ED573.toInt(), "Theme designer & UI nerd"),
        UserSeed("riley", "riley@example.com", "1000000000000000007",
            "https://i.pravatar.cc/150?u=riley", "https://images.unsplash.com/photo-1557682220-4a4b79d4c2d5?w=600", 0xFFA29BFE.toInt(), "Chart reviewer | QA"),
        UserSeed("zara", "zara@example.com", "1000000000000000008",
            "https://i.pravatar.cc/150?u=zara", "https://images.unsplash.com/photo-1557683311-eac922347aa1?w=600", 0xFFFF9FF3.toInt(), "Collector of rare charts"),
        UserSeed("orion", "orion@example.com", "1000000000000000009",
            "https://i.pravatar.cc/150?u=orion", "https://images.unsplash.com/photo-1557682237-64a0d53d6307?w=600", 0xFF1DD1A1.toInt(), "Extreme enjoyer"),
        UserSeed("pixel", "pixel@example.com", "1000000000000000010",
            "https://i.pravatar.cc/150?u=pixel", "https://images.unsplash.com/photo-1557682250-0bf86a8e8aaf?w=600", 0xFFF8A5C2.toInt(), "New here, learning the ropes"),
    )

    val users = mutableListOf<SimplifiedUser>()
    for (seed in seeds) {
        try {
            val user = userRepository.createUser(
                CreateUserRequest(
                    username = seed.username,
                    email = seed.email,
                    discordId = seed.discordId,
                    avatarUrl = seed.avatarUrl,
                    bannerUrl = seed.bannerUrl,
                    accentColor = seed.accentColor,
                )
            )
            users.add(
                SimplifiedUser(
                    id = user.id,
                    username = user.username,
                    avatarUrl = user.avatarUrl,
                    bannerUrl = user.bannerUrl,
                    bio = seed.bio,
                    accentColor = seed.accentColor,
                    isVerified = user.isVerified,
                )
            )
            log.info("Created user: ${seed.username}")
        } catch (e: Exception) {
            log.error("Failed to create user ${seed.username}: ${e.message}", e)
        }
    }
    return users
}

// ---------------------------------------------------------------------------
// Charts
// ---------------------------------------------------------------------------

private suspend fun createCharts(
    count: Int,
    users: List<SimplifiedUser>,
    chartRepository: IChartRepository,
    versionRepository: IVersionRepository,
    contributorRepository: IContributorRepository,
): List<String> = coroutineScope {
    val chartIds = mutableListOf<String>()
    val userIds = users.map { it.id }
    val difficulties = Difficulty.entries.toTypedArray()
    val contributorRoles = ContributorRole.entries.toTypedArray()

    val dispatcher = Dispatchers.IO.limitedParallelism(20)
    val batchSize = 15

    (0 until count step batchSize).map { batchStart ->
        val batchEnd = minOf(batchStart + batchSize, count)

        async(dispatcher) {
            val batchIds = mutableListOf<String>()

            (batchStart until batchEnd).forEach { i ->
                try {
                    val ownerId = userIds.random()

                    val chart = chartRepository.createChart(
                        userId = ownerId,
                        chart = CreateChartRequest(
                            catalogId = NanoIdUtils.generateCatalogId(),
                            artist = getRandomArtist(),
                            track = getRandomTrack(),
                            album = if (Random.nextFloat() > 0.3f) getRandomAlbum() else null,
                            trackUrls = listOf(
                                StreamingRef(
                                    platform = StreamingPlatform.SPOTIFY,
                                    url = "https://open.spotify.com/track/${randomHexId(22)}",
                                ),
                                StreamingRef(
                                    platform = StreamingPlatform.entries.random(),
                                    url = "https://example.com/track/${randomHexId(10)}",
                                ),
                            ),
                            trackPreviewUrl = "https://example.com/preview/${randomHexId(10)}.mp3",
                            coverUrl = getRandomCoverUrl(),
                            difficulty = difficulties.random(),
                            isDeluxe = Random.nextFloat() > 0.6f,
                            isExplicit = Random.nextFloat() > 0.7f,
                            genres = listOf(Genre.entries.random()),
                            previewUrl = "https://example.com/chartpreviews/${randomHexId(10)}.jpg",
                            fileSizeBytes = Random.nextLong(1_000_000, 30_000_000),
                            duration = Random.nextFloat() * 4 + 2,
                            notesAmount = Random.nextInt(100, 1000),
                            effectsAmount = Random.nextInt(10, 100),
                            bpm = Random.nextInt(80, 180),
                        ),
                    )

                    batchIds.add(chart.id)

                    // Additional versions (50% chance)
                    if (Random.nextFloat() > 0.5f) {
                        val count2 = Random.nextInt(1, 4)
                        repeat(count2) {
                            val bundleHash = randomHexId(64)
                            suspendTransaction {
                                versionRepository.addVersion(
                                    catalogItemId = chart.id,
                                    version = VersionBundleData(
                                        fileSizeBytes = Random.nextLong(1_000_000, 30_000_000),
                                        changelog = listOf(
                                            getRandomChangelog(),
                                            getRandomChangelog(),
                                        ).filter { Random.nextFloat() > 0.5f }.joinToString("\n"),
                                    ),
                                    bundleHash = bundleHash,
                                )
                            }
                        }
                    }

                    // Contributors (1-3)
                    val count2 = Random.nextInt(1, 4)
                    val contributors = userIds.filter { it != ownerId }.shuffled().take(count2)
                    contributorRepository.addContributors(
                        chart.id,
                        contributors.map { SimplifiedContributor(it, contributorRoles.random()) }
                    )
                } catch (e: Exception) {
                    log.error("Error generating chart $i: ${e.message}", e)
                }
            }

            synchronized(chartIds) { batchIds.forEach { chartIds.add(it) } }
        }
    }.awaitAll()

    log.info("Created ${chartIds.size} charts")
    chartIds
}

// ---------------------------------------------------------------------------
// Tour Passes
// ---------------------------------------------------------------------------

private suspend fun createTourPasses(
    users: List<SimplifiedUser>,
    chartIds: List<String>,
    tourPassRepository: ITourPassRepository,
    activityRepository: IActivityRepository,
) {
    if (chartIds.isEmpty()) return

    data class TourPassSeed(
        val name: String,
        val description: String,
        val artist: String?,
        val chartCount: Int,
    )

    val seeds = listOf(
        TourPassSeed("Summer Vibes 2025", "The ultimate summer playlist for chart enthusiasts", null, 5),
        TourPassSeed("Rock Legends Pack", "Iconic rock tracks from the golden era", null, 4),
        TourPassSeed("Electronic Dreams", "Best EDM and dance charts to keep you moving", null, 5),
        TourPassSeed("Hip Hop Essentials", "Core hip hop tracks every player should try", null, 4),
        TourPassSeed("Beginner Friendly", "Perfect charts for newcomers to the game", null, 3),
        TourPassSeed("Extreme Challenge", "Only for the most skilled players", null, 5),
        TourPassSeed("K-Pop Collection", "Top K-Pop hits remixed as charts", "Various", 4),
        TourPassSeed("Classic Throwbacks", "Nostalgic tracks that defined a generation", null, 4),
    )

    seeds.forEachIndexed { index, seed ->
        try {
            val ownerId = users[index % users.size].id
            val shuffled = chartIds.shuffled()
            val selectedCharts = shuffled.take(minOf(seed.chartCount, chartIds.size))

            val tourPass = tourPassRepository.createTourPass(
                userId = ownerId,
                name = seed.name,
                description = seed.description,
                artist = seed.artist,
                chartIds = selectedCharts,
            )

            activityRepository.logActivity(
                userId = ownerId,
                type = ActivityType.CREATED_TOUR_PASS,
                targetId = tourPass.id,
            )

            log.info("Created tour pass: ${seed.name} with ${selectedCharts.size} charts")
        } catch (e: Exception) {
            log.error("Error creating tour pass ${seed.name}: ${e.message}", e)
        }
    }
}

// ---------------------------------------------------------------------------
// Themes
// ---------------------------------------------------------------------------

private suspend fun createThemes(
    users: List<SimplifiedUser>,
    themeRepository: IThemeRepository,
    activityRepository: IActivityRepository,
) {
    data class ThemeSeed(
        val name: String,
        val replaces: String,
        val previewUrl: String,
    )

    val seeds = listOf(
        ThemeSeed("Midnight Dark", "Dark Mode", "https://images.unsplash.com/photo-1557683316-973673baf926?w=400"),
        ThemeSeed("Ocean Blue", "Default Theme", "https://images.unsplash.com/photo-1557682250-33bd709cbe85?w=400"),
        ThemeSeed("Sunset Glow", "Accent Colors", "https://images.unsplash.com/photo-1557682224-5b8590cd9ec5?w=400"),
        ThemeSeed("Forest Green", "Default Theme", "https://images.unsplash.com/photo-1557682268-e3955ed5d83f?w=400"),
        ThemeSeed("Neon Pulse", "Dark Mode", "https://images.unsplash.com/photo-1557682260-96773506d070?w=400"),
        ThemeSeed("Pastel Dreams", "Accent Colors", "https://images.unsplash.com/photo-1557682254-62e0f12dfa1e?w=400"),
    )

    seeds.forEachIndexed { index, seed ->
        try {
            val ownerId = users[index % users.size].id

            val theme = themeRepository.createTheme(
                userId = ownerId,
                name = seed.name,
                replaces = seed.replaces,
                previewUrl = seed.previewUrl,
                originalArtwork = null,
                id = null,
            )

            activityRepository.logActivity(
                userId = ownerId,
                type = ActivityType.CREATED_THEME,
                targetId = theme.id,
            )

            log.info("Created theme: ${seed.name}")
        } catch (e: Exception) {
            log.error("Error creating theme ${seed.name}: ${e.message}", e)
        }
    }
}

// ---------------------------------------------------------------------------
// Collections
// ---------------------------------------------------------------------------

private suspend fun createCollections(
    users: List<SimplifiedUser>,
    chartIds: List<String>,
    collectionRepository: ICollectionRepository,
    activityRepository: IActivityRepository,
) {
    if (chartIds.isEmpty()) return

    data class CollectionSeed(
        val name: String,
        val isPublic: Boolean,
        val itemCount: Int,
    )

    val seeds = listOf(
        CollectionSeed("My Favorites", true, 8),
        CollectionSeed("Workout Mix", true, 6),
        CollectionSeed("Chill Charts", true, 5),
        CollectionSeed("Party Playlist", true, 7),
        CollectionSeed("Hidden Gems", false, 4),
        CollectionSeed("Hard Mode Only", true, 5),
        CollectionSeed("New Releases", true, 6),
        CollectionSeed("All-Time Best", true, 10),
        CollectionSeed("Quick Plays", false, 4),
        CollectionSeed("For Beginners", true, 5),
        CollectionSeed("Speed Runs", true, 6),
        CollectionSeed("Draft Ideas", false, 3),
    )

    seeds.forEachIndexed { index, seed ->
        try {
            val ownerId = users[index % users.size].id

            val collection = collectionRepository.createCollection(
                userId = ownerId,
                name = seed.name,
                isPublic = seed.isPublic,
            )

            val shuffled = chartIds.shuffled()
            val selectedItems = shuffled.take(minOf(seed.itemCount, chartIds.size))
            selectedItems.forEach { catalogId ->
                collectionRepository.addItemToCollection(
                    collectionId = collection.id,
                    collectionKind = CollectionKind.USER,
                    userId = ownerId,
                    catalogId = catalogId,
                )
            }

            log.info("Created collection: ${seed.name} with ${selectedItems.size} items")
        } catch (e: Exception) {
            log.error("Error creating collection ${seed.name}: ${e.message}", e)
        }
    }
}

// ---------------------------------------------------------------------------
// User Follows
// ---------------------------------------------------------------------------

private suspend fun createFollows(
    users: List<SimplifiedUser>,
    userRepository: IUserRepository,
    activityRepository: IActivityRepository,
) {
    val userIds = users.map { it.id }
    var followCount = 0

    // Each user follows 2-4 random other users
    for (user in users) {
        val followCount2 = Random.nextInt(2, 5)
        val targets = userIds.filter { it != user.id }.shuffled().take(followCount2)

        for (targetId in targets) {
            try {
                val didFollow = userRepository.followUser(user.id, targetId)
                if (didFollow) {
                    followCount++
                    activityRepository.logActivity(
                        userId = user.id,
                        type = ActivityType.FOLLOWED_USER,
                        targetId = targetId.toString(),
                    )
                }
            } catch (e: Exception) {
                // Ignore duplicate follows
            }
        }
    }

    log.info("Created $followCount follow relationships")
}

// ---------------------------------------------------------------------------
// Random data generators
// ---------------------------------------------------------------------------

private fun randomHexId(length: Int): String =
    (1..length).map { "0123456789abcdef"[Random.nextInt(16)] }.joinToString("")

private fun getRandomArtist(): String = listOf(
    "Avicii", "Coldplay", "Imagine Dragons", "The Weeknd", "Dua Lipa",
    "Linkin Park", "BTS", "Billie Eilish", "Taylor Swift", "Ed Sheeran",
    "Queen", "The Beatles", "Michael Jackson", "Madonna", "Prince",
    "David Bowie", "Elton John", "Rihanna", "Katy Perry", "Justin Bieber",
    "Drake", "Beyonce", "Bruno Mars", "Adele", "Sia",
    "Maroon 5", "Ariana Grande", "Post Malone", "Harry Styles", "Lorde",
    "Kendrick Lamar", "Eminem", "Kanye West", "Oasis", "Blur",
    "Red Hot Chili Peppers", "Nirvana", "Metallica", "Guns N' Roses",
    "Daft Punk", "The Chainsmokers", "Skrillex", "Deadmau5", "Marshmello",
).random()

private fun getRandomTrack(): String = listOf(
    "Levels", "Viva La Vida", "Believer", "Blinding Lights", "Don't Start Now",
    "In The End", "Dynamite", "Bad Guy", "Shake It Off", "Shape of You",
    "Bohemian Rhapsody", "Hey Jude", "Billie Jean", "Like a Prayer", "Purple Rain",
    "Space Oddity", "Rocket Man", "Umbrella", "Firework", "Baby",
    "God's Plan", "Single Ladies", "Uptown Funk", "Rolling in the Deep", "Chandelier",
    "Sugar", "Thank U Next", "Rockstar", "Watermelon Sugar", "Royals",
    "Humble", "Lose Yourself", "Stronger", "Blank Space", "Wonderwall",
    "Song 2", "Californication", "Smells Like Teen Spirit", "Enter Sandman", "Sweet Child O Mine",
    "One More Time", "Titanium", "Scary Monsters", "Bangarang", "Alone",
).random()

private fun getRandomAlbum(): String = listOf(
    "True", "Viva la Vida", "Evolve", "After Hours", "Future Nostalgia",
    "Hybrid Theory", "Map of the Soul", "When We All Fall Asleep", "1989",
    "A Night at the Opera", "Abbey Road", "Thriller", "Purple Rain",
    "Goodbye Yellow Brick Road", "Teenage Dream", "Views", "Lemonade",
    "24K Magic", "21", "Sweetener", "Fine Line", "Pure Heroine",
    "DAMN", "The Eminem Show", "Red", "Definitely Maybe", "Nevermind",
    "Discovery", "Random Access Memories", "Worlds",
).random()

private fun getRandomChangelog(): String = listOf(
    "Fixed notes out of sync with the beat",
    "Added missing effects at the chorus",
    "Corrected BPM in the bridge section",
    "Fixed overlapping notes in the second verse",
    "Adjusted sound effects volume",
    "Resolved chart crash at the end",
    "Improved performance on older devices",
    "Updated preview image",
    "Fixed timing in the bridge section",
    "Added missing lyrics section",
    "Improved note density for better flow",
    "Fixed audio sync issue",
).random()

private fun getRandomCoverUrl(): String = listOf(
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
).random()
