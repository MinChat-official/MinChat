package io.minchat.client.ui.chat

import arc.graphics.Color
import arc.scene.ui.Label
import arc.scene.ui.layout.Table
import com.github.mnemotechnician.mkui.extensions.dsl.addLabel
import com.github.mnemotechnician.mkui.extensions.elements.content
import com.github.mnemotechnician.mkui.extensions.groups.child
import io.minchat.client.ui.managers.hasUnreads
import io.minchat.rest.entity.MinchatChannelGroup
import io.minchat.client.ui.MinchatStyle as Style

class ChannelGroupElement(
	val chat: ChatFragment,
	group: MinchatChannelGroup
) : AbstractGroupElement() {
	var group = group
		set(value) {
			field = value
			rebuildContents()
		}

	/** Rebuilds to account for the new channel group object. */
	override fun Table.rebuildContentsInternal() {
		toggleButton.child<Label>(0).content = "#" + group.name

		clearChildren()

		for (channel in group.channels) {
			add(ChannelElement(chat, channel))
				.growX()
				.row()
		}

		if (group.channels.isEmpty()) {
			addLabel("<No channels>", Style.Label)
		}
	}

	override fun act(delta: Float) {
		// If there are unread channels, the group itself should be marked as unread too.
		if (isBuilt) {
			val label = toggleButton.child<Label>(0)
			if (group.channels.any { it.hasUnreads() }) {
				label.setColor(Color.white)
			} else {
				label.setColor(Color.lightGray)
			}
		}

		super.act(delta)
	}
}
