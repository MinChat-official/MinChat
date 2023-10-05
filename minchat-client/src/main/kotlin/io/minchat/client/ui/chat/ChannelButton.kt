package io.minchat.client.ui.chat

import arc.Core
import arc.input.KeyCode
import arc.scene.event.*
import arc.scene.ui.TextButton
import arc.util.Align
import io.minchat.client.*
import io.minchat.client.ui.dialog.ChannelDialog
import io.minchat.rest.entity.MinchatChannel
import kotlinx.coroutines.CoroutineScope
import mindustry.Vars
import io.minchat.client.ui.MinchatStyle as Style

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
				ClientEvents.fireAsync(ChannelChangeEvent(channel))
				updateChatUi()
			}
		}

		// On any platform, open the channel menu if this element is right-clicked
		update {
			if (hasMouse() && Core.input.keyTap(KeyCode.mouseRight)) {
				showDialog()
			}
		}

		// On mobile, add a long click listener to show the channel dialog
		if (Vars.mobile || Vars.android || Vars.ios) {
			addListener(object : InputListener() {
				var touchBegin = -1L

				override fun touchDown(event: InputEvent?, x: Float, y: Float, pointer: Int, button: KeyCode?): Boolean {
					touchBegin = System.currentTimeMillis()
					return true
				}

				override fun touchUp(event: InputEvent?, x: Float, y: Float, pointer: Int, button: KeyCode?) {
					// Show the dialog if the button was pressed for 400 ms or more
					if (touchBegin > 0L && System.currentTimeMillis() - touchBegin > 400) {
						showDialog()
					}
					touchBegin = -1L
				}
			})
		}
	}

	fun showDialog() {
		ChannelDialog(channel).show()
	}
}
