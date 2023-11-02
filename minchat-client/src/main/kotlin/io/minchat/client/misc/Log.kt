package io.minchat.client.misc

import arc.*
import arc.util.*
import io.minchat.client.plugin.MinchatPluginHandler
import io.minchat.client.plugin.impl.NewConsoleIntegrationPlugin
import io.minchat.common.*
import mindustry.Vars
import mindustry.game.EventType.ClientLoadEvent
import java.io.PrintWriter
import java.util.concurrent.ConcurrentLinkedQueue

object Log : AbstractLogger() {
	val lastLogFile = Core.settings.dataDirectory.child("last_minchat_log.txt").file()
//	/** A directory to copy the last minchat log to in case of a crash. */
//	val persistentCrashesDir = Core.settings.dataDirectory.child("crashes").child("minchat").file()

	/** A PrintWriter that writer to [lastLogFile]. */
	val printer by lazy { PrintWriter(lastLogFile.writer()) }

	@PublishedApi
	internal var loadingLogsQueue: ConcurrentLinkedQueue<String>? = ConcurrentLinkedQueue()
	private val ncIntegration by lazy { MinchatPluginHandler.get<NewConsoleIntegrationPlugin>() }

	init {
		arc.util.Log.info("[MinChat] Important: MinChat logs are saved into a special file in the game directory - last_minchat_log.txt!")

		lastLogFile.delete()
//		if (!persistentCrashesDir.isDirectory && persistentCrashesDir.exists()) {
//			persistentCrashesDir.delete()
//		}
//		persistentCrashesDir.mkdirs()

		val startupTime = currentTimestamp()

		// TODO: doesn't work
//		val defaultEH = Thread.getDefaultUncaughtExceptionHandler()
//		Thread.setDefaultUncaughtExceptionHandler { thread, exception ->
//			val dest = persistentCrashesDir.resolve("minchat_log_$startupTime.txt")
//			lastLogFile.copyTo(dest, overwrite = true)
//
//			defaultEH.uncaughtException(thread, exception)
//		}

		printer.println("=== MinChat log: $startupTime ===")
		printer.println("=== MinChat version: $MINCHAT_VERSION ===")
		printer.print("\n\n")

		Events.on(ClientLoadEvent::class.java) {
			Time.run(10f) {
				loadingLogsQueue?.forEach {
					displayLog(it)
				}
				loadingLogsQueue = null
			}
		}
	}

	override fun log(levelString: String, message: String) {
		when (levelString.lowercase()) {
			"all" -> all { message }
			"lifecycle" -> lifecycle { message }
			"debug" -> debug { message }
			"info" -> info { message }
			"warn" -> warn { message }
			"error" -> error { message }
			else -> error { "Logger tried to log an invalid level: $levelString. Message: $message" }
		}
	}

	inline fun log(logLevel: LogLevel, crossinline message: () -> String) {
		if (logLevel.level < level.level) return

		val time = currentTimestamp()
		val msg = message()

		val logEntry = buildString {
			append("[[[#${logLevel.color.toString(16)}]${logLevel.name.first()}[]]")
			append("[[[blue]MinChat[] ")
			append("[green]$time[]] ")
			append(Strings.stripColors(msg))
		}

		if (Vars.clientLoaded || loadingLogsQueue == null) {
			displayLog(logEntry)
		} else {
			loadingLogsQueue?.add(logEntry)
		}

		printer.println("[${logLevel.name.first()}][MinChat $time] $msg")
	}

	inline fun lifecycle(crossinline message: () -> String) = log(LogLevel.LIFECYCLE, message)

	inline fun debug(crossinline message: () -> String) = log(LogLevel.DEBUG, message)

	inline fun info(crossinline message: () -> String) = log(LogLevel.INFO, message)

	inline fun warn(crossinline message: () -> String) = log(LogLevel.WARN, message)

	inline fun error(crossinline message: () -> String) = log(LogLevel.ERROR, message)

	inline fun error(throwable: Throwable, crossinline message: () -> String) = log(LogLevel.ERROR) {
		"${message()}: ${throwable.stackTraceToString().lines().joinToString("\n    ")}"
	}

	inline fun all(crossinline message: () -> String) = log(LogLevel.ALL, message)

	/** Adds the log to the console fragment and, if the new-console is loaded and [newConsole] is true, new-console's. */
	fun displayLog(log: String) {
		Vars.ui.consolefrag.addMessage(log)
		ncIntegration?.addLog(log)
	}
}
