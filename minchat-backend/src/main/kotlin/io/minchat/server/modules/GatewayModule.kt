package io.minchat.server.modules

import io.ktor.websocket.*
import io.ktor.serialization.kotlinx.*
import io.ktor.server.application.*
import io.ktor.server.plugins.websocket.*
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

	override fun Application.onLoad() {
		install(WebSockets) {
			pingPeriodMillis = Constants.connectionPingPeriod
			timeoutMillis = Constants.connectionTimeout
			maxFrameSize = 1024 * 30L // there's no way an event could take more than that
			masking = false

			contentConverter = KotlinxWebsocketSerializationConverter(Json)
		}

		routing {
			webSocket(Route.Gateway.websocket) {
				val connection = Connection(this).also(connections::add)

				Log.info { "Incoming connection: $connection" }

				try {
					handleConnection(connection)
				} catch (e: Exception) {
					Log.error(e) { "Connection ${connection.id} terminated" }
				} finally {
					connections -= connection
				}
			}
		}
	}
	
	override suspend fun Context.afterLoad() {
		// A coroutine that sends events
		launch {
			while (pendingEvents.isNotEmpty()) {
				val event = pendingEvents.remove()

				connections.forEach {
					it.session.sendSerialized(event)
				}
			}
		}
	}

	suspend fun handleConnection(connection: Connection) {
		// todo: do I need anything here?
	}

	/** Requests to send the event to all connections.. */
	fun send(event: Event) {
		pendingEvents += event
	}

	class Connection(val session: WebSocketServerSession) {
		val id = lastId.getAndIncrement()

		companion object {
			val lastId = AtomicLong(0L)
		}
	}
}
