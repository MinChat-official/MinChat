package io.minchat.client.ui.chat

import arc.math.Mathf
import arc.scene.ui.TextArea
import arc.util.Time
import com.github.mnemotechnician.mkui.extensions.elements.content
import io.minchat.client.ui.MinchatStyle
import mindustry.ui.Fonts
import kotlin.math.max

/** Match :emoji_name: unless it's prefixed by \. */
private val emojiRegex = "(?<!\\\\):([a-zA-Z_0-9-]{0,30}):".toRegex()

class ChatTextArea : TextArea("", MinchatStyle.TextInput) {
	private var lastPrefHeight = -1f
	private var textHidden = false

	init {
		// Desktop-only emoji handler
		typed { handleType(it) }
	}

	override fun act(delta: Float) {
		super.act(delta)

		val prefHeight = prefHeight
		if (prefHeight != lastPrefHeight) {
			if (lastPrefHeight != -1f) invalidateHierarchy()
			lastPrefHeight = prefHeight
		}
	}

	override fun draw() {
		if (textHidden) {
			if (isDisabled) {
				displayText = ""
			} else {
				updateDisplayText()
				textHidden = false
			}
		} else if (isDisabled) {
			displayText = ""
			textHidden = true
		}

		super.draw()
	}

	override fun getPrefHeight(): Float {
		val rowsHeight = Mathf.clamp(lines,1, 20) * textHeight
		val requiredHeight = max(
			style.background.bottomHeight + style.background.topHeight,
			style.background.minHeight
		)
		return rowsHeight + requiredHeight
	}

	override fun paste(content: String, fireChangeEvent: Boolean) {
		super.paste(handleEmojiText(content), fireChangeEvent)
	}

	override fun setText(str: String) {
		super.setText(handleEmojiText(str))
	}

	/** Transforms all emojis in the text into actual emojis. */
	private fun handleEmojiText(str: String): String {
		return if (emojiRegex in str) buildString {
			var match = emojiRegex.find(str)
			var lastMatchPos = -1
			while (match != null) {
				val (emojiName) = match.destructured

				// Append everything between last match pos and the beginning of this match
				append(str.substring((lastMatchPos + 1)..<match.range.start))

				// Try to look up the emoji and append it
				val uni = Fonts.getUnicodeStr(emojiName)
				if (uni != null && uni.length > 0) {
					append(uni)
				}

				lastMatchPos = match.range.endInclusive
				match = match.next()
			}

			// Append everything after the last match
			append(str.drop(lastMatchPos + 1))
		} else {
			str
		}
	}

	/** Desktop-only emoji handler - borrowed from mindustry. */
	private fun handleType(char: Char) {
		val cursor = cursorPosition

		if (char == ':') {
			val startIndex = text.lastIndexOf(':', cursor - 2)
			if (startIndex >= 0 && startIndex < cursor) {
				val emojiText = text.substring(startIndex + 1, cursor - 1)
				val uni = Fonts.getUnicodeStr(emojiText)
				if (uni != null && uni.length > 0) {
					content = text.substring(0, startIndex) + uni + text.substring(cursor)
					setCursorPosition(startIndex + uni.length)
				}
			}
		}
	}

	override fun change() {
		// to recalculate pref height and prevent the input from being scrolled if multiple lines were added at the end
		calculateOffsets()
		act(Time.delta)
		sizeChanged()

		super.change()
	}

	override fun sizeChanged() {
		super.sizeChanged()

		if (lines <= linesShowing) {
			firstLineShowing = 0
		}
	}
}
