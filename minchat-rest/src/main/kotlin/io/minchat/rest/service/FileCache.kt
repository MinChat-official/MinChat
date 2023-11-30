package io.minchat.rest.service

import io.minchat.common.BaseLogger
import io.minchat.rest.MinchatRestClient
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.*
import kotlinx.serialization.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.*
import java.util.*

class FileCache(
	val cacheDirectoryPath: String,
	val rest: MinchatRestClient
) {
	val lock = Mutex()
	val entries = Collections.synchronizedMap<Key, Entry>(mutableMapOf())

	private val listingFileName = "listing.json"
	private val json = Json {
		ignoreUnknownKeys = true
		encodeDefaults = true
		allowStructuredMapKeys = true
	}

	suspend fun getFile(hash: String, format: String): File? = lock.withLock {
		val key = Key(hash, format)
		entries[key]?.awaitFile()
	}

	suspend fun getData(hash: String, format: String) {
		val entry = lock.withLock { entries[Key(hash, format)] }
		entry?.awaitData()
	}

	/** Sets the contents of the entry for the given hash-format pair, using the default expiration time. */
	suspend fun setData(hash: String, format: String, data: InputStream) =
		withContext(Dispatchers.IO) {
			val entry = lock.withLock {
				val key = Key(hash, format)
				val entry = entries[key]

				if (entry == null) {
					Entry(
						"$cacheDirectoryPath/$hash.$format",
						System.currentTimeMillis() + FileCache.expirationTime
					).also {
						entries[key] = it
					}
				} else {
					entry
				}
			}

			entry.writeData(data)
		}

	/** Sets the contents of the entry for the given hash-format pair, using the default expiration time. */
	suspend fun setData(hash: String, format: String, data: ByteArray) =
		setData(hash, format, ByteArrayInputStream(data))

	/** Overrides the entry for the given hash-format pair with the provided file, with the provided expiration time. */
	suspend fun overrideFile(
		hash: String,
		format: String,
		file: File,
		expirationTime: Long = FileCache.expirationTime
	) {
		lock.withLock {
			entries[Key(hash, format)] = Entry(file.absolutePath, System.currentTimeMillis() + expirationTime)
		}
	}

	/** Creates a new not-loaded entry or returns an existing one. The left element indicates whether a new entry was created. */
	suspend fun getEntryOrCreate(key: Key): Pair<Boolean, Entry> =
		lock.withLock {
			val entry = entries[key]

			return if (entry == null) {
				true to Entry(
					"$cacheDirectoryPath/${key.hash}.${key.format}",
					System.currentTimeMillis() + FileCache.expirationTime
				)
			} else {
				false to entry
			}
		}

	inline suspend fun getDataOrPut(hash: String, format: String, crossinline block: suspend () -> ByteArray): ByteArray {
		val key = Key(hash, format)
		val (newEntry, entry) = getEntryOrCreate(key)

		return if (!newEntry) {
			entry.awaitData()
		} else {
			val data = withContext(Dispatchers.IO) {
				block()
			}
			entry.writeData(data)
			data
		}
	}

	inline suspend fun getFileOrPut(hash: String, format: String, crossinline block: suspend () -> ByteArray): File {
		val key = Key(hash, format)
		val (newEntry, entry) = getEntryOrCreate(key)

		return if (!newEntry) {
			entry.awaitFile()
		} else {
			val data = withContext(Dispatchers.IO) {
				block()
			}
			entry.writeData(data)
			entry.file
		}
	}

	fun loadFromDrive() {
		val listingFile = File(cacheDirectoryPath, listingFileName)
		if (listingFile.exists()) {
			try {
				val listing = json.decodeFromString<Map<Key, Entry>>(listingFile.readText())
					.filter { (_, v) -> v.file.exists() && !v.hasExpired() && v.isFullyCreated }

				entries += listing

				val totalSize = listing.values.sumOf { it.file.length() }
				logger.info("Loaded ${entries.size} cache entries from the drive.")
				logger.info("Total size: ${totalSize / 1024 / 1024} MB.")
			} catch (e: Exception) {
				logger.warn("Failed to load cache entries from the drive due to exception: $e")
			}
		} else {
			logger.info("No cache entries found on the drive.")
		}
	}

	fun saveToDrive() {
		val listingFile = File(cacheDirectoryPath, listingFileName)
		if (listingFile.exists() && listingFile.isDirectory) {
			listingFile.deleteRecursively()
		}
		listingFile.writeText(json.encodeToString(entries))
		logger.info("Saved ${entries.size} cache entries to the drive.")
	}

	@Serializable
	data class Key(
		val hash: String,
		val format: String
	)

	@Serializable
	data class Entry(
		val filepath: String,
		val expiresAt: Long? = null,
		@Volatile
		var isFullyCreated: Boolean = false
	) {
		val file get() = File(filepath)
		val localLock = Mutex()

		/** True if this entry can be safely removed. */
		fun hasExpired(): Boolean {
			if (expiresAt == null) return false
			return expiresAt < System.currentTimeMillis()
		}

		suspend fun awaitData(): ByteArray {
			val file = awaitFile()
			return withContext(Dispatchers.IO) {
				file.readBytes()
			}
		}

		suspend fun awaitFile(): File {
			while (!isFullyCreated) {
				delay(50L)
			}
			return localLock.withLock {
				file.takeIf { it.exists() }
			} ?: run {
				logger.error("Failed to load file $filepath due to missing data after awaiting.")
				error("File $filepath is missing data after awaiting.")
			}
		}

		fun getDataBlocking(): ByteArray? {
			return file.takeIf { it.exists() }?.readBytes()
		}

		suspend fun writeData(data: InputStream) {
			localLock.withLock {
				try {
					if (file.exists() && file.isDirectory) {
						file.delete()
					}
					file.parentFile.mkdirs()

					data.copyTo(file.outputStream())

					isFullyCreated = true
				} catch (e: Exception) {
					file.delete()

					logger.warn("Failed to save file $file due to exception: $e")

					isFullyCreated = false
				}
			}
		}

		suspend fun writeData(data: ByteArray) =
			writeData(ByteArrayInputStream(data))
	}

	companion object {
		/** The time after which a cache entry can be safely deleted. 3 days. */
		const val expirationTime = 1000L * 60 * 60 * 24 * 3
		private val logger = BaseLogger.getContextSawmill()
	}
}
