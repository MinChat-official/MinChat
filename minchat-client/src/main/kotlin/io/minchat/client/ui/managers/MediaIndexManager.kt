package io.minchat.client.ui.managers

import io.minchat.client.Minchat
import io.minchat.common.BaseLogger
import io.minchat.common.BaseLogger.Companion.getContextSawmill
import kotlinx.serialization.*
import kotlinx.serialization.json.Json
import mindustry.Vars
import java.nio.file.*
import kotlin.io.path.*

/**
 * Manages an index of all relevant media files stored on the user's device,
 * with the purpose of providing swift access to them.
 *
 * Currently indexes:
 * - images
 */
object MediaIndexManager {
	val indexFile = Minchat.cacheDir.resolve("media-index.json")
	val index = Index(mutableSetOf())

	private val logger = BaseLogger.getContextSawmill()

	fun saveIndex() {
		try {
			indexFile.writeText(Json.encodeToString(index))
		} catch (e: Exception) {
			logger.error(e) { "Failed to save media index" }
		}
	}

	fun loadIndex() {
		try {
			val new = Json.decodeFromString<Index>(indexFile.readText())

			index.images.addAll(new.images)
		} catch (e: Exception) {
			logger.error(e) { "Failed to load media index" }
		}
	}

	fun addIndexable(name: String) {
		when (name.substringAfterLast(".")) {
			"" -> error("$name does not denote a file.")

			"jpg", "jpeg", "png" -> index.images.add(name)

			else -> error("Attempt to index unknown file type: $name")
		}
	}

	fun performIndex() {
		val baseFolders = buildList<String> {
			System.getProperty("user.home")?.takeIf { it.isNotBlank() }?.let(::add)

			if (Vars.android) {
				// On android, the home dir is the game's private directory.
				add("/sdcard/")
				add("/storage/emulated/0/")
			}
		}

		if (baseFolders.isEmpty()) {
			logger.warn { "Failed to find any base dirs to scan for media files." }
		}

		// Perform a recursive search, going up to 7 levels deep.
		var level = 0
		fun recurse(dir: Path) {
			if (level >= 7) return
			level++

			try {
				Files.newDirectoryStream(dir).use {
					it.iterator().forEachRemaining {
						if (Files.isDirectory(it)) {
							if (isIndexableDir(it.name)) recurse(it)
						} else {
							if (isIndexable(it.name)) {
								addIndexable(it.name)
							}
						}
					}
				}
			} catch (e: Exception) {
				logger.warn { "Failed to recurse into $dir: ${e.message}" }
			} finally {
				level--
			}
		}
		baseFolders.map(::Path).forEach(::recurse)
	}

	private fun isIndexable(name: String) =
		name.substringAfterLast('.').let {
			it == "png" || it == "jpg" || it == "jpeg"
		} && !name.startsWith(".") && !name.startsWith("~")

	private fun isIndexableDir(name: String) =
		!name.startsWith(".")

	@Serializable
	data class Index(
		val images: MutableSet<String>
	)
}
