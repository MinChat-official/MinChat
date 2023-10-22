package io.minchat.client.ui.chat

import arc.scene.ui.Label
import arc.scene.ui.layout.Table
import arc.util.Align
import com.github.mnemotechnician.mkui.extensions.dsl.*
import com.github.mnemotechnician.mkui.extensions.elements.content
import com.github.mnemotechnician.mkui.extensions.groups.child
import com.github.mnemotechnician.mkui.extensions.runUi
import io.minchat.client.Minchat
import io.minchat.client.misc.addMinTable
import io.minchat.client.ui.tutorial.Tutorials
import io.minchat.rest.entity.MinchatChannel
import kotlinx.coroutines.*
import io.minchat.client.ui.MinchatStyle as Style

class DMGroupBar(
	val chat: ChatFragment,
	dmMap: Map<Long, List<MinchatChannel>>
) : AbstractGroupElement(false), CoroutineScope by chat {
	var dmMap = dmMap
		set(value) {
			field = value
			rebuildContents()
		}

	override fun toggleGroup(shown: Boolean) {
		super.toggleGroup(shown)

		if (shown) Tutorials.directMessages.trigger()
	}

	override fun Table.rebuildContentsInternal() {
		toggleButton.child<Label>(0).content = "Direct channels"

		limitedScrollPane(limitH = false) {
			it.isScrollingDisabledX = true

			for ((userId, channels) in dmMap) {
				// DM group
				addTable {
					left()
					vsplitter(padLeft = 0f, padRight = Style.layoutPad, color = Style.comment)

					addTable {
						left()

						lateinit var nameLabel: Label
						addMinTable {
							addLabel("Loading...", Style.Label, align = Align.left)
								.left()
								.color(Style.comment)
								.growX()
								.also { nameLabel = it.get() }
						}.growX().row()

						// Channel list
						addMinTable {
							left()
							for (channel in channels) {
								add(ChannelElement(chat, channel))
									.growX()
									.row()
							}
						}.growX()

						launch {
							val user = Minchat.client.cache.getUser(userId)

							runUi {
								nameLabel.content = user?.displayTag ?: "<unknown user>"
							}
						}
					}.grow()
				}.pad(Style.layoutPad)
					.margin(Style.layoutMargin)
					.growX()
					.row()
			}
		}.grow().row()

		if (dmMap.isEmpty()) {
			addLabel("<no DMs>", Style.Label)
		}
	}
}
