package io.minchat.client.config

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.serialization.kotlinx.json.*
import io.minchat.common.BuildVersion
import kotlinx.serialization.*
import kotlinx.serialization.json.Json

/**
 * A GitHub client allowing to fetch data from the official MinChat repository.
 */
class MinchatGithubClient {
	val httpClient = HttpClient(CIO) {
		expectSuccess = true
		install(ContentNegotiation) {
			json(Json {
				ignoreUnknownKeys = true
			})
		}
	}

	/** "raw.GithubUserContent.com" */
	val rawGithubUrl = "https://raw.githubusercontent.com"
	/** "api.github.com" */
	val githubApiUrl = "https://api.github.com"
	/** The raw GitHub user content url of the official minchat repo. */
	val rawMinchatRepoUrl = "$rawGithubUrl/minchat-official/minchat/main"
	/** The GitHub api url of the official minchat repo. */
	val minchatRepoApiUrl = "$githubApiUrl/repos/minchat-official/minchat"

	/** Fetches the latest [BuildVersion] of the MinChat client from GitHub. */
	suspend fun getLatestStableVersion() =
		httpClient.get("$rawMinchatRepoUrl/remote/latest-stable.json")
			.body<String>() // body<BuildVersion> doesn't work
			.let { Json.decodeFromString<BuildVersion>(it) }

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
						appendLine(descriptionLine.trim())
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

	/**
	 * Returns a list of [GithubRelease]s associated with the official MinChat repo,
	 * sorted by date in a descending manner.
	 */
	suspend fun getReleases(): List<GithubRelease> =
		httpClient.get("$minchatRepoApiUrl/releases")
			.body<List<GithubRelease>>()

	data class ChangelogEntry(
		val version: BuildVersion,
		val description: String
	)

	@Serializable
	data class GithubRelease(
		val url: String,
		val id: Int,
		val name: String,
		@SerialName("tag_name")
		val tag: String,
		@SerialName("body")
		val description: String,
		val assets: List<GithubAsset>
	)

	@Serializable
	data class GithubAsset(
		val url: String,
		@SerialName("browser_download_url")
		val downloadUrl: String,
		val name: String
	)
}
