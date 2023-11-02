package io.minchat.common.event

import io.minchat.common.entity.Channel
import kotlinx.serialization.*

@Serializable
@SerialName("ChannelCreate")
data class ChannelCreateEvent(
	val channel: Channel
) : Event() {
	override fun toString() =
		"ChannelCreateEvent(${channel.loggable()})"
}

@Serializable
@SerialName("ChannelModify")
data class ChannelModifyEvent(
	val channel: Channel
) : Event() {
	override fun toString() =
		"ChannelModifyEvent(${channel.loggable()})"
}

@Serializable
@SerialName("ChannelDelete")
data class ChannelDeleteEvent(
	val channelId: Long
) : Event()
