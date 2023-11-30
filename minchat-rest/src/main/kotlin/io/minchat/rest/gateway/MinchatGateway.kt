package io.minchat.rest.gateway

import io.minchat.common.BaseLogger
import io.minchat.common.BaseLogger.Companion.getContextSawmill
import io.minchat.common.event.*
import io.minchat.rest.*
import io.minchat.rest.event.MinchatEvent
import io.minchat.rest.gateway.MinchatGateway.EventTransformer
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.lang.reflect.Constructor
import kotlin.reflect.KClass

/**
 * A high-level class wrapping a low-level [RawGateway].
 *
 * This class receives low-level event objects from the server
 * and then transforms them into high-level ones.
 */
class MinchatGateway(
	val client: MinchatRestClient
) {
	/**
	 * The underlying raw MinChat gateway.
	 */
	var rawGateway = RawGateway(client.baseUrl, client.httpClient, token = client.account?.token)
	val isConnected by rawGateway::isConnected

	private val logger = BaseLogger.getContextSawmill()
	
	/**
	 * A hot flow of all events sent by the server to the client,
	 * transformed to their respective high-level types.
	 */
	val events: Flow<MinchatEvent<out Event>> =
		rawGateway.events.mapNotNull {
			val transformation = transformationForClass<Event>(it::class)

			transformation?.run { transform(it, client) }
				?: run {
					logger.warn("No transformation found for event class ${it::class.qualifiedName}")
					null
				}
		}

	init {
		client.accountObservable.observe {
			if (isConnected) {
				logger.lifecycle("MinChat gateway: reconnecting due to account change.")
				disconnect()
				client.launch {
					connect()
				}
			}
		}
	}
	
	/** See [RawGateway.connectIfNecessary]. */
	suspend fun connectIfNecessary() {
		rawGateway.token = client.account?.token
		rawGateway.connectIfNecessary()
	}

	/** See [RawGateway.connect]. */
	suspend fun connect() {
		rawGateway.token = client.account?.token
		rawGateway.connect()
	}

	/** See [RawGateway.disconnect]. */
	fun disconnect() =
		rawGateway.disconnect()

	/** Adds a failure listener, which gets called when the underlying gateway experiences a failure decoding an event. */
	fun onFailure(listener: (Exception) -> Unit) {
		rawGateway.onFailure(listener)
	}
	
	companion object {
		/** A map of all transformations a MinchatGateway can apply to the received events. */
		val transformations = mutableMapOf<KClass<out Event>, EventTransformer<*, *>>()

		init {
			// All these classes share a common constructor and name pattern, so we can use reflection allocate them
			// All of them are located in io.minchat.common.event
			val classes = arrayOf(
				MessageCreateEvent::class,
				MessageModifyEvent::class,
				MessageDeleteEvent::class,
				UserModifyEvent::class,
				ChannelCreateEvent::class,
				ChannelModifyEvent::class,
				ChannelDeleteEvent::class,
				ChannelGroupCreateEvent::class,
				ChannelGroupModifyEvent::class,
				ChannelGroupDeleteEvent::class
			)

			@Suppress("UNCHECKED_CAST")
			classes.forEach {
				val simpleName = it.simpleName?.removeSuffix("Event") ?: return@forEach
				val classConstructor = Class.forName("io.minchat.rest.event.Minchat$simpleName")
					.getDeclaredConstructor(it.java, MinchatRestClient::class.java)
					.let { it as Constructor<MinchatEvent<Event>> }

				transformations[it] = EventTransformer<Event, _> { data, client ->
					classConstructor.newInstance(data, client)
				}
			}
		}

		/** Add an entry to [transformations]. */
		inline fun <reified Input : Event, Output : MinchatEvent<Input>> addTransformation(
			transformer: EventTransformer<Input, Output>
		) {
			transformations[Input::class] = transformer
		}

		@Suppress("UNCHECKED_CAST")
		fun <T : Event> transformationForClass(eventClass: KClass<out Event>) =
			transformations.getOrDefault(eventClass, null) as EventTransformer<T, MinchatEvent<T>>?
	}

	fun interface EventTransformer<Input : Event, Output : MinchatEvent<Input>> {
		fun transform(event: Input, client: MinchatRestClient): Output
	}
}
