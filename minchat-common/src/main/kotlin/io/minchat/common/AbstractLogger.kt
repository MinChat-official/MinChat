package io.minchat.common

import java.time.*
import java.time.format.DateTimeFormatter

/**
 * Abstract MinChat logger.
 * Modules are expected to provide a Log implementation extending this class.
 *
 * Each module-specific logger must overload all the inline log methods,
 * typically by providing a generic log method and log level-specific overloads:
 * lifecycle, debug, info, warn, error, all.
 */
abstract class AbstractLogger {
	var level = LogLevel.LIFECYCLE
		set(level: LogLevel) {
			if (level.level > LogLevel.ERROR.level) throw IllegalArgumentException("illegal log level")
			field = level
		}

	/** A java DateTimeFormatter used to format the log timestamps. */
	val timeFormatter = DateTimeFormatter.ofPattern("yyyy.MM.dd HH:mm:ss")
	val timezone = ZoneId.systemDefault()

	/** Creates a timestamp of the current moment. */
	fun currentTimestamp() =
		Instant.now()
			.atZone(timezone)
			.let(timeFormatter::format)

	/** A workaround function that should parse levelString as a LogLevel and choose the appropriate log function. */
	abstract fun log(levelString: String, message: String)

	enum class LogLevel(val level: Int, val color: Int) {
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
		/** Log level for messages that must be shown no matter what - but not for errors. */
		ALL(5, 0x999999);

		companion object {
			fun of(level: Int) = values().find { it.level == level } ?: INFO
		}
	}
}
