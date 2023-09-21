package io.minchat.server.util

import io.minchat.common.AbstractLogger
import io.minchat.common.AbstractLogger.LogLevel
import kotlinx.coroutines.*
import java.io.*
import java.time.Instant

/** A logger class inherited from the flarogus discord bot project. */
@SuppressWarnings("NOTHING_TO_INLINE")
object Log : AbstractLogger() {
	/** Control sequence introducer. */
	val csi = "\u001B["

	/** The base directory in which a "log" direvtory will be created. Must be initialised statically. */
	lateinit var baseLogDir: File
	val logDir by lazy {
		val root = baseLogDir.ensureDir()
		root.resolve("log").ensureDir()
	}
	val stacktraceDir by lazy { 
		logDir.resolve("stacktrace").ensureDir() 
	}
	val currentLogFile by lazy {
		logDir.resolve("log-${currentTimestamp()}.txt")
	}
	val currentLogWriter by lazy {
		currentLogFile.printWriter()
	}

	override fun log(levelString: String, message: String) {
		when (levelString.lowercase()) {
			"all" -> all { message }
			"lifecycle" -> lifecycle { message }
			"debug" -> debug { message }
			"info" -> info { message }
			"warn" -> warn { message }
			"error" -> error { message }
		}
	}

	inline fun log(logLevel: LogLevel, crossinline message: () -> String) {
		if (logLevel.level < level.level) return

		val time = currentTimestamp()
		val msg = message()

		logLevel.color.let {
			// set the color
			val r = (it and 0xff0000) shr 16
			val g = (it and 0xff00) shr 8
			val b = it and 0xff
			print("${csi}38;2;${r};${g};${b}m")
		}
		print("[$time][$logLevel]")
		print("${csi}0m") // reset the color
		println(": $msg")

		currentLogWriter.println("[$time][$logLevel] $msg")
		currentLogWriter.flush()
	}
	
	inline fun lifecycle(crossinline message: () -> String) = log(LogLevel.LIFECYCLE, message)
	
	inline fun debug(crossinline message: () -> String) = log(LogLevel.DEBUG, message)
	
	inline fun info(crossinline message: () -> String) = log(LogLevel.INFO, message)

	inline fun warn(crossinline message: () -> String) = log(LogLevel.WARN, message)

	inline fun error(crossinline message: () -> String) = log(LogLevel.ERROR, message)

	inline fun error(throwable: Throwable, crossinline message: () -> String) {
		val file = printStackTrace(throwable)

		log(LogLevel.ERROR) { "${message()}: $throwable. Stacktrace saved to ${file.absolutePath}."  }
	}

	inline fun all(crossinline message: () -> String) = log(LogLevel.ALL, message)

	/** Saves the stacktrace to the stacktrace directory. Returns the created file. */
	fun printStackTrace(throwable: Throwable) = run {
		getStacktraceFile(throwable, Instant.now()).also {
			it.writeText(throwable.stackTraceToString())
		}
	}

	fun getStacktraceFile(throwable: Throwable, instant: Instant): File {
		val time = instant
		 	.atZone(timezone)
			.let(timeFormatter::format)
		val cls = throwable::class.java.simpleName.orEmpty()

		return stacktraceDir.resolve("stacktrace-$time-$cls.txt")
	}
}

/** Logs the message if [this] is true. Returns [this]. */
inline fun Boolean.andLog(
	logLevel: LogLevel = LogLevel.INFO,
	crossinline message: () -> String
) = apply {
	if (this) Log.log(logLevel, message)
}

private fun File.ensureDir() = also {
	if (exists() && isDirectory().not()) delete()
	if (!exists()) mkdirs()
}
private fun File.ensureFile() = also {
	if (exists() && isDirectory()) deleteRecursively()
}
