package io.minchat.common

object Constants {
	/**
	 * Passwords must be hashed witn bcrypt before being sent to the server.
	 * This number indicates the required hash complexity.
	 */
	val hashComplexityPre = 11
}
