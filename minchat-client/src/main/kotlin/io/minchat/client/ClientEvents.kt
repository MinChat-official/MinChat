package io.minchat.client

import arc.Core
import arc.struct.SnapshotSeq
import io.minchat.client.misc.Log
import io.minchat.rest.MinchatRestClient
import io.minchat.rest.entity.*
import kotlinx.coroutines.launch

object ClientEvents {
	private val subscribers = mutableMapOf<Class<*>, SnapshotSeq<Subscriber<*>>>()

	/** This debug setting allows to see all registered subscribers and fired events, but is only read during startup. */
	private val eventDebug by lazy { Core.settings.getBool("minchat.enable-event-debug") }

	/**
	 * Subscribes to the event type, [subscriber] gets invoked every time [eventType] is fired.
	 */
	fun <T> subscribe(eventType: Class<T>, subscriber: Subscriber<T>) {
		val list = synchronized(subscribers) {
			subscribers.computeIfAbsent(eventType, { SnapshotSeq() })
		}

		synchronized(list) {
			list.add(subscriber)
		}

		if (eventDebug) {
			Log.debug { "Client events: registered subscriber for $eventType at ${getStackElement("subscribe")}" }
		}
	}

	/**
	 * Subscribes to the event type, [subscriber] gets invoked every time an event of type [T] is fired.
	 */
	inline fun <reified T> subscribe(subscriber: Subscriber<T>) {
		subscribe(T::class.java, subscriber)
	}

	/** Fires an event, notifying all subscribers of the given type in a suspending manner. */
	@Suppress("UNCHECKED_CAST")
	suspend fun fire(event: Any) {
		if (eventDebug) {
			Log.debug { "Client events: fired $event at ${getStackElement("fire")}" }
		}

		val subscriberList = synchronized(subscribers) {
			subscribers[event::class.java]
		} ?: return

		subscriberList.withSnapshot { size, items ->
			for (i in 0..<size) {
				try {
					(items[i] as Subscriber<Any>).receiveEvent(event)
				} catch (e: Exception) {
					Log.debug { "Subscriber of event ${event::class.java} failed with exception: $e" }
				}
			}
		}
	}

	/** Fires an event, notifying all existing subscribers of the given type in an asynchronous manner. */
	@Suppress("UNCHECKED_CAST")
	fun fireAsync(event: Any) {
		if (eventDebug) {
			Log.debug { "Client events: fired async $event at ${getStackElement("fireAsync")}" }
		}

		Minchat.launch {
			val subscriberList = synchronized(subscribers) {
				subscribers[event::class.java]
			} ?: return@launch

			subscriberList.withSnapshot { size, items ->
				for (i in 0..<size) {
					try {
						(items[i] as Subscriber<Any>).receiveEvent(event)
					} catch (e: Exception) {
						Log.warn { "Subscriber of event ${event::class.java} failed with exception: $e" }
					}
				}
			}
		}
	}

	private fun getStackElement(ignoredMethod: String) =
		Throwable().stackTrace.find {
			it.fileName != "ClientEvents.kt"
			&& it.methodName != ignoredMethod
		}

	private inline fun <T : Any> SnapshotSeq<T>.withSnapshot(block: (size: Int, itmes: Array<out Any>) -> Unit) {
		try {
			block(size, begin())
		} finally {
			end()
		}
	}

	fun interface Subscriber<T> {
		suspend fun Subscriber<T>.receive(event: T)

		suspend fun receiveEvent(event: T) {
			with(this) { receive(event) }
		}

		/** Unsubscribes from this event. Can be invoked from the action of the subscriber. */
		fun unsubscribe() {
			synchronized(subscribers) {
				subscribers.forEach { (_, list) ->
					list.remove(this)
				}
			}
		}
	}
}

/** Fired when MinChat loads. */
object LoadEvent

/** Fired when MinChat connects to a remote server. */
data class ConnectEvent(val url: String)

/** Fired when the user opens or closes the MinChat chat dialog. */
data class ChatDialogEvent(val isClosed: Boolean)

/** Fired when the user authorizes in MinChat. */
data class AuthorizationEvent(val client: MinchatRestClient, val manually: Boolean)

/** Fired when the user switches to a minchat channel. */
data class ChannelChangeEvent(val channel: MinchatChannel)

/** Fired when the client user sends a message in a channel. */
data class ClientMessageSendEvent(val message: MinchatMessage)

/** Fired when the client user edits a sent message (not necessarily their own). */
data class ClientMessageEditEvent(val old: MinchatMessage, val new: MinchatMessage)

/** Fired when the client user deletes a sent message (not necessarily their own). */
data class ClientMessageDeleteEvent(val old: MinchatMessage)
