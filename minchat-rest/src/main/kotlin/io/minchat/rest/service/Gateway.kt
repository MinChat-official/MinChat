package io.minchat.rest.service

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

class Gateway(
	baseUrl: String,
	client: HttpClient
) : RestService(baseUrl, client), CoroutineScope {
	override val coroutineContext = client.newCoroutineContext(EmptyCoroutineContext)
	private val lock = Any()

	/**
	 * Current gateway websocket session.
	 *
	 * Do not interact with its channels directly!
	 */
	var session: DefaultClientWebSocketSession? = null
	val isConnected get() = session?.isActive ?: false
	/**
	 * A cold flow of all MinChat events sent by the server.
	 *
	 * If [connect] hasn't been called yet, resolving this flow
	 * will result in an attempt to connect to the gateway first.
	 *
	 * The channel never closes unless the underlying connection
	 * gets terminated.
	 *
	 * Currently, there is no way to safely pause this channel.
	 */
	val events by lazy {
		produce {
			while (true) {
				if (!isConnected) connect()

				if (!isConnected) {
					delay(10L)
					continue
				}
				val session = session!!

				try {
					val event = session.receiveDeserialized<Event>()
					send(event)
				} catch (e: Exception) {
					// TODO: handle it properly
					e.printStackTrace()
				}
			}
		}
	}

	/** 
	 * Connects to the chat websocket. 
	 *
	 * This method opens up a new WebSocket session.
	 */
	suspend fun connect() {
		session = client.webSocketSession(makeRouteUrl(Route.Gateway.websocket))
	}

	// /** 
	//  * Closes the already existing chat connection.
	//  */
	// suspend fun disconnect() {
	// 
	// }
}
