package io.minchat.client.ui.chat

import arc.util.Align
import com.github.mnemotechnician.mkui.extensions.dsl.*
import io.minchat.client.Minchat
import io.minchat.client.misc.MinchatStyle
import io.minchat.client.ui.UserDialog
import io.minchat.rest.entity.MinchatMessage
import kotlinx.coroutines.CoroutineScope

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
			// username
			addLabel(message.author.username, ellipsis = "...").color(when {
				message.author.id == Minchat.client.account?.id -> MinchatStyle.green
				message.author.isAdmin -> MinchatStyle.pink // I just like pink~
				else -> MinchatStyle.purple
			}).fillY().get().clicked(::showUserDialog)
			// tag
			addLabel("#$discriminator")
				.fillY().color(MinchatStyle.comment)
				.get().clicked(::showUserDialog)
			// filler + timestamp
			addSpace().growX()
			addLabel({ formatTimestamp() })
				.color(MinchatStyle.comment).padLeft(20f)
		}.growX().padBottom(5f).row()
		// bottom row: message content
		addLabel(message.content, wrap = true, align = Align.left)
			.growX().color(MinchatStyle.foreground)
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
