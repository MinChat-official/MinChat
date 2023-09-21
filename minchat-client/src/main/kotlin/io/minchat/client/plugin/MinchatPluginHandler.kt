package io.minchat.client.plugin

import io.minchat.client.*
import io.minchat.client.misc.Log
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
	 * the value of this property gets replaced twice as the game loads.
	 */
	var loadedPlugins: List<LoadedPlugin<out MinchatPlugin>> = listOf()
		private set

	init {
		register(::AutoupdaterPlugin)
		register(::AccountSaverPlugin)
		register(::NewConsoleIntegrationPlugin)

		ClientEvents.subscribe<LoadEvent> {
			onLoad()
		}

		ClientEvents.subscribe<ConnectEvent> {
			onConnect()
		}
	}

	/**
	 * Registers a new plugin for loading. If [hasLoaded] is true, this function will have no effect.
	 * This function may call [Plugin.onInit] in-place if the mod has been initialised already.
	 *
	 * This function must be called BEFORE the game loads.
	 */
	fun register(factory: () -> MinchatPlugin) {
		val entry = PluginLoadEntry(factory)

		if (!hasLoaded && hasInitialised) {
			loadedPlugins += initPlugin(factory())
		} else if (!hasLoaded) {
			loadingQueue.add(entry)
		} else {
			error("Cannot register new plugins after MinChat has finished loading.")
		}
	}

	/** Initializes all plugins in [loadingQueue]. Must only be called once. */
	internal fun onInit() {
		if (hasInitialised) error("Already initialized.")

		hasInitialised = true
		loadedPlugins = loadingQueue.map {
			initPlugin(it.factory())
		}

		Log.info { "Initialized MinChat plugins: ${loadedPlugins.joinToString { it.plugin.name }}" }
	}

	fun initPlugin(plugin: MinchatPlugin): LoadedPlugin<*> {
		return try {
			plugin.onInit()
			LoadedPlugin(plugin, null, false)
		} catch (e: Throwable) {
			Log.error(e) { "Could not initialise plugin ${plugin.name}" }
			LoadedPlugin(plugin, e, false)
		}
	}

	internal fun onLoad() {
		if (hasLoaded) error("Already loaded.")
		hasLoaded = true

		// Retain already-loaded (?) and failed plugins, load the rest
		val (toRetain, toLoad) = loadedPlugins.partition { it.isLoaded || it.error != null }
		Log.info { "Loading MinChat plugins: ${toLoad.joinToString { it.plugin.name }}" }

		val loaded = toLoad.map {
			try {
				it.plugin.onLoad()
				it.copy(isLoaded = true)
			} catch (e: Throwable) {
				Log.error { "Could not load plugin ${it.plugin.name}" }
				it.copy(error = e)
			}
		}

		loadedPlugins = toRetain + loaded
	}

	internal suspend fun onConnect() {
		loadedPlugins.forEach {
			try {
				it.plugin.onConnect()
			} catch (e: Throwable) {
				Log.error(e) { "Plugin ${it.plugin.name} failed onConnect" }
			}
		}
	}

	/** Gets the entry of a loaded plugin. Returns null if it was not registered. */
	@Suppress("UNCHECKED_CAST")
	inline fun <reified T : MinchatPlugin> getEntry() =
		loadedPlugins.find { it.plugin is T } as LoadedPlugin<T>?

	/** Gets a loaded plugin. Returns null if it was not registered, failed to init, or failed to load. */
	inline fun <reified T : MinchatPlugin> get(): T? =
		getEntry<T>()?.takeIf { it.isLoaded }?.plugin

	data class PluginLoadEntry(
		val factory: () -> MinchatPlugin
	)

	data class LoadedPlugin<T : MinchatPlugin>(
		val plugin: T,
		val error: Throwable?,
		val isLoaded: Boolean
	)
}
