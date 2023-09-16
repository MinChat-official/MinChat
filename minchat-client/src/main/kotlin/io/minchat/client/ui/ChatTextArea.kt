package io.minchat.client.ui

import arc.graphics.g2d.*
import arc.math.Mathf
import arc.scene.style.Drawable
import arc.scene.ui.TextArea
import io.minchat.client.misc.MinchatStyle
import kotlin.math.max

/** Similar to TextArea but optimized for MinChat's needs. */
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

	override fun drawSelection(selection: Drawable?, font: Font?, x: Float, y: Float) {
		Draw.alpha(1f)
		super.drawSelection(selection, font, x, y)
	}

	override fun drawCursor(cursorPatch: Drawable?, font: Font?, x: Float, y: Float) {
		Draw.alpha(1f)
		super.drawCursor(cursorPatch, font, x, y)
	}
}
