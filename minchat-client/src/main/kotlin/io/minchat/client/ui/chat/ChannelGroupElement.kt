package io.minchat.client.ui.chat

import arc.scene.ui.Label
import arc.scene.ui.layout.*
import com.github.mnemotechnician.mkui.extensions.dsl.*
import com.github.mnemotechnician.mkui.extensions.elements.content
import com.github.mnemotechnician.mkui.extensions.groups.child
import com.github.mnemotechnician.mkui.ui.element.ToggleButton
import io.minchat.rest.entity.MinchatChannelGroup
import io.minchat.client.ui.MinchatStyle as Style

class ChannelGroupElement(
	val chat: ChatFragment,
	var group: MinchatChannelGroup
) : Table(Style.surfaceBackground) {
	val toggleButton: ToggleButton
	lateinit var collapser: Collapser
	lateinit var contents: Table

	init {
		toggleButton = textToggle("", Style.ActionToggleButton) {
			// nothing here, for now
		}.growX()
			.pad(Style.layoutPad)
			.margin(Style.buttonMargin)
			.get()

		hider {
			collapser = addCollapser({ toggleButton.isEnabled }) {
				contents = this
				background = Style.surfaceInner

				margin(Style.layoutMargin)
			}.growX().pad(Style.layoutPad).get()
		}
	}

	/** Rebuilds to account for the new channel group object. */
	fun rebuild() {
		toggleButton.child<Label>(0).content = "#" + group.name

		contents.clearChildren()

		for (channel in group.channels) {
			val element = ChannelElement(chat, channel)

			contents.add(element)
				.growX()
		}
	}
}
