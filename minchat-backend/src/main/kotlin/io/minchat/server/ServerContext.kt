package io.minchat.server

import io.ktor.server.engine.*
import io.minchat.common.event.Event
import io.minchat.server.modules.*
import io.minchat.server.util.*
import java.io.File
import kotlinx.coroutines.*

class ServerContext(
	val engine: ApplicationEngine,
	val modules: List<MinchatServerModule>,
	val dataDir: File,
	val dbFile: File
) : CoroutineScope {
	val application by engine::application

	val exceptionHandler = CoroutineExceptionHandler { _, e -> 
 		Log.error(e) { "An exception has occurred" }
	}
	override val coroutineContext = SupervisorJob() + exceptionHandler + Dispatchers.Default

	/** Returns the first module of the specified type. */
	inline fun <reified T : MinchatServerModule> module() =
		modules.find { it is T } as T?
	
	/** 
	 * Sends the specified gateway event to all connected clients.
	 * Does nothing if the gateway module is not present in this context. 
	 */
	fun sendEvent(event: Event) = 
		module<GatewayModule>()?.send(event)
}
