package io.minchat.client.ui.chat

import arc.Core
import arc.graphics.Color
import arc.scene.event.Touchable
import arc.scene.ui.Label
import arc.util.*
import com.github.mnemotechnician.mkui.extensions.dsl.*
import com.github.mnemotechnician.mkui.extensions.elements.*
import com.github.mnemotechnician.mkui.extensions.runUi
import io.minchat.client.Minchat
import io.minchat.client.config.MinchatSettings
import io.minchat.client.misc.*
import io.minchat.client.ui.AsyncImage
import io.minchat.client.ui.MinchatStyle.layoutMargin
import io.minchat.client.ui.MinchatStyle.layoutPad
import io.minchat.client.ui.dialog.*
import io.minchat.common.entity.User
import io.minchat.rest.entity.MinchatMessage
import kotlinx.coroutines.*
import io.minchat.client.ui.MinchatStyle as Style

/**
 * Displays a MinChat message sent by a real user or a bot.
 */
class NormalMessageElement(
	val chat: ChatFragment,
	val message: MinchatMessage,
	addContextActions: Boolean = true
) : AbstractMessageElement(addContextActions), CoroutineScope by chat.fork() {
	override val timestamp get() = message.timestamp

	@Volatile
	var referencedMessage: MinchatMessage? = null

	init {
		clip = true
		left().margin(4f)

		if (message.referencedMessageId != null) {
			lateinit var authorLabel: Label
			lateinit var contentLabel: Label

			// Top row: referenced message
			addMinTable {
				left().margin(layoutMargin)

				addLabel("Reply to ")
					.color(Color.darkGray)
				addLabel("Loading reply message...", ellipsis = "...")
					.color(Color.gray)
					.also { authorLabel = it.get() }
				addLabel("", ellipsis = "...", align = Align.left)
					.left()
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
					runUi {
						authorLabel.content = it.author.displayTag.let { "$it: " }

						contentLabel.content = it.content.replace("\n", " ").let {
							if (it.length > 72) it.take(69) + "..." else it
						}
					}
				}
			}
		}

		// Middle row: author avatar + tag + timestamp
		addTable {
			left()

			val discriminator = message.author.discriminator.toString().padStart(4, '0')
			val nameColor = when {
				message.author.id == Minchat.client.account?.id -> Style.green
				message.author.role.isAdmin -> Style.pink // I just like pink~
				message.author.role.isModerator -> Style.cyan
				else -> Style.purple
			}
			val nameColorTag = "[#$nameColor]"

			val avatar = message.author.avatar ?: User.Avatar.defaultAvatar
			if (avatar is User.Avatar.IconAvatar) {
				addImage(Core.atlas.find(avatar.iconName), scaling = Scaling.fill)
					.size(48f)
			} else {
				add(AsyncImage(this@NormalMessageElement).apply {
					setFileAsync {
						message.author.getImageAvatar(false)!!
					}
				}).size(48f).scaleImage(Scaling.fill)
			}

			addSpace(width = 5f)

			// Display name
			addLabel(nameColorTag + message.author.displayName, ellipsis = "...").fillY()
				.get().clicked(::showUserDialog)
			// Discriminator
			addLabel("#$discriminator")
				.fillY().color(Style.comment)
				.get().clicked(::showUserDialog)

			// Role icon
			if (MinchatSettings.userIcons) message.author.getIcon()?.let { icon ->
				addImage(icon, scaling = Scaling.fill)
					.color(Color.valueOf(Tmp.c1, "#ffffff55"))
					.fillY()
					.padLeft(10f)
			}

			// Timestamp
			addLabel({ formatTimestamp() }, ellipsis = "...", align = Align.right)
				.growX()
				.color(Style.comment).padLeft(20f)
		}.growX().padBottom(5f).row()

		// Bottom row: message content
		val suffix = if (message.editTimestamp != null) "[#${Style.comment}] (edited)" else ""

		addLabel(message.content + suffix, wrap = true, align = Align.left)
			.growX().color(Style.foreground)
			.padBottom(4f)
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
