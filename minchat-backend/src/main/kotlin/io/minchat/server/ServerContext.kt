package io.minchat.server

import io.ktor.server.engine.*
import io.minchat.common.BaseLogger
import io.minchat.common.event.Event
import io.minchat.server.modules.*
import kotlinx.coroutines.*
import java.io.File

class ServerContext(
	val engine: ApplicationEngine,
	val modules: List<AbstractMinchatServerModule>,
	val dataDir: File,
	val dbFile: File,
	val globalLogger: BaseLogger.LoggerSawmill
) : CoroutineScope {
	val application by engine::application

	val exceptionHandler = CoroutineExceptionHandler { _, e -> 
 		globalLogger.error(e) { "An exception has occurred" }
	}
	override val coroutineContext = SupervisorJob() + exceptionHandler + Dispatchers.Default

	/** Returns the first module of the specified type. */
	inline fun <reified T : AbstractMinchatServerModule> module() =
		modules.find { it is T } as T?
	
	/** 
	 * Sends the specified gateway event to all connected clients.
	 * Does nothing if the gateway module is not present in this context. 
	 */
	fun sendEvent(event: Event) = 
		module<GatewayModule>()?.send(event)
}
