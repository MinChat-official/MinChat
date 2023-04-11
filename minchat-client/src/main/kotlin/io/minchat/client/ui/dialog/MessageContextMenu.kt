package io.minchat.client.ui.dialog

import arc.Core
import arc.scene.style.Drawable
import arc.scene.ui.Dialog
import arc.scene.ui.layout.Table
import arc.util.*
import com.github.mnemotechnician.mkui.extensions.dsl.*
import io.minchat.client.Minchat
import io.minchat.client.misc.MinchatStyle
import io.minchat.client.ui.chat.*
import io.minchat.rest.entity.MinchatMessage
import kotlinx.coroutines.*
import mindustry.Vars
import mindustry.gen.Icon

/**
 * A context menu dialog that allows the user to
 * perform various actions with the message.
 */
class MessageContextMenu(
	val chat: ChatFragment,
	val message: MinchatMessage,
	parentScope: CoroutineScope
) : Dialog(), CoroutineScope by parentScope {
	val messageElement = NormalMinchatMessageElement(chat, message, false)
	lateinit var actionTable: Table

	init {
		closeOnBack()

		titleTable.remove()
		buttons.remove()

		cont.apply {
			addTable(MinchatStyle.surfaceBackground) {
				margin(MinchatStyle.layoutMargin)

				add(messageElement).minWidth(400f)
			}.fill().pad(MinchatStyle.layoutPad).row()

			hsplitter(MinchatStyle.foreground)

			addTable {
				actionTable = this
			}.fill()
		}

		action(Icon.copy, "Copy text") {
			Core.app.clipboardText = messageElement.message.content
		}

		if (Minchat.client.canEditMessage(messageElement.message)) {
			action(Icon.pencil, "Edit message") {
				// TODO
				Vars.ui.showInfo("TODO: Edit message")
			}

			action(Icon.trash.tint(MinchatStyle.red), "Delete message") {
				// TODO: should there be a confirmation dialog?
				launch {
					runCatching {
						messageElement.message.delete()
					}.onFailure {
						Log.err("Failed to delete message", it)
					}
				}
				hide()
			}
		}

		action(Icon.exit, "Close") {
			hide()
		}
	}

	inline fun action(icon: Drawable?, text: String, crossinline listener: () -> Unit) {
		actionTable.customButton({
			margin(MinchatStyle.buttonMargin)

			addLabel(text, MinchatStyle.Label).growX()

			if (icon != null) {
				addImage(icon, Scaling.fit)
					.minSize(30f).fill()
					.padLeft(20f)
			}
		}, MinchatStyle.ActionButton) {
			listener()
		}.pad(MinchatStyle.layoutPad).growX()
			.row()
	}
}

fun CoroutineScope.MessageContextMenu(chat: ChatFragment, message: MinchatMessage) =
	MessageContextMenu(chat, message, this)
