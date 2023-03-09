package io.minchat.client.misc

import io.minchat.common.BuildVersion

class VersionMismatchException(val server: BuildVersion, val client: BuildVersion) : RuntimeException() {
	override val message = 
		"The version of the server is not compatible with the version of the client: ${when {
			server > client -> "The server uses a newer version of MinChat"
			else -> "The server uses an outdated version of MinChat"
		}} (server $server vs client $client)"
}
