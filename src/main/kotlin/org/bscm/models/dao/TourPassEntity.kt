package org.bscm.models.dao

import org.bscm.models.tables.TourPassChartTable
import org.bscm.models.tables.TourPassTable
import org.jetbrains.exposed.dao.Entity
import org.jetbrains.exposed.dao.EntityClass
import org.jetbrains.exposed.dao.id.EntityID

class TourPassEntity(id: EntityID<String>) : Entity<String>(id) {
    companion object : EntityClass<String, TourPassEntity>(TourPassTable)

    var name by TourPassTable.name
    var description by TourPassTable.description
    var artist by TourPassTable.artist
    var coverId by TourPassTable.coverId

    val charts by ChartEntity via TourPassChartTable
}
