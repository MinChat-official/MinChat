package io.minchat.client

import arc.Core
import arc.scene.Element
import io.minchat.client.config.MinchatKeybinds
import java.util.concurrent.ConcurrentLinkedDeque

object MinchatBackgroundHandler {
	val dummyElement = Element()
	val customListeners = ConcurrentLinkedDeque<() -> Unit>()

	init {
		dummyElement.update {
			if (Core.scene.keyboardFocus != null) return@update

			MinchatKeybinds.allBindings.forEach {
				if (Core.input.keyTap(it)) {
					it.action()
				}
			}

			customListeners.forEach {
				it()
			}
		}
	}

	fun start() {
		Core.scene.add(dummyElement)
	}

	fun stop() {
		dummyElement.remove()
	}

	fun subscribeUpdate(listener: () -> Unit) {
		customListeners.add(listener)
	}

	fun unsubscribeUpdate(listener: () -> Unit) {
		customListeners.remove(listener)
	}
}
