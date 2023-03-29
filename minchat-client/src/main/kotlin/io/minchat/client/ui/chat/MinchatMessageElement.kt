package io.minchat.client.ui.chat

import arc.math.Interp
import arc.scene.actions.Actions
import arc.scene.ui.layout.Table
import io.minchat.client.Minchat
import io.minchat.client.misc.MinchatStyle
import java.time.Instant

/**
 * Displays a message in MinChat. It's not guaranteed that a message represented with this class actually exists
 * and/or visible to others.
 */
abstract class MinchatMessageElement : Table(MinchatStyle.surfaceInner) {
	abstract val timestamp: Long
	/** When a DateTimeFormatter has to be used to acquire a timestamp, the result is saved here. */
	private var cachedLongTimestamp: String? = null

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
}
