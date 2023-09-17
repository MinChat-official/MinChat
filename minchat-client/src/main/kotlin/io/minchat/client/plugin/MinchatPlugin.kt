package io.minchat.client.plugin

import io.minchat.client.*
import io.minchat.rest.event.MinchatEvent
import kotlinx.coroutines.flow.*

/**
 * A plugin that extends or changes the functionality of the minchat mod.
 *
 * @param name the name of this plugin. Should use kebab-case, e.g.: "amazing-minchat-plugin".
 */
abstract class MinchatPlugin(
	val name: String
) {
	/**
	 * Called when the mod is initialised for the first time.
	 *
	 * At that moment, [Minchat.isConnected] is guaranteed to be false.
 	 */
	open fun onInit() {}

	/**
	 * Called after the game has loaded and the mod has been initialised.
	 * This function can perform network and other initialization tasks.
	 *
	 * One should note, however, that this function should not block the
	 * thread, as other plugins will have their loading delayed.
	 */
	open fun onLoad() {}

	/**
	 * Called when the minchat client connects to the server and [Minchat.client]/[Minchat.gateway] get changed.
	 * This function can be called multiple times if the server changes.
	 *
	 * If this function suspends, the user may see a loading screen for additional time.
	 */
	suspend open fun onConnect() {}

	/** Subscribes to a client event. Equivalent to `ClientEvents.subscribe`. */
	inline fun <reified T> subscribe(subscriber: ClientEvents.Subscriber<T>) =
		ClientEvents.subscribe<T>(subscriber)

	/**
	 * Subscribes to a MinChat gateway event.
	 *
	 * This must only be called from [onConnect].
	 *
	 * This is equivalent to `Minchat.gateway.events.filterIsInstance<T>.onEach { ... }.launchIn(Minchat)`.
	 */
	inline fun <reified T : MinchatEvent<*>> subscribeGateway(crossinline subscriber: (T) -> Unit) {
		if (!Minchat.isConnected) error("MinChat client is not connected to a server yet. Call this function from onConnect.")

		Minchat.gateway.events
			.filterIsInstance<T>()
			.onEach {
				subscriber(it)
			}
			.launchIn(Minchat)
	}
}
