package org.bscm.models.tables

import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import org.bscm.models.enums.ContributorRole
import org.jetbrains.exposed.dao.id.CompositeIdTable
import org.jetbrains.exposed.sql.ReferenceOption
import org.jetbrains.exposed.sql.javatime.CurrentDateTime
import org.jetbrains.exposed.sql.javatime.datetime
import org.jetbrains.exposed.sql.json.jsonb

object ContributorTable : CompositeIdTable("contributors") {
    val userId = reference("user_id", UserTable, onDelete = ReferenceOption.CASCADE)
    val chartId = reference("chart_id", ChartTable, onDelete = ReferenceOption.CASCADE)

    val roles = jsonb(
        "roles",
        Json { ignoreUnknownKeys = true },
        ListSerializer(ContributorRole.serializer())
    ).default(emptyList())
    val joinedAt = datetime("joined_at").defaultExpression(CurrentDateTime)

    init {
        addIdColumn(userId)
        addIdColumn(chartId)

        index(false, userId, chartId)
    }

    override val primaryKey = PrimaryKey(userId, chartId)
}