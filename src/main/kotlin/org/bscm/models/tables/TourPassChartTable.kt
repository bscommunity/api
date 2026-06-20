package org.bscm.models.tables

import org.jetbrains.exposed.dao.id.CompositeIdTable
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.sql.Column

object TourPassChartTable : CompositeIdTable("tour_pass_charts") {
    val tourPassId: Column<EntityID<String>> = varchar("tour_pass_id", 255).entityId()
    val chartId: Column<EntityID<String>> = varchar("chart_id", 255).entityId()
    val position = integer("position").default(0)

    init {
        addIdColumn(tourPassId)
        addIdColumn(chartId)
    }

    override val primaryKey = PrimaryKey(tourPassId, chartId)

    init {
        index(false, tourPassId, position)
    }
}
