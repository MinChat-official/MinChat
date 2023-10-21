package io.minchat.client.misc

import arc.scene.style.*
import arc.scene.ui.*
import arc.scene.ui.layout.*
import io.minchat.common.entity.User.RoleBitSet.Masks
import io.minchat.rest.entity.MinchatUser
import mindustry.ui.Styles
import io.minchat.client.ui.MinchatStyle as Style

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

/** Returns an icon for this user's role, or null if it has no icon. */
fun MinchatUser.getIcon(): TextureRegionDrawable? {
	return when {
		role.get(Masks.admin) -> Style.adminIcon
		role.get(Masks.moderator) -> Style.moderatorIcon
		else -> null
	}
}

/** Makes it so that the button only gets enabled when all of the specified fields are valid. */
fun Button.enabledWhenValid(vararg fields: TextField) {
	setDisabled {
		!fields.all { it.isValid }
	}
}
