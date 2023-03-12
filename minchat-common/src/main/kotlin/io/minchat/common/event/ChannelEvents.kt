package io.minchat.common.event

import io.minchat.common.entity.*
import kotlinx.serialization.*

@Serializable
@SerialName("ChannelCreate")
data class ChannelCreateEvent(
	val channel: Channel
) : Event()

@Serializable
@SerialName("ChannelModify")
data class ChannelModifyEvent(
	val channel: Channel
) : Event()

@Serializable
@SerialName("ChannelDelete")
data class ChannelDeleteEvent(
	val channelId: Long
) : Event()
