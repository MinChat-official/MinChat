package io.minchat.client.config

import arc.*
import arc.input.InputDevice.DeviceType
import arc.input.KeyCode
import arc.struct.ObjectMap
import arc.util.Reflect
import io.minchat.client.Minchat
import io.minchat.common.BaseLogger
import io.minchat.common.BaseLogger.Companion.getContextSawmill

object MinchatKeybinds {
	val allBindings = mutableListOf<KeyBind>()

	private val defaultsCache: ObjectMap<KeyBind, ObjectMap<DeviceType, KeyBinds.Axis>> =
		Reflect.get(Core.keybinds, "defaultCache")
	private val logger = BaseLogger.getContextSawmill()

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
		val bindings = Core.keybinds.sections[0].binds.get(DeviceType.keyboard)
		if (bindings == null) {
			logger.warn { "No keyboard device detected; Cannot register $keybinding" }
			return
		}

		bindings.put(keybinding, KeyBinds.Axis(keybinding.key))
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

		keybinding.putDefault()
	}

	data class KeyBind internal constructor(
		val name: String,
		val key: KeyCode,
		val category: String,
		val action: () -> Unit
	) : KeyBinds.KeyBind {
		fun putDefault() {
			defaultsCache.put(this, ObjectMap())

			for (type in DeviceType.values()) {
				defaultsCache.get(this)?.put(
					type,
					KeyBinds.Axis(this.key)
				)
			}
		}

		override fun name() = name

		override fun defaultValue(type: DeviceType?) = key

		override fun category() = category
	}
}
