package io.minchat.common.entity

import kotlinx.serialization.Serializable

@Serializable
data class User(
	val id: Long,
	val username: String,
	val discriminator: Int,

	val lastMessageTimestamp: Long,
	val creationTimestamp: Long
)
