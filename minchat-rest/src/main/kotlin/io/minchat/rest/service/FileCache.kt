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
	}

	suspend fun getData(hash: String, format: String) =
		withContext(Dispatchers.IO) { getDataBlocking(hash, format) }

	suspend fun setData(hash: String, format: String, data: InputStream) =
		withContext(Dispatchers.IO) { setDataBlocking(hash, format, data) }

	suspend fun setData(hash: String, format: String, data: ByteArray) =
		withContext(Dispatchers.IO) { setDataBlocking(hash, format, data) }

	fun getDataBlocking(hash: String, format: String): ByteArray? {
		val key = Key(hash, format)

		return entries[key]?.file?.readBytes()
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
		} catch (e: Exception) {
			file.delete()

			MinchatRestLogger.log("warn", "Failed to save file $name due to exception: $e")
		}

		entries[key] = Entry(file.absolutePath)
	}

	fun setDataBlocking(hash: String, format: String, data: ByteArray) {
		setDataBlocking(hash, format, ByteArrayInputStream(data))
	}

	fun loadFromDrive() {
		val listingFile = File(cacheDirectoryPath, listingFileName)
		if (listingFile.exists()) {
			try {
				val listing = json.decodeFromString<Map<Key, Entry>>(listingFile.readText())
					.filter { (k, v) -> v.file.exists() }

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
		val filepath: String
	) {
		val file get() = File(filepath)
	}
}
