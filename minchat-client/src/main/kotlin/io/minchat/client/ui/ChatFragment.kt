package io.minchat.client.ui

import arc.Core
import arc.graphics.*
import arc.math.Interp.*
import arc.scene.*
import arc.scene.actions.Actions.*
import arc.scene.event.*
import arc.scene.ui.*
import arc.scene.ui.layout.*
import arc.scene.style.*
import arc.util.*
import com.github.mnemotechnician.mkui.extensions.*
import com.github.mnemotechnician.mkui.extensions.dsl.*
import com.github.mnemotechnician.mkui.extensions.elements.*
import com.github.mnemotechnician.mkui.extensions.groups.*
import io.minchat.client.*
import io.minchat.client.misc.*
import io.minchat.client.misc.MinchatStyle as Style
import io.minchat.common.entity.*
import io.minchat.rest.*
import io.minchat.rest.entity.*
import java.time.Instant
import java.util.concurrent.ConcurrentLinkedQueue
import mindustry.Vars
import mindustry.ui.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

class ChatFragment(parentScope: CoroutineScope) : Fragment<Table, Table>(parentScope) {
	@Volatile var currentChannel: MinchatChannel? = null

	val notificationStack = ConcurrentLinkedQueue<Notification>()

	lateinit var notificationBar: Table
	lateinit var channelsBar: Table
	lateinit var channelsContainer: Table

	lateinit var chatBar: Table
	lateinit var chatPane: ScrollPane
	lateinit var chatContainer: Table
	lateinit var chatField: TextField
	lateinit var sendButton: TextButton

	private var closeListener: (() -> Unit)? = null
	
	private var lastChatUpdateJob: Job? = null

	override fun build() = createTable {
		update(::tick)
		setClip(false)

		// top bar
		addStack {
			setClip(false)
			// the bar itself
			addTable {
				setFillParent(true)

				textButton("[#${Style.red}]X", Style.ActionButton) {
					closeListener?.invoke() ?:
						Vars.ui.showInfo("No close listener.")
				}.padRight(10f).margin(Style.buttonMargin).fill()

				addTable(Style.surfaceBackground) {
					margin(Style.layoutMargin)

					addLabel("MinChat", Style.Label)
						.color(Style.purple).scaleFont(1.3f)
					// channel name
					addLabel({
						currentChannel?.let { "#${it.name}" } ?: "Choose a channel"
					}, Style.Label, ellipsis = "...").growX()
				}.growX().fillY()

				// account button
				textButton({
					Minchat.client.account?.user?.tag ?: "[Not logged in]"
				}, Style.ActionButton, ellipsis = "...") {
					AuthDialog().apply {
						hidden(::updateChatUi)
						show()
						update()
					}
				}.minWidth(200f).padLeft(8f).margin(Style.buttonMargin)
			}
			// Notification bar. A table is neccessary to render a background.
			addTable(Styles.black8) {
				center()
				notificationBar = this
				touchable = Touchable.disabled
				translation.y -= 20f // offset it a bit down

				addLabel({ notificationStack.peek()?.content ?: "" }).with {
					it.setColor(Style.orange)
					it.setFontScale(1.2f)
				}.pad(12f)

				visible { notificationStack.peek() != null }
			}
		}.growX().pad(Style.layoutPad).colspan(3).row()

		hsplitter(padBottom = 0f).colspan(3)

		// left bar: channel list + notification labeo
		addTable(Style.surfaceBackground) {
			margin(Style.layoutMargin)
			channelsBar = this

			addLabel("Channels").color(Style.purple).growX().row()

			limitedScrollPane {
				it.isScrollingDisabledX = true
				channelsContainer = this
			}.grow().row()
		}.width(150f).minHeight(300f).pad(Style.layoutPad).growY()

		vsplitter()

		// right bar: chat
		addTable {
			margin(Style.layoutMargin)

			chatBar = this
			limitedScrollPane {
				it.isScrollingDisabledX = true
				setBackground(Style.surfaceBackground)

				chatPane = it
				chatContainer = this
			}.grow().pad(Style.layoutPad).row()

			// chatbox
			addTable {
				margin(Style.layoutMargin)

				textArea().with {
					chatField = it
					it.setStyle(Style.TextInput)
				}.growX()

				textButton(">", Style.ActionButton) {
					if (Minchat.client.isLoggedIn) {
						sendCurrentMessage()
					} else {
						Vars.ui.showInfo("You must login or register first.")
					}
				}.with { sendButton = it }.disabled {
					!Minchat.client.isLoggedIn || currentChannel == null ||
						chatField.content.length !in Message.contentLength
				}.padLeft(8f).fill().width(80f)

				updateChatbox()
			}.growX().padTop(Style.layoutPad).padLeft(10f).padRight(10f)
		}.grow()
	}

	fun tick() {
		if (notificationStack.isNotEmpty()) run {
			if (notificationStack.first().shownUntil <= System.currentTimeMillis()) {
				notificationStack.remove()
			}
			// the notification box should quickly fade in and then fade out
			val notification = notificationStack.peek() ?: return@run
			val progress = with(notification) { 1f - (shownUntil - System.currentTimeMillis()) / lifetime.toFloat() }

			notificationBar.color.a = when {
				progress < 0.1f -> progress * 10f
				progress > 0.9f -> (1 - progress) * 10f
				else -> 1f
			}
		} else {
			notificationBar.color.a = 0f
		}
	}

	/**
	 * Show a notification on the top bar.
	 * [maxTime] defines that maximum time it will be shown for (in seconds).
	 */
	fun notification(text: String, maxTime: Long) = 
		Notification(text, maxTime * 1000)
			.also { notificationStack.add(it) }

	override fun applied(cell: Cell<Table>) {
		cell.grow()
		reloadChannels()
		updateChatUi()
	}

	fun reloadChannels(): Job {
		val notif = notification("Loading channels...", 10)
		return launch {
			runSafe {
				val channels = Minchat.client.getAllChannels()
				runUi { setChannels(channels) }
			}
		}.then { notif.cancel() }
	}

	fun setChannels(channels: List<MinchatChannel>) {
		channelsContainer.clearChildren()
		
		channels.forEach { channel ->
			// TODO: custom style
			channelsContainer.textButton("#${channel.name}", Styles.flatBordert, align = Align.left) {
				currentChannel = channel
				lastChatUpdateJob?.cancel()
				lastChatUpdateJob = updateChatUi()
			}.with {
				it.label.setColor(Style.foreground)
			}.align(Align.left).growX().row()
		}
	}

	fun updateChatUi(): Job? {
		val channel = currentChannel ?: return null
		val notif = notification("Loading messages...", 10)

		return launch {
			val messages = runSafe {
				channel.getAllMessages(limit = 60).toList().reversed()
			}.getOrThrow()
			// if the channel was changed, return
			if (channel != currentChannel) return@launch

			runUi {
				updateChatbox()

				chatContainer.clearChildren()

				messages.forEachIndexed { index, message ->
					val element = MinchatMessageElement(message)
					chatContainer.add(element)
						.padBottom(10f).pad(4f).growX().row()

					// play a move-in animation, with newer messages appearing first
					val prolongation = 0.01f * (messages.size - index)
					element.addAction(sequence(
						translateBy(chatContainer.width, 0f),
						translateBy(-chatContainer.width, 0f, 0.5f + prolongation, sineOut)
					))

					// force the scroll pane to recslculate its dimensions and scroll to the bottom
					chatPane.validate()
					chatPane.setScrollYForce(chatPane.maxY)
				}
			}
		}.then { notif.cancel() }
	}

	fun updateChatbox() {
		chatField.hint = when {
			!Minchat.client.isLoggedIn -> "Log in or register to send messages."
			currentChannel == null -> "Choose a channel to chat in."
			else -> "Message #${currentChannel?.name}"
		}
	}

	fun sendCurrentMessage(): Job? {
		if (!Minchat.client.isLoggedIn) return null

		val content = chatField.content.trim()
		val channel = currentChannel ?: return null
		chatField.content = ""

		return if (content.isNotEmpty()) {
			val notif = notification("Sending...", 1)
			launch {
				runSafe {
					channel.createMessage(content)
				}
				if (currentChannel == channel) updateChatUi()?.join()
			}.then { notif.cancel() }
		} else null
	}

	/** Executes an action when the close button is pressed. Overrires the previous listener. */
	fun onClose(action: () -> Unit) {
		closeListener = action
	}

	/** Executes [block] and sends a notification if an important exception is thrown. */
	inline fun <R> runSafe(notificationTime: Long = 5, block: () -> R) =
		runCatching {
			block()
		}.onFailure {
			if (it.isImportant()) notification(it.userReadable(), notificationTime)
		}

	/**
	 * Represents a notification shown in the top bar.
	 */
	data class Notification(
		val content: String,
		val lifetime: Long,
		@Volatile var shownUntil: Long = System.currentTimeMillis() + lifetime
	) {
		fun cancel() {
			shownUntil = 0
		}
	}

	/**
	 * Displays a MinChat message.
	 */
	inner class MinchatMessageElement(val message: MinchatMessage) : Table(Style.surfaceInner) {
		/** When a DateTimeFormatter has to be used to acquire a timestamp, the result is saved here. */
		private var cachedLongTimestamp: String? = null

		init {
			margin(4f)

			val discriminator = message.author.discriminator.toString().padStart(4, '0')
			
			// Top row: author tag + timestamp
			addTable {
				left()
				// username
				addLabel(message.author.username, ellipsis = "...").color(when {
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

		protected fun formatTimestamp(): String {
			val minutesSince = (System.currentTimeMillis() - message.timestamp) / 1000 / 60
			if (minutesSince < 60 * 24) {
				// less than 1 day ago
				return when {
					minutesSince == 0L -> "Just now"
					minutesSince == 1L -> "A minute ago"
					minutesSince in 2L..<60L -> "$minutesSince minutes ago"
					minutesSince in 60L..<120L -> "An hour ago"
					else -> "${minutesSince / 60} hours ago"
				}
			}
			
			// more than 1 day ago. try to returned the cached timestamp or create a new one
			cachedLongTimestamp?.let { return it }

			val longTimestamp = Instant.ofEpochMilli(message.timestamp)
				.atZone(Minchat.timezone)
				.let { Minchat.timestampFormatter.format(it) }

			return longTimestamp.also { cachedLongTimestamp = it }
		}
	}
}
