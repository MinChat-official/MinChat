package io.minchat.rest.gateway

import io.ktor.client.*
import io.ktor.client.plugins.websocket.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.minchat.common.*
import io.minchat.common.event.Event
import io.minchat.rest.MinchatRestLogger
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.net.URL
import kotlin.coroutines.EmptyCoroutineContext

/**
 * A low-level MinChat gateway client that receives raw
 * [Event] objects from the server and emits them into 
 * [RawGateway.events].
 *
 * @param useTsl Whether to use the `wss` protocol instead of `ws`.
 *     By default, `wss` is used if [baseUrl] uses `https`, and `ws` is used in all other cases.
 */
class RawGateway(
	val baseUrl: String,
	val client: HttpClient,
	val useTsl: Boolean = baseUrl.startsWith("https")
) : CoroutineScope {
	override val coroutineContext = client.newCoroutineContext(EmptyCoroutineContext)

	/**
	 * Current gateway websocket session.
	 *
	 * Do not interact with its channels directly!
	 */
	var session: DefaultClientWebSocketSession? = null
	/** A job that reads events from [session] and emits them into the flow */
	private var sessionReader: Job? = null
	val isConnected get() = 
		(session?.isActive ?: false) && (sessionReader?.isActive ?: false)

	private val eventsMutable = MutableSharedFlow<Event>()
	/**
	 * A hot flow of all MinChat events sent by the server.
	 *
	 * This flow never terminates normally.
	 */
	val events = eventsMutable.asSharedFlow()

	/**
	 * Invokes [connect] if [isConnected] is false. Does nothing otherwise.
	 */
	suspend fun connectIfNecessary() {
		if (!isConnected) connect()
	}
	
	/** 
	 * Connects to the chat websocket. 
	 *
	 * This method opens up a new WebSocket session and closes
	 * the previous one if it hasn't been closed yet.
	 *
	 * This method must be called before [events] can be read.
	 */
	suspend fun connect() {
		// invoked in the caller coroutine
		disconnect()
		openSession()

		// launched in the gateway coroutine
		sessionReader = this@RawGateway.launch {
			while (true) {
				var thisSession: DefaultClientWebSocketSession? = null

				try {
					if (!isConnected) {
						openSession()
						if (!isConnected) {
							delay(10L)
							continue
						}
					}
					thisSession = session!!

					val event = thisSession.receiveDeserialized<Event>()
					eventsMutable.emit(event)
				} catch (e: Exception) {
					MinchatRestLogger.log("warn", "Closing current raw gateway session due to exception: $e")
					delay(100L)

					// terminate this session
					thisSession?.cancel()
					if (session == thisSession) session = null
				}
			}
		}
	}

	private suspend fun openSession() {
		session?.cancel()
		session = client.webSocketSession {
			val location = URL(baseUrl)

			url.set(
				if (useTsl) "wss" else "ws",
				location.host,
				location.port,
				location.path.removeSuffix("/") + Route.Gateway.websocket
			)

			parameter("version", MINCHAT_VERSION.toString())
		}
	}

	/** 
	* Closes the already existing chat connection.
	*/
	fun disconnect() {
		session?.cancel()
		session = null
		sessionReader?.cancel()
		sessionReader = null
	}
}
