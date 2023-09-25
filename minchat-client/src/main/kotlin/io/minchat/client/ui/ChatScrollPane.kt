package io.minchat.client.ui

import arc.scene.ui.ScrollPane
import arc.scene.ui.layout.Table
import mindustry.ui.Styles

/** Similar to ScrollPane but optimized for MinChat's needs. */
class ChatScrollPane() : ScrollPane(Table(), Styles.defaultPane) {
	private var oldHeight = 0f

	constructor(block: Table.(ChatScrollPane) -> Unit) : this() {
		block(widget as Table, this)
	}

	override fun sizeChanged() {
		super.sizeChanged()

		val heightDiff = height - oldHeight
		oldHeight = height

		setScrollYForce(scrollY - heightDiff)
	}

	override fun getPrefHeight(): Float {
		return height
	}

	override fun getPrefWidth(): Float {
		return width
	}
}
