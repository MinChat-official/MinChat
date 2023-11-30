package io.minchat.client.ui.managers


import arc.util.Time
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
			"[$prefix][$level][$timestamp]: $message"
		}
		BaseLogger.logFile = logFile
		BaseLogger.postLogAction = { level, timestamp, sawmill, rawMessage: String ->
			val message = buildString {
				val color = getColorForName(sawmill.name)

				append("[#${color.toString(16)}bb]")
				append("[${sawmill.name}]")
				append("[#${level.color.toString(16)}bb]")
				append("[$level][]")
				append("[$timestamp][]")
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
			Time.run(60f) {
				// NC is trash and will try to erase the logs after the client load - hence the delay
				loadingLogQueue?.forEach {
					addUILogMessage(it)
				}
				loadingLogQueue = null
			}
		}
	}

	fun addUILogMessage(message: String) {
		ncPlugin?.addLog(message)
		Vars.ui.consolefrag?.addMessage(message)
	}

	fun getColorForName(name: String): Int {
		val hash = name.hashCode()
		val r = ((hash and 0xff0000) ushr 16) % 128 + 127
		val g = ((hash and 0x00ff00) ushr 8) % 128 + 127
		val b = (hash and 0x0000ff) % 128 + 127

		return (r shl 16) + (g shl 8) + b
	}
}
