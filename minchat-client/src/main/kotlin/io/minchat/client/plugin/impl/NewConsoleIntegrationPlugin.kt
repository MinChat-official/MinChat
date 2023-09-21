package io.minchat.client.plugin.impl

import arc.util.Time
import arc.util.Timer.Task
import io.minchat.client.misc.Log
import io.minchat.client.plugin.MinchatPlugin
import newconsole.ConsoleVars
import newconsole.ui.dialogs.Console

/**
 * A plugin that redirects the standard output to the NewConsole ui.
 */
class NewConsoleIntegrationPlugin : MinchatPlugin("new-console-integration") {
	var console: Console? = null
	var deferredConsoleScrollTask: Task? = null

	// The actual initialisation is delayed by 1 frame to let other mods load; This can cause a race condition.
	override fun onInit() = Time.run(1f) {
		console = try {
			// This is just to check whether the class is loaded or not.
			ConsoleVars.console
		} catch (e: NoClassDefFoundError) {
			Log.warn { "NewConsole is either not loaded or cannot be detected; skipping the NewConsole integration plugin." }
			return@run
		}
	}

	/** Adds a log that's only visible in NC. */
	fun addLog(log: String) {
		console?.apply {
			try {
				Console.logBuffer.append(log.replace("\t", "    ") + "\n")

				if (deferredConsoleScrollTask?.isScheduled?.not() ?: true) {
					deferredConsoleScrollTask = Time.runTask(4f) {
						scrollDown()
					}
				}
			} catch (e: ConcurrentModificationException) {
				// nothing I can do here, other than cry.
			}
		}
	}
}
