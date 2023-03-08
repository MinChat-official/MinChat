package io.minchat.client.misc

import arc.graphics.*
import arc.scene.style.*
import mindustry.gen.Tex

object MinchatStyle {
	val layoutMargin = 3f
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
	val surfaceBackground = surfaceWhite.tint(background)
	/** Pressed surface. */
	val surfaceDown = surfaceWhite.tint(background.cpy().mul(0.7f))
	/** Surface that's hovered over. */
	val surfaceOver = surfaceWhite.tint(background.cpy().mul(0.9f))
}
