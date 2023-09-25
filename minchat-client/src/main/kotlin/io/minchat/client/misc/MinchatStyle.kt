package io.minchat.client.misc

import arc.graphics.Color
import arc.scene.style.*
import arc.scene.ui.*
import com.github.mnemotechnician.mkui.extensions.times
import mindustry.gen.Tex
import mindustry.ui.Styles
import arc.scene.ui.Label as MindustryLabel

object MinchatStyle {
	val layoutMargin = 5f
	val buttonMargin = 10f
	val layoutPad = 4f

	// darcula theme specs
	val background = Color.valueOf("282A36")
	val foreground = Color.valueOf("F8F8F2")
	val comment = Color.valueOf("6272A4")
	val red = Color.valueOf("FF5555")
	val orange = Color.valueOf("FFB86C")
	val yellow = Color.valueOf("F1FA8C")
	val green = Color.valueOf("50FA7B")
	val purple = Color.valueOf("BD93F9")
	val cyan = Color.valueOf("8BE9FD")
	val pink = Color.valueOf("FF79C6") // uwu

	val surfaceWhite = Tex.whiteui as TextureRegionDrawable
	val surfaceBackground = surfaceWhite.tintMul(background)
	/** Surface inside a surface. */
	val surfaceInner = surfaceBackground.tintMul(0.5f)
	/** Pressed surface. */
	val surfaceDown = surfaceBackground.tintMul(0.7f)
	/** Surface that's hovered over. */
	val surfaceOver = surfaceBackground.tintMul(0.9f)

	private val blackTints = (0..10).map { surfaceWhite.tint(0f, 0f, 0f, it / 10f) }

	fun black(opacity: Int): Drawable {
		require(opacity in 0..10) { "opacity must be a value in the range 0..10" }
		return blackTints[opacity]
	}
	
	object Label : MindustryLabel.LabelStyle(Styles.defaultLabel) {
		init {
			fontColor = foreground
		}
	}

	object SurfaceLabel : MindustryLabel.LabelStyle(Styles.defaultLabel) {
		init {
			background = surfaceBackground
			fontColor = foreground
		}
	}

	object ActionButton : TextButton.TextButtonStyle(Styles.defaultt) {
		init {
			fontColor = foreground
		
			up = surfaceBackground
			down = surfaceDown
			over = surfaceOver

			disabled = surfaceDown
			disabledFontColor = foreground * 0.7f
			pressedOffsetY = -2f
		}
	}

	/** A button inside a surface. */
	object InnerButton : TextButton.TextButtonStyle(ActionButton) {
		init {
			up = surfaceInner
		}
	}

	object ActionToggleButton : TextButton.TextButtonStyle(ActionButton) {
		init {
			checked = down
		}
	}

	object ChannelButton : TextButton.TextButtonStyle(Styles.defaultt) {
		init {
			fontColor = foreground
		
			up = surfaceInner
			down = surfaceDown
			over = surfaceOver

			disabled = surfaceDown
			disabledFontColor = foreground * 0.7f
			pressedOffsetY = -2f
		}
	}


	object TextInput : TextField.TextFieldStyle(Styles.areaField) {
		init {
			fontColor = foreground
			background = surfaceBackground.margin(layoutMargin)
			focusedBackground = surfaceOver.margin(layoutMargin)
			invalidBackground = surfaceBackground.tintMul(0.9f, 0.88f, 0.88f).margin(layoutMargin)
		}
	}
}

private val tintProperty = TextureRegionDrawable::class.java.getDeclaredField("tint").also {
	it.isAccessible = true
}

/** 
 * Copies this drawable and tints it, multiplying its "tint" property by the specified color.
 */
fun TextureRegionDrawable.tintMul(r: Float, g: Float = r, b: Float = r, a: Float = 1f) = run {
	val old = tintProperty.get(this) as Color

	object : TextureRegionDrawable(region) {
		init {
			tint = old.cpy().mul(r, g, b, a)
		}
	}
}
/** 
 * Copies this drawable and tints it, multiplying its "tint" property by the specified color.
 */
fun TextureRegionDrawable.tintMul(color: Color) =
	tintMul(color.r, color.g, color.b, color.a)

/** Creates a new drawable and adds property-based margin to it. */
fun TextureRegionDrawable.margin(padding: Float) = margin(padding, padding, padding, padding)

/** Creates a new drawable and adds property-based margin to it. */
fun TextureRegionDrawable.margin(padLeft: Float, padRight: Float, padTop: Float, padBottom: Float)
	= tintMul(1f, 1f, 1f, 1f).apply {
		leftWidth = padLeft
		rightWidth = padRight
		topHeight = padTop
		bottomHeight = padBottom
	}
