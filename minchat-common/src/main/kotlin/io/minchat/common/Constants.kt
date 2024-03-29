package io.minchat.common

object Constants {
	// TODO:
	//  this must be randomly generated and stored on the server
	//  and sent to the client before it makes an authorization attempt!!!
	/**
	 * Passwords must be hashed witn bcrypt before being sent to the server.
	 * This string is the default hash salt.
	 */
	const val hashSaltPre = "\$2a\$11\$IvRbjBtbeZqVwqrnhXg7zO"
	/**
	 * Passwords must be hashed witn bcrypt before being sent to the server.
	 * This number indicates the required hash complexity.
	 */
	const val hashComplexityPre = 11

	/** Deleted accounts have this name. */
	const val deletedAccountName = "<DELETED>"
	/**
	 * Deleted accounts have password hash instead of a normal one.
	 *
	 * Accounts with this hash can not be logged into 
	 * because this value is not a valid bcrypt hash.
	 */
	const val deletedAccountPasswordHash = "<?!>"

	/** Websocket connection timeout. */
	val connectionTimeout = 10000L
	/** Websocket connection ping period. */
	val connectionPingPeriod = 4000L
}
