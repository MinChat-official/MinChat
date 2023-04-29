package io.minchat.client

import arc.Core
import arc.scene.Element
import io.minchat.client.config.MinchatKeybinds

object MinchatBackgroundHandler {
	val dummyElement = Element()

	init {
		dummyElement.update {
			MinchatKeybinds.allBindings.forEach {
				if (Core.input.keyTap(it)) {
					it.action()
				}
			}
		}
	}

	fun start() {
		Core.scene.add(dummyElement)
	}

	fun stop() {
		dummyElement.remove()
	}
}
