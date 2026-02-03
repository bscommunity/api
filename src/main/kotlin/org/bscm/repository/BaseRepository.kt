package org.bscm.repository

import org.bscm.models.UserContext

/**
 * Base repository class providing common UserContext management functionality.
 */
abstract class BaseRepository {
    companion object {
        private val userContext = ThreadLocal<UserContext>()

        fun setUserContext(context: UserContext) {
            println("[BaseRepository] Setting UserContext: userId=${context.userId}")
            userContext.set(context)
        }

        fun getUserContext(): UserContext? {
            val context = userContext.get()
            println("[BaseRepository] Getting UserContext: userId=${context?.userId}")
            return context
        }

        fun clearUserContext() {
            val context = userContext.get()
            println("[BaseRepository] Clearing UserContext: userId=${context?.userId}")
            userContext.remove()
        }
    }
}