package io.minchat.client.ui.chat

import arc.Core
import arc.scene.style.Drawable
import arc.scene.ui.Dialog
import arc.scene.ui.layout.Table
import arc.util.*
import com.github.mnemotechnician.mkui.extensions.dsl.*
import io.minchat.client.Minchat
import io.minchat.client.ui.dialog.UserDialog
import io.minchat.rest.entity.MinchatMessage
import kotlinx.coroutines.*
import mindustry.Vars
import mindustry.gen.Icon
import io.minchat.client.misc.MinchatStyle as Style

/**
 * Displays a MinChat message sent by a real user or a bot.
 */
class NormalMinchatMessageElement(
	val chat: ChatFragment,
	val message: MinchatMessage,
	addContextActions: Boolean = true
) : MinchatMessageElement(addContextActions), CoroutineScope by chat {
	override val timestamp get() = message.timestamp

	init {
		margin(4f)

		val discriminator = message.author.discriminator.toString().padStart(4, '0')

		// Top row: author tag + timestamp
		addTable {
			left()
			// display name
			addLabel(message.author.displayName, ellipsis = "...").color(when {
				message.author.id == Minchat.client.account?.id -> Style.green
				message.author.isAdmin -> Style.pink // I just like pink~
				else -> Style.purple
			}).fillY().get().clicked(::showUserDialog)
			// tag
			addLabel("#$discriminator")
				.fillY().color(Style.comment)
				.get().clicked(::showUserDialog)
			// filler + timestamp
			addSpace().growX()
			addLabel({ formatTimestamp() }, ellipsis = "...")
				.color(Style.comment).padLeft(20f)
		}.growX().padBottom(5f).row()
		// bottom row: message content
		addLabel(message.content, wrap = true, align = Align.left)
			.growX().color(Style.foreground)
	}

	fun showUserDialog() {
		UserDialog(message.author).apply {
			show()
			update()
		}
	}

	override fun onRightClick() {
		MessageContextMenu().show()
	}

	inner class MessageContextMenu : Dialog() {
		val messageElementCopy = NormalMinchatMessageElement(chat, message, false)

		lateinit var actionTable: Table

		init {
			closeOnBack()

			titleTable.remove()
			buttons.remove()

			cont.apply {
				addTable(Style.surfaceBackground) {
					margin(Style.layoutMargin)

					add(messageElementCopy).minWidth(400f)
				}.fill().pad(Style.layoutPad).row()

				hsplitter(Style.foreground)

				addTable {
					actionTable = this
				}.fill()
			}

			action(Icon.copy, "Copy text") {
				Core.app.clipboardText = messageElementCopy.message.content
			}

			action(Icon.pencil, "Edit message") {
				// TODO
				Vars.ui.showInfo("TODO: Edit message")
			}

			action(Icon.trash.tint(Style.red), "Delete message") {
				// TODO: should there be a confirmation dialog?
				launch {
					runCatching {
						messageElementCopy.message.delete()
					}.onFailure {
						Log.err("Failed to delete message", it)
					}
				}
				hide()
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
}
