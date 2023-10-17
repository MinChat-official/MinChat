package io.minchat.client.ui.dialog

import arc.Core
import arc.scene.style.Drawable
import arc.scene.ui.Dialog
import arc.scene.ui.layout.Table
import arc.util.Scaling
import com.github.mnemotechnician.mkui.extensions.dsl.*
import io.minchat.client.*
import io.minchat.client.misc.Log
import io.minchat.client.ui.chat.*
import io.minchat.rest.entity.MinchatMessage
import kotlinx.coroutines.*
import mindustry.gen.Icon
import io.minchat.client.ui.MinchatStyle as Style

/**
 * A context menu dialog that allows the user to
 * perform various actions with the message.
 */
class MessageContextMenu(
	val chat: ChatFragment,
	val message: MinchatMessage,
	parentScope: CoroutineScope
) : Dialog(), CoroutineScope by parentScope {
	val messageElement = NormalMessageElement(chat, message, false)
	lateinit var actionTable: Table

	init {
		closeOnBack()

		titleTable.remove()
		buttons.remove()

		cont.apply {
			addTable(Style.surfaceBackground) {
				margin(Style.layoutMargin)

				add(messageElement)
					.minWidth(400f)
					.row()

				if (message.editTimestamp != null) {
					addLabel("Edited: ${messageElement.formatTimestamp(message.editTimestamp!!)}")
						.color(Style.comment)
						.left()
				}
			}.fill().pad(Style.layoutPad).row()

			hsplitter(Style.foreground)

			addTable {
				actionTable = this
			}.fill()
		}

		action(Icon.copy, "Copy text") {
			Core.app.clipboardText = messageElement.message.content
		}

		action(Icon.chat, "Reply") {
			chat.setReplyMessage(message)
			hide()
		}
		if (Minchat.client.selfOrNull()?.canEditMessage(message) == true) {
			action(Icon.pencil, "Edit message") {
				chat.setEditMessage(message)
				hide()
			}
		}

		if (Minchat.client.selfOrNull()?.canDeleteMessage(message) == true) {
			action(Icon.trash.tint(Style.red), "Delete message") {
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
			margin(Style.buttonMargin)

			addLabel(text, Style.Label).growX()

			if (icon != null) {
				addImage(icon, Scaling.fit)
					.minSize(30f).fill()
					.padLeft(20f)
			}
		}, Style.ActionButton) {
			listener()
		}.pad(Style.layoutPad).growX()
			.row()
	}
}

fun CoroutineScope.MessageContextMenu(chat: ChatFragment, message: MinchatMessage) =
	MessageContextMenu(chat, message, this)
