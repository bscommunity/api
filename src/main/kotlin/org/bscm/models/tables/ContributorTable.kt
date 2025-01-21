package org.bscm.models.tables

import org.bscm.models.enums.ContributorRole
import org.jetbrains.exposed.dao.id.CompositeIdTable
import org.jetbrains.exposed.sql.ReferenceOption
import org.jetbrains.exposed.sql.javatime.date

object ContributorTable : CompositeIdTable("contributor") {
    val userId = reference(
        "user_id",
        UserTable,
        onDelete = ReferenceOption.CASCADE
    ).entityId()
    val chartId = reference(
        "chart_id",
        ChartTable,
        onDelete = ReferenceOption.CASCADE
    ).entityId()
    val role = enumerationByName("role", 50, ContributorRole::class)
    val joinedAt = date("joined_at")
}