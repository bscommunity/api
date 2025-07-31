package org.bscm.models.tables

import org.jetbrains.exposed.dao.id.UUIDTable
import org.jetbrains.exposed.sql.javatime.date

object AccountTable : UUIDTable("accounts") {
    val provider = varchar("provider", 50)
    // val providerAccountId = varchar("provider_account_id", 255).uniqueIndex()
    val refreshToken = varchar("refresh_token", 255).nullable()
    val accessToken = varchar("access_token", 255).nullable()
    val expiresAt = date("expires_at").nullable()
    val tokenType = varchar("token_type", 50).nullable()
    val scope = varchar("scope", 255).nullable()

    val userId = reference("user_id", UserTable).index()

    init {
        uniqueIndex(provider, userId)
    }
}