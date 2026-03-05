package org.bscm.models.tables

import org.bscm.models.enums.UserRole
import org.jetbrains.exposed.dao.id.UUIDTable
import org.jetbrains.exposed.sql.javatime.datetime
import java.time.LocalDateTime

object UserTable : UUIDTable("users") {
    val username = varchar("username", 255).uniqueIndex()
    val email = varchar("email", 255).uniqueIndex()
    val imageUrl = varchar("image_url", 255).nullable()
    val bannerUrl = varchar("banner_url", 255).nullable()
    val avatarUrl = varchar("avatar_url", 255).nullable()
    val accentColor = integer("accent_color").nullable() // e.g., "16711680"
    val bio = text("bio").nullable()

    val isPublic = bool("is_public").default(true)

    val role = enumerationByName("role", 20, UserRole::class)
        .default(UserRole.USER)

    val isVerified = bool("is_verified").default(false)
    val verifiedAt = datetime("verified_at").nullable()

    val verifiedBy = reference(
        "verified_by",
        UserTable
    ).nullable()

    val followerCount = integer("follower_count").default(0)
    val followingCount = integer("following_count").default(0)

    val discordId = varchar("discord_id", 255).uniqueIndex()
    val createdAt = datetime("created_at").clientDefault { LocalDateTime.now() }
}