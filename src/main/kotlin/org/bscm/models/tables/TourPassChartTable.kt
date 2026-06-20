package org.bscm.models.tables

import org.jetbrains.exposed.dao.id.CompositeIdTable
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.sql.Column
import org.jetbrains.exposed.sql.ForeignKeyConstraint
import org.jetbrains.exposed.sql.ReferenceOption

object TourPassChartTable : CompositeIdTable("tour_pass_charts") {
    val tourPassId: Column<EntityID<String>> = varchar("tour_pass_id", 255).entityId()
    val chartId: Column<EntityID<String>> = varchar("chart_id", 255).entityId()
    val position = integer("position").default(0)

    init {
        // Set FK metadata for Exposed JOIN resolution (referee) without adding to
        // table.foreignKeys — DDL is handled separately in Database.kt
        tourPassId.foreignKey = ForeignKeyConstraint(tourPassId, TourPassTable.id, ReferenceOption.CASCADE, null, "fk_tpc_tour_pass")
        chartId.foreignKey = ForeignKeyConstraint(chartId, ChartTable.id, ReferenceOption.CASCADE, null, "fk_tpc_chart")

        addIdColumn(tourPassId)
        addIdColumn(chartId)
    }

    override val primaryKey = PrimaryKey(tourPassId, chartId)

    init {
        index(false, tourPassId, position)
    }
}
