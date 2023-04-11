package io.minchat.client.ui.chat

import arc.Core
import arc.input.KeyCode
import arc.math.Interp
import arc.scene.Element
import arc.scene.actions.Actions
import arc.scene.event.*
import arc.scene.ui.layout.Table
import io.minchat.client.Minchat
import mindustry.Vars
import java.time.Instant
import io.minchat.client.misc.MinchatStyle as Style

/**
 * Displays a message in MinChat. It's not guaranteed that a message represented with this class actually exists
 * and/or visible to others.
 */
abstract class MinchatMessageElement(
	val addContextActions: Boolean = true
) : Table(Style.surfaceInner) {
	abstract val timestamp: Long
	/** When a DateTimeFormatter has to be used to acquire a timestamp, the result is saved here. */
	private var cachedLongTimestamp: String? = null

	/** Invoked when this message is right-clicked. */
	abstract fun onRightClick()

	private var longClickBegin = -1L

	init {
		touchable = Touchable.enabled

		addListener(object : InputListener() {
			override fun enter(event: InputEvent?, x: Float, y: Float, pointer: Int, fromActor: Element?): Unit =
				updateBackground()

			override fun exit(event: InputEvent?, x: Float, y: Float, pointer: Int, toActor: Element?) =
				updateBackground()

			override fun mouseMoved(event: InputEvent?, x: Float, y: Float) =
				false.also { updateBackground() }

			override fun touchDown(event: InputEvent?, x: Float, y: Float, pointer: Int, button: KeyCode?): Boolean {
				if (Vars.mobile) {
					updateBackground()
					longClickBegin = System.currentTimeMillis()
					return true
				} else if (button == KeyCode.mouseRight) {
					onRightClick()
					return true
				}
				return false
			}

			override fun touchUp(event: InputEvent?, x: Float, y: Float, pointer: Int, button: KeyCode?) {
				if (Vars.mobile && longClickBegin > 0L) {
					if (System.currentTimeMillis() - longClickBegin > 400L) {
						onRightClick()
						event?.stop() // prevent other intractable elements from getting clicked
					}
					updateBackground()
					longClickBegin = -1L
				}
			}
		})
	}

	/** Formats the timestamp to a user-readable form. */
	protected fun formatTimestamp(): String {
		val minutesSince = (System.currentTimeMillis() - timestamp) / 1000 / 60
		if (minutesSince < 60 * 24) {
			// less than 1 day ago
			return when (minutesSince) {
				0L -> "Just now"
				1L -> "A minute ago"
				in 2L..<60L -> "$minutesSince minutes ago"
				in 60L..<120L -> "An hour ago"
				else -> when {
					minutesSince > 0L -> "${minutesSince / 60} hours ago"
					else -> "In the future"
				}
			}
		}

		// more than 1 day ago. try to return the cached timestamp or create a new one
		cachedLongTimestamp?.let { return it }

		val longTimestamp = Instant.ofEpochMilli(timestamp)
			.atZone(Minchat.timezone)
			.let { Minchat.timestampFormatter.format(it) }

		return longTimestamp.also { cachedLongTimestamp = it }
	}

	/**
	 * Animates this message element by playing a move-in animation.
	 * @param length The length of the animation
	 */
	fun animateMoveIn(length: Float) {
		addAction(Actions.sequence(
			Actions.translateBy(parent.width, 0f),
			Actions.translateBy(-parent.width, 0f, length, Interp.sineOut)
		))
	}

	/**
	 * Animates this message element by playing a shrink animation and removing it..
	 *
	 * @param length The length of the animation
	 */
	fun animateDisappear(length: Float) {
		addAction(Actions.sequence(
			Actions.sizeBy(0f, -height, length),
			Actions.remove()
		))
	}

	fun updateBackground() {
		if (hasMouse()) {
			val isPressed = Core.input.keyDown(KeyCode.mouseRight) || longClickBegin > 0L

			if (isPressed && background != Style.surfaceDown) {
				// Hovered and right- or long-pressed: surface down
				background(Style.surfaceDown)
			} else if (!Core.input.keyDown(KeyCode.mouseRight)) {
				// Hovered, not clicked: surface over
				background(Style.surfaceOver)
			}
		} else if (background != Style.surfaceInner) {
			// Normal: surface inner
			background(Style.surfaceInner)
		}
	}
}
