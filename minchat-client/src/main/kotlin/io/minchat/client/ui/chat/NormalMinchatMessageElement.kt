package io.minchat.client.ui.chat

import arc.util.Align
import com.github.mnemotechnician.mkui.extensions.dsl.*
import io.minchat.client.Minchat
import io.minchat.client.ui.dialog.UserDialog
import io.minchat.rest.entity.MinchatMessage
import kotlinx.coroutines.CoroutineScope
import io.minchat.client.misc.MinchatStyle as Style

/**
 * Displays a MinChat message sent by a real user or a bot.
 */
class NormalMinchatMessageElement(
	val chat: ChatFragment,
	val message: MinchatMessage,
	val showActionsMenu: Boolean = true
) : MinchatMessageElement(), CoroutineScope by chat {
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
			addLabel({ formatTimestamp() })
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

	fun showActionBar() {

	}
}
