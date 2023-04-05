package io.minchat.client.ui.chat

import arc.scene.ui.TextButton
import arc.util.Align
import io.minchat.rest.entity.MinchatChannel
import kotlinx.coroutines.CoroutineScope
import io.minchat.client.misc.MinchatStyle as Style

class ChannelButton(
	val chat: ChatFragment,
	val channel: MinchatChannel
) : TextButton("#${channel.name}", Style.ChannelButton), CoroutineScope by chat {
	init {
		margin(Style.buttonMargin)

		left()
		label.setAlignment(Align.left)

		clicked {
			chat.apply {
				currentChannel = channel
				updateChatUi()
			}
		}
	}
}
