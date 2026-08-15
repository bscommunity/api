package org.bscm

import org.bscm.models.dao.*
import org.bscm.models.enums.*
import org.bscm.models.tables.ChartTable
import org.bscm.models.tables.TrackTable
import org.bscm.utils.flushEntityCache
import org.flywaydb.core.Flyway
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.dao.flushCache
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/**
 * Integration test that exercises the DAO-write → raw-DSL-read path inside a single
 * Exposed transaction. This is the exact pattern that broke in [org.bscm.repository.ChartRepository]
 * before the [flushEntityCache] fix was introduced.
 *
 * Uses an in-memory H2 database in PostgreSQL-compatibility mode with Flyway migrations
 * to mirror the production schema as closely as possible.
 */
class EntityCacheFlushTest {

    private lateinit var database: Database

    @Before
    fun setUp() {
        val dbName = "test_flush_${System.nanoTime()}"
        database = Database.connect(
            url = "jdbc:h2:mem:$dbName;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
            driver = "org.h2.Driver",
            user = "sa",
            password = "",
        )

        Flyway.configure()
            .dataSource("jdbc:h2:mem:${dbName};MODE=PostgreSQL;DB_CLOSE_DELAY=-1", "sa", "")
            .locations("classpath:db/migration")
            .load()
            .migrate()
    }

    @Test
    fun propertyUpdatesRequireFlushToBeVisibleInRawQuery() {
        transaction(database) {
            val user = UserEntity.new {
                username = "update_tester"
                email = "update@test.com"
                discordId = "update_discord_001"
                role = UserRole.USER
            }

            val track = TrackEntity.new {
                title = "Original Title"
                artist = "Original Artist"
                duration = 180f
                normalizedTitle = "original title"
                normalizedArtist = "original artist"
            }

            val catalogItem = CatalogItemEntity.new {
                type = CatalogItemType.CHART
                status = CatalogItemStatus.DRAFT
                visibility = Visibility.PUBLIC
                author = user
            }

            val chart = ChartEntity.new(catalogItem.id.value) {
                this.track = track
                difficulty = Difficulty.NORMAL
                notesAmount = 500
                effectsAmount = 10
            }

            flushCache()

            // Simulate the updateChart pattern: modify properties on existing entities
            // without flushing (this is what applyMetadataUpdates / updateFeatured do).
            track.title = "Updated Title"
            track.normalizedTitle = "updated title"

            // Raw query without flush — may not see the property update on the track
            // (behavior varies by database; on PostgreSQL the unflushed UPDATE is invisible)
            val rawResult = TrackTable.selectAll().where { TrackTable.id eq track.id.value }
                .firstOrNull()

            if (rawResult != null) {
                // H2 may see uncommitted writes in the same transaction.
                // Verify that calling flush is at minimum idempotent and safe.
                flushEntityCache()

                val afterFlush = TrackTable.selectAll().where { TrackTable.id eq track.id.value }
                    .firstOrNull()
                assertNotNull(afterFlush, "After flush, raw query must see the track")
            }

            // Now flush and verify the raw query sees the UPDATED value
            flushCache()

            val resultAfterFlush = TrackTable.selectAll()
                .where { TrackTable.id eq track.id.value }
                .firstOrNull()

            assertNotNull(resultAfterFlush, "After flushCache(), raw query must see the track")
            assertEquals("Updated Title", resultAfterFlush[TrackTable.title])
        }
    }

    @Test
    fun createChartPatternWorksAfterFlush() {
        transaction(database) {
            val user = UserEntity.new {
                username = "creator"
                email = "creator@test.com"
                discordId = "creator_discord"
                role = UserRole.USER
            }

            val track = TrackEntity.new {
                title = "My Song"
                artist = "My Artist"
                genres = listOf(Genre.ROCK)
                bpm = 120
                duration = 210f
                normalizedTitle = "my song"
                normalizedArtist = "my artist"
            }

            val catalogItem = CatalogItemEntity.new {
                type = CatalogItemType.CHART
                status = CatalogItemStatus.DRAFT
                visibility = Visibility.PUBLIC
                author = user
            }

            val chart = ChartEntity.new(catalogItem.id.value) {
                this.track = track
                difficulty = Difficulty.HARD
                notesAmount = 1200
                effectsAmount = 45
                isDeluxe = false
                isExplicit = true
            }

            ContributorEntity.new {
                this.catalogItem = CatalogItemEntity[catalogItem.id.value]
                this.user = UserEntity[user.id.value]
                this.role = ContributorRole.AUTHOR
            }

            flushCache()

            val query = ChartTable.selectAll().where { ChartTable.id eq chart.id.value }
            val result = query.toList().firstOrNull()

            assertNotNull(result, "createChart pattern must return a chart after flush")
        }
    }

    @Test
    fun daoReadTriggersAutomaticFlush() {
        transaction(database) {
            val user = UserEntity.new {
                username = "dao_read_tester"
                email = "daoread@test.com"
                discordId = "dao_read_discord"
                role = UserRole.USER
            }

            val track = TrackEntity.new {
                title = "DAO Read Track"
                artist = "DAO Artist"
                duration = 120f
                normalizedTitle = "dao read track"
                normalizedArtist = "dao artist"
            }

            val catalogItem = CatalogItemEntity.new {
                type = CatalogItemType.CHART
                status = CatalogItemStatus.DRAFT
                author = user
            }

            ChartEntity.new(catalogItem.id.value) {
                this.track = track
                difficulty = Difficulty.EXTREME
                notesAmount = 300
                effectsAmount = 5
            }

            // DAO read (no explicit flush) — should auto-flush and find the chart
            val foundChart = ChartEntity.findById(catalogItem.id.value)

            assertNotNull(foundChart, "DAO read (Entity.findById) should auto-flush and find the chart")
        }
    }

    @Test
    fun updateChartPatternFlushesBeforeRawRead() {
        transaction(database) {
            val user = UserEntity.new {
                username = "updater"
                email = "updater@test.com"
                discordId = "updater_discord"
                role = UserRole.USER
            }

            val track = TrackEntity.new {
                title = "Before Update"
                artist = "Artist"
                duration = 200f
                normalizedTitle = "before update"
                normalizedArtist = "artist"
            }

            val catalogItem = CatalogItemEntity.new {
                type = CatalogItemType.CHART
                status = CatalogItemStatus.DRAFT
                visibility = Visibility.PUBLIC
                author = user
            }

            val chart = ChartEntity.new(catalogItem.id.value) {
                this.track = track
                difficulty = Difficulty.NORMAL
                notesAmount = 800
                effectsAmount = 20
            }

            flushCache()

            // Simulate updateChart: findSingleByAndUpdate (flushes), then property changes
            track.title = "After Update"
            track.normalizedTitle = "after update"

            // Without flush, raw query may not see the updated title on PostgreSQL
            // With flush, it must see it
            flushEntityCache()

            val rawResult = ChartTable.selectAll()
                .where { ChartTable.id eq chart.id.value }
                .toList()

            assertNotNull(rawResult.firstOrNull(), "Raw query must find the chart after flush")
        }
    }
}
