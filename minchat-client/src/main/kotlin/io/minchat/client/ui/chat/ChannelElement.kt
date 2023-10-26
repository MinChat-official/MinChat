package io.minchat.client.ui.chat

import arc.Core
import arc.graphics.Color
import arc.input.KeyCode
import arc.scene.event.*
import arc.scene.ui.*
import arc.util.Align
import com.github.mnemotechnician.mkui.extensions.dsl.addLabel
import com.github.mnemotechnician.mkui.extensions.elements.content
import io.minchat.client.*
import io.minchat.client.ui.dialog.ChannelDialog
import io.minchat.client.ui.managers.hasUnreads
import io.minchat.rest.entity.MinchatChannel
import kotlinx.coroutines.CoroutineScope
import mindustry.Vars
import io.minchat.client.ui.MinchatStyle as Style

class ChannelElement(
	val chat: ChatFragment,
	val channel: MinchatChannel
) : TextButton("#${channel.name}", Style.ChannelButton), CoroutineScope by chat {
	val unreadsLabel: Label

	init {
		margin(Style.buttonMargin)

		left()
		label.setAlignment(Align.left)

		addLabel("")
			.color(Color.white)
			.padRight(2f)
			.width(10f)
			.also { unreadsLabel = it.get() }
		cells.reverse() // to put the new label to the beginning.

		clicked {
			chat.apply {
				currentChannel = channel
				ClientEvents.fireAsync(ChannelChangeEvent(channel))
				updateChatUi()
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

	override fun act(delta: Float) {
		super.act(delta)

		// On any platform, show the channel dialog on right click.
		if (hasMouse() && Core.input.keyTap(KeyCode.mouseRight)) {
			showDialog()
		}

		// If the channel has unread messages in it, change the unread and name labels respectively.
		if (channel.hasUnreads()) {
			unreadsLabel.content = ">"
			label.setColor(Color.white)
		} else {
			unreadsLabel.content = ""
			label.setColor(Color.lightGray)
		}
	}

	fun showDialog() {
		ChannelDialog(channel).show()
	}
}
