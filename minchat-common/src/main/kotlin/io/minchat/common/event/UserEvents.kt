package io.minchat.common.event

import io.minchat.common.entity.*
import kotlinx.serialization.*

/**
 * A user has been modified or deleted.
 *
 * In the latter case, a placeholder user object is providen.
 */
@Serializable
@SerialName("UserModify")
data class UserModifyEvent(
	val channel: Channel
) : Event()
