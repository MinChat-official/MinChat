package io.minchat.client.ui.chat

import arc.Core
import arc.input.KeyCode
import arc.scene.Element
import arc.scene.actions.Actions
import arc.scene.event.Touchable
import arc.scene.ui.*
import arc.scene.ui.layout.*
import arc.util.Align
import com.github.mnemotechnician.mkui.extensions.dsl.*
import com.github.mnemotechnician.mkui.extensions.elements.*
import com.github.mnemotechnician.mkui.extensions.runUi
import io.minchat.client.Minchat
import io.minchat.client.misc.*
import io.minchat.client.ui.*
import io.minchat.common.entity.Message
import io.minchat.rest.entity.MinchatChannel
import io.minchat.rest.event.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import mindustry.Vars
import mindustry.ui.Styles
import java.util.concurrent.ConcurrentLinkedQueue

class ChatFragment(parentScope: CoroutineScope) : Fragment<Table, Table>(parentScope) {
	@Volatile var currentChannel: MinchatChannel? = null

	val notificationStack = ConcurrentLinkedQueue<Notification>()

	/** A bar displaying the current notification. */
	lateinit var notificationBar: Table
	/** A bar containing the channel list. */
	lateinit var channelsBar: Table
	lateinit var channelsContainer: Table

	lateinit var chatBar: Table
	lateinit var chatPane: ScrollPane
	/** Contains a list of message elements. */
	lateinit var chatContainer: Table
	/** A field that allows either to enter a new message or edit an existing one. */
	lateinit var chatField: TextField
	lateinit var sendButton: TextButton

	/** If present, a message being edited. This listener overrides the default "send" action, but only once. */
	private var editListener: ((String) -> Unit)? = null
	private var closeListener: (() -> Unit)? = null

	private var lastChatUpdateJob: Job? = null
	private var messageListenerJob: Job? = null

	override fun build() = createTable {
		update(::tick)
		setClip(false)

		// top bar
		addStack {
			setClip(false)
			// the bar itself
			addTable {
				setFillParent(true)

				textButton("[#${MinchatStyle.red}]X", MinchatStyle.ActionButton) {
					closeListener?.invoke() ?: Vars.ui.showInfo("No close listener.")
				}.padRight(10f).margin(MinchatStyle.buttonMargin).fill()

				addTable(MinchatStyle.surfaceBackground) {
					margin(MinchatStyle.layoutMargin)

					addLabel("MinChat", MinchatStyle.Label)
						.color(MinchatStyle.purple).scaleFont(1.3f)
					// channel name
					addLabel({
						currentChannel?.let { "#${it.name}" } ?: "Choose a channel"
					}, MinchatStyle.Label, ellipsis = "...").growX()
				}.growX().fillY()

				// account button
				textButton({
					Minchat.client.account?.user?.tag ?: "[Not logged in]"
				}, MinchatStyle.ActionButton, ellipsis = "...") {
					AuthDialog().apply {
						hidden(::updateChatUi)
						show()
						update()
					}
				}.minWidth(200f).padLeft(8f).margin(MinchatStyle.buttonMargin)
			}
			// Notification bar. A table is neccessary to render a background.
			addTable(Styles.black8) {
				center()
				notificationBar = this
				touchable = Touchable.disabled
				translation.y -= 20f // offset it a bit down

				addLabel({ notificationStack.peek()?.content ?: "" }).with {
					it.setColor(MinchatStyle.orange)
					it.setFontScale(1.2f)
				}.pad(12f)

				visible { notificationStack.peek() != null }
			}
		}.growX().pad(MinchatStyle.layoutPad).colspan(3).row()

		hsplitter(padBottom = 0f).colspan(3)

		// left bar: channel list + notification labeo
		addTable(MinchatStyle.surfaceBackground) {
			margin(MinchatStyle.layoutMargin)
			channelsBar = this

			addLabel("Channels").color(MinchatStyle.purple).growX().row()

			limitedScrollPane {
				it.isScrollingDisabledX = true
				channelsContainer = this
			}.grow().row()
		}.width(150f).minHeight(300f).pad(MinchatStyle.layoutPad).growY()

		vsplitter()

		// right bar: chat
		addTable {
			margin(MinchatStyle.layoutMargin)

			chatBar = this
			limitedScrollPane {
				it.isScrollingDisabledX = true
				setBackground(MinchatStyle.surfaceBackground)

				chatPane = it
				chatContainer = this
			}.grow().pad(MinchatStyle.layoutPad).row()

			// chatbox
			addTable {
				margin(MinchatStyle.layoutMargin)

				textArea().with {
					chatField = it
					it.setStyle(MinchatStyle.TextInput)

					// Send the current message when the user presses shift+enter
					it.keyDown(KeyCode.enter) {
						if (it.content.isEmpty()) return@keyDown
						if (Core.input.keyDown(KeyCode.shiftLeft) || Core.input.keyDown(KeyCode.shiftRight)) {
							sendCurrentMessage()
						}
					}
				}.growX()

				textButton(">", MinchatStyle.ActionButton) { sendCurrentMessage() }
					.with { sendButton = it }
					.disabled {
						!Minchat.client.isLoggedIn || currentChannel == null ||
							chatField.content.length !in Message.contentLength
					}
					.padLeft(8f).fill().width(80f)

				updateChatbox()
			}.growX().padTop(MinchatStyle.layoutPad).padLeft(10f).padRight(10f)
		}.grow()

		// Listen for minchat message events in this channel and update the message list accordingly
		messageListenerJob = Minchat.gateway.events
			.filter { scene != null } // only receive events if the chat is visible
			.onEach { event ->
				when (event) {
					is MinchatMessageCreate -> {
						val message = event.message
						if (message.channelId == currentChannel?.id) {
							val isAtBottom = chatPane.isBottomEdge
							val element = NormalMinchatMessageElement(this@ChatFragment, message)
							addMessage(element, 0.5f)

							// Scroll down to show the new message, but only if the bottom was already visible.
							if (isAtBottom) {
								chatPane.validate()
								chatPane.fling(0.3f,  0f, -element.height)
							}
						}
					}

					is MinchatMessageModify -> {
						val newMessage = event.message
						if (newMessage.channelId == currentChannel?.id) {
							// Find and replace the old message element in its cell
							val messageCell = chatContainer.cells.find {
								it.getAsOrNull<NormalMinchatMessageElement>()?.message?.id == newMessage.id
							}
							messageCell.setElement<Element>(NormalMinchatMessageElement(this@ChatFragment, newMessage))
							chatContainer.invalidateHierarchy()
						}
					}

					is MinchatMessageDelete -> {
						if (event.channelId == currentChannel?.id) {
							// Play a shrinking animation and finally rremove the element.
							chatContainer.children.find {
								(it as? NormalMinchatMessageElement)?.message?.id == event.messageId
							}?.let {
								it.addAction(Actions.sequence(
									Actions.sizeBy(0f, -it.height, 1f),
									Actions.remove()
								))
							}
						}
					}

					is MinchatChannelCreate,
					is MinchatChannelModify,
					is MinchatChannelDelete -> {
						// TODO properly handle this
						reloadChannels()
					}

					is MinchatUserModify -> {
						//println("received user modify event: ${event.user}")
						val newUser = event.user
						// FIXME: doesn't work, the container seems empty?
						chatContainer.cells.forEach {
							val element = it.getAsOrNull<NormalMinchatMessageElement>()?.let { old ->
								println("old: $old")
								old.takeIf { it.message.authorId == newUser.id }?.let {
									NormalMinchatMessageElement(old.chat, old.message.copy(author = newUser.data))
								}
							} ?: return@onEach

							println("replaced with ${newUser.username}")

							it.setElement<NormalMinchatMessageElement>(element)
						}
					}
				}
			}.launchIn(this@ChatFragment)
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

	/** Adds a message to the list of messages. */
	fun addMessage(element: MinchatMessageElement, animationLegth: Float) = synchronized(chatContainer) {
		chatContainer.add(element)
			.padBottom(10f).pad(4f).growX().row()
		element.animateMoveIn(animationLegth)
	}

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
				it.label.setColor(MinchatStyle.foreground)
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
					addMessage(NormalMinchatMessageElement(this@ChatFragment, message),
						animationLegth = 0.5f + 0.01f * (messages.size - index))
				}

				// force the scroll pane to recalculate its dimensions and scroll to the bottom
				chatPane.validate()
				chatPane.setScrollYForce(chatPane.maxY)
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

		if (!Minchat.client.isLoggedIn) {
			Vars.ui.showInfo("You must login or register to send messages.")
			return null
		}
		if (content.length !in Message.contentLength) {
			Vars.ui.showInfo("Messages must have a length of ${Message.contentLength} characters")
			return null
		}

		val notif = notification("Sending...", 1)
		return launch {
			runSafe {
				channel.createMessage(content)
			}
		}.then { notif.cancel() }
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
}
