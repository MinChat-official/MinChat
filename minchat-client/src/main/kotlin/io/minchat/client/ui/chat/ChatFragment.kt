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
import io.minchat.client.*
import io.minchat.client.misc.*
import io.minchat.client.ui.*
import io.minchat.client.ui.dialog.*
import io.minchat.client.ui.managers.UnreadsManager
import io.minchat.client.ui.tutorial.Tutorials
import io.minchat.common.BaseLogger
import io.minchat.common.BaseLogger.Companion.getContextSawmill
import io.minchat.common.entity.Message
import io.minchat.rest.entity.*
import io.minchat.rest.event.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import mindustry.Vars
import mindustry.ui.Styles
import java.util.concurrent.ConcurrentLinkedDeque
import io.minchat.client.ui.MinchatStyle as Style

class ChatFragment(parentScope: CoroutineScope) : Fragment<Table, Table>(parentScope) {
	@Volatile var currentChannel: MinchatChannel? = null

	val notificationStack = ConcurrentLinkedDeque<Notification>()

	/** A bar displaying the current notification. */
	lateinit var notificationBar: Table
	/** A bar containing the channel list. */
	lateinit var channelsBar: Table
	lateinit var dmsSubBar: DMGroupBar
	/** A vertical table containing [ChannelGroupElement]s */
	lateinit var channelsContainer: Table

	lateinit var chatBar: Table
	/** Wraps [chatContainer]. */
	lateinit var chatPane: ChatScrollPane
	/** Contains a list of message elements. Each element must be placed on a separate row. */
	lateinit var chatContainer: Table
	/** A field that allows either to enter a new message or edit an existing one. */
	lateinit var chatField: TextField
	lateinit var sendButton: TextButton

	/** If present, the next sent message will be a reply to this message. See [setReplyMessage]. */
	private var referencedMessage: MinchatMessage? = null
	/**
	 * If present, a message being edited. This listener overrides the default "send" action, but only once.
	 * See [setEditMessage]
	 */
	private var editListener: (suspend (String) -> Unit)? = null
	private var closeListener: (() -> Unit)? = null

	private var lastChatUpdateJob: Job? = null
	private var messageListenerJob: Job? = null

	private val logger = BaseLogger.getContextSawmill()

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

				addMinTable(Style.surfaceBackground) {
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
		addMinSizedTable(
			prefWidth = 150f,
			prefHeight = 300f,
			background = Style.surfaceBackground
		) {
			margin(Style.layoutMargin)
			channelsBar = this

			dmsSubBar = DMGroupBar(this@ChatFragment, emptyMap())
			val dmCell = add(dmsSubBar).grow()
			row()

			addLabel("Channels", Style.Label)
				.color(Style.purple)
				.pad(Style.layoutPad)
				.growX().row()

			limitedScrollPane {
				it.isScrollingDisabledX = true
				channelsContainer = this
			}.grow().row()

			// At the very bottom, if the user is an admin, add a button to open the admin panel.
			hider(hideVertical = { Minchat.client.selfOrNull()?.role?.isAdmin != true }) {
				margin(Style.layoutMargin)
				textButton("Admin panel", Style.ActionButton) {
					AdminPanelDialog().show()
				}.growX().margin(Style.buttonMargin).pad(Style.layoutPad)
			}

			// Make sure the DM pane does not take more than 50% space
			var lastMaxH = 0f
			updateLast {
				val maxH = channelsBar.height - Style.layoutMargin * 2
				if (lastMaxH != maxH) {
					lastMaxH = maxH
					dmCell.maxHeight(maxH)
					dmCell.get()?.invalidateHierarchy()
				}
			}
		}.growY().pad(Style.layoutPad).growY()

		vsplitter()

		// right bar: chat
		addTable {
			margin(Style.layoutMargin)

			chatBar = this
			add(ChatScrollPane(this@ChatFragment) {
				it.isScrollingDisabledX = true
				setBackground(Style.surfaceBackground)

				chatPane = it
				chatContainer = this

				defaults().growX().pad(Style.layoutPad)
			}).grow().pad(Style.layoutPad).row()

			// Chatbox itself (at the bottom)
			val overlayGroup = OverlayGroup(createTable {
				addTable {
					margin(Style.layoutMargin)

					add(ChatTextArea()).with {
						chatField = it

						// Send the current message when the user presses shift+enter
						it.keyDown(KeyCode.enter) {
							if (it.content.isEmpty()) return@keyDown
							if (sendButton.isDisabled) return@keyDown
							if (Core.input.keyDown(KeyCode.shiftLeft) || Core.input.keyDown(KeyCode.shiftRight)) {
								confirmCurrentMessage()
							}
						}
					}.growX()

					textButton(">", Style.ActionButton) { confirmCurrentMessage() }
						.with { sendButton = it }
						.padLeft(8f).fill().width(80f)
					updateChatbox()
				}.growX().padTop(Style.layoutPad).padLeft(10f).padRight(10f)
			})

			// Overlay
			overlayGroup.addChild(createTable {
				touchable = Touchable.enabled
				hider(hideVertical = { referencedMessage == null && editListener == null && chatField.content.isEmpty() }) {
					background = Style.black(9)
					left()

					// Character counter
					val exp40 = Interp.ExpIn(2f, 40f)
					addLabel({ "${chatField.content.length}/${Message.contentLength.endInclusive}" })
						.pad(Style.layoutPad)
						.minHeight(30f)
						.updateLast {
							val colorRatio = chatField.content.length.toFloat() / Message.contentLength.endInclusive
							val interpolated = exp40.apply(colorRatio)
							it.setColor(Color.lightGray.cpy().lerp(Color.crimson, interpolated))
						}

					// Reply status
					hider(hideHorizontal = { referencedMessage == null }) {
						textButton("[lightgray]Cancel", Styles.nonet) {
							setReplyMessage(null)
						}.pad(Style.layoutPad).fillY()

						addLabel({ "Replying to ${referencedMessage?.author?.displayName}" })
							.pad(Style.layoutPad)

						addSpace(width = 50f)
					}

					// Edit status
					hider(hideHorizontal = { editListener == null }) {
						textButton("[lightgray]Cancel", Styles.nonet) {
							setEditMessage(null)
						}.pad(Style.layoutPad).fillY()

						addLabel("Editing a message...")
							.pad(Style.layoutPad)

						addSpace(width = 50f)
					}
				}.growX().row()
			})

			add(overlayGroup).growX()
		}.grow()

		val connectionListener = {
			listenForMessages()

			Minchat.gateway.onFailure {
				updateChatUi(notificationText = "An error has occurred. Reloading messages...")
				logger.warn { "Chat ui reloading due to a gateway failure." }
			}
		}

		ClientEvents.subscribe<ConnectEvent> { connectionListener() }
		if (Minchat.isConnected) {
			connectionListener()
		}
	}

	/** Starts listening for incoming message events and cancels the previous listener, if any. */
	private fun listenForMessages() {
		messageListenerJob?.cancel()
		messageListenerJob = Minchat.gateway.events
			.filter { instance?.scene != null } // only react to events if the chat is visible\
			.onEach { event ->
				when (event) {
					is MinchatMessageCreate -> {
						val message = event.message
						if (message.channelId == currentChannel?.id) {
							val element = NormalMessageElement(this@ChatFragment, message)
							addMessage(element, 0.5f)

							// A message was received, so we update the last time the current channel was read
							UnreadsManager.setForChannel(message.channelId, System.currentTimeMillis())
						} else {
							updateParticularChannel(message.channel)
						}
					}

					is MinchatMessageModify -> {
						val newMessage = event.message
						if (newMessage.channelId == currentChannel?.id) {
							// Find and replace the old message element in its cell
							val messageCell = chatContainer.cells.find {
								// getAsOrNull won't work for some reason
								(it.get() as? NormalMessageElement)?.message?.id == newMessage.id
							}
							val oldElement = messageCell?.get()
							val newElement = NormalMessageElement(this@ChatFragment, newMessage)
							messageCell?.setElement<Element>(newElement)

							// Replace the old element
							val index = chatContainer.children.indexOf(oldElement)
							if (index != -1) {
								chatContainer.children.set(index, newElement)
							}

							chatContainer.invalidateHierarchy()
							// See above
							UnreadsManager.setForChannel(newMessage.channelId, System.currentTimeMillis())
						} else {
							updateParticularChannel(newMessage.channel)
						}
					}

					is MinchatMessageDelete -> {
						if (event.channelId == currentChannel?.id) {
							// Play a shrinking animation and finally remove the element.
							chatContainer.children.find {
								(it as? NormalMessageElement)?.message?.id == event.messageId
							}?.let {
								(it as NormalMessageElement).animateDisappear(1f)
							}
						}
					}

					is MinchatChannelGroupCreate,
					is MinchatChannelGroupModify,
					is MinchatChannelGroupDelete,
					is MinchatChannelCreate,
					is MinchatChannelDelete -> {
						reloadChannels() // not much I can do here
					}

					is MinchatChannelModify -> {
						updateParticularChannel(event.channel)
					}

					is MinchatUserModify -> {
						val newUser = event.user
						chatContainer.cells.forEach {
							val element = (it.get() as? NormalMessageElement)?.let { old ->
								old.takeIf { it.message.authorId == newUser.id }?.let {
									NormalMessageElement(old.chat, old.message.copy(author = newUser.data))
								}
							} ?: return@forEach

							it.setElement<NormalMessageElement>(element)
						}
					}
				}
			}.launchIn(Minchat)
	}

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

		updateChatbox()
	}

	/**
	 * Show a notification on the top bar.
	 * [maxTime] defines that maximum time it will be shown for (in seconds).
	 */
	fun notification(text: String, maxTime: Long) =
		Notification(text, maxTime * 1000)
			.also { notificationStack.addFirst(it) }

	/**
	 * Adds a message to the list of messages.
	 *
	 * If [autoscroll] is true and the chat is already scrolled to the bottom,
	 * it will be scrolled down more to show the message.
	 */
	fun addMessage(
		element: AbstractMessageElement,
		animationLength: Float = 1f,
		autoscroll: Boolean = true
	) = synchronized(chatContainer) {
		val isAtBottom = !chatPane.isScrollY || chatPane.scrollY >= chatPane.maxY - 20f;

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
		val notif = notification("Loading channels and DMs...", 10)
		return launch {
			runSafe {
				val groups = Minchat.client.getAllChannelGroups()
				val dms = Minchat.client.getAllDMChannels()

				dmsSubBar.dmMap = dms

				channelsContainer.clearChildren()
				for (channel in groups) {
					channelsContainer.add(ChannelGroupElement(this@ChatFragment, channel))
						.growX()
						.row()
				}

				if (currentChannel == null) {
					// try to find and focus the #rules channel if none is selected
					for (group in groups) {
						group.channels.find { "rules" in it.name.lowercase() }?.let {
							currentChannel = it
						}
					}
					if (currentChannel != null) {
						updateChatUi()
					}
				}
			}
		}.then { notif.cancel() }
	}

	/** Tries to find a [ChannelElement] corresponding to the given channel and update it. */
	private fun updateParticularChannel(channel: MinchatChannel) {
		val all = channelsContainer.children + dmsSubBar.children

		all.forEach {
			if (it is ChannelElement && it.channel.id == channel.id) {
				it.channel = channel
			}
		}
	}

	/**
	 * Reloads messages from the server and updates the chat pane.
	 *
	 * @param forcibly if true, even unchanged messages will be updated.
	 * @param fromTimestamp the timestamp of the oldest message to be loaded, or null.
	 * @param toTimestamp the timestamp of the newest message to be loaded, or null.
	 */
	fun updateChatUi(forcibly: Boolean = false, notificationText: String = "Loading messages..."): Job? {
		val channel = currentChannel ?: return null
		val notif = notification(notificationText, 10)

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
				val sameChannel = chatContainer.children.find { it is NormalMessageElement }
					?.let { it as NormalMessageElement }
					?.message?.channelId == currentChannel?.id

				if (forcibly || !sameChannel) {
					// If not, replace everything and play an incrementing move-in animation
					chatContainer.clearChildren()
					messages.forEachIndexed { index, message ->
						addMessage(NormalMessageElement(this@ChatFragment, message),
							animationLength = 0.5f + 0.01f * (messages.size - index))
					}
				} else {
					// Otherwise, immediately remove normal message elements that should not be here anymore
					chatContainer.children.forEach { element ->
						if (element !is NormalMessageElement) return@forEach
						if (messages.none { it.similar(element.message) }) {
							element.animateDisappear(0.5f)
						}
					}
					// Then add all the missing message elements
					messages.forEach { message ->
						if (chatContainer.children.none { it is NormalMessageElement && it.message.similar(message) }) {
							addMessage(NormalMessageElement(this@ChatFragment, message), 1f)
						}
					}
					sortMessageElements()
					// finally, help the table recover from the moral trauma we just gave it
					chatContainer.invalidate()
					chatContainer.validate()
				}

				// force the scroll pane to recalculate its dimensions and scroll to the bottom
				chatPane.validate()
				chatPane.setScrollYForce(chatPane.maxY)

				chatPane.isAtEnd = true
			}
		}.also {
			lastChatUpdateJob = it
		}.then {
			notif.cancel()
		}
	}

	/** Loads more messages either before the oldest or after the newest message. */
	fun loadMoreMessages(before: Boolean, notificationText: String = "Loading more messages..."): Job? {
		val batchSize = 60
		val channel = currentChannel ?: return null

		val messageElements = chatContainer.children.filterIsInstance<AbstractMessageElement>()
		if (messageElements.isEmpty()) return null

		val oldest = messageElements.minOf { it.timestamp }
		val newest = messageElements.maxOf { it.timestamp }
		// A visible element
		val anchorElement = if (before) {
			chatContainer.children.find { it.y < chatContainer.height - chatPane.scrollY + chatPane.height }
		} else {
			chatContainer.children.findLast { it.y > chatContainer.height - chatPane.scrollY }
		} ?: return null

		val notif = notification(notificationText, 10)
		lastChatUpdateJob?.cancel()

		return launch {
			val rawMessages = runSafe {
				channel.getAllMessages(
					if (before) null else newest,
					if (before) oldest else null,
					limit = batchSize
				).take(batchSize).toList()
			}.getOrThrow()

			val messages = rawMessages.filter { msg ->
				messageElements.none { (it as? NormalMessageElement)?.message?.id == msg.id }
			}

			if (channel != currentChannel || messages.isEmpty()) return@launch

			runUi {
				// Remove any old non-message elements from chat container
				val oldChildren = chatContainer.children.filterIsInstance<AbstractMessageElement>()
				val newChildren = messages.map {
					NormalMessageElement(this@ChatFragment, it, true)
				}

				chatContainer.clearChildren() // to remove empty cells

				oldChildren.forEach {
					addMessage(it, 0f, false)
				}
				newChildren.forEachIndexed { index, it ->
					addMessage(it, 0.5f + 0.01f * (newChildren.size - index), false)
				}

				sortMessageElements()

				val anchor = anchorElement.takeIf { it.parent == chatContainer } ?: run {
					logger.warn { "Chat fragment: anchor not found, falling back to last message" }
					if (before) oldChildren.first() else oldChildren.last()
				}

				// Leave only up to 100 messages in the chat, either latest or newest
				val children = chatContainer.cells.mapNotNull { it.get() }.toMutableList()
				if (!before) children.reverse()

				// TODO really broken
//				children.take(100).forEach { it.remove() }

				chatPane.invalidateHierarchy()
				chatPane.validate()

				chatPane.setScrollYForce(chatContainer.height - anchor.y)
				if (before) {
					chatPane.setScrollYForce(chatPane.scrollY - anchor.height)
				}
//				chatPane.isAtEnd = !before && rawMessages.size < batchSize
			}
		}.also {
			lastChatUpdateJob = it
		}.then {
			notif.cancel()
		}
	}

	/** Sorts the cells of [chatContainer] so that messages remain at the end and are sorted by their timestamps. */
	fun sortMessageElements() {
		// (pro-tip: never let silly idiots like me write front-end code)
		chatContainer.cells.sort { cellA, cellB ->
			val a = cellA.get()
			val b = cellB.get()

			when {
				a is AbstractMessageElement && b is AbstractMessageElement -> {
					a.timestamp.compareTo(b.timestamp)
				}
				a is AbstractMessageElement -> 1
				b is AbstractMessageElement -> -1
				else -> 0
			}
		}

		// Recreate chatContainer.children
		chatContainer.children.clear()
		chatContainer.cells.forEach { 
			it.get()?.let { chatContainer.children.add(it) }
		}
	}

	/** Lifecycle function to update the chat field and the send message button. */
	fun updateChatbox() {
		val channel = currentChannel
		val chatDisabled = when {
			!Minchat.client.isLoggedIn -> true
			channel == null -> true
			Minchat.client.self().mute?.isExpired == false -> true
			Minchat.client.self().ban?.isExpired == false -> true
			!Minchat.client.self().canMessageChannel(channel) -> true
			else -> false
		}
		val buttonDisabled = when {
			chatDisabled -> true
			chatField.content.length !in Message.contentLength -> true
			else -> false
		}
		chatField.isDisabled = chatDisabled
		sendButton.isDisabled = buttonDisabled

		chatField.hint = when {
			!Minchat.client.isLoggedIn -> "Log in or register to send messages."
			channel == null -> "Choose a channel to chat in."
			Minchat.client.self().mute?.isExpired == false -> "You are muted. Check your user stats for more details."
			Minchat.client.self().ban?.isExpired == false -> "You are banned. Check your user stats for more details."
			!Minchat.client.self().canMessageChannel(channel) -> "You are not allowed to chat in #${channel.name}."
			else -> "Message #${channel.name}"
		}
	}

	/** Either sends the current message or, if in edit mode, edits the current message. */
	fun confirmCurrentMessage(): Job? {
		if (!Minchat.client.isLoggedIn) return null

		val content = chatField.content.trim()
		val channel = currentChannel ?: return null

		if (!Minchat.client.isLoggedIn) {
			Vars.ui.showInfo("You must login or register to send messages.")
			return null
		}
		if (content.length !in Message.contentLength) {
			Vars.ui.showInfo("Messages must have a length of ${Message.contentLength} characters")
			return null
		}

		runUi { chatField.content = "" }

		if (editListener == null) {
			val notif = notification("Sending...", 1)
			return launch {
				runSafe {
					val msg = channel.createMessage(content, referencedMessage?.id)
					referencedMessage = null
					ClientEvents.fire(ClientMessageSendEvent(msg))
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
	 * Clears the current message and replaces it with the contents of the provided message;
	 * clears [referencedMessage].
	 *
	 * Sets up an edit listener so that when the user presses "send", the message gets edited.
	 */
	fun setEditMessage(message: MinchatMessage?) {
		// TODO editListener is dumb, should use editedMessage instead.
		if (message != null) {
			referencedMessage = null
			editListener = {
				val newMessage = message.edit(newContent = it)
				ClientEvents.fire(ClientMessageEditEvent(message, newMessage))
			}
			chatField.content = message.content
		} else if (editListener != null) {
			editListener = null
			chatField.content = ""
		}
	}

	/**
	 * Sets [referencedMessage] to the given message.
	 * The next sent message will be a reference to it.
	 */
	fun setReplyMessage(message: MinchatMessage?) {
		if (message != null) {
			editListener = null
		}
		referencedMessage = message
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
