package io.minchat.rest.entity

import io.minchat.common.entity.*
import io.minchat.rest.MinchatRestClient

class MinchatUser(
	val data: User,
	override val rest: MinchatRestClient
) : MinchatEntity<MinchatUser>() {
	override val id by data::id

	val username by data::username
	val discriminator by data::discriminator
	/** Unique identifier of this user in the form of name#discriminator. */
	val tag by data::tag

	val isAdmin by data::isAdmin
	val isBanned by data::isBanned

	/** The total number of messages ever sent by this user. */
	val messageCount by data::messageCount
	/** The last moment this user has sent a message. */
	val lastMessageTimestamp by data::lastMessageTimestamp

	/** The moment this user was created. */
	val creationTimestamp by data::creationTimestamp

	override suspend fun fetch() = rest.getUser(id)

	/** 
	 * Edits this user. 
	 * Unless this user is same as [rest.account.user], requires admin rights. 
	 *
	 * This function returns a __new__ user object.
	 */
	suspend fun edit(newUsername: String? = null) =
		rest.editUser(id, newUsername)

	/** Deletes this user. Unless this user is same as [rest.account.user], requires admin rights. */
	suspend fun delete() =
		rest.deleteUser(id)

	override fun toString() =
		"MinchatUser(id=$id, tag=$tag, isAdmin=$isAdmin, isBanned=$isBanned, messageCount=$messageCount)"
}

fun User.withClient(rest: MinchatRestClient) =
	MinchatUser(this, rest)
