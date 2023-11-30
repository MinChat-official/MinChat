package io.minchat.client.ui.managers


import io.minchat.client.Minchat
import io.minchat.client.config.MinchatSettings.prefix
import io.minchat.common.BaseLogger
import io.minchat.common.BaseLogger.Companion.getContextSawmill
import java.nio.file.Files

/**
 * Handles the setup of the MinChat logger.
 */
object LoggerManager {
	val logsDir = Minchat.dataDir.resolve("logs").also { it.mkdir() }
	val logFile = logsDir.resolve("log-${BaseLogger.currentTimestamp()}.log")
	/** A symlink to the last log file. May not exist. */
	val lastLogFile = Minchat.dataDir.resolve("last-log.txt")

	private val logger = BaseLogger.getContextSawmill() // the logger manager needs a logger, hilarious

	fun init() {
		BaseLogger.logFile = logFile
		BaseLogger.sawmillFactory = { name -> ColoredLoggerSawmill(name) }
		BaseLogger.stdoutFormatter = { level, timestamp, sawmill, message ->
			buildString {
				val color = (sawmill as? ColoredLoggerSawmill)?.color ?: "dddddd"

				append("[#$color]")
				append("[$timestamp][$prefix][]")
				append("[#${level.color.toString(16)}")
				append("[$level][]")
				append(": ")
				append(message)
			}
		}

		// Try to create a symlink, if possible. This is likely to fail on android but will work on linux/windows
		try {
			Files.createSymbolicLink(lastLogFile.toPath(), logFile.toPath())
			Minchat.logger.info { "Created a link to the last log in ${lastLogFile}." }
		} catch (e: Throwable) {
			Minchat.logger.error { "Could not create a symlink to ${logFile}. You'll have to access it manually. $e" }
		}
	}

	class ColoredLoggerSawmill(
		name: String
	) : BaseLogger.LoggerSawmill(name) {
		val color = run {
			val hash = name.hashCode()
			val r = ((hash and 0xff0000) ushr 16) / 2 + 127
			val g = ((hash and 0x00ff00) ushr 8) / 2 + 127
			val b = (hash and 0x0000ff) / 2 + 127

			((r shl 16) + (g shl 8) + b).toString(16)
		}
	}
}
