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
import io.minchat.rest.*
import io.minchat.rest.entity.*
import java.time.*
import java.time.format.DateTimeFormatter
import java.util.concurrent.ConcurrentLinkedQueue
import mindustry.Vars
import mindustry.graphics.Pal
import mindustry.gen.Tex
import mindustry.ui.*
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

class ChatFragment(context: CoroutineContext) : Fragment<Table, Table>(context) {
	@Volatile var currentChannel: MinchatChannel? = null

	val notificationStack = ConcurrentLinkedQueue<Notification>()

	lateinit var notificationBar: Table
	lateinit var channelsBar: Table
	lateinit var channelsContainer: Table

	lateinit var chatBar: Table
	lateinit var chatContainer: Table
	lateinit var chatField: TextField
	lateinit var sendButton: TextButton

	private var closeListener: (() -> Unit)? = null
	
	private var lastChatUpdateJob: Job? = null

	override fun build() = createTable {
		update(::tick)

		// top bar
		addStack {
			setClip(false)
			// the bar itself
			addTable(Style.surfaceBackground) {
				setFillParent(true)

				textButton("[#${Style.red}]X") {
					closeListener?.invoke() ?: 
						Vars.ui.showInfo("No close listener.")
				}.padRight(8f)

				addLabel("MinChat").color(Style.purple)

				// channel name
				addLabel({
					currentChannel?.let { "#${it.name}" } ?: "Choose a channel"
				}, ellipsis = "...").growX().minWidth(150f).color(Style.foreground)

				// account button
				textButton({
					Minchat.client.account?.user?.tag ?: "[Not logged in]"
				}, ellipsis = "...") {
					AccountDialog().show()
				}.minWidth(200f).padLeft(8f).with {
					it.label.setColor(Style.foreground)
				}
			}
			// Notification bar. A table is neccessary to render a background.
			addTable(Styles.black5) {
				center()
				notificationBar = this
				touchable = Touchable.disabled
				translation.y -= 20f // offset it a bit down

				addLabel({ notificationStack.peek()?.content ?: "" }).with {
					it.setColor(Style.orange)
					it.setFontScale(1.2f)
				}.pad(8f)

				visible { notificationStack.peek() != null }
			}
		}.growX().pad(4f).colspan(3).row()

		hsplitter(padBottom = 0f).colspan(3)

		// left bar: channel list + notification labeo
		addTable(Style.surfaceBackground) {
			channelsBar = this

			addLabel("Channels", align = Align.left)
				.color(Style.purple).growX().marginTop(5f).row()

			limitedScrollPane {
				it.isScrollingDisabledX = true
				channelsContainer = this
			}.grow().row()
		}.width(150f).minHeight(300f).pad(4f).growY()

		vsplitter()

		// right bar: chat
		addTable {
			chatBar = this
			limitedScrollPane {
				it.isScrollingDisabledX = true
				addLabel("this is a WIP chat ui")
				setBackground(Style.surfaceBackground)
				chatContainer = this
			}.grow().pad(4f).row()

			// chatbox
			addTable(Style.surfaceBackground) {
				textField().with {
					it.setStyle(TextField.TextFieldStyle(Styles.defaultField).apply {
						fontColor = Style.foreground
					})
					chatField = it
				}.growX()

				textButton(">") {
					if (Minchat.client.isLoggedIn) {
						sendCurrentMessage()
					} else {
						Vars.ui.showInfo("You must login or register first.")
					}
				}.with { sendButton = it }.disabled {
					Minchat.client.isLoggedIn || currentChannel == null
				}
				updateChatbox()
			}.growX().pad(4f).padLeft(10f).padRight(10f)
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
	}

	fun reloadChannels(): Job {
		val notif = notification("Loading channels...", 10)
		return launch {
			runCatching {
				val channels = Minchat.client.getAllChannels()
				runUi { setChannels(channels) }
			}.onFailure {
				notification(it.userReadable(), 5)
			}
			notif.cancel()
		}
	}

	fun setChannels(channels: List<MinchatChannel>) {
		channelsContainer.clearChildren()
		
		channels.forEach { channel ->
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
			val messages = runCatching {
				channel.getAllMessages(limit = 60).toList().reversed()
			}.onFailure {
				notif.cancel()
				notification(it.userReadable(), 5)
			}.getOrThrow()

			notif.cancel()
			// if the channel was changed, return
			if (channel != currentChannel) return@launch

			runUi {
				updateChatbox()

				chatContainer.clearChildren()

				messages.forEach { message ->
					val element = MinchatMessageElement(message)
					chatContainer.add(element)
						.padBottom(10f).pad(4f).growX().row()

					// play a move-in animation
					element.addAction(sequence(
						translateBy(chatContainer.width, 0f),
						translateBy(-chatContainer.width, 0f, 0.5f, sineOut)
					))
				}
			}
		}
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

		val content = chatField.content.trim().replace("\\n", "\n")
		val channel = currentChannel ?: return null

		return if (content.isNotEmpty()) {
			val notif = notification("Sending...", 1)
			launch {
				runCatching {
					channel.createMessage(content)
				}.onFailure {
					notification(it.userReadable(), 10)
				}
				if (currentChannel == channel) updateChatUi()?.join()
				notif.cancel()
			}
		} else null
	}

	/** Executes an action when the close button is pressed. Overrires the previous listener. */
	fun onClose(action: () -> Unit) {
		closeListener = action
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
	class MinchatMessageElement(val message: MinchatMessage) : Table(Styles.black5) {
		/** When a DateTimeFormatter has to be used to acquire a timestamp, the result is saved here. */
		private var cachedLongTimestamp: String? = null

		init {
			val discriminator = message.author.discriminator.toString().padStart(4, '0')
			
			// Top row: author tag + timeatamp
			addTable {
				left()
				addLabel(message.author.username, ellipsis = "...").color(when {
					message.author.id == Minchat.client.account?.id -> Style.green
					message.author.isAdmin -> Style.pink // I just like pink~
					else -> Style.purple
				})
				addLabel("#$discriminator").color(Style.comment)
				addLabel({ formatTimestamp() }).color(Style.comment).padLeft(20f)
			}.growX().row()
			// bottom row: message content
			addLabel(message.content, wrap = true, align = Align.left)
				.growX().color(Style.foreground)
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
				.atZone(timezone)
				.let { timestampFormatter.format(it) }

			return longTimestamp.also { cachedLongTimestamp = it }
		}

		companion object {
			val timestampFormatter = DateTimeFormatter.ofPattern("yyyy.MM.dd HH:mm")
			val timezone = ZoneId.systemDefault()
		}
	}

	object Style {
		// darcula theme specs
		val background = Color.valueOf("282A36")
		val foreground = Color.valueOf("F8F8F2")
		val comment = Color.valueOf("6272A4")
		val red = Color.valueOf("FF5555")
		val orange = Color.valueOf("FFB86C")
		val yellow = Color.valueOf("F1FA8C")
		val green = Color.valueOf("50FA7B")
		val purple = Color.valueOf("BD93F9")
		val cyan = Color.valueOf("8BE9FD")
		val pink = Color.valueOf("FF79C6") // uwu

		val surfaceWhite = Tex.whiteui as TextureRegionDrawable
		val surfaceBackground = surfaceWhite.tint(background)
	}
}
