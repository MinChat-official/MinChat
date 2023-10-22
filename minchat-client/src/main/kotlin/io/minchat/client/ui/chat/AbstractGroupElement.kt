package io.minchat.client.ui.chat

import arc.scene.ui.layout.*
import com.github.mnemotechnician.mkui.extensions.dsl.*
import com.github.mnemotechnician.mkui.ui.element.ToggleButton
import io.minchat.client.ui.MinchatStyle as Style

/**
 * A lazily-built group element.
 *
 * Consists of a toggle button and a collapser.
 *
 * The collapser shows up when the toggle button is activated.
 * Its contents are created when it's shown for the first time.
 */
abstract class AbstractGroupElement(
	val defaultShown: Boolean = true
) : Table() {
	lateinit var toggleButton: ToggleButton
	lateinit var collapser: Collapser
	lateinit var contents: Table

	private var isBuilt = false

	init {
		margin(Style.layoutMargin)
	}

	/**
	 * Must add a toggle button to this table and returns its cell.
	 *
	 * By default this function creates a toggle button with a single empty label in it.
	 *
	 * This button must call [toggleGroup] in its onClick listener.
	 */
	open fun createToggleButton(): Cell<ToggleButton> {
		return textToggle("", Style.InnerActionToggleButton) {
			toggleGroup(it)
		}
	}

	/**
	 * Should clear the children of the [contents] table and rebuild it;
	 * optionally may change the contents of [toggleButton] if [createToggleButton] is not overridden.
	 *
	 * This function MUST NOT be called directly, use [rebuildContents] instead.
	 */
	abstract protected fun Table.rebuildContentsInternal()

	/** Rebuilds contents if possible (the group is already built). */
	fun rebuildContents() {
		if (isBuilt) {
			contents.clearChildren()
			contents.rebuildContentsInternal()
		}
	}

	/** Rebuilds everything. Called on first validate call. */
	protected open fun rebuild() {
		clearChildren()

		createToggleButton()
			.growX()
			.margin(Style.buttonMargin)
			.also { toggleButton = it.get() }
			.row()

		addCollapser(defaultShown, Style.surfaceInner) {
			addTable {
				margin(Style.layoutMargin)
				contents = this
			}.grow()
		}.also {
			collapser = it.get()
		}.growX()

		contents.rebuildContentsInternal()

		toggleButton.isEnabled = defaultShown
	}

	/** Toggles the state of this group. Does NOT toggle [toggleButton] as that will lead to an infinite recursion. */
	fun toggleGroup(shown: Boolean = toggleButton.isEnabled) {
		collapser.setCollapsed(!shown)
	}

	override fun validate() {
		if (!isBuilt) {
			isBuilt = true
			rebuild()
		}

		super.validate()
	}
}
