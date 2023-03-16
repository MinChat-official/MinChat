package io.minchat.rest.gateway

typealias EventTransformer<I : Event, O : MinchatRestClient> = (I, MinchatRestClient) -> O

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
	val rawGateway = RawGateway(client.host, client.host, client.httpClient)

	val isConnected by rawGateway::isConnected
	
	/**
	 * A hot flow of all events sent by the server to the client,
	 * transformed to their respective high-level types.
	 */
	val events =
		rawGateway.events.map {
			val transformation = transformationForClass(it
		}
	
	/** See [RawGateway.connectIfNeccessary]. */
	suspend fun connectIfNeccessary() =
		rawGateway.connectIfNeccessary()

	/** See [RawGateway.connect]. */
	suspend fun connect() =
		rawGateway.connect()

	/** See [RawGateway.disconnect]. */
	suspend fun disconnect() =
		rawGateway.disconnect()
	
	companion object {
		/** A map of all transformations a MinchatGateway can apply to the received events. */
		val transformations = mutableMapOf() as MutableMap<EventTransformer<*, *>>

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

			classes.forEach {
				val transformedName = "Minchat" + it.simpleName.removeSuffix("Event")
				val classConstructor = Class.forName("io.minchat.rest.event$transformedName")
					.getDeclaredConstructor(Event::class.java, MinchatRestClient::class.java)

				transformations[it] = { data, client ->
					classConstructor.newInstance(data, client)
				}
			}
		}

		/** Add an entry to [transformations]. */
		inline fun <reified I : Event, O : MinchatEvent> addTransformation(
			transformer: EventTransformer<I, O>
		) {
			transformations[I::class] = transformer
		}

		fun transformationForClass(eventClass: KClass<Event>) =
			transformations.getOrDefault(eventClass, null)
	}
}
