package io.minchat.common.entity

import kotlinx.serialization.Serializable

@Serializable
data class Message(
	val id: Long,

	val content: String,
	val author: User,
	val channel: Channel,

	val timestamp: Long,
	val editTimestamp: Long?,

	val referencedMessageId: Long?
) {
	fun canBeEditedBy(user: User) = user.id == author.id

	fun canBeDeletedBy(user: User) = (user.id == author.id) || when (channel.type) {
		Channel.Type.NORMAL -> user.role.isAdmin
		Channel.Type.DM -> false
	}

	/** Creates a short string containing data useful for logging. */
	fun loggable() =
		"Message($id, #${channel.name}, @${author.tag}, ${content.length} chars)"

	companion object {
		val contentLength = 1..1024
	}

	/** A file attached to a message. Not yet implemented. */
	@Serializable
	sealed class Attachment(val url: String, val location: Location) {
		class Image(
			val width: Int,
			val height: Int,
			url: String,
			location: Location
		) : Attachment(url, location)

//		class Video(
//			val width: Int,
//			val height: Int,
//			val lengthMs: Long,
//			url: String,
//			location: Location
//		) : Attachment(url, location)

		class Schematic(
			val width: Int,
			val height: Int,
			val name: String,
			val description: String,
			url: String,
			location: Location
		) : Attachment(url, location)

		class Map(
			val width: Int,
			val height: Int,
			val name: String,
			val description: String,
			val author: String,
			url: String,
			location: Location
		) : Attachment(url, location)

		enum class Location {
			/** A file uploaded to MinChat. */
			MINCHAT,
			/** A file on a third-party server. */
			EXTERNAL,
			/** A local file, cannot be transferred to a different client. */
			LOCAL
		}
	}
}
