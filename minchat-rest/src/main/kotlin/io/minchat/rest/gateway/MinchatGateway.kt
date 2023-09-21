package io.minchat.rest.gateway

import io.minchat.common.event.*
import io.minchat.rest.*
import io.minchat.rest.event.MinchatEvent
import io.minchat.rest.gateway.MinchatGateway.EventTransformer
import kotlinx.coroutines.flow.*
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
	val rawGateway = RawGateway(client.baseUrl, client.httpClient)

	val isConnected by rawGateway::isConnected
	
	/**
	 * A hot flow of all events sent by the server to the client,
	 * transformed to their respective high-level types.
	 */
	val events: Flow<MinchatEvent<out Event>> =
		rawGateway.events.mapNotNull {
			val transformation = transformationForClass<Event>(it::class)

			transformation?.run { transform(it, client) }
				?: run {
					MinchatRestLogger.log("warn", "No transformation found for event class ${it::class.qualifiedName}")
					null
				}
		}
	
	/** See [RawGateway.connectIfNecessary]. */
	suspend fun connectIfNecessary() =
		rawGateway.connectIfNecessary()

	/** See [RawGateway.connect]. */
	suspend fun connect() =
		rawGateway.connect()

	/** See [RawGateway.disconnect]. */
	fun disconnect() =
		rawGateway.disconnect()
	
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
				ChannelDeleteEvent::class
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
