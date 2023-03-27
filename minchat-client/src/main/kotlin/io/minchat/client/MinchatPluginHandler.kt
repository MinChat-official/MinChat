package io.minchat.client

import arc.util.Log
import io.minchat.client.plugin.MinchatPlugin
import io.minchat.client.plugin.impl.NewConsoleIntegrationPlugin
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
	val loadingQueue = ConcurrentLinkedQueue<MinchatPlugin>()
	/**
	 * All plugins loaded in this MinChat instance.
	 *
	 * This list is immutable and is empty at startup;
	 * the value of this property gets replaced after the game loads.
	 */
	var loadedPlugins: List<MinchatPlugin> = listOf()
		private set

	init {
		// default
		register(NewConsoleIntegrationPlugin())
	}

	/**
	 * Registers a new plugin for loading. If [hasLoaded] is true, this function will have no effect.
	 * This function may call [Plugin.onInit] in-place if the mod has been initialised already.
	 *
	 * This function must be called BEFORE the game loads.
	 */
	fun register(plugin: MinchatPlugin) {
		loadingQueue.add(plugin)

		if (!hasLoaded && hasInitialised) {
			plugin.onInit()
		}
	}

	internal fun onInit() {
		Log.info("Initialising MinChat plugins: ${loadingQueue.joinToString { it.name }}")

		val iterator = loadingQueue.iterator()
		iterator.forEach {
			try {
				it.onInit()
			} catch (e: Throwable) {
				Log.err("Could not initialise plugin ${it.name}", e)
				iterator.remove()
			}
		}
	}

	internal suspend fun onLoad() {
		Log.info("Loading MinChat plugins: ${loadingQueue.joinToString { it.name }}")

		val loaded = mutableListOf<MinchatPlugin>()
		val iterator = loadingQueue.iterator()
		iterator.forEach {
			try {
				it.onLoad()
				loaded += it
			} catch (e: Throwable) {
				Log.err("Could not load plugin ${it.name}", e)
			}
			iterator.remove()
		}

		loadedPlugins = loaded
		hasLoaded = true
	}

	internal suspend fun onConnect() {
		loadedPlugins.forEach {
			try {
				it.onConnect()
			} catch (e: Throwable) {
				Log.err("Plugin ${it.name} failed to connect", e)
			}
		}
	}
}
