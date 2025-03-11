package org.bscm.models.tables

import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import org.bscm.models.enums.ContributorRole
import org.jetbrains.exposed.dao.id.CompositeIdTable
import org.jetbrains.exposed.sql.ReferenceOption
import org.jetbrains.exposed.sql.javatime.date
import org.jetbrains.exposed.sql.json.jsonb

object ContributorTable : CompositeIdTable("contributor") {
    val userId = reference("user_id", UserTable, onDelete = ReferenceOption.CASCADE)
    val chartId = reference("chart_id", ChartTable, onDelete = ReferenceOption.CASCADE)

    val roles = jsonb(
        "roles",
        Json { ignoreUnknownKeys = true },
        ListSerializer(ContributorRole.serializer())
    ).default(emptyList())
    val joinedAt = date("joined_at")

    init {
        addIdColumn(userId)
        addIdColumn(chartId)
    }

    override val primaryKey = PrimaryKey(userId, chartId)
}