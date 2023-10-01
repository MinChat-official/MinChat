package io.minchat.client.misc

import arc.scene.style.Drawable
import arc.scene.ui.layout.*
import mindustry.ui.Styles

/** Adds a table whose min size can be provided by the caller (0 by default). */
inline fun Table.addMinTable(
	background: Drawable = Styles.none,
	minWidth: Float = 0f,
	minHeight: Float = 0f,
	constructor: Table.() -> Unit = {}
): Cell<Table> {
	val table = object : Table(background) {
		override fun getMinWidth() = minWidth
		override fun getMinHeight() = minHeight
	}

	return add(table.also {
		it.constructor()
	})
}

/** Adds a table whose min size is 0 and pref size can be provided by the caller. */
inline fun Table.addMinSizedTable(
	prefWidth: Float,
	prefHeight: Float,
	background: Drawable = Styles.none,
	constructor: Table.() -> Unit = {}
): Cell<Table> {
	val table = object : Table(background) {
		override fun getMinWidth() = 0f
		override fun getMinHeight() = 0f
		override fun getPrefWidth() = prefWidth
		override fun getPrefHeight() = prefHeight
	}

	return add(table.also {
		it.constructor()
	})
}
