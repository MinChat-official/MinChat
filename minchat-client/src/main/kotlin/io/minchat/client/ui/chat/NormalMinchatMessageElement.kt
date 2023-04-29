package io.minchat.client.ui.chat

import arc.Core
import arc.input.KeyCode
import arc.scene.Element
import arc.scene.event.*
import arc.scene.ui.Label
import arc.scene.ui.layout.Table
import arc.util.Align
import com.github.mnemotechnician.mkui.extensions.dsl.*
import com.github.mnemotechnician.mkui.extensions.elements.content
import io.minchat.client.Minchat
import io.minchat.client.ui.dialog.*
import io.minchat.rest.entity.MinchatMessage
import kotlinx.coroutines.CoroutineScope
import mindustry.Vars
import java.time.Instant
import io.minchat.client.misc.MinchatStyle as Style

/**
 * Displays a MinChat message sent by a real user or a bot.
 */
class NormalMinchatMessageElement(
	val chat: ChatFragment,
	message: MinchatMessage?,
	val addContextActions: Boolean = true
) : Table(Style.surfaceInner), CoroutineScope by chat {
	var message = message
		set(value) {
			field = value
			cachedLongTimestamp = null
			updateDisplay()
		}

	val timestamp get() = message?.timestamp ?: System.currentTimeMillis()
	val prettyDiscriminator get() =
		(message?.author?.discriminator ?: 0).toString().padStart(4, '0')

	/** When a DateTimeFormatter has to be used to acquire a timestamp, the result is saved here. */
	private var cachedLongTimestamp: String? = null

	private var longClickBegin = -1L
	private var tempDisableLayout = false

	lateinit var nameLabel: Label
	lateinit var tagLabel: Label
	lateinit var timestampLabel: Label
	lateinit var contentLabel: Label

	init {
		margin(4f)

		touchable = Touchable.enabled

		addCaptureListener(object : InputListener() {
			override fun enter(event: InputEvent?, x: Float, y: Float, pointer: Int, fromActor: Element?): Unit =
				updateBackground()

			override fun exit(event: InputEvent?, x: Float, y: Float, pointer: Int, toActor: Element?) =
				updateBackground()

			override fun mouseMoved(event: InputEvent?, x: Float, y: Float) =
				false.also { updateBackground() }

			override fun touchDown(event: InputEvent?, x: Float, y: Float, pointer: Int, button: KeyCode?): Boolean {
				if (!addContextActions) return false

				if (Vars.mobile) {
					updateBackground()
					longClickBegin = System.currentTimeMillis()
					return true
				} else if (button == KeyCode.mouseRight) {
					showContextMenu()
					return true
				}
				return false
			}

			override fun touchUp(event: InputEvent?, x: Float, y: Float, pointer: Int, button: KeyCode?) {
				if (!addContextActions) return

				if (Vars.mobile && longClickBegin > 0L) {
					if (System.currentTimeMillis() - longClickBegin > 400L) {
						showContextMenu()
						event?.stop() // prevent other intractable elements from getting clicked
					}
					updateBackground()
					longClickBegin = -1L
				}
			}
		})



		// Top row: author tag + timestamp
		addTable {
			left()
			// display name
			addLabel("", ellipsis = "...")
				.fillY().also {
					it.get().clicked(::showUserDialog)
					nameLabel = it.get()
				}
			// tag
			addLabel("")
				.fillY().color(Style.comment)
				.also {
					it.get().clicked(::showUserDialog)
					tagLabel = it.get()
				}
			// timestamp
			addLabel({ formatTimestamp() }, ellipsis = "...", align = Align.right)
				.growX()
				.color(Style.comment).padLeft(20f)
				.also {
					timestampLabel = it.get()
				}
		}.growX().padBottom(5f).row()

		// bottom row: message content
		addLabel("", wrap = true, align = Align.left)
			.growX().color(Style.foreground)

		updateDisplay()
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

//	/**
//	 * Animates this message element by playing a move-in animation.
//	 * @param length The length of the animation
//	 */
//	fun animateMoveIn(length: Float) {
//		addAction(Actions.sequence(
//			Actions.translateBy(parent.width, 0f),
//			Actions.translateBy(-parent.width, 0f, length, Interp.sineOut)
//		))
//	}
//
//	/**
//	 * Animates this message element by playing a shrink animation and removing it.
//	 *
//	 * If this animation is interrupted, this element is likely to become unusable.
//	 *
//	 * @param length The length of the animation
//	 */
//	fun animateDisappear(length: Float) {
//		val clipWasEnabled = clip
//		clip = true
//		tempDisableLayout = true
//
//		addAction(Actions.sequence(
//			Actions.sizeBy(0f, -height, length),
//			Actions.remove(),
//			Actions.run {
//				tempDisableLayout = false
//				clip = clipWasEnabled
//			}
//		))
//	}

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

	override fun layout() {
		// Do not lay out if the message is currently disappearing.
		if (tempDisableLayout) {
			return
		}
		super.layout()
	}

	fun updateDisplay() {
		val message = message ?: return

		nameLabel.content = message.author.displayName
		nameLabel.setColor(when {
			message.author.id == Minchat.client.account?.id -> Style.green
			message.author.isAdmin -> Style.pink // I just like pink~
			else -> Style.purple
		})
		tagLabel.content = message.author.tag
		contentLabel.content = message.content
	}

	fun showUserDialog() {
		val message = message ?: return

		UserDialog(message.author).apply {
			show()
			update()
		}
	}

	fun showContextMenu() {
		message?.let {
			MessageContextMenu(chat, it).show()
		}
	}
}
