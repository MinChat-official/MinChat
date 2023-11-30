package io.minchat.common

import io.minchat.common.BaseLogger.LoggerSawmill
import java.io.*
import java.time.*
import java.time.format.DateTimeFormatter
import java.util.*

typealias LogHandler<T> = (level: BaseLogger.LogLevel, timestamp: String, sawmill: LoggerSawmill, message: String) -> T
typealias SawmillFactory = (name: String) -> LoggerSawmill

/**
 * The base MinChat logger.
 *
 * Users of this logger are expected to call the getSawmill method and use the
 * acquired [LoggerSawmill] to log data.
 *
 * Every module using this object is expected to overwrite [BaseLogger.Companion.logFile]
 * and [BaseLogger.Companion.stdoutFormatter] in one way or another.
 * Failure to do so will result in the data being only output to STDOUT.
 */
open class BaseLogger(
	defaultLogFile: File?,
	var sawmillFactory: SawmillFactory = { name -> LoggerSawmill(name) },
	var stdoutFormatter: LogHandler<String> = { ll, time, sawmill, message -> "[$ll][$time][${sawmill.name}] $message" },
	var postLogAction: LogHandler<Unit>? = null
) {
	private val sawmill by lazy { getSawmill("BASE LOGGER") }

	var minLevel = LogLevel.LIFECYCLE
		set(level) {
			if (level.severity > LogLevel.ERROR.severity) throw IllegalArgumentException("illegal log level")
			field = level
		}
	var logFile: File? = defaultLogFile
		set(value) {
			if (value == null) error("Log file cannot be null")

			field = value
			printer = PrintWriter(value.outputStream(), true).apply {
				println("=== MinChat log: ${currentTimestamp()} ===")
				println("=== MinChat version: $MINCHAT_VERSION ===")
				print("\n\n")
			}

			sawmill.info("Log file was set to $value.")

			val lostLogs = sawmills.values.sumOf { it.logCount } - 1
			if (lostLogs > 0) {
				sawmill.warn("Log file was changed but $lostLogs logs were already printed. They may have been lost.")
			}
		}
	private var printer: PrintWriter? = null

	private val sawmills = Collections.synchronizedMap(WeakHashMap<String, LoggerSawmill>())

	/** A java DateTimeFormatter used to format the log timestamps. */
	val timeFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy.MM.dd HH:mm:ss")
	val timezone: ZoneId = ZoneId.systemDefault()

	val logFileEntryRegex = """\[(\d{4}\.\d{2}\.\d{2} \d{2}:\d{2}:\d{2})]\[(\w+?)]\[(.*?)] (.+)""".toRegex()

	/** Creates a timestamp representing the current moment. */
	fun currentTimestamp(): String =
		Instant.now()
			.atZone(timezone)
			.let(timeFormatter::format)

	fun log(level: LogLevel, sawmill: LoggerSawmill, message: String) {
		if (level.severity < minLevel.severity) return

		val timestamp = currentTimestamp()
		val formatted = stdoutFormatter(level, timestamp, sawmill, message)

		println(formatted)

		// File output format is strict. In addition to that, every trailing line is prefixed with a pipe.
		val transformed = message.lineSequence().joinToString("\n|")
		printer?.println("[$timestamp][$level][${sawmill.name}] $transformed") // File output is strict.
		postLogAction?.invoke(level, timestamp, sawmill, message)
	}

	fun debug(sawmill: LoggerSawmill, message: String) = log(LogLevel.DEBUG, sawmill, message)

	fun lifecycle(sawmill: LoggerSawmill, message: String) = log(LogLevel.LIFECYCLE, sawmill, message)

	fun info(sawmill: LoggerSawmill, message: String) = log(LogLevel.INFO, sawmill, message)

	fun warn(sawmill: LoggerSawmill, message: String) = log(LogLevel.WARN, sawmill, message)

	fun error(sawmill: LoggerSawmill, message: String) = log(LogLevel.ERROR, sawmill, message)

	fun error(throwable: Throwable, sawmill: LoggerSawmill, message: String) {
		val trace = throwable.stackTraceToString().replace("\n", "\n    ").trim()

		log(LogLevel.ERROR, sawmill, message + "\n" + trace)
	}

	fun all(sawmill: LoggerSawmill, message: String) = log(LogLevel.ALL, sawmill, message)

	/** Returns a sawmill for the given name. Creates one if needed. */
	fun getSawmill(name: String): LoggerSawmill = sawmills.getOrPut(name) {
		sawmillFactory(name)
	}

	/**
	 * Returns a sawmill for the current class context. Creates one if needed.
	 *
	 * E.g. when invoked inside the class body of `class Cat` or its companion object,
	 * returns a `LoggerSawmill("Cat")`.
	 */
	context(Any)
	@Suppress("NOTHING_TO_INLINE")
	inline fun getContextSawmill() =
		getSawmill(this@Any.javaClass.name.removeSuffix("\$Companion").substringAfterLast('.'))

	/** Parses a log file, assuming that no external modifications were made to it. */
	fun parseLogFile(file: File) = buildList<LogEntry> {
		val iterator = file.bufferedReader().readLines().listIterator()

		for (startingLine in iterator) {
			// Trailing liens are prefixed with a | symbol.
			val trailing = buildString {
				for (nextLine in iterator) {
					if (nextLine.startsWith("|")) {
						appendLine(nextLine)
					} else {
						iterator.previous()
						break
					}
				}
			}

			val (logLevel, timestamp, prefix, message) = logFileEntryRegex.find(startingLine)?.destructured ?: continue

			add(LogEntry(
				LogLevel.entries.find { it.name == logLevel } ?: LogLevel.ALL,
				timestamp,
				prefix,
				message + trailing.takeIf { it.isNotBlank() }?.let { "\n$it"}?.trim()
			))
		}
	}

	/** Parses the current log file. */
	fun parseCurrentLogFile() =
		parseLogFile(logFile ?: error("Log file has not yet been set."))

	enum class LogLevel(val severity: Int, val color: Int) {
		/** Log level for stuff that is only useful for debugging and is not useful otherwise. */
		DEBUG(0, 0x00897B),
		/** The lowest log level for stuff that occurs on a normal basis. */
		LIFECYCLE(1, 0xAFB42B),
		/** Log level for stuff that may be useful but doesn't require immediate attention. */
		INFO(2, 0x3F51B5),
		/** Log level for things that could lead to errors or unwanted behavior. */
		WARN(3, 0xF5D357),
		/** Log level for exceptions and runtime errors. */
		ERROR(4, 0xF50057),
		/** Log level for messages that must be shown no matter what. Used for unparsable log levels. */
		ALL(5, 0x999999);

		companion object {
			fun of(level: Int) = entries.find { it.severity == level } ?: ALL
		}
	}

	open class LoggerSawmill(val name: String) {
		var logCount = 0

		open fun log(level: LogLevel, message: String) {
			logCount++
			BaseLogger.log(level, this, message)
		}

		open fun error(throwable: Throwable, message: String) {
			logCount++
			BaseLogger.error(throwable, this, message)
		}


		fun debug(message: String) = log(LogLevel.DEBUG, message)

		fun lifecycle(message: String) = log(LogLevel.LIFECYCLE, message)

		fun info(message: String) = log(LogLevel.INFO, message)

		fun warn(message: String) = log(LogLevel.WARN, message)

		fun error(message: String) = log(LogLevel.ERROR, message)
		fun all(message: String) = log(LogLevel.ALL, message)

		inline fun debug(message: () -> String) {
			if (minLevel <= LogLevel.DEBUG) debug(message())
		}

		inline fun lifecycle(message: () -> String) {
			if (minLevel <= LogLevel.LIFECYCLE) lifecycle(message())
		}

		inline fun info(message: () -> String) {
			if (minLevel <= LogLevel.INFO) info(message())
		}

		inline fun warn(message: () -> String) {
			if (minLevel <= LogLevel.WARN) warn(message())
		}

		inline fun error(message: () -> String) {
			if (minLevel <= LogLevel.ERROR) error(message())
		}

		inline fun error(throwable: Throwable, message: () -> String) {
			if (minLevel <= LogLevel.ERROR) error(throwable, message())
		}

		inline fun all(message: () -> String) {
			all(message())
		}
	}

	data class LogEntry(
		val level: LogLevel,
		val prefix: String,
		val timestamp: String,
		val message: String
	)

	companion object : BaseLogger(null)
}
