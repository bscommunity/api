package org.bscm.models.tables

import org.jetbrains.exposed.dao.id.CompositeIdTable
import org.jetbrains.exposed.sql.ReferenceOption

object TourPassChartTable : CompositeIdTable("tour_pass_charts") {
    val tourPassId = reference("tour_pass_id", TourPassTable, onDelete = ReferenceOption.CASCADE)
    val chartId = reference("chart_id", ChartTable, onDelete = ReferenceOption.CASCADE)

    init {
        addIdColumn(tourPassId)
        addIdColumn(chartId)
    }

    override val primaryKey = PrimaryKey(tourPassId, chartId)
}
