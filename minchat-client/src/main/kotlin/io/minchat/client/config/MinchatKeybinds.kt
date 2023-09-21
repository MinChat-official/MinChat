package io.minchat.client.config

import arc.*
import arc.input.*
import arc.util.Reflect
import io.minchat.client.Minchat
import io.minchat.client.misc.Log

object MinchatKeybinds {
	val allBindings = mutableListOf<KeyBind>()

	val openFullscreen = KeyBind("minchat-open-fullscreen", KeyCode.backtick, "general") {
		Minchat.showChatDialog()
	}

	/**
	 * Registers all default keybinds.
	 * Must only be called once.
	 */
	fun registerDefaultKeybinds() {
		this::class.java.declaredFields.forEach {
			if (it.type == KeyBind::class.java) {
				it.isAccessible = true // kotlin property
				register(it.get(null) as KeyBind)
			}
		}
	}

	/**
	 * Registers a key binding.
	 */
	fun register(keybinding: KeyBind) {
		val bindings = Core.keybinds.sections[0].binds.get(InputDevice.DeviceType.keyboard)
		if (bindings == null) {
			Log.warn { "No keyboard device detected; Cannot register $keybinding" }
			return
		}

		bindings.put(keybinding, keybinding)
		allBindings += keybinding

		// Core.keybindings has a weird type - can't use Array.plus here
		val original = Core.keybinds.keybinds
		val definitions = Array<KeyBinds.KeyBind>(original.size + 1) {
			if (it == original.size) {
				keybinding
			} else {
				original[it]
			}
		}
		Reflect.set(Core.keybinds, "definitions", definitions)
	}

	data class KeyBind internal constructor(
		val name: String,
		val key: KeyCode,
		val category: String,
		val action: () -> Unit
	) : KeyBinds.Axis(key), KeyBinds.KeyBind {
		override fun name() = name

		override fun defaultValue(type: InputDevice.DeviceType?) = this // This is gonna be a headache

		override fun category() = category
	}
}
