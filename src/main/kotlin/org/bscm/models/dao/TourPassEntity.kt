package org.bscm.models.dao

import org.bscm.models.tables.TourPassChartTable
import org.bscm.models.tables.TourPassTable
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.dao.Entity
import org.jetbrains.exposed.v1.dao.EntityClass

class TourPassEntity(id: EntityID<String>) : Entity<String>(id) {
    companion object : EntityClass<String, TourPassEntity>(TourPassTable)

    var name by TourPassTable.name
    var description by TourPassTable.description
    var artist by TourPassTable.artist

    val charts by ChartEntity via TourPassChartTable
}
