package io.minchat.client.ui.chat

import arc.math.Mathf
import arc.scene.ui.TextArea
import arc.util.Time
import io.minchat.client.misc.MinchatStyle
import kotlin.math.max

class ChatTextArea : TextArea("", MinchatStyle.TextInput) {
	private var lastPrefHeight = -1f

	override fun act(delta: Float) {
		super.act(delta)

		val prefHeight = prefHeight
		if (prefHeight != lastPrefHeight) {
			if (lastPrefHeight != -1f) invalidateHierarchy()
			lastPrefHeight = prefHeight
		}
	}

	override fun getPrefHeight(): Float {
		val rowsHeight = Mathf.clamp(lines + 1, 2, 20) * textHeight
		val requiredHeight = max(
			style.background.bottomHeight + style.background.topHeight,
			style.background.minHeight
		)
		return rowsHeight + requiredHeight
	}

	override fun change() {
		// to recalculate pref height and prevent the input from being scrolled if multiple lines were added at the end
		calculateOffsets()
		act(Time.delta)
		sizeChanged()

		super.change()
	}

	override fun sizeChanged() {
		super.sizeChanged()

		if (lines <= linesShowing) {
			firstLineShowing = 0
		}
	}
}
