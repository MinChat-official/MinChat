package io.minchat.client.ui.managers

import com.github.mnemotechnician.mkui.delegates.setting
import io.minchat.client.*
import io.minchat.client.misc.Log
import io.minchat.rest.entity.MinchatChannel
import kotlinx.coroutines.launch
import kotlinx.serialization.*
import kotlinx.serialization.json.Json
import java.util.concurrent.ConcurrentHashMap

object UnreadsManager {
	private val json = Json {
		ignoreUnknownKeys = true
		encodeDefaults = true
	}

	/** Maps channel ids with last times they were visited at. */
	val channelVisitCache = ConcurrentHashMap<Long, Long>()
	private var lastSave = 0L

	private var unreadsJson by setting("", "minchat")
	private var cacheMaxSize by setting(50, "minchat")

	init {
		ClientEvents.subscribe<ChannelChangeEvent> {
			// Mark the channel as "read now"
			setForChannel(it.channel.id, System.currentTimeMillis())
		}
	}

	/**
	 * Gets the last timestamp the channel was visited at.
	 *
	 * If it was never visited, returns -1 (so that new channels are marked unread).
	 */
	fun getForChannel(id: Long) = channelVisitCache[id] ?: -1L

	/** Sets the last timestamp the channel was visited at. */
	fun setForChannel(id: Long, timestamp: Long) {
		channelVisitCache[id] = timestamp
		save()
	}

	/** Checks if the channel may have unread messages. */
	fun hasUnreads(channel: MinchatChannel) =
		channel.data.lastMessageTimestamp > getForChannel(channel.id)

	/** Loads the unreads cache from the respective settings. Called on startup. */
	fun load() {
		val cache = try {
			unreadsJson.takeIf { it.isNotBlank() && it.length > 5 }?.let {
				json.decodeFromString<CacheState>(it)
			} ?: return
		} catch (e: Exception) {
			Log.error(e) { "Failed to load unreads cache" }
			return
		}

		channelVisitCache.putAll(cache.channelVisitCache)
	}

	/** Saves the unreads cache to the respective settings. Called when the cache changes. */
	fun save() {
		// Do not allow saving more than once per 5 seconds
		if (System.currentTimeMillis() - lastSave < 5000L) return
		lastSave = System.currentTimeMillis()

		unreadsJson = json.encodeToString(CacheState(channelVisitCache))

		if (channelVisitCache.size > cacheMaxSize) Minchat.chatFragment.apply {
			// launch a job to fetch all channels and dms and clear the deleted ones
			// this is done in the context of a chat fragment to notify the user
			launch {
				val notification = notification("Cache overflow! Trying to clear unused entries...", 5)
				runSafe {
					val channels = Minchat.client.getAllChannels()
					val dms = Minchat.client.getAllDMChannels().values.flatten()
					val all = channels + dms

					val iterator = channelVisitCache.iterator()
					iterator.forEach { (id, _) ->
						if (all.none { it.id == id }) {
							iterator.remove()
						}
					}

					// Update the max cache size to a realistic number
					cacheMaxSize = (all.size * 1.5).toInt() + 10

					notification.cancel()
				}
			}
		}
	}

	@Serializable
	data class CacheState(
		val channelVisitCache: Map<Long, Long>
	)
}

fun MinchatChannel.hasUnreads() = UnreadsManager.hasUnreads(this)
