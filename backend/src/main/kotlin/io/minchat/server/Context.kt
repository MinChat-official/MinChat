package io.minchat.server

import io.ktor.server.engine.*
import io.minchat.server.modules.MinchatServerModule

class Context(
	val server: ApplicationEngine,
	val modules: List<MinchatServerModule>
)
