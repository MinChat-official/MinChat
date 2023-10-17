package io.minchat.client.ui.chat

import arc.input.KeyCode
import arc.scene.event.*
import arc.scene.ui.ScrollPane
import arc.scene.ui.layout.Table
import arc.util.Time
import io.minchat.rest.entity.MinchatMessage
import mindustry.Vars
import mindustry.ui.Styles

/** Similar to ScrollPane but optimized for MinChat's needs. */
class ChatScrollPane(val chat: ChatFragment) : ScrollPane(Table(), Styles.defaultPane) {
	private var oldHeight = 0f
	var messageReloadDelay = 0f

	/** Whether it's guaranteed that there are no more messages after the last one in this pane. */
	var isAtEnd = true

	init {
		fun reloadCheck(scroll: Float): Boolean {
			if (messageReloadDelay >= 0f) return false

			if (scroll < 0 && scrollPercentY == 0f) {
				chat.loadMoreMessages(true)
				messageReloadDelay = 120f
				return true
			} else if (scroll > 0 && scrollPercentY == 1f && !isAtEnd) {
				chat.loadMoreMessages(false)
				messageReloadDelay = 120f
				return true
			}
			return false
		}

		addListener(object : InputListener() {
			var yBegin = 0f

			override fun scrolled(event: InputEvent?, x: Float, y: Float, amountX: Float, amountY: Float): Boolean {
				return reloadCheck(amountY)
			}

			override fun touchDown(event: InputEvent?, x: Float, y: Float, pointer: Int, button: KeyCode?): Boolean {
				yBegin = y
				return true
			}

			override fun touchDragged(event: InputEvent?, x: Float, y: Float, pointer: Int) {
				reloadCheck(y - yBegin)
			}
		})
	}

	constructor(chat: ChatFragment, block: Table.(ChatScrollPane) -> Unit) : this(chat) {
		block(widget, this)
	}

	override fun getWidget(): Table {
		return super.getWidget() as Table
	}

	override fun act(delta: Float) {
		super.act(delta)
		messageReloadDelay -= Time.delta
	}

	override fun layout() {
		super.layout()
	}

	override fun sizeChanged() {
		super.sizeChanged()

		val heightDiff = height - oldHeight
		oldHeight = height

		setScrollYForce(scrollY - heightDiff)
	}

	override fun getPrefHeight(): Float {
		return height
	}

	override fun getPrefWidth(): Float {
		return width
	}

	fun scrollToMessage(message: MinchatMessage) {
		val element = widget.children.find {
			(it as? NormalMessageElement)?.message?.id == message.id
		} ?: run {
			Vars.ui.showInfo("Cannot find message. Try scrolling up to load more messages.")
			return
		}

		validate()
		scrollY = widget.height - element.y - element.height
	}
}
