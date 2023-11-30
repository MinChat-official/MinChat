package io.minchat.client.ui.managers


import io.minchat.client.*
import io.minchat.client.plugin.MinchatPluginHandler
import io.minchat.client.plugin.impl.NewConsoleIntegrationPlugin
import io.minchat.common.BaseLogger
import mindustry.Vars
import java.nio.file.Files
import java.util.concurrent.ConcurrentLinkedDeque

/**
 * Handles the setup of the MinChat logger.
 */
object LoggerManager {
	val logsDir = Minchat.dataDir.resolve("logs").also { it.mkdir() }
	val logFile = logsDir.resolve("log-${BaseLogger.currentTimestamp()}.log")
	/** A symlink to the last log file. May not exist. */
	val lastLogLink = Minchat.dataDir.resolve("last-log.txt")

	private val logger by lazy { BaseLogger.getContextSawmill() } // the logger manager needs a logger, hilarious
	private val ncPlugin by MinchatPluginHandler.getting<NewConsoleIntegrationPlugin>(false)

	private var loadingLogQueue: ConcurrentLinkedDeque<String>? = ConcurrentLinkedDeque<String>()

	fun init() {
		BaseLogger.stdoutFormatter = { level, timestamp, sawmill, message ->
			val prefix = sawmill.name
			"[$level][$prefix][$timestamp]: $message"
		}
		BaseLogger.logFile = logFile
		BaseLogger.postLogAction = { level, timestamp, sawmill, rawMessage: String ->
			val message = buildString {
				val color = run {
					val hash = sawmill.name.hashCode()
					val r = ((hash and 0xff0000) ushr 16) / 2 + 127
					val g = ((hash and 0x00ff00) ushr 8) / 2 + 127
					val b = (hash and 0x0000ff) / 2 + 127

					(r shl 16) + (g shl 8) + b
				}

				append("[#${level.color.toString(16)}]")
				append("[$level][]")
				append("[#$color]")
				append("[$timestamp][${sawmill.name}][]")
				append(": ")
				append(rawMessage)
			}

			if (Vars.clientLoaded || loadingLogQueue == null) {
				addUILogMessage(message)
			} else {
				loadingLogQueue!!.add(message)
			}
		}

		// Try to create a symlink, if possible. This is likely to fail on android but will work on linux/windows
		try {
			Files.deleteIfExists(lastLogLink.toPath())

			Files.createSymbolicLink(lastLogLink.toPath(), logFile.toPath())
			Minchat.logger.info { "Created a link to the last log in ${lastLogLink}." }
		} catch (e: Throwable) {
			Minchat.logger.error { "Could not create a symlink to ${logFile}. You'll have to access it manually. $e" }
		}

		ClientEvents.subscribe<LoadEvent> {
			loadingLogQueue?.forEach {
				addUILogMessage(it)
			}
			loadingLogQueue = null
		}
	}

	fun addUILogMessage(message: String) {
		ncPlugin?.addLog(message)
		Vars.ui.consolefrag?.addMessage(message)
	}
}
