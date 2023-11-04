package io.minchat.server.modules

import io.ktor.serialization.*
import io.ktor.serialization.kotlinx.*
import io.ktor.server.application.*
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import io.minchat.common.*
import io.minchat.common.Route
import io.minchat.common.entity.User
import io.minchat.common.event.Event
import io.minchat.server.ServerContext
import io.minchat.server.databases.Users
import io.minchat.server.util.Log
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.*
import kotlinx.coroutines.flow.*
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.sql.transactions.transaction
import java.util.*
import java.util.concurrent.atomic.AtomicLong
import kotlin.coroutines.cancellation.CancellationException

class GatewayModule : AbstractMinchatServerModule() {
	val connections = Collections.synchronizedSet<Connection>(LinkedHashSet())
	/** Events to send to the clients. */
	val pendingEvents = Channel<Event>(capacity = Channel.BUFFERED, onBufferOverflow = BufferOverflow.DROP_LATEST)

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
				val version = call.request.queryParameters["version"]
					?.let(BuildVersion::fromStringOrNull)
				val user = call.request.queryParameters["token"]?.let {
					transaction { Users.getByToken(it) }
				}
				val connection = Connection(this, version, user)
					.also(connections::add)

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
		val server = this

		// A coroutine that sends events
		pendingEvents.consumeAsFlow().onEach { event ->
			try {
				val frame = jsonConverter.serialize(event)

				val recipients = event.recipients?.map { id ->
					connections.find { it.user?.id == id }
				}?.filterNotNull() ?: connections

				if (recipients.isEmpty()) {
					Log.lifecycle { "Skipping $event because there are no valid recipients." }
					return@onEach
				}

				Log.lifecycle { "Sending $event to ${recipients.size} connections" }

				recipients.forEach {
					try {
						it.session.outgoing.send(frame)
					} catch (e: Exception) {
						Log.error { "Failed to send $event to $it! Message: ${e.message}" }
					}
				}
			} catch (e: Exception) {
				if (e is CancellationException) throw e
				Log.error { "Failed to send an event! $e" }
			}
		}.launchIn(server)
	}

	suspend fun handleConnection(connection: Connection) {
		// suspend until this connection terminates
		connection.session.coroutineContext[Job]?.join()
	}

	/** Requests to send the event to all connections. */
	fun send(event: Event) {
		val result = pendingEvents.trySend(event)
		if (result.isSuccess.not()) {
			Log.error { "Count not send $event: buffer overflow! Is the coroutine dead?!" }
		}
	}

	class Connection(
		val session: WebSocketServerSession,
		val clientVersion: BuildVersion?,
		val user: User?
	) {
		val id = lastId.getAndIncrement()

		override fun toString() =
			"Connection #$id (v${clientVersion ?: "unknown"}, ${user?.loggable()})"

		companion object {
			val lastId = AtomicLong(0L)
		}
	}
}
