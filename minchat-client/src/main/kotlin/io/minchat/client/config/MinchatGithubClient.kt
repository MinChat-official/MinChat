package io.minchat.client.config

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.serialization.kotlinx.json.*
import io.minchat.common.BuildVersion
import kotlinx.serialization.json.Json

/**
 * A GitHub client allowing to fetch data from the official MinChat repository.
 */
class MinchatGithubClient {
	val httpClient = HttpClient(CIO) {
		expectSuccess = true
		install(ContentNegotiation) {
			json(Json { ignoreUnknownKeys = true })
		}
	}

	/** "raw.GithubUserContent.com" */
	val rawGithubUrl = "https://raw.githubusercontent.com"
	/** The raw GitHub user content url of the official minchat repo. */
	val rawMinchatRepoUrl = "$rawGithubUrl/minchat-official/minchat/main"

	/** Fetches the latest [BuildVersion] of the MinChat client from GitHub. */
	suspend fun getLatestStableVersion() =
		httpClient.get("$rawMinchatRepoUrl/remote/latest-stable.json")
			.body<BuildVersion>()

	/**
	 * Fetches the changelog of the MinChat client from GitHub,
	 * parses it into a list of [ChangelogEntry] and returns it.
	 *
	 * Invalid entries in the changelog file are silently ignored.
	 */
	suspend fun getChangelog(): List<ChangelogEntry> {
		val file = httpClient.get("$rawMinchatRepoUrl/remote/changelog")
			.body<String>()

		val changelog = mutableListOf<ChangelogEntry>()
		val iterator = file.lines().listIterator()

		for (line in iterator) {
			if (line.startsWith("#VERSION")) {
				val versionString = line.substringAfter("#VERSION ")
				val version = BuildVersion.fromStringOrNull(versionString) ?: continue

				// Read everything until the next #VERSION line
				val description = buildString {
					while (iterator.hasNext()) {
						val descriptionLine = iterator.next()
						if (descriptionLine.startsWith("#VERSION")) {
							iterator.previous()
							break
						}
						append(descriptionLine)
					}
				}

				changelog.add(ChangelogEntry(version, description))
			}
		}

		return changelog
	}

	/**
	 * Fetches the url of the official MinChat server from the GitHub.
	 */
	suspend fun getDefaultUrl() =
		httpClient.get("$rawMinchatRepoUrl/remote/default-url")
			.body<String>()

	data class ChangelogEntry(
		val version: BuildVersion,
		val description: String
	)
}
