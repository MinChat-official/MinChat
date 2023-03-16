package io.minchat.rest.gateway

import io.ktor.http.*
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.websocket.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.minchat.common.*
import io.minchat.common.entity.*
import io.minchat.common.event.*
import kotlin.coroutines.EmptyCoroutineContext
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.*

/**
 * A low-level MinChat gateway client that receives raw
 * [Event] objects from the server and emits them into 
 * [RawGateway.events].
 */
class RawGateway(
	val host: String,
	val port: Int?,
	val client: HttpClient
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
	suspend fun connectIfNeccessary() {
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
		disconnect()

		openSession()

		sessionReader = launch {
			while (true) {
				if (!isConnected) {
					openSession()

					if (!isConnected) {
						delay(10L)
						continue
					}
				}
				val session = session!!

				try {
					val event = session.receiveDeserialized<Event>()
					eventsMutable.connect()
				} catch (e: Exception) {
					// terminate this session
					session?.cancel()
					session = null
				}
			}
		}
	}

	private suspend fun openSession() {
		session?.cancel()
		session = client.webSocketSession(
			host = host,
			port = port,
			path = Route.Gateway.websocket
		)
	}

	/** 
	* Closes the already existing chat connection.
	*/
	suspend fun disconnect() {
		session?.cancel()
		session = null
		sessionReader?.cancel()
		sessionReader = null
	}
}
