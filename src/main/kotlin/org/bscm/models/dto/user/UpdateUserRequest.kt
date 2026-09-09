package org.bscm.models.dto.user

import kotlinx.serialization.Serializable
import org.bscm.models.enums.ContributorInvitePolicy

@Serializable
data class UpdateUserRequest(
    val username: String? = null,
    val email: String? = null,
    val bio: String? = null,
    val isPublic: Boolean? = null,
    val discordId: String? = null,
    val avatarUrl: String? = null,
    val bannerUrl: String? = null,
    val accentColor: Int? = null,
    val allowContributorInvitesFrom: ContributorInvitePolicy? = null,
)