package io.minchat.client.plugin.impl

import arc.util.*
import io.minchat.client.plugin.MinchatPlugin
import newconsole.ConsoleVars
import newconsole.ui.dialogs.Console
import java.io.*

/**
 * A plugin that redirects the standard output to the NewConsole ui.
 */
class NewConsoleIntegrationPlugin : MinchatPlugin("new-console-integration") {
	lateinit var console: Console
	val isLoaded get() = ::console.isInitialized

	// The actual initialisation is delayed by 1 frame to let other mods load; This can cause a race condition.
	override fun onInit() = Time.run(1f) {
		console = try {
			// This is just to check whether the class is loaded or not.
			ConsoleVars.console
		} catch (e: NoClassDefFoundError) {
			Log.info("NewConsole is not loaded or cannot be detected; skipping the NewConsole integration plugin.")
			return@run
		}

		val oldOut = System.`out`
		// Append everything that's being printed to a string builder; print it when a newline is printed.
		val builder = StringBuilder()
		object : OutputStream() {
			override fun write(b: Int) {
				if (b != '\n'.code && b != '\r'.code) {
					// 32 is the first printable ascii character: space;
					// however, dontResend and \u001b, the escape char, are also included to filter away
					// strings meant for a terminal emulator: they begin with one of them
					if (b >= 32 || b == 0x001b || b == Console.dontResend.code) {
						builder.append(b.toChar())
					}
				} else {
					val string = builder.toString()

					if (!string.startsWith(Console.dontResendStr) && !string.startsWith("\u001b[")) {
						Console.logBuffer.appendLine("[lightgrey][STDOUT]: $string[]")
					}
					builder.clear()
				}

				oldOut.write(b)
			}
		}.let { System.setOut(PrintStream(it)) }
	}
}
