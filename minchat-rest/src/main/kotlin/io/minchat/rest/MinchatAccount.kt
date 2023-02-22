package io.minchat.rest

import io.minchat.common.entity.User
import kotlinx.serialization.Serializable

/**
 * A registered MinChar user whose api token is known.
 */
 @Serializable
 class MinChatAccount(user: User, val token: String) {
	var user = user	
		private set
	
	override fun toString() =
		"MinChatAccount(user=$user)"
 }

 fun User.withToken(token: String) =
 	MinChatAccount(this, token)
