package io.minchat.client.ui

import arc.Core
import arc.graphics.*
import arc.scene.*
import arc.scene.ui.*
import arc.scene.ui.layout.*
import arc.util.*
import com.github.mnemotechnician.mkui.extensions.*
import com.github.mnemotechnician.mkui.extensions.dsl.*
import com.github.mnemotechnician.mkui.extensions.groups.*
import io.minchat.client.*
import io.minchat.rest.*
import io.minchat.rest.entity.*
import java.util.concurrent.ConcurrentLinkedQueue
import mindustry.Vars
import mindustry.ui.Styles
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

class ChatFragment(context: CoroutineContext) : Fragment<Table, Table>(context) {
	@Volatile var currentChannel: MinchatChannel? = null

	val notificationStack = ConcurrentLinkedQueue<Notification>()

	lateinit var channelsBar: Table
	lateinit var channelsContainer: Table
	lateinit var chatBar: Table
	lateinit var chatContainer: Table

	override fun build() = createTable(Styles.black3) {
		update(::tick)

		// top bar
		addStack {
			// the bar itself bar: minchat label, channel name, account button
			addTable {
				setFillParent(true)

				addLabel("MinChat").marginRight(8f)

				// channel name
				addLabel({
					currentChannel?.let { "#${it.name}" } ?: "Choose a channel"
				}, ellipsis = "...").minWidth(150f).growX()

				// account button
				textButton({
					Minchat.client.account?.user?.tag ?: "Not logged in"
				}, align = Align.right, ellipsis = "...") {
					Vars.ui.showInfo("TODO")
				}.minWidth(120f)
			}
			// notification label
			this + Label({ notificationStack.peek()?.content ?: "" }).apply {
				setColor(Color.red)
				setAlignment(Align.center)
			}
		}.growX().colspan(3).row()

		hsplitter().colspan(3)

		// left bar: channel list + notification labeo
		addTable {
			channelsBar = this

			addLabel("Channels", align = Align.left).growX().row()
			limitedScrollPane {
				it.isScrollingDisabledX = true
				channelsContainer = this
			}.grow()
		}.width(150f).minHeight(300f).growY()

		vsplitter()

		// right bar: chat
		addTable {
			chatBar = this
			limitedScrollPane {
				it.isScrollingDisabledX = true
				addLabel("this is a WIP chat ui")
				chatContainer = this
			}.grow()
		}.grow()
	}

	fun tick() {
		if (notificationStack.isNotEmpty() && notificationStack.first().shownUntil <= System.currentTimeMillis()) {
			notificationStack.remove()
		}
	}

	/**
	 * Show a notification on the top bar.
	 * [maxTime] defines that maximum time it will be shown for (in seconds).
	 */
	fun notification(text: String, maxTime: Long) = 
		Notification(text, System.currentTimeMillis() + maxTime * 1000)
			.also { notificationStack.add(it) }

	override fun applied(cell: Cell<Table>) {
		cell.grow()

		val notif = notification("Loading channels...", 10)
		launch {
			val channels = Minchat.client.getAllChannels()
			runUi { setChannels(channels) }
			notif.cancel()
		}
	}

	fun setChannels(channels: List<MinchatChannel>) {
		channelsContainer.clearChildren()
		
		channels.forEach { channel ->
			channelsContainer.textButton("#${channel.name}") {
				currentChannel = channel
				updateChatUi()
			}.align(Align.left).row()
		}
	}

	fun updateChatUi() {
		val channel = currentChannel ?: return
		val notif = notification("Loading messages...", 10)

		launch {
			val messages = channel.getAllMessages(limit = 60).toList()
			notif.cancel()
			// if the channel was changed, return
			if (channel != currentChannel) return@launch

			runUi {
				chatContainer.clearChildren()

				messages.forEach { message ->
					chatContainer.addTable {
						addLabel(message.author.username, ellipsis = "...").width(100f)
						addLabels(
							"#",
							message.author.discriminator.toString().padStart(4, '0'),
							": "
						)
						addLabel(message.content, wrap = true).growX()
					}.marginBottom(10f).growX().row()
				}
			}
		}
	}

	data class Notification(
		val content: String, 
		@Volatile var shownUntil: Long
	) {
		fun cancel() {
			shownUntil = 0
		}
	}
}
