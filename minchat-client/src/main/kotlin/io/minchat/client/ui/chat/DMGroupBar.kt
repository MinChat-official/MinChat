package io.minchat.client.ui.chat

import arc.scene.ui.Label
import arc.scene.ui.layout.Table
import com.github.mnemotechnician.mkui.extensions.dsl.*
import com.github.mnemotechnician.mkui.extensions.elements.content
import com.github.mnemotechnician.mkui.extensions.groups.child
import com.github.mnemotechnician.mkui.extensions.runUi
import io.minchat.client.Minchat
import io.minchat.client.misc.addMinTable
import io.minchat.rest.entity.MinchatChannel
import kotlinx.coroutines.*
import io.minchat.client.ui.MinchatStyle as Style

class DMGroupBar(
	val chat: ChatFragment,
	dmMap: Map<Long, List<MinchatChannel>>
) : AbstractGroupElement(), CoroutineScope by chat {
	var dmMap = dmMap
		set(value) {
			field = value
			rebuildContents()
		}

	override fun Table.rebuildContentsInternal() {
		toggleButton.child<Label>(0).content = "Direct channels"

		for ((userId, channels) in dmMap) {
			// DM group
			addTable {
				vsplitter(padLeft = 0f, padRight = Style.layoutPad)

				addTable {
					val nameLabel = addLabel("Loading user name...", Style.Label)
						.growX().get()

					// Channel list
					addMinTable {
						for (channel in channels) {
							add(ChannelElement(chat, channel))
								.growX()
								.row()
						}
					}.growX()

					launch {
						val user = Minchat.client.getUserOrNull(userId)

						runUi {
							nameLabel.content = user?.displayTag ?: "<unknown user>"
						}
					}
				}
			}.pad(Style.layoutPad).margin(Style.layoutMargin)
				.growX()
		}

		if (dmMap.isEmpty()) {
			addLabel("<no DMs>", Style.Label)
		}
	}
}
