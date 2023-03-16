package io.minchat.server.modules

import io.ktor.websocket.*
import io.ktor.serialization.*
import io.ktor.serialization.kotlinx.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import io.minchat.common.*
import io.minchat.common.Route
import io.minchat.common.event.*
import io.minchat.server.*
import io.minchat.server.util.*
import java.util.*
import java.util.concurrent.*
import java.util.concurrent.atomic.*
import kotlinx.coroutines.*
import kotlinx.serialization.json.*

class GatewayModule : MinchatServerModule() {
	val connections = Collections.synchronizedSet<Connection>(LinkedHashSet())
	/** Events to send to the clients. */
	val pendingEvents = ConcurrentLinkedQueue<Event>()

	val jsonConverter = KotlinxWebsocketSerializationConverter(Json)

	override fun Application.onLoad() {
		install(WebSockets) {
			pingPeriodMillis = Constants.connectionPingPeriod
			timeoutMillis = Constants.connectionTimeout
			maxFrameSize = 1024 * 30L // there's no way an event could take more than that
			masking = false

			contentConverter = jsonConverter
		}

		routing {
			webSocket(Route.Gateway.websocket) {
				val connection = Connection(this).also(connections::add)

				Log.lifecycle { "Incoming: $connection" }

				try {
					handleConnection(connection)
					Log.lifecycle { "$connection has closed." }
				} catch (e: Exception) {
					Log.error(e) { "$connection has been terminated" }
				} finally {
					connections -= connection
				}
			}
		}
	}
	
	override suspend fun ServerContext.afterLoad() {
		// A coroutine that sends events
		launch {
			while (true) {
				if (pendingEvents.isNotEmpty()) {
					val event = pendingEvents.remove()
					val frame = jsonConverter.serialize(event)

					connections.forEach {
						it.session.outgoing.send(frame)
						Log.info { "Sending $event to ${it.id}" }
					}
				}
				delay(20L)
			}
		}
	}

	suspend fun handleConnection(connection: Connection) {
		// suspend until this connection terminates
		connection.session.coroutineContext[Job]?.join()
	}

	/** Requests to send the event to all connections. */
	fun send(event: Event) {
		pendingEvents += event
	}

	class Connection(val session: WebSocketServerSession) {
		val id = lastId.getAndIncrement()

		override fun toString() =
			"Connection #$id"

		companion object {
			val lastId = AtomicLong(0L)
		}
	}
}
