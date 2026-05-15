package org.bscm.models.mappers

import org.bscm.models.ChartVersion
import org.bscm.models.dao.VersionEntity
import org.bscm.models.tables.VersionTable
import org.jetbrains.exposed.sql.ResultRow

object VersionMapper {
    /**
     * Maps a [org.jetbrains.exposed.sql.ResultRow] to a [org.bscm.models.ChartVersion].
     *
     * [index] must always be provided by the caller — never computed here.
     * This keeps mapping pure and free of extra queries.
     */
    fun rowToVersion(row: ResultRow, index: Int): ChartVersion = ChartVersion(
        id = row[VersionTable.id].value.toString(),
        index = index,
        chartId = row[VersionTable.chartId].value.toString(),
        bundleUrl = row[VersionTable.bundleUrl],
        previewUrl = row[VersionTable.previewUrl],
        duration = row[VersionTable.duration],
        notesAmount = row[VersionTable.notesAmount],
        effectsAmount = row[VersionTable.effectsAmount],
        bpm = row[VersionTable.bpm],
        difficulty = row[VersionTable.difficulty],
        isDeluxe = row[VersionTable.isDeluxe],
        isExplicit = row[VersionTable.isExplicit],
        downloadsAmount = row[VersionTable.downloadsAmount],
        changelog = row[VersionTable.changelog],
        createdAt = row[VersionTable.createdAt],
    )

    fun entityToVersion(entity: VersionEntity, index: Int): ChartVersion = ChartVersion(
        id = entity.id.value.toString(),
        index = index,
        chartId = entity.chartId.value.toString(),
        bundleUrl = entity.bundleUrl,
        previewUrl = entity.previewUrl,
        duration = entity.duration,
        notesAmount = entity.notesAmount,
        effectsAmount = entity.effectsAmount,
        bpm = entity.bpm,
        difficulty = entity.difficulty,
        isDeluxe = entity.isDeluxe,
        isExplicit = entity.isExplicit,
        downloadsAmount = entity.downloadsAmount,
        changelog = entity.changelog,
        createdAt = entity.createdAt
    )
}