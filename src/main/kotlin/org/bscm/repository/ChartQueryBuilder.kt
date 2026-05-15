package org.bscm.repository

import org.bscm.models.enums.SortOption
import org.bscm.models.tables.*
import org.bscm.utils.QueryUtils
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.like

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
            innerJoin(CatalogItemTable, { ChartTable.catalogItemId }, { CatalogItemTable.id })
                .innerJoin(TrackTable, { ChartTable.trackId }, { TrackTable.id })
        }

        query.adjustColumnSet {
            leftJoin(ContributorTable, { ChartTable.id }, { ContributorTable.chartId })
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
            query.andWhere { CatalogItemTable.isPublic eq true }
        }

        filters?.userId?.let { userId ->
            query.andWhere {
                ChartTable.id inSubQuery (
                    ContributorTable.select(ContributorTable.chartId)
                        .where { ContributorTable.userId eq userId }
                )
            }
        }

        filters?.chartIds?.takeIf { it.isNotEmpty() }?.let { ids ->
            query.andWhere { ChartTable.id inList ids }
        }

        filters?.contentIds?.takeIf { it.isNotEmpty() }?.let { ids ->
            query.andWhere { ChartTable.catalogItemId inList ids.map { EntityID(it, CatalogItemTable) } }
        }

        if (!filters?.search.isNullOrBlank()) {
            applySearch(query, filters.search)
        }

        filters?.difficulties?.takeIf { it.isNotEmpty() }?.let { diffs ->
            query.andWhere { ChartTable.difficulty inList diffs }
        }

        filters?.genres?.takeIf { it.isNotEmpty() }?.let { genres ->
            query.andWhere { TrackTable.genre inList genres }
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
