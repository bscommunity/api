
package org.bscm.models.tables

import org.jetbrains.exposed.dao.id.IntIdTable
import org.jetbrains.exposed.sql.ReferenceOption
import org.jetbrains.exposed.sql.javatime.datetime
import org.jetbrains.exposed.sql.or

object CollectionItemTable : IntIdTable("collection_items") {
    val collectionId = reference("collection_id", CollectionTable, onDelete = ReferenceOption.CASCADE)

    val chartId = reference("chart_id", ChartTable, onDelete = ReferenceOption.CASCADE).nullable()
    val tourPassId = reference("tour_pass_id", TourPassTable, onDelete = ReferenceOption.CASCADE).nullable()
    val themeId = reference("theme_id", ThemeTable, onDelete = ReferenceOption.CASCADE).nullable()

    val addedAt = datetime("added_at")

    init {
        // Pelo menos um destino não nulo
        check("ck_collection_items_at_least_one_target") {
            (chartId.isNotNull()) or (tourPassId.isNotNull()) or (themeId.isNotNull())
        }

        // Índices úteis
        index(false, collectionId)
        index(false, chartId)
        index(false, tourPassId)
        index(false, themeId)

        // Evitar duplicatas na mesma coleção
        uniqueIndex(collectionId, chartId)
        uniqueIndex(collectionId, tourPassId)
        uniqueIndex(collectionId, themeId)
    }
}
