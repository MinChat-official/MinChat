package io.minchat.client.plugin

import arc.util.Log
import io.minchat.client.plugin.impl.*
import java.util.concurrent.ConcurrentLinkedQueue

object MinchatPluginHandler {
	/** If true, a newly registered plugin will be initialised immediately, unless [hasLoaded] is also true. */
	var hasInitialised = false
		private set
	/** If true, calling [register] will have no effect. */
	var hasLoaded = false
		private set
	/**
	 * The list of plugins that are to be loaded on startup.
	 * After the mod has loaded, this queue will be empty.
	 */
	val loadingQueue = ConcurrentLinkedQueue<PluginLoadEntry>()
	/**
	 * All plugins loaded in this MinChat instance.
	 *
	 * This list is immutable and is empty at startup;
	 * the value of this property gets replaced after the game loads.
	 */
	var loadedPlugins: List<LoadedPlugin<out MinchatPlugin>> = listOf()
		private set

	init {
		register(::AutoupdaterPlugin)
		register(::AccountSaverPlugin)
	}

	/**
	 * Registers a new plugin for loading. If [hasLoaded] is true, this function will have no effect.
	 * This function may call [Plugin.onInit] in-place if the mod has been initialised already.
	 *
	 * This function must be called BEFORE the game loads.
	 */
	fun register(plugin: () -> MinchatPlugin) {
		val entry = PluginLoadEntry(plugin)

		if (!hasLoaded && hasInitialised) {
			entry.get()
		} else if (hasLoaded) {
			error("Cannot register new plugins after MinChat has finished loading.")
		}
		loadingQueue.add(entry)
	}

	internal fun onInit() {
		val iterator = loadingQueue.iterator()
		iterator.forEach {
			try {
				it.get()
			} catch (e: Throwable) {
				Log.err("Could not initialise plugin ${it.factory}", e)
				iterator.remove()
			}
		}

		Log.info("Initialized MinChat plugins: ${loadingQueue.joinToString { it.get().name }}")
	}

	internal suspend fun onLoad() {
		Log.info("Loading MinChat plugins: ${loadingQueue.joinToString { it.get().name }}")

		val loaded = mutableListOf<LoadedPlugin<*>>()
		val iterator = loadingQueue.iterator()
		iterator.forEach {
			val plugin = it.get()
			try {
				plugin.onLoad()
				loaded += LoadedPlugin(plugin, null, true)
			} catch (e: Throwable) {
				Log.err("Could not load plugin $plugin.name}", e)
				loaded += LoadedPlugin(plugin, e, false)
			}
			iterator.remove()
		}

		loadedPlugins = loaded
		hasLoaded = true
	}

	internal suspend fun onConnect() {
		loadedPlugins.forEach {
			try {
				it.plugin.onConnect()
			} catch (e: Throwable) {
				Log.err("Plugin ${it.plugin.name} failed onConnect", e)
			}
		}
	}

	/** Gets the entry of a loaded plugin. Returns null if it has failed to init or was not registered. */
	@Suppress("UNCHECKED_CAST")
	inline fun <reified T : MinchatPlugin> getEntry() =
		loadedPlugins.find { it.plugin is T } as LoadedPlugin<T>?

	/** Gets a loaded plugin. Returns null if it was not registered, failed to init, or failed to load. */
	inline fun <reified T : MinchatPlugin> get(): T? =
		getEntry<T>()?.takeIf { it.isLoaded }?.plugin

	data class PluginLoadEntry(
		val factory: () -> MinchatPlugin,
		var instance: MinchatPlugin? = null
	) {
		fun get() = instance ?: factory().also {
			instance = it
			it.onInit()
		}
	}

	data class LoadedPlugin<T : MinchatPlugin>(
		val plugin: T,
		val error: Throwable?,
		val isLoaded: Boolean
	)
}
