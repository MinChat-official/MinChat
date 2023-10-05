package io.minchat.client.ui.chat

import arc.graphics.Color
import arc.scene.event.Touchable
import arc.scene.ui.Label
import arc.util.Align
import com.github.mnemotechnician.mkui.extensions.dsl.*
import com.github.mnemotechnician.mkui.extensions.elements.content
import io.minchat.client.Minchat
import io.minchat.client.ui.MinchatStyle.layoutMargin
import io.minchat.client.ui.MinchatStyle.layoutPad
import io.minchat.client.ui.dialog.*
import io.minchat.rest.entity.MinchatMessage
import kotlinx.coroutines.*
import io.minchat.client.ui.MinchatStyle as Style

/**
 * Displays a MinChat message sent by a real user or a bot.
 */
class NormalMinchatMessageElement(
	val chat: ChatFragment,
	val message: MinchatMessage,
	addContextActions: Boolean = true
) : MinchatMessageElement(addContextActions), CoroutineScope by chat {
	override val timestamp get() = message.timestamp

	@Volatile
	var referencedMessage: MinchatMessage? = null

	init {
		left().margin(4f)

		if (message.referencedMessageId != null) {
			lateinit var authorLabel: Label
			lateinit var contentLabel: Label

			// Top row: referenced message
			addTable {
				margin(layoutMargin)

				addLabel("Reply to ")
					.color(Color.darkGray)
				addLabel("Loading reply message...", ellipsis = "...")
					.color(Color.gray)
					.also { authorLabel = it.get() }
				addLabel("", ellipsis = "...", align = Align.left)
					.growX()
					.color(Color.lightGray)
					.also { contentLabel = it.get() }

				touchable = Touchable.enabled
				clicked {
					referencedMessage?.let { chat.chatPane.scrollToMessage(it) }
				}
			}.growX()
				.padBottom(layoutPad)
				.row()

			launch {
				referencedMessage = message.getReferencedMessage()?.also {
					authorLabel.content = it.author.displayTag.let { "$it: "}

					contentLabel.content = it.content.replace("\n", " ").let {
						if (it.length > 72) it.take(69) + "..." else it
					}
				}
			}
		}

		// Middle row: author tag + timestamp
		addTable {
			left()

			val discriminator = message.author.discriminator.toString().padStart(4, '0')
			// Display name
			addLabel(message.author.displayName, ellipsis = "...").color(when {
				message.author.id == Minchat.client.account?.id -> Style.green
				message.author.role.isModerator -> Style.purple
				message.author.role.isAdmin -> Style.pink // I just like pink~
				else -> Style.purple
			}).fillY()
				.get().clicked(::showUserDialog)
			// Discriminator
			addLabel("#$discriminator")
				.fillY().color(Style.comment)
				.get().clicked(::showUserDialog)

			//

			// Timestamp
			addLabel({ formatTimestamp() }, ellipsis = "...", align = Align.right)
				.growX()
				.color(Style.comment).padLeft(20f)
		}.growX().padBottom(5f).row()
		// Bottom row: message content
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
		MessageContextMenu(chat, message).show()
	}
}
