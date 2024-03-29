package io.minchat.common.event

import io.minchat.common.entity.User
import kotlinx.serialization.*

/**
 * A user has been modified or deleted.
 *
 * In the latter case, a placeholder usee user object is providen.
 */
@Serializable
@SerialName("UserModify")
data class UserModifyEvent(
	val newUser: User
) : Event() {
	override fun toString() =
		"UserModifyEvent(${newUser.loggable()})"
}
