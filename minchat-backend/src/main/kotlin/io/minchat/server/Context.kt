package io.minchat.server

import io.ktor.server.engine.*
import io.minchat.server.modules.MinchatServerModule
import io.minchat.server.util.*
import java.io.File
import kotlinx.coroutines.*

class Context(
	val server: ApplicationEngine,
	val modules: List<MinchatServerModule>,
	val dataDir: File,
	val dbFile: File
) : CoroutineScope {
	val application by server::application

	val exceptionHandler = CoroutineExceptionHandler { _, e -> 
 		Log.error(e) { "An exception has occurred" }
	}
	override val coroutineContext = SupervisorJob() + exceptionHandler + Dispatchers.Default

	inline fun <reified T : MinchatServerModule> module() =
		modules.find { it is T }
}
