package org.bscm.models.dao

import org.bscm.models.tables.AccountTable
import org.bscm.models.tables.UserBadgeTable
import org.bscm.models.tables.UserFollowTable
import org.bscm.models.tables.UserTable
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.dao.java.UUIDEntity
import org.jetbrains.exposed.v1.dao.java.UUIDEntityClass
import java.util.*

// "Entity" is equivalent to DAO (Data Access Object) in Exposed
class UserEntity(id: EntityID<UUID>) : UUIDEntity(id) {
    companion object : UUIDEntityClass<UserEntity>(UserTable)

    var username by UserTable.username
    var email by UserTable.email
    var avatarUrl by UserTable.avatarUrl
    var bannerUrl by UserTable.bannerUrl
    var accentColor by UserTable.accentColor
    var bio by UserTable.bio
    var isPublic by UserTable.isPublic

    var role by UserTable.role
    var isVerified by UserTable.isVerified
    var verifiedAt by UserTable.verifiedAt

    var discordId by UserTable.discordId
    val createdAt by UserTable.createdAt

    val badges by BadgeEntity.via(
        UserBadgeTable.userId,
        UserBadgeTable.badgeId
    )

    val accounts by AccountEntity referrersOn AccountTable.userId

    // Users this user follows
    val following by UserEntity.via(
        UserFollowTable.follower,
        UserFollowTable.followed
    )

    // Users that follow this user
    val followers by UserEntity.via(
        UserFollowTable.followed,
        UserFollowTable.follower
    )

    val followerCount by UserTable.followerCount
    val followingCount by UserTable.followingCount

    var allowContributorInvitesFrom by UserTable.allowContributorInvitesFrom
}