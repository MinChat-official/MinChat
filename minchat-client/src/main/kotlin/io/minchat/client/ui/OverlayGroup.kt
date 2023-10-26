package io.minchat.client.ui

import arc.scene.Element
import arc.scene.ui.layout.*
import arc.util.Align
import io.minchat.client.ui.OverlayGroup.OverlayDirection.*
import io.minchat.client.ui.MinchatStyle as Style

/**
 * A WidgetGroup that adds an overlay above its primary child.
 *
 * It's adviced to use [OverlayGroup.applyTo] instead of creating this element manually.
 *
 * @param target the element to display this group above.
 */
class OverlayGroup<T : Element>(
	val target: T
) : WidgetGroup() {
	val overlays get() = children.asSequence().filter { it != target }

	var direction = TOP
	var border = Style.layoutPad
	var clip = true

	/** If true, overlays will fill all width available to them. */
	var overlayFillsWidth = true
	/** If true, the target will fill all width available to it. */
	var targetFillsHeight = true
	/** If true, overlays will fill all height available to them. */
	var overlayFillsHeight = true
	/** If true, the target will fill all height available to it. */
	var targetFillsWidth = true

	init {
		addChild(target)

		isTransform = true
	}

	override fun getPrefWidth(): Float = when (direction) {
		TOP, BOTTOM -> children.maxOf { it.prefWidth }
	}

	override fun getPrefHeight(): Float = when (direction) {
		TOP, BOTTOM -> target.prefHeight
	}

	override fun layout() {
		val maxW = when (direction) {
			TOP, BOTTOM -> children.maxOf { it.prefWidth }.coerceAtLeast(width)
			// LEFT, RIGHT -> (overlays.maxOf { it.prefWidth } + target.prefWidth).coerceAtLeast(width)
		}
		val maxH = when (direction) {
			TOP, BOTTOM -> (overlays.maxOf { it.prefHeight } + target.prefHeight).coerceAtLeast(height)
			// LEFT, RIGHT -> children.maxOf { it.prefHeight }.coerceAtLeast(height)
		}
		val overlaysW = overlays.maxOf { it.prefWidth }
		val overlaysH = overlays.maxOf { it.prefHeight }

		// First make children layout themselves
		children.forEach {
			it.validate()
		}

		// Always place the main element first and at (center, 0)
		target.setPosition(0f, 0f)
		layoutTarget(maxW, maxH, overlaysW, overlaysH, target)
		when (direction) {
			TOP, BOTTOM -> target.setPosition((maxW - target.width) / 2, 0f, Align.bottomLeft)
			// LEFT, RIGHT -> target.setPosition(0f, (maxH - target.height) / 2, Align.bottomLeft)
		}

		when (direction) {
			TOP -> {
				// Overlays go above the target
				for (overlay in overlays) {
					layoutOverlay(maxW, overlaysH, overlay.prefWidth, overlay.prefHeight, overlay)
					overlay.setPosition((maxW - overlay.width) / 2, target.height + border, Align.bottomLeft)
				}
			}
			BOTTOM -> {
				// Overlays go under the target
				for (overlay in overlays) {
					layoutOverlay(maxW, overlaysH, overlay.prefWidth, overlay.prefHeight, overlay)
					overlay.setPosition((maxW - overlay.width) / 2, -border, Align.topLeft)
				}
			}
		}
	}

	private fun layoutOverlay(maxW: Float, maxH: Float, pw: Float, ph: Float, overlay: Element) {
		val w = when (overlayFillsWidth) {
			true -> maxW
			false -> pw
		}
		val h = when (overlayFillsHeight) {
			true -> maxH
			false -> ph
		}

		overlay.setSize(w, h)
		overlay.validate()
	}

	private fun layoutTarget(maxW: Float, maxH: Float, overlaysW: Float, overlaysH: Float, target: Element) {
		val w = when (targetFillsWidth) {
			true -> when (direction) {
				TOP, BOTTOM -> maxW
				// LEFT, RIGHT -> maxW - overlaysW
			}
			false -> target.prefWidth
		}
		val h = when (targetFillsHeight) {
			true -> when (direction) {
				TOP, BOTTOM -> maxH - overlaysH
				// LEFT, RIGHT -> maxH
			}
			false -> target.prefHeight
		}

		target.setSize(w, h)
		target.validate()
	}

	companion object {
		/**
		 * Adds an overlay group to the provided element.
		 *
		 * This function may modify the hierarchy of ui elements
		 * by replacing the provided element with itself and wrapping it.
		 *
		 * @throws IllegalArgumentException if the provided element has no parent.
		 */
		fun <T : Element> applyTo(element: T): OverlayGroup<T> {
			val parent = element.parent

			when {
				parent == null -> throw IllegalArgumentException("$element has no parent")

				parent is OverlayGroup<*> -> throw IllegalArgumentException("$element is already a child of an OverlayGroup")

				parent is Table -> {
					val cell = parent.cells.find { it.get() == element }
					           ?: throw IllegalArgumentException("$element is not a valid child of $parent")
					val group = OverlayGroup(element)

					cell.setElement(group)
					parent.invalidate()
					return group
				}

				else -> {
					element.remove()
					val group = OverlayGroup(element)
					parent.addChild(group)

					if (element.fillParent) group.setFillParent(true)

					return group
				}
			}
		}

		/** Creates an OverlayGroup with Table content. */
		inline operator fun invoke(
			overlayBuilder: Table.() -> Unit,
			contentBuilder: Table.() -> Unit,
			block: OverlayGroup<Table>.() -> Unit = {}
		): OverlayGroup<Table> {
			val overlay = Table()
			val content = Table()
			val group = OverlayGroup(content).apply {
				addChild(overlay)
			}
			overlayBuilder(overlay)
			contentBuilder(content)
			block(group)

			return group
		}
	}

	enum class OverlayDirection {
		TOP,
		BOTTOM, // me :3
		// Unsupported.
		// LEFT,
		// Unsupported.
		// RIGHT
	}
}
