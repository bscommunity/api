package org.bscm.models.dao

import org.bscm.models.tables.InteractionTable
import org.jetbrains.exposed.dao.Entity
import org.jetbrains.exposed.dao.EntityClass
import org.jetbrains.exposed.dao.id.EntityID

class InteractionEntity(id: EntityID<Int>) : Entity<Int>(id) {
    companion object : EntityClass<Int, InteractionEntity>(InteractionTable)

    var user by UserEntity referencedOn InteractionTable.userId

    var chart by ChartEntity optionalReferencedOn InteractionTable.chartId
    var tourPass by TourPassEntity optionalReferencedOn InteractionTable.tourPassId
    var theme by ThemeEntity optionalReferencedOn InteractionTable.themeId

    var likedAt by InteractionTable.likedAt
    var favoritedAt by InteractionTable.favoritedAt
}