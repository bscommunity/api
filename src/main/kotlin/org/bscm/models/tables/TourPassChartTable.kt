package org.bscm.models.tables

import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.core.Table

object TourPassChartTable : Table("tour_pass_charts") {
    val tourPassId = reference("tour_pass_id", TourPassTable, onDelete = ReferenceOption.CASCADE)
    val chartId = reference("chart_id", ChartTable, onDelete = ReferenceOption.CASCADE)
    val position = integer("position").default(0)

    override val primaryKey = PrimaryKey(tourPassId, chartId)

    init {
        index(false, tourPassId, position)
    }
}
