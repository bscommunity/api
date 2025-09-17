package org.bscm.models.tables

import org.jetbrains.exposed.dao.id.IntIdTable
import org.jetbrains.exposed.sql.ReferenceOption
import org.jetbrains.exposed.sql.javatime.datetime
import org.jetbrains.exposed.sql.or

object InteractionTable : IntIdTable("interactions") {
    val userId = reference("user_id", UserTable, onDelete = ReferenceOption.CASCADE)

    val chartId = reference("chart_id", ChartTable, onDelete = ReferenceOption.SET_NULL).nullable()
    val tourPassId = reference("tour_pass_id", TourPassTable, onDelete = ReferenceOption.SET_NULL).nullable()
    val themeId = reference("theme_id", ThemeTable, onDelete = ReferenceOption.SET_NULL).nullable()

    val likedAt = datetime("liked_at").nullable()
    val favoritedAt = datetime("favorited_at").nullable()

    init {
        // Pelo menos um destino não nulo
        check("ck_interactions_at_least_one_target") {
            (chartId.isNotNull()) or (tourPassId.isNotNull()) or (themeId.isNotNull())
        }
        // Índices úteis
        index(false, userId)
        index(false, chartId)
        index(false, tourPassId)
        index(false, themeId)
    }
}
