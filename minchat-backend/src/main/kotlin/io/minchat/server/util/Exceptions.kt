package io.minchat.server.util

class IllegalInputException(message: String?) : Exception(message)

class AccessDeniedException(message: String?) : Exception(message)

class EntityNotFoundException(message: String?) : Exception(message)

/** Throws an [IllegalInputException] with the specified message. */
fun illegalInput(message: String? = null): Nothing =
	throw IllegalInputException(message)

/** Throws an [AccessDeniedException] with the specified message. */
fun accessDenied(message: String? = null): Nothing =
	throw AccessDeniedException(message)

/** Throws an [EntityNotFoundException] with the specified message. */
fun notFound(message: String? = null): Nothing =
	throw EntityNotFoundException(message)

/** Throws an EntityNotFoundException if the receiver is <= 0. */
inline fun Int.throwIfNotFound(message: () -> String) = also {
	if (this <= 0) notFound(message())
}

/** Throws an [IllegalInputException] if the length of the string is not in the range. */
inline fun String.requireLength(range: IntRange, message: () -> String) = also {
	if (length !in range) illegalInput(message())
}
