package io.minchat.server.util

import kotlinx.coroutines.*
import kotlinx.datetime.*
import java.io.*
import java.time.format.DateTimeFormatter

/** A logger class inherited from the flarogus discord bot project. */
@SuppressWarnings("NOTHING_TO_INLINE")
object Log {
	var level = LogLevel.LIFECYCLE
		set(level: LogLevel) {
			if (level.level > LogLevel.ERROR.level) throw IllegalArgumentException("illegal log level")
			field = level
		}
	
	/** A java DateTimeFormatter used to format the log timestamps. */
	val timeFormatter = DateTimeFormatter.ofPattern("yyyy.MM.dd HH:mm:ss")
	val timezone = TimeZone.currentSystemDefault()
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
	
	inline fun error(crossinline message: () -> String) = log(LogLevel.ERROR, message)

	inline fun error(throwable: Throwable, crossinline message: () -> String) {
		val file = printStackTrace(throwable)

		log(LogLevel.ERROR) { "${message()}: $throwable. Stacktrace saved to ${file.absolutePath}."  }
	}

	inline fun all(crossinline message: () -> String) = log(LogLevel.ALL, message)

	/** Saves the stacktrace to the stacktrace directory. Returns the created file. */
	fun printStackTrace(throwable: Throwable) = run {
		getStacktraceFile(throwable, Clock.System.now()).also {
			it.writeText(throwable.stackTraceToString())
		}
	}

	fun getStacktraceFile(throwable: Throwable, instant: Instant): File {
		val time = instant
		 	.toLocalDateTime(timezone)
			.toJavaLocalDateTime()
			.let(timeFormatter::format)
		val cls = throwable::class.java.simpleName.orEmpty()

		return stacktraceDir.resolve("stacktrace-$time-$cls.txt")
	}

	fun currentTimestamp() =
		Clock.System.now()
		 	.toLocalDateTime(timezone)
			.toJavaLocalDateTime()
			.let(timeFormatter::format)
	
	enum class LogLevel(val level: Int, val color: Int) {
		LIFECYCLE(0, 0xAFB42B), 
		DEBUG(1, 0x00897B), 
		INFO(2, 0x3F51B5), 
		ERROR(3, 0xF50057), 
		ALL(4, 0x999999);
		
		companion object {
			fun of(level: Int) = values().find { it.level == level } ?: INFO
		}
	}	
}

/** Logs the message if [this] is true. Returns [this]. */
inline fun Boolean.andLog(
	logLevel: Log.LogLevel = Log.LogLevel.INFO,
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
