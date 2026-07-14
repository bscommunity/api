package org.bscm.models.tables

import org.jetbrains.exposed.v1.core.dao.id.java.UUIDTable
import org.jetbrains.exposed.v1.javatime.datetime

object AccountTable : UUIDTable("accounts") {
    val provider = varchar("provider", 50)
    // val providerAccountId = varchar("provider_account_id", 255).uniqueIndex()
    val refreshToken = varchar("refresh_token", 255).nullable()
    val accessToken = varchar("access_token", 255).nullable()
    val expiresAt = datetime("expires_at").nullable()
    val tokenType = varchar("token_type", 50).nullable()
    val scope = varchar("scope", 255).nullable()

    val userId = reference("user_id", UserTable).index()

    init {
        uniqueIndex(provider, userId)
    }
}