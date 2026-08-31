package org.bscm.plugins

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
import org.bscm.repository.*
import org.bscm.storage.StaticUrlStorageAdapter
import org.bscm.storage.StorageService
import org.bscm.utils.NanoIdUtils
import org.flywaydb.core.Flyway
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.suspendTransaction
import java.util.*
import kotlin.random.Random

/**
 * Standalone seed command — run via:
 *   ./gradlew runSeed
 *
 * Requires POSTGRES_URL, POSTGRES_USER, POSTGRES_PASSWORD env vars.
 * The URL must NOT have the jdbc: prefix (e.g. "//localhost:5432/bscm").
 */
private val log = KtorSimpleLogger("SeedCommand")

fun main(): Unit = runBlocking {
    val pgUrl = System.getenv("POSTGRES_URL")
        ?: error("POSTGRES_URL env var is required (e.g. //localhost:5432/bscm)")
    val pgUser = System.getenv("POSTGRES_USER") ?: error("POSTGRES_USER env var is required")
    val pgPassword = System.getenv("POSTGRES_PASSWORD") ?: error("POSTGRES_PASSWORD env var is required")

    val jdbcUrl = "jdbc:$pgUrl"
    println("[Seed] Connecting to $jdbcUrl ...")

    Database.connect(
        url = jdbcUrl,
        driver = "org.postgresql.Driver",
        user = pgUser,
        password = pgPassword,
    )

    Flyway.configure()
        .dataSource(jdbcUrl, pgUser, pgPassword)
        .baselineOnMigrate(true)
        .baselineVersion("0")
        .load()
        .migrate()

    println("[Seed] Migrations applied. Setting up repositories...")

    // Minimal DI — only repositories needed for seeding (no external services)
    val storageService = StorageService(
        adapter = StaticUrlStorageAdapter(publicBaseUrl = "https://example.com/assets"),
        publicBucket = "public",
    )
    val catalogItemRepository = CatalogItemRepository()
    val versionRepository: IVersionRepository = VersionRepository()
    val chartRepository: IChartRepository = ChartRepository(
        TrackRepository(storageService), catalogItemRepository, versionRepository,
        AlbumRepository()
    )
    val contributorRepository: IContributorRepository = ContributorRepository()
    val tourPassRepository: ITourPassRepository = TourPassRepository(
        chartRepository, catalogItemRepository, storageService
    )
    val themeRepository: IThemeRepository = ThemeRepository(catalogItemRepository, storageService)
    val collectionRepository: ICollectionRepository = CollectionRepository(
        chartRepository, themeRepository, tourPassRepository, TrackRepository(storageService), storageService
    )
    val activityRepository: IActivityRepository = ActivityRepository()
    val userRepository: IUserRepository = UserRepository(
        chartRepository, themeRepository, tourPassRepository, collectionRepository
    )

    // Check if data already exists
    val existingUsers = suspendTransaction {
        org.bscm.models.tables.UserTable.selectAll().count()
    }
    if (existingUsers > 0) {
        println("[Seed] Database already contains $existingUsers users — skipping.")
        return@runBlocking
    }

    println("[Seed] Seeding database...")

    // ── Users ──────────────────────────────────────────────────────────────
    val users = createSeedUsers(userRepository)
    if (users.isEmpty()) {
        println("[Seed] ERROR: No users created. Aborting.")
        return@runBlocking
    }
    val userIds = users.map { it.id }

    // ── Charts ─────────────────────────────────────────────────────────────
    val chartIds = createSeedCharts(50, userIds, chartRepository, versionRepository, contributorRepository)

    // ── Post-chart steps (parallel) ────────────────────────────────────────
    coroutineScope {
        launch { createSeedTourPasses(userIds, chartIds, tourPassRepository, activityRepository) }
        launch { createSeedThemes(userIds, themeRepository, activityRepository) }
        launch { createSeedCollections(userIds, chartIds, collectionRepository) }
        launch { createSeedFollows(userIds, userRepository, activityRepository) }
    }

    println("[Seed] Done! ${users.size} users, ${chartIds.size} charts, 8 tour passes, 6 themes, 12 collections.")
}

// ===========================================================================
// Below: duplicated helpers from Seed.kt (standalone variant, no Ktor DI).
// ===========================================================================

private suspend fun createSeedUsers(userRepository: IUserRepository): List<SimplifiedUser> {
    data class UserSeed(
        val username: String, val email: String, val discordId: String,
        val avatarUrl: String?, val bannerUrl: String?, val accentColor: Int?, val bio: String?,
    )

    val seeds = listOf(
        UserSeed("nova", "nova@example.com", "1000000000000000001", "https://i.pravatar.cc/150?u=nova", "https://images.unsplash.com/photo-1557683316-973673baf926?w=600", 0xFF6C63FF.toInt(), "Chart creator & rhythm game enthusiast"),
        UserSeed("axel", "axel@example.com", "1000000000000000002", "https://i.pravatar.cc/150?u=axel", "https://images.unsplash.com/photo-1557682250-33bd709cbe85?w=600", 0xFFFF6B6B.toInt(), "Audio engineer | BPM detective"),
        UserSeed("luna", "luna@example.com", "1000000000000000003", "https://i.pravatar.cc/150?u=luna", "https://images.unsplash.com/photo-1557682224-5b8590cd9ec5?w=600", 0xFFFFE66D.toInt(), "Making charts since 2019"),
        UserSeed("kai", "kai@example.com", "1000000000000000004", "https://i.pravatar.cc/150?u=kai", "https://images.unsplash.com/photo-1557682268-e3955ed5d83f?w=600", 0xFF4ECDC4.toInt(), "Hard mode only"),
        UserSeed("ember", "ember@example.com", "1000000000000000005", "https://i.pravatar.cc/150?u=ember", "https://images.unsplash.com/photo-1557682260-96773506d070?w=600", 0xFFFF4757.toInt(), "Tour pass curator"),
        UserSeed("sage", "sage@example.com", "1000000000000000006", "https://i.pravatar.cc/150?u=sage", "https://images.unsplash.com/photo-1557682254-62e0f12dfa1e?w=600", 0xFF2ED573.toInt(), "Theme designer & UI nerd"),
        UserSeed("riley", "riley@example.com", "1000000000000000007", "https://i.pravatar.cc/150?u=riley", "https://images.unsplash.com/photo-1557682254-62e0f12dfa1e?w=600", 0xFFA29BFE.toInt(), "Chart reviewer | QA"),
        UserSeed("zara", "zara@example.com", "1000000000000000008", "https://i.pravatar.cc/150?u=zara", "https://images.unsplash.com/photo-1557683311-eac922347aa1?w=600", 0xFFFF9FF3.toInt(), "Collector of rare charts"),
        UserSeed("orion", "orion@example.com", "1000000000000000009", "https://i.pravatar.cc/150?u=orion", "https://images.unsplash.com/photo-1557682237-64a0d53d6307?w=600", 0xFF1DD1A1.toInt(), "Extreme enjoyer"),
        UserSeed("pixel", "pixel@example.com", "1000000000000000010", "https://i.pravatar.cc/150?u=pixel", "https://images.unsplash.com/photo-1557682250-0bf86a8e8aaf?w=600", 0xFFF8A5C2.toInt(), "New here, learning the ropes"),
    )

    val users = mutableListOf<SimplifiedUser>()
    for (s in seeds) {
        try {
            val user = userRepository.createUser(CreateUserRequest(
                username = s.username, email = s.email, discordId = s.discordId,
                avatarUrl = s.avatarUrl, bannerUrl = s.bannerUrl, accentColor = s.accentColor,
            ))
            users.add(SimplifiedUser(
                id = user.id, username = user.username, avatarUrl = user.avatarUrl,
                bannerUrl = user.bannerUrl, bio = s.bio, accentColor = s.accentColor, isVerified = user.isVerified,
            ))
            println("[Seed]   user: ${s.username}")
        } catch (e: Exception) {
            System.err.println("[Seed]   FAILED user ${s.username}: ${e.message}")
        }
    }
    return users
}

private suspend fun createSeedCharts(
    count: Int, userIds: List<UUID>,
    chartRepo: IChartRepository, versionRepo: IVersionRepository,
    contributorRepo: IContributorRepository,
): List<String> = coroutineScope {
    val chartIds = mutableListOf<String>()
    val difficulties = Difficulty.entries.toTypedArray()
    val roles = ContributorRole.entries.toTypedArray()

    val dispatcher = Dispatchers.IO.limitedParallelism(20)
    val batchSize = 15

    (0 until count step batchSize).map { batchStart ->
        val batchEnd = minOf(batchStart + batchSize, count)

        async(dispatcher) {
            val batchIds = mutableListOf<String>()

            (batchStart until batchEnd).forEach { i ->
                try {
                    val ownerId = userIds.random()
                    val chart = chartRepo.createChart(
                        userId = ownerId,
                        chart = CreateChartRequest(
                            catalogId = NanoIdUtils.generateCatalogId(),
                            artist = seedArtist(), track = seedTrack(),
                            album = if (Random.nextFloat() > 0.3f) seedAlbum() else null,
                            trackUrls = listOf(
                                StreamingRef(StreamingPlatform.SPOTIFY, "https://open.spotify.com/track/${hexId(22)}"),
                                StreamingRef(StreamingPlatform.entries.random(), "https://example.com/track/${hexId(10)}"),
                            ),
                            trackPreviewUrl = "https://example.com/preview/${hexId(10)}.mp3",
                            coverUrl = seedCover(), difficulty = difficulties.random(),
                            isDeluxe = Random.nextFloat() > 0.6f, isExplicit = Random.nextFloat() > 0.7f,
                            genres = listOf(Genre.entries.random()),
                            previewUrl = "https://example.com/chartpreviews/${hexId(10)}.jpg",
                            fileSizeBytes = Random.nextLong(1_000_000, 30_000_000),
                            duration = Random.nextFloat() * 4 + 2,
                            notesAmount = Random.nextInt(100, 1000),
                            effectsAmount = Random.nextInt(10, 100),
                            bpm = Random.nextInt(80, 180),
                        ),
                    )
                    batchIds.add(chart.id)

                    // Versions (50%)
                    if (Random.nextFloat() > 0.5f) {
                        repeat(Random.nextInt(1, 4)) {
                            suspendTransaction {
                                versionRepo.addVersion(
                                    catalogItemId = chart.id,
                                    version = VersionBundleData(
                                        fileSizeBytes = Random.nextLong(1_000_000, 30_000_000),
                                    ),
                                    bundleHash = hexId(64),
                                )
                            }
                        }
                    }

                    // Contributors (1-3)
                    val contribs = userIds.filter { it != ownerId }.shuffled().take(Random.nextInt(1, 4))
                    contributorRepo.addContributors(chart.id, contribs.map { SimplifiedContributor(it, roles.random()) })
                } catch (e: Exception) {
                    System.err.println("[Seed]   FAILED chart $i: ${e.message}")
                }
            }

            synchronized(chartIds) { batchIds.forEach { chartIds.add(it) } }
        }
    }.awaitAll()

    println("[Seed]   charts: ${chartIds.size}/$count")
    chartIds
}

private suspend fun createSeedTourPasses(
    userIds: List<UUID>, chartIds: List<String>,
    repo: ITourPassRepository, activityRepo: IActivityRepository,
) {
    data class Seed(val name: String, val desc: String, val artist: String?, val n: Int)
    val seeds = listOf(
        Seed("Summer Vibes 2025", "The ultimate summer playlist", null, 5),
        Seed("Rock Legends Pack", "Iconic rock tracks", null, 4),
        Seed("Electronic Dreams", "Best EDM charts", null, 5),
        Seed("Hip Hop Essentials", "Core hip hop tracks", null, 4),
        Seed("Beginner Friendly", "Perfect for newcomers", null, 3),
        Seed("Extreme Challenge", "Only for the best", null, 5),
        Seed("K-Pop Collection", "Top K-Pop hits", "Various", 4),
        Seed("Classic Throwbacks", "Nostalgic tracks", null, 4),
    )
    seeds.forEachIndexed { i, s ->
        try {
            val owner = userIds[i % userIds.size]
            val selected = chartIds.shuffled().take(minOf(s.n, chartIds.size))
            val tp = repo.createTourPass(userId = owner, name = s.name, description = s.desc, artist = s.artist, chartIds = selected)
            activityRepo.logActivity(owner, ActivityType.CREATED_TOUR_PASS, tp.id)
            println("[Seed]   tour pass: ${s.name}")
        } catch (e: Exception) {
            System.err.println("[Seed]   FAILED tour pass ${s.name}: ${e.message}")
        }
    }
}

private suspend fun createSeedThemes(
    userIds: List<UUID>, repo: IThemeRepository, activityRepo: IActivityRepository,
) {
    data class Seed(val name: String, val replaces: String, val preview: String)
    val seeds = listOf(
        Seed("Midnight Dark", "Dark Mode", "https://images.unsplash.com/photo-1557683316-973673baf926?w=400"),
        Seed("Ocean Blue", "Default Theme", "https://images.unsplash.com/photo-1557682250-33bd709cbe85?w=400"),
        Seed("Sunset Glow", "Accent Colors", "https://images.unsplash.com/photo-1557682224-5b8590cd9ec5?w=400"),
        Seed("Forest Green", "Default Theme", "https://images.unsplash.com/photo-1557682268-e3955ed5d83f?w=400"),
        Seed("Neon Pulse", "Dark Mode", "https://images.unsplash.com/photo-1557682260-96773506d070?w=400"),
        Seed("Pastel Dreams", "Accent Colors", "https://images.unsplash.com/photo-1557682254-62e0f12dfa1e?w=400"),
    )
    seeds.forEachIndexed { i, s ->
        try {
            val owner = userIds[i % userIds.size]
            val theme = repo.createTheme(userId = owner, name = s.name, replaces = s.replaces, previewUrl = s.preview, id = null, originalArtwork = null)
            activityRepo.logActivity(owner, ActivityType.CREATED_THEME, theme.id)
            println("[Seed]   theme: ${s.name}")
        } catch (e: Exception) {
            System.err.println("[Seed]   FAILED theme ${s.name}: ${e.message}")
        }
    }
}

private suspend fun createSeedCollections(
    userIds: List<UUID>, chartIds: List<String>, repo: ICollectionRepository,
) {
    data class Seed(val name: String, val pub: Boolean, val n: Int)
    val seeds = listOf(
        Seed("My Favorites", true, 8), Seed("Workout Mix", true, 6),
        Seed("Chill Charts", true, 5), Seed("Party Playlist", true, 7),
        Seed("Hidden Gems", false, 4), Seed("Hard Mode Only", true, 5),
        Seed("New Releases", true, 6), Seed("All-Time Best", true, 10),
        Seed("Quick Plays", false, 4), Seed("For Beginners", true, 5),
        Seed("Speed Runs", true, 6), Seed("Draft Ideas", false, 3),
    )
    seeds.forEachIndexed { i, s ->
        try {
            val owner = userIds[i % userIds.size]
            val col = repo.createCollection(userId = owner, name = s.name, isPublic = s.pub)
            chartIds.shuffled().take(minOf(s.n, chartIds.size)).forEach { cid ->
                repo.addItemToCollection(col.id, CollectionKind.USER, owner, cid)
            }
            println("[Seed]   collection: ${s.name}")
        } catch (e: Exception) {
            System.err.println("[Seed]   FAILED collection ${s.name}: ${e.message}")
        }
    }
}

private suspend fun createSeedFollows(
    userIds: List<UUID>, repo: IUserRepository, activityRepo: IActivityRepository,
) {
    var n = 0
    for (uid in userIds) {
        val targets = userIds.filter { it != uid }.shuffled().take(Random.nextInt(2, 5))
        for (tid in targets) {
            try {
                if (repo.followUser(uid, tid)) {
                    n++
                    activityRepo.logActivity(uid, ActivityType.FOLLOWED_USER, tid.toString())
                }
            } catch (_: Exception) {}
        }
    }
    println("[Seed]   follows: $n")
}

// ── Random generators ──────────────────────────────────────────────────────

private fun hexId(len: Int) = (1..len).map { "0123456789abcdef"[Random.nextInt(16)] }.joinToString("")

private fun seedArtist() = listOf(
    "Avicii","Coldplay","Imagine Dragons","The Weeknd","Dua Lipa","Linkin Park","BTS",
    "Billie Eilish","Taylor Swift","Ed Sheeran","Queen","The Beatles","Michael Jackson",
    "Madonna","Prince","David Bowie","Elton John","Rihanna","Katy Perry","Justin Bieber",
    "Drake","Beyonce","Bruno Mars","Adele","Sia","Maroon 5","Ariana Grande","Post Malone",
    "Harry Styles","Lorde","Kendrick Lamar","Eminem","Kanye West","Oasis","Blur",
    "Red Hot Chili Peppers","Nirvana","Metallica","Guns N' Roses","Daft Punk",
    "The Chainsmokers","Skrillex","Deadmau5","Marshmello",
).random()

private fun seedTrack() = listOf(
    "Levels","Viva La Vida","Believer","Blinding Lights","Don't Start Now","In The End",
    "Dynamite","Bad Guy","Shake It Off","Shape of You","Bohemian Rhapsody","Hey Jude",
    "Billie Jean","Like a Prayer","Purple Rain","Space Oddity","Rocket Man","Umbrella",
    "Firework","Baby","God's Plan","Single Ladies","Uptown Funk","Rolling in the Deep",
    "Chandelier","Sugar","Thank U Next","Rockstar","Watermelon Sugar","Royals","Humble",
    "Lose Yourself","Stronger","Blank Space","Wonderwall","Song 2","Californication",
    "Smells Like Teen Spirit","Enter Sandman","Sweet Child O Mine","One More Time",
    "Titanium","Scary Monsters","Bangarang","Alone",
).random()

private fun seedAlbum() = listOf(
    "True","Viva la Vida","Evolve","After Hours","Future Nostalgia","Hybrid Theory",
    "Map of the Soul","When We All Fall Asleep","1989","A Night at the Opera","Abbey Road",
    "Thriller","Purple Rain","Goodbye Yellow Brick Road","Teenage Dream","Views",
    "Lemonade","24K Magic","21","Sweetener","Fine Line","Pure Heroine","DAMN",
    "The Eminem Show","Red","Definitely Maybe","Nevermind","Discovery",
    "Random Access Memories","Worlds",
).random()

private fun seedChangelog() = listOf(
    "Fixed notes out of sync with the beat","Added missing effects at the chorus",
    "Corrected BPM in the bridge section","Fixed overlapping notes in the second verse",
    "Adjusted sound effects volume","Resolved chart crash at the end",
    "Improved performance on older devices","Updated preview image",
    "Fixed timing in the bridge section","Added missing lyrics section",
    "Improved note density for better flow","Fixed audio sync issue",
).random()

private fun seedCover() = listOf(
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
).random()
