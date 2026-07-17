package org.bscm.repository

import org.bscm.models.enums.SortOption
import org.bscm.models.enums.Visibility
import org.bscm.models.tables.*
import org.bscm.utils.QueryUtils
import org.jetbrains.exposed.v1.core.*
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.jdbc.Query
import org.jetbrains.exposed.v1.jdbc.andWhere
import org.jetbrains.exposed.v1.jdbc.select

class ChartQueryBuilder {
    fun applyJoinsAndSelect(
        query: Query,
        fetchStreamingRefs: Boolean,
    ) {
        val columnsToSelect = mutableListOf<Column<*>>()
        columnsToSelect.addAll(ChartTable.columns)
        columnsToSelect.addAll(CatalogItemTable.columns)
        columnsToSelect.addAll(TrackTable.columns)

        query.adjustColumnSet {
            innerJoin(CatalogItemTable, { ChartTable.id }, { CatalogItemTable.id })
                .innerJoin(TrackTable, { ChartTable.trackId }, { TrackTable.id })
        }

        query.adjustColumnSet {
            leftJoin(ContributorTable, { ChartTable.id }, { ContributorTable.catalogItemId })
                .leftJoin(UserTable, { ContributorTable.userId }, { UserTable.id })
        }
        columnsToSelect.addAll(ContributorTable.columns)
        columnsToSelect.addAll(UserTable.columns)

        if (fetchStreamingRefs) {
            query.adjustColumnSet {
                leftJoin(TrackStreamingRefTable, { TrackTable.id }, { TrackStreamingRefTable.trackId })
            }
            columnsToSelect.addAll(TrackStreamingRefTable.columns)
        }

        query.adjustColumnSet {
            leftJoin(VersionTable, { CatalogItemTable.latestVersionId }, { VersionTable.id })
        }
        columnsToSelect.addAll(VersionTable.columns)

        query.adjustSelect { select(columnsToSelect) }
    }

    fun applyFilters(query: Query, filters: ChartRepository.ChartFilters?) {
        if (filters?.userId == null && filters?.includePrivate != true) {
            query.andWhere { CatalogItemTable.visibility eq Visibility.PUBLIC }
        }

        filters?.userId?.let { userId ->
            val contributorChartIds = ContributorTable
                .select(ContributorTable.catalogItemId)
                .where { ContributorTable.userId eq userId }
                .map { it[ContributorTable.catalogItemId] }
            query.andWhere {
                ChartTable.id inList contributorChartIds
            }
        }

        filters?.chartIds?.takeIf { it.isNotEmpty() }?.let { ids ->
            query.andWhere { ChartTable.id inList ids.map { EntityID(it, ChartTable) } }
        }

        if (!filters?.search.isNullOrBlank()) {
            applySearch(query, filters.search)
        }

        filters?.difficulties?.takeIf { it.isNotEmpty() }?.let { diffs ->
            query.andWhere { ChartTable.difficulty inList diffs }
        }

        filters?.genres?.takeIf { it.isNotEmpty() }?.let { genres ->
            query.andWhere {
                genres.map { genre ->
                    stringParam(genre.name) eq anyFrom(TrackTable.genres)
                }.fold(Op.FALSE as Op<Boolean>) { acc, next -> acc.or(next) }
            }
        }

        filters?.isDeluxe?.let { deluxe ->
            query.andWhere { ChartTable.isDeluxe eq deluxe }
        }
    }

    fun applySearch(query: Query, search: String) {
        val normalizedSearchTerm = QueryUtils.getNormalizedQuery(search)
        val searchTerms = normalizedSearchTerm.split(" ").filter { it.isNotBlank() }

        val whereConditions = mutableListOf<Op<Boolean>>()

        val exactPhraseMatchCondition = (TrackTable.normalizedArtist like "%$normalizedSearchTerm%") or
            (TrackTable.normalizedTitle like "%$normalizedSearchTerm%") or
            (TrackTable.normalizedAlbum like "%$normalizedSearchTerm%")
        whereConditions.add(exactPhraseMatchCondition)

        if (searchTerms.isNotEmpty()) {
            val allTermsPresentCondition = searchTerms.map { term ->
                (TrackTable.normalizedArtist like "%$term%") or
                    (TrackTable.normalizedTitle like "%$term%") or
                    (TrackTable.normalizedAlbum like "%$term%")
            }.reduce { acc, cond -> acc and cond }
            whereConditions.add(allTermsPresentCondition)
        }

        if (whereConditions.isNotEmpty()) {
            query.andWhere {
                whereConditions.reduce { acc, cond -> acc or cond }
            }
        }
    }

    fun applySorting(query: Query, sortBy: SortOption) {
        when (sortBy) {
            SortOption.LAST_UPDATED -> query.orderBy(CatalogItemTable.updatedAt to SortOrder.DESC)
            else -> query.orderBy(CatalogItemTable.downloadsSum to SortOrder.DESC)
        }
    }
}
