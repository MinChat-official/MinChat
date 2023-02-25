package io.minchat.rest

import io.minchat.common.entity.User
import kotlinx.serialization.Serializable

/**
 * A registered MinChar user whose api token is known.
 */
 class MinChatAccount(user: User, val token: String) {
 	/** 
	 * The user object associated with this account.
	 * Can not be overwritten with a different user.
	 */
	var user = user	
		set(newUser) {
			if (field.id != newUser.id) {
				error("$this: attempt to replace $field with $newUser (id mismatch)")
			}
			field = newUser
		}
	
	override fun toString() =
		"MinChatAccount(user=$user)"
 }

 fun User.withToken(token: String) =
 	MinChatAccount(this, token)
