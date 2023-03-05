package io.minchat.client.ui

import arc.Core
import arc.graphics.*
import arc.scene.*
import arc.scene.ui.*
import arc.scene.ui.layout.*
import arc.util.*
import com.github.mnemotechnician.mkui.extensions.dsl.*
import com.github.mnemotechnician.mkui.extensions.groups.*
import io.minchat.client.*
import io.minchat.rest.*
import io.minchat.rest.entity.*
import java.util.concurrent.ConcurrentLinkedQueue
import mindustry.ui.Styles
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.*

class ChatFragment(context: CoroutineContext) : Fragment<Table, Table>(context) {
	@Volatile var currentChannel: MinchatChannel? = null

	val notificationStack = ConcurrentLinkedQueue<Notification>()

	lateinit var channelsBar: Table
	lateinit var channelsContainer: Table
	lateinit var chatBar: Table

	override fun build() = createTable(Styles.black3) {
		update(::tick)

		// top bar
		addStack {
			// the bar itself bar: minchat label and channel name
			addTable {
				addLabel("MinChat").marginRight(4f)
				addLabel({
					currentChannel?.let { "#${it.name}" } ?: "Choose a channel!"
				}, ellipsis = "...").growX()
			}
			// notification label
			this + Label({ notificationStack.peek()?.content ?: "" }).apply {
				setColor(Color.red)
				setAlignment(Align.center)
			}
		}.colspan(2).row()

		// left bar: channel list + notification labeo
		addTable {
			channelsBar = this

			addLabel("Channels")
			limitedScrollPane {
				channelsContainer = this
			}.growX()
		}.width(150f).minHeight(300f).growY()

		// right bar: chat
		addTable {
			chatBar = this
			addLabel("this is a WIP chat ui")
		}.grow()
	}

	fun tick() {
		if (notificationStack.isNotEmpty() && notificationStack.first().shownUntil <= System.currentTimeMillis()) {
			notificationStack.remove()
		}
	}

	fun notification(text: String, maxTime: Long) = 
		Notification(text, System.currentTimeMillis() + maxTime)
			.also { notificationStack.add(it) }

	override fun applied(cell: Cell<Table>) {
		cell.grow()

		val notif = notification("Loading channels...", 10)
		launch {
			val channels = Minchat.client.getAllChannels()
			Core.app.post { setChannels(channels) }
			notif.cancel()
		}
	}

	fun setChannels(channels: List<MinchatChannel>) {
		channelsContainer.clearChildren()
		
		channels.forEach { channel ->
			channelsContainer.textButton("#${channel.name}") {
				currentChannel = channel
				updateChatUi()
			}.align(Align.left)
		}
	}

	fun updateChatUi() {

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
