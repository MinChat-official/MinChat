package io.minchat.client.plugin

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
	 * One should note, however, that this function should not block or suspend the
	 * coroutine, as other plugins will have their initialization delayed.
	 */
	open suspend fun onLoad() {}

	/**
	 * Called when the minchat client connects to the server and [Minchat.client]/[Minchat.gateway] get changed.
	 * This function can be called multiple times if the server changes.
	 *
	 * If this function suspends, the user may see a loading screen for additional time.
	 */
	open suspend fun onConnect() {}
}
