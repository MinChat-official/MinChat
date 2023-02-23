package io.minchat.server

import io.ktor.server.engine.*
import io.minchat.server.modules.MinchatServerModule
import java.io.File

class Context(
	val server: ApplicationEngine,
	val modules: List<MinchatServerModule>,
	val dataDir: File,
	val dbFile: File
) {
	val application by server::application
}
