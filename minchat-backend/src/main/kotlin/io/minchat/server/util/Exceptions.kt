package io.minchat.server.util

/** Http statua 400. */
class IllegalInputException(message: String?) : Exception(message)

/** Http status 401. */
class AccessDeniedException(message: String?) : Exception(message)

/** Http status 404. */
class EntityNotFoundException(message: String?) : Exception(message)

/** Http status 429. */
class TooManyRequestsException(message: String?) : Exception(message)

/** Throws an [IllegalInputException] with the specified message. */
fun illegalInput(message: String? = null): Nothing =
	throw IllegalInputException(message)

/** Throws an [AccessDeniedException] with the specified message. */
fun accessDenied(message: String? = null): Nothing =
	throw AccessDeniedException(message)

/** Throws an [EntityNotFoundException] with the specified message. */
fun notFound(message: String? = null): Nothing =
	throw EntityNotFoundException(message)

/** Throws an [TooManyRequestsException] with the specified message. */
fun tooManyRequests(message: String? = null): Nothing =
	throw TooManyRequestsException(message)

/** Throws an [EntityNotFoundException] if the receiver is <= 0. */
inline fun Int.throwIfNotFound(message: () -> String) = also {
	if (this <= 0) notFound(message())
}

/** Throws an [IllegalInputException] if the length of the string is not in the range. */
inline fun String.requireLength(range: IntRange, message: () -> String) = also {
	if (length !in range) illegalInput(message())
}
