package org.bscm.models

import java.util.*

data class UserContext(
    val userId: UUID?,
    val isAuthenticated: Boolean = userId != null
)
