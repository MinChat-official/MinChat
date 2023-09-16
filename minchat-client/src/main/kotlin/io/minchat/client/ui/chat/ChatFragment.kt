package io.minchat.client.ui.chat

import arc.Core
import arc.graphics.Color
import arc.input.KeyCode
import arc.math.Interp
import arc.scene.Element
import arc.scene.event.Touchable
import arc.scene.ui.*
import arc.scene.ui.layout.*
import com.github.mnemotechnician.mkui.extensions.dsl.*
import com.github.mnemotechnician.mkui.extensions.elements.*
import com.github.mnemotechnician.mkui.extensions.runUi
import io.minchat.client.Minchat
import io.minchat.client.misc.*
import io.minchat.client.ui.Fragment
import io.minchat.client.ui.dialog.*
import io.minchat.client.ui.tutorial.Tutorials
import io.minchat.common.entity.Message
import io.minchat.rest.entity.*
import io.minchat.rest.event.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import mindustry.Vars
import mindustry.ui.Styles
import java.util.concurrent.ConcurrentLinkedQueue
import io.minchat.client.misc.MinchatStyle as Style

class ChatFragment(parentScope: CoroutineScope) : Fragment<Table, Table>(parentScope) {
	@Volatile var currentChannel: MinchatChannel? = null

	val notificationStack = ConcurrentLinkedQueue<Notification>()

	/** A bar displaying the current notification. */
	lateinit var notificationBar: Table
	/** A bar containing the channel list. */
	lateinit var channelsBar: Table
	lateinit var channelsContainer: Table

	lateinit var chatBar: Table
	/** Wraps [chatContainer]. */
	lateinit var chatPane: ScrollPane
	/** Contains a list of message elements. Each element must be placed on a separate row. */
	lateinit var chatContainer: Table
	/** A field that allows either to enter a new message or edit an existing one. */
	lateinit var chatField: TextField
	lateinit var sendButton: TextButton

	/** If present, a message being edited. This listener overrides the default "send" action, but only once. */
	private var editListener: (suspend (String) -> Unit)? = null
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

				textButton("[#${Style.red}]X", Style.ActionButton) {
					closeListener?.invoke() ?: Vars.ui.showInfo("No close listener.")
				}
					.fill()
					.padRight(10f).margin(Style.buttonMargin)

				addTable(Style.surfaceBackground) {
					touchable = Touchable.enabled
					margin(Style.layoutMargin)

					addLabel("MinChat", Style.Label)
						.fill()
						.color(Style.purple).scaleFont(1.3f)
					// channel name
					addLabel({
						currentChannel?.let { "#${it.name}" } ?: "Choose a channel"
					}, Style.Label, ellipsis = "...")
						.growX().fillY()
						.row()
					// Empty left cell
					addSpace()
					// Channel description
					hider(hideVertical = { currentChannel?.description?.isEmpty() ?: true }) {
						addLabel({
							currentChannel?.description.orEmpty()
						}, ellipsis = "...")
							.grow().color(Style.comment)
					}.growX().fillY()

					clicked {
						if (currentChannel == null) return@clicked

						ChannelDialog(currentChannel!!).show()
					}
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
				}
					.minWidth(200f).fillY()
					.padLeft(8f).margin(Style.buttonMargin)
			}
			// An overlay notification bar. A table is necessary to render a background.
			addTable(Styles.black8) {
				center()
				notificationBar = this
				touchable = Touchable.disabled
				translation.y -= 10f // offset it down by a little bit

				addLabel({ notificationStack.peek()?.content ?: "" }, wrap = true).with {
					it.setColor(Style.orange)
					it.setFontScale(1.2f)
				}.growX().pad(12f)

				visible { notificationStack.peek() != null }
			}
		}.growX().pad(Style.layoutPad).colspan(3).row()

		hsplitter(padBottom = 0f).colspan(3)

		// left bar: channel list + notification label
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

			// Status bar above the chat
			hider(hideVertical = { editListener == null && chatField.content.isEmpty() }) {
				background = Style.black(2)
				left()

				hider(hideHorizontal = { editListener == null }) {
					textButton("[lightgray]Cancel", Styles.nonet) {
						editListener = null
						chatField.content = ""
					}.pad(Style.layoutPad).fillY()

					addLabel("Editing message...")
						.pad(Style.layoutPad)

					addSpace(width = 50f)
				}

				val exp10 = Interp.ExpIn(20f, 10f)
				val charCounter = addLabel({ "${chatField.content.length}/${Message.contentLength.endInclusive}" })
					.pad(Style.layoutPad)
					.get()
				update {
					val colorRatio = chatField.content.length.toFloat() / Message.contentLength.endInclusive
					val interpolated = exp10.apply(colorRatio)

					if (interpolated >= 0.001f) {
						charCounter.setColor(Color.lightGray.lerp(Color.crimson, colorRatio))
					}
				}
			}.growX().row()

			// chatbox
			addTable {
				margin(Style.layoutMargin)

				textArea().with {
					chatField = it
					it.setStyle(Style.TextInput)

					// Send the current message when the user presses shift+enter
					it.keyDown(KeyCode.enter) {
						if (it.content.isEmpty()) return@keyDown
						if (Core.input.keyDown(KeyCode.shiftLeft) || Core.input.keyDown(KeyCode.shiftRight)) {
							confirmCurrentMessage()
						}
					}
				}.growX()

				textButton(">", Style.ActionButton) { confirmCurrentMessage() }
					.with { sendButton = it }
					.disabled {
						!Minchat.client.isLoggedIn || currentChannel == null ||
							chatField.content.length !in Message.contentLength
					}
					.padLeft(8f).fill().width(80f)

				updateChatbox()
			}.growX().padTop(Style.layoutPad).padLeft(10f).padRight(10f)
		}.grow()

		// Listen for minchat message events in this channel and update the message list accordingly
		messageListenerJob = launch {
			Minchat.awaitConnection()
			listenForMessages()
		}
	}

	private suspend fun listenForMessages() = Minchat.gateway.events
		.filter { instance?.scene != null } // only react to events if the chat is visible
		.onEach { event ->
			when (event) {
				is MinchatMessageCreate -> {
					val message = event.message
					if (message.channelId == currentChannel?.id) {
						val element = NormalMinchatMessageElement(this@ChatFragment, message)
						addMessage(element, 0.5f)
					}
				}

				is MinchatMessageModify -> {
					val newMessage = event.message
					if (newMessage.channelId == currentChannel?.id) {
						// Find and replace the old message element in its cell
						val messageCell = chatContainer.cells.find {
							// getAsOrNull won't work for some reason
							(it.get() as? NormalMinchatMessageElement)?.message?.id == newMessage.id
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
							(it as NormalMinchatMessageElement).animateDisappear(1f)
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
					val newUser = event.user
					chatContainer.cells.forEach {
						val element = (it.get() as? NormalMinchatMessageElement)?.let { old ->
							old.takeIf { it.message.authorId == newUser.id }?.let {
								NormalMinchatMessageElement(old.chat, old.message.copy(author = newUser.data))
							}
						} ?: return@forEach

						it.setElement<NormalMinchatMessageElement>(element)
					}
				}
			}
		}.collect()

	private fun tick() {
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

	/**
	 * Adds a message to the list of messages.
	 *
	 * If [autoscroll] is true and the chat is already scrolled to the bottom,
	 * it will be scrolled down more to show the message.
	 */
	fun addMessage(
		element: MinchatMessageElement,
		animationLength: Float,
		autoscroll: Boolean = true
	) = synchronized(chatContainer) {
		val isAtBottom = chatPane.isBottomEdge

		chatContainer.add(element)
			.padBottom(10f).pad(4f).growX().row()
		element.animateMoveIn(animationLength)

		// Scroll down to show the new message, but only if the bottom was already visible.
		if (isAtBottom && autoscroll) {
			chatPane.validate()
			runUi {
				chatPane.scrollY = chatPane.maxY
			}
		}
	}

	override fun applied(cell: Cell<Table>) {
		cell.grow()
		reloadChannels()
		updateChatUi()

		Tutorials.welcome.trigger()
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
			channelsContainer.add(ChannelButton(this, channel))
				.pad(Style.layoutPad).growX().row()
		}
	}

	/**
	 * Reloads messages from the server and updates the chat pane.
	 *
	 * @param forcibly if true, even unchanged messages will be updated.
	 */
	fun updateChatUi(forcibly: Boolean = false): Job? {
		val channel = currentChannel ?: return null
		val notif = notification("Loading messages...", 10)

		lastChatUpdateJob?.cancel()

		return launch {
			val messages = runSafe {
				channel.getAllMessages(limit = 50).toList().reversed()
			}.getOrThrow()
			// if the channel was changed, return
			if (channel != currentChannel) return@launch

			runUi {
				updateChatbox()

				// take a sample and determine if the messages are from the same channel as before
				val sameChannel = chatContainer.children.find { it is NormalMinchatMessageElement }
					?.let { it as NormalMinchatMessageElement }
					?.message?.channelId == currentChannel?.id

				if (forcibly || !sameChannel) {
					// If not, replace everything and play an incrementing move-in animation
					chatContainer.clearChildren()
					messages.forEachIndexed { index, message ->
						addMessage(NormalMinchatMessageElement(this@ChatFragment, message),
							animationLength = 0.5f + 0.01f * (messages.size - index))
					}
				} else {
					// Otherwise, immediately remove normal message elements that should not be here anymore
					chatContainer.children.forEach { element ->
						if (element !is NormalMinchatMessageElement) return@forEach
						if (element.message !in messages) {
							element.animateDisappear(0.5f)
						}
					}
					// Then add all the missing message elements
					messages.forEach { message ->
						if (chatContainer.children.none { it is NormalMinchatMessageElement && it.message == message }) {
							addMessage(NormalMinchatMessageElement(this@ChatFragment, message), 1f)
						}
					}
					// ...And then sort the cells in the most brutal way, leaving the table confused
					// (pro-tip: never let silly idiots like me write front-end code)
					chatContainer.cells.sort { cellA, cellB ->
						val a = cellA.get()
						val b = cellB.get()

						when {
							a is MinchatMessageElement && b is MinchatMessageElement -> {
								a.timestamp.compareTo(b.timestamp)
							}
							a is MinchatMessageElement -> 1
							b is MinchatMessageElement -> -1
							else -> 0
						}
					}
					// finally, help the table recover from the moral trauma we just gave it
					chatContainer.invalidate()
					chatContainer.validate()
				}

				// force the scroll pane to recalculate its dimensions and scroll to the bottom
				chatPane.validate()
				chatPane.setScrollYForce(chatPane.maxY)
			}
		}.also {
			lastChatUpdateJob = it
		}.then { notif.cancel() }
	}

	fun updateChatbox() {
		chatField.hint = when {
			!Minchat.client.isLoggedIn -> "Log in or register to send messages."
			currentChannel == null -> "Choose a channel to chat in."
			else -> "Message #${currentChannel?.name}"
		}
	}

	/** Either sends the current message or, if in edit mode, edits the current message. */
	fun confirmCurrentMessage(): Job? {
		if (!Minchat.client.isLoggedIn) return null

		val content = chatField.content.trim()
		val channel = currentChannel ?: return null
		runUi { chatField.content = "" }

		if (!Minchat.client.isLoggedIn) {
			Vars.ui.showInfo("You must login or register to send messages.")
			return null
		}
		if (content.length !in Message.contentLength) {
			Vars.ui.showInfo("Messages must have a length of ${Message.contentLength} characters")
			return null
		}

		if (editListener == null) {
			val notif = notification("Sending...", 1)
			return launch {
				runSafe {
					channel.createMessage(content)
				}
			}.then { notif.cancel() }
		} else {
			val notif = notification("Editing...", 1)
			val listener = editListener!!
			editListener = null

			return launch {
				runSafe {
					listener(content)
				}
			}.then { notif.cancel() }
		}
	}

	/**
	 * Clears the current message and replaces it with the contents of the provided message.
	 *
	 * Sets up an edit listener so that when the user presses "send", the message gets edited.
	 */
	fun setEditMessage(message: MinchatMessage?) {
		if (message != null) {
			editListener = { message.edit(newContent = it) }
			chatField.content = message.content
		} else if (editListener != null) {
			editListener = null
			chatField.content = ""
		}
	}

	/** Executes an action when the close button is pressed. Overrides the previous listener. */
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
