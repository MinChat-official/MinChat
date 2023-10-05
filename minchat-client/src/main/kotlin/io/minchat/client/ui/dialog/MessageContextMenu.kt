package io.minchat.client.ui.dialog

import arc.Core
import arc.scene.style.Drawable
import arc.scene.ui.Dialog
import arc.scene.ui.layout.Table
import arc.util.Scaling
import com.github.mnemotechnician.mkui.extensions.dsl.*
import io.minchat.client.*
import io.minchat.client.misc.*
import io.minchat.client.ui.MinchatStyle
import io.minchat.client.ui.chat.*
import io.minchat.rest.entity.MinchatMessage
import kotlinx.coroutines.*
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
			action(Icon.chat, "Reply") {
				chat.setReplyMessage(message)
				hide()
			}

			// TODO admins can abuse this!!! They should not be able to edit other's messages, only delete them!!!
			action(Icon.pencil, "Edit message") {
				chat.setEditMessage(message)
				hide()
			}

			action(Icon.trash.tint(MinchatStyle.red), "Delete message") {
				launch {
					runCatching {
						messageElement.message.delete()
						ClientEvents.fire(ClientMessageDeleteEvent(messageElement.message))
					}.onFailure {
						Log.error(it) { "Failed to delete message" }
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
