package io.minchat.common.event

import io.minchat.common.entity.ChannelGroup
import kotlinx.serialization.*

@Serializable
@SerialName("ChannelGroupCreate")
data class ChannelGroupCreateEvent(
	val group: ChannelGroup
) : Event() {
	override fun toString() =
		"ChannelGroupCreateEvent(${group.loggable()})"
}

@Serializable
@SerialName("ChannelGroupModify")
data class ChannelGroupModifyEvent(
	val group: ChannelGroup
) : Event() {
	override fun toString() =
		"ChannelGroupModifyEvent(${group.loggable()})"
}

@Serializable
@SerialName("ChannelGroupDelete")
data class ChannelGroupDeleteEvent(
	val groupId: Long
) : Event()

