package io.minchat.common.entity

import kotlinx.serialization.*
import java.io.File

@Serializable
data class User(
	val id: Long,
	/** The real name of the user. Used for login. */
	val username: String,
	/** The display name of the user. Can be null if the user didn't have their name changed. */
	val nickname: String?,
	/** A unique discriminator used to distinguish users with the same display names. */
	val discriminator: Int,
	val avatar: Avatar? = null,

	val role: RoleBitSet = RoleBitSet.REGULAR_USER,

	/** If this user is muted, this property indicates the duration and reason. */
	val mute: Punishment? = null,
	/** If this user is banned, this property indicates the duration and reason. */
	val ban: Punishment? = null,

	var messageCount: Int,
	val lastMessageTimestamp: Long,

	val creationTimestamp: Long
){
	/** Returns [nickname] if the user has one, or [username] otherwise. */
	val displayName get() = nickname ?: username

	/** username#discriminator */
	val tag get() = run {
		val disc = discriminator.toString().padStart(4, '0')
		"$username#$disc"
	}

	/** displayName#discriminator */
	val displayTag get() = run {
		val disc = discriminator.toString().padStart(4, '0')
		"$displayName#$disc"
	}

	fun canViewChannel(channel: Channel) =
		channel.canBeSeenBy(this)

	fun canMessageChannel(channel: Channel) =
		channel.canBeMessagedBy(this)

	fun canEditUser(other: User) =
		(role.isAdmin && !other.role.isAdmin) || id == other.id

	fun canDeleteUser(other: User) =
		(role.isAdmin && other.role < role) || id == other.id

	fun canModifyUserPunishments(other: User) =
		role.isModerator && other.role < role

	fun canEditMessage(message: Message) =
		message.canBeEditedBy(this)

	fun canDeleteMessage(message: Message) =
		message.canBeDeletedBy(this)

	/** Creates a short string containing data useful for logging. */
	fun loggable() =
		"User($id, @$tag)"

	companion object {
		/** The minimum amount of milliseconds a normal user has to wait before sending another message. */
		val messageRateLimit = 2500L
		val nameLength = 3..64
		val passwordLength = 8..40

		/** A placeholder object returned instead of a deleted user. */
		val deletedUser = User(
			-1,
			"<this user was deleted>",
			"<deleted_user>",
			0,
			role = RoleBitSet.REGULAR_USER,
			messageCount = 0,
			lastMessageTimestamp = 0L,
			creationTimestamp = 0L
		)

		/** Checks if the provided user is a placeholder. */
		fun isDeletedUser(user: User) = user.id == deletedUser.id
	}

	@Serializable
	@JvmInline
	value class RoleBitSet(val bits: Long) : Comparable<RoleBitSet> {
		val value get() = bits

		val isAdmin get() = get(Masks.admin)
		/** True for both admins and moderators. To check just for mod permissions, use `get(Masks.moderator)`. */
		val isModerator get() = get(Masks.moderator) || isAdmin

		val readableName get() = when {
			isAdmin -> "Admin"
			isModerator -> "Moderator"
			else -> "Regular user"
		}

		/** Gets a value from the bit set. */
		@Suppress("NOTHING_TO_INLINE")
		inline fun get(bitMask: Long): Boolean
			= bits and bitMask != 0L

		/** Returns a copy of this bit set with the given bit changed. */
		fun with(bitMask: Long, value: Boolean): RoleBitSet {
			return RoleBitSet(if (value) {
				bits or bitMask
			} else {
				bits and bitMask.inv()
			})
		}

		override fun compareTo(other: RoleBitSet) = when {
			isAdmin != other.isAdmin -> isAdmin.compareTo(other.isAdmin)
			isModerator != other.isModerator -> isModerator.compareTo(other.isModerator)
			else -> 0
		}

		object Masks {
			const val admin = 0b1L
			const val moderator = 0x2L
		}

		companion object {
			val REGULAR_USER = RoleBitSet(0)
			val MODERATOR = RoleBitSet(Masks.moderator)
			val ADMIN = RoleBitSet(Masks.admin)
			val ALL = RoleBitSet(0x7fffffffffffffff)
		}
	}

	/**
	 * Represents an abstract punishment.
	 *
	 * May have an expiration date or be indefinite, and may have a reason.
	 *
	 * @param expiresAt an epoch timestamp showing when this punishment expires.
	 *      If null, the punishment is indefinite.
	 * @param reason optional reason for the punishment.
	 */
	@Serializable
	data class Punishment(
		val expiresAt: Long?,
		val reason: String?
	) {
		val isExpired get() = expiresAt == null || System.currentTimeMillis() >= expiresAt

		companion object {
			val reasonLength = 0..128
		}
	}

	/**
	 * An avatar of a user.
	 * Can be an in-game icon or a custom image.
	 */
	@Serializable
	sealed class Avatar {
		abstract val type: Type

		abstract fun isValid(): Boolean

		/** An avatar that corresponds to a mindustry icon, coming either from the game or from the mod. */
		@Serializable
		@SerialName("icon")
		data class IconAvatar(
			val iconName: String
		) : Avatar() {
			override val type get() = Type.ICON

			override fun isValid(): Boolean {
				return iconName != invalid
			}
		}

		/**
		 * An avatar that corresponds to an image hosted on MinChat. Currently not supported.
		 *
		 * @param hash A unique hash of this avatar. If the hash changes on the server, the avatar is no longer up-to-date.
		 */
		@Serializable
		@SerialName("image")
		data class ImageAvatar(
			val hash: String,
			val width: Int,
			val height: Int
		) : Avatar() {
			override val type get() = Type.IMAGE

			override fun isValid(): Boolean {
				return hash != invalid
			}
		}

		/**
		 * A local image avatar that has not yet been uploaded to the MinChat server.
		 *
		 * Must never be returned by a server. Not serializable for the same reason.
		 */
		data class LocalAvatar(
			val file: File
		) : Avatar() {
			override val type get() = Type.IMAGE

			override fun isValid(): Boolean {
				return file.exists()
			}
		}

		enum class Type {
			ICON, IMAGE
		}

		companion object {
			/** Maximum size during upload, 4 megabytes. */
			val maxUploadSize = 4 * 1024 * 1024
			/** Maximum server-side image width all bigger images will be downscaled to. */
			val maxWidth = 128
			/** Maximum server-side image height all bigger images will be downscaled to. */
			val maxHeight = 128
			/** Supported image file formats. */
			val supportedFormats = setOf("png", "jpg", "jpeg")
			/** The value of [ImageAvatar.hash] and [IconAvatar.iconName] used for invalid avatars. */
			val invalid = "<INVALID>"

			val defaultAvatar = IconAvatar("unit-alpha-full")
		}
	}
}
