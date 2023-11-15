package io.minchat.rest.service

import io.minchat.rest.*
import kotlinx.coroutines.*
import kotlinx.serialization.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.*

class FileCache(
	val cacheDirectoryPath: String,
	val rest: MinchatRestClient
) {
	val entries = mutableMapOf<Key, Entry>()

	private val listingFileName = "listing.json"
	private val json = Json {
		ignoreUnknownKeys = true
		encodeDefaults = true
		allowStructuredMapKeys = true
	}

	fun getFile(hash: String, format: String): File? {
		val key = Key(hash, format)
		return entries[key]?.file
	}

	suspend fun getData(hash: String, format: String) =
		withContext(Dispatchers.IO) { getDataBlocking(hash, format) }

	/** Sets the contents of the entry for the given hash-format pair, using the default expiration time. */
	suspend fun setData(hash: String, format: String, data: InputStream) =
		withContext(Dispatchers.IO) { setDataBlocking(hash, format, data) }

	/** Sets the contents of the entry for the given hash-format pair, using the default expiration time. */
	suspend fun setData(hash: String, format: String, data: ByteArray) =
		withContext(Dispatchers.IO) { setDataBlocking(hash, format, data) }

	/** Overrides the entry for the given hash-format pair with the provided file, with the provided expiration time. */
	fun overrideFile(
		hash: String,
		format: String,
		file: File,
		expirationTime: Long = FileCache.expirationTime
	) {
		entries[Key(hash, format)] = Entry(file.absolutePath, System.currentTimeMillis() + expirationTime)
	}

	fun getDataBlocking(hash: String, format: String): ByteArray? {
		val key = Key(hash, format)
		val entry = entries[key]

		return entry?.file?.readBytes()
	}

	fun setDataBlocking(hash: String, format: String, data: InputStream) {
		val key = Key(hash, format)
		val name = "${hash}.${format}"
		val file = File(cacheDirectoryPath, name)

		try {
			if (file.exists() && file.isDirectory) {
				file.delete()
			}
			file.parentFile.mkdirs()

			data.copyTo(file.outputStream())

			entries[key] = Entry(file.absolutePath, System.currentTimeMillis() + expirationTime)
		} catch (e: Exception) {
			file.delete()

			MinchatRestLogger.log("warn", "Failed to save file $name due to exception: $e")
		}
	}

	fun setDataBlocking(hash: String, format: String, data: ByteArray) {
		setDataBlocking(hash, format, ByteArrayInputStream(data))
	}

	inline suspend fun getDataOrPut(hash: String, format: String, crossinline block: suspend () -> ByteArray): ByteArray {
		val key = Key(hash, format)
		val entry = entries[key]

		if (entry != null) {
			return entry.file.readBytes()
		} else {
			return withContext(Dispatchers.IO) {
				val data = block()
				setDataBlocking(hash, format, data)
				return@withContext data
			}
		}
	}

	inline suspend fun getFileOrPut(hash: String, format: String, crossinline block: suspend () -> ByteArray): File {
		val key = Key(hash, format)
		val entry = entries[key]

		if (entry != null) {
			return entry.file
		} else {
			return withContext(Dispatchers.IO) {
				val data = block()
				setDataBlocking(hash, format, data)
				File(cacheDirectoryPath, "${hash}.${format}")
			}
		}
	}

	fun loadFromDrive() {
		val listingFile = File(cacheDirectoryPath, listingFileName)
		if (listingFile.exists()) {
			try {
				val listing = json.decodeFromString<Map<Key, Entry>>(listingFile.readText())
					.filter { (k, v) -> v.file.exists() && !v.hasExpired() }

				entries += listing

				val totalSize = listing.values.sumOf { it.file.length() }
				MinchatRestLogger.log("info", "Loaded ${entries.size} cache entries from the drive.")
				MinchatRestLogger.log("info", "Total size: ${totalSize / 1024 / 1024} MB.")
			} catch (e: Exception) {
				MinchatRestLogger.log("warn", "Failed to load cache entries from the drive due to exception: $e")
			}
		} else {
			MinchatRestLogger.log("info", "No cache entries found on the drive.")
		}
	}

	fun saveToDrive() {
		val listingFile = File(cacheDirectoryPath, listingFileName)
		if (listingFile.exists() && listingFile.isDirectory) {
			listingFile.deleteRecursively()
		}
		listingFile.writeText(json.encodeToString(entries))

		MinchatRestLogger.log("info", "Saved ${entries.size} cache entries to the drive.")
	}

	@Serializable
	data class Key(
		val hash: String,
		val format: String
	)

	@Serializable
	data class Entry(
		val filepath: String,
		val expiresAt: Long? = null
	) {
		val file get() = File(filepath)

		/** True if this entry can be safely removed. */
		fun hasExpired(): Boolean {
			if (expiresAt == null) return false
			return expiresAt < System.currentTimeMillis()
		}
	}

	companion object {
		/** The time after which a cache entry can be safely deleted. 3 days. */
		const val expirationTime = 1000L * 60 * 60 * 24 * 3
	}
}
