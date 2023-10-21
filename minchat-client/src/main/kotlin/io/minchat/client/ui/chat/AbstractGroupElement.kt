package io.minchat.client.ui.chat

import arc.scene.style.Drawable
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
	background: Drawable = Style.surfaceBackground
) : Table(background) {
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
	 * This button must call [toggle] in its onClick listener.
	 */
	open fun createToggleButton(): Cell<ToggleButton> {
		return textToggle("", Style.ActionToggleButton) {
			toggle(it)
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
			rebuildContentsInternal()
		}
	}

	/** Rebuilds everything. Called on first validate call. */
	protected open fun rebuild() {
		clearChildren()

		createToggleButton()
			.growX()
			.pad(Style.layoutPad)
			.margin(Style.layoutMargin)

		addCollapser(false, Style.surfaceInner) {
			contents = this
		}.also {
			collapser = it.get()
		}

		contents.rebuildContentsInternal()
	}

	fun toggle(shown: Boolean = !toggleButton.isEnabled) {
		toggleButton.isEnabled = shown

		collapser.setCollapsed(shown)
	}

	override fun validate() {
		if (!isBuilt) {
			isBuilt = true
			rebuild()
		}

		super.validate()
	}
}
