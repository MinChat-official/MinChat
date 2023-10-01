package io.minchat.rest.entity

import io.minchat.common.entity.*
import io.minchat.rest.MinchatRestClient

data class MinchatUser(
	val data: User,
	override val rest: MinchatRestClient
) : MinchatEntity<MinchatUser>() {
	override val id by data::id

	/** The real name of the user. Used for login. */
	val username by data::username
	/** The display name of the user. Can be null if the user didn't have their name changed. */
	val nickname by data::nickname
	/** Returns [nickname] if the user has one, or [username] otherwise. */
	val displayName by data::displayName

	/** A unique discriminator used to distinguish users with the same display names. */
	val discriminator by data::discriminator
 	/** A display name of this user in the form of displayName#discriminator. */
	val tag by data::tag

	val isAdmin by data::isAdmin
	/** If this user is muted, this property indicates the duration and reason. */
	val mute by data::mute
	/** If this user is banned, this property indicates the duration and reason. */
	val ban by data::ban

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
	suspend fun edit(newNickname: String? = null) =
		rest.editUser(id, newNickname)

	/** Deletes this user. Unless this user is same as [rest.account.user], requires admin rights. */
	suspend fun delete() =
		rest.deleteUser(id)

	override fun toString() =
		"MinchatUser(id=$id, tag=$tag, isAdmin=$isAdmin, ban=$ban, mute=$mute, messageCount=$messageCount)"

	override fun equals(other: Any?): Boolean {
		if (this === other) return true
		if (javaClass != other?.javaClass) return false
		return data == (other as MinchatUser).data
	}

	/** Returns true if the two users are indistinguishable in terms of visual info. */
	fun similar(other: MinchatUser): Boolean {
		if (id != other.id) return false
		if (username != other.username) return false
		if (discriminator != other.discriminator) return false
		if (nickname != other.nickname) return false
		if (isAdmin != other.isAdmin) return false
		if (mute != other.mute) return false
		if (ban != other.ban) return false
		return true
	}

	override fun hashCode(): Int = data.hashCode()

	/**
	 * Copies this [MinchatUser] object, allowing to override some of its data values.
	 */
	fun copy(
		username: String = data.username,
		nickname: String? = data.nickname,
		discriminator: Int = data.discriminator,
		role: User.RoleBitSet = data.role,
		mute: User.Punishment? = data.mute,
		ban: User.Punishment? = data.ban,
		messageCount: Int = data.messageCount,
		lastMessageTimestamp: Long = data.lastMessageTimestamp,
		creationTimestamp: Long = data.creationTimestamp
	) = MinchatUser(data.copy(
		username = username,
		nickname = nickname,
		discriminator = discriminator,
		role = role,
		mute = mute,
		ban = ban,
		messageCount = messageCount,
		lastMessageTimestamp = lastMessageTimestamp,
		creationTimestamp = creationTimestamp
	), rest)
}

fun User.withClient(rest: MinchatRestClient) =
	MinchatUser(this, rest)
