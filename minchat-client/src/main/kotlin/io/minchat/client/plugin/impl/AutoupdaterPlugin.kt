package io.minchat.client.plugin.impl

import arc.Core
import arc.graphics.Color
import arc.scene.ui.layout.Table
import arc.util.Align
import com.github.mnemotechnician.mkui.extensions.dsl.*
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.utils.io.core.*
import io.minchat.client.Minchat
import io.minchat.client.config.MinchatGithubClient.ChangelogEntry
import io.minchat.client.misc.*
import io.minchat.client.plugin.MinchatPlugin
import io.minchat.client.ui.dialog.*
import io.minchat.common.*
import kotlinx.coroutines.*
import mindustry.Vars
import java.io.RandomAccessFile
import io.minchat.client.ui.MinchatStyle as Style

class AutoupdaterPlugin : MinchatPlugin("autoupdater") {
	/** The latest available version on GitHub. Set by [performCheck]. */
	var latestVersion: BuildVersion? = null

	val maxAttempts = 3
	val httpClient = HttpClient(CIO) {
		expectSuccess = true
		engine {
			requestTimeout = 0 // Disabled
		}
	}

	override fun onLoad() {
		Minchat.launch {
			performCheck()
		}
	}

	/** Runs an update check; Shows an update dialog if an update is found or an info dialog if not. */
	suspend fun performCheckVerbose(): CheckResult {
		return performCheck().also {
			when {
				it == CheckResult.NO_UPDATE -> {
					val serverVersion = runCatching {
						Minchat.client.getServerVersion().toString()
					}.getOrDefault("<an error has occurred>")

					Dialogs.info("""
						No update found.
						
						Local version: $MINCHAT_VERSION
						GitHub version: $latestVersion
						Sever version: $serverVersion
					""".trimIndent())
				}
				it == CheckResult.ERROR -> {
					Dialogs.info("An error has occurred. Check your internet connection or try again later.")
				}
			}
		}
	}

	/** Runs an update check; Shows an update dialog if an update is found. */
	suspend fun performCheck(): CheckResult {
		var attempt = 1
		while (true) {
			try {
				latestVersion = Minchat.githubClient.getLatestStableVersion()
				break
			} catch (e: Exception) {
				if (attempt++ == maxAttempts) {
					Log.error(e) { "Failed to get latest version from GitHub after $maxAttempts attempts" }
					return CheckResult.ERROR
				}
			}
		}
		val latestVersion = latestVersion ?: return CheckResult.ERROR

		if (latestVersion <= MINCHAT_VERSION) {
			Log.info { "Autoupdater: skipping. $latestVersion <= $MINCHAT_VERSION" }
			return CheckResult.NO_UPDATE
		}

		Log.info { "Autoupdater: downloading the changelog..." }
		val changelog = runCatching { Minchat.githubClient.getChangelog() }
			.onFailure { Log.error { "Autoupdater: failed to get changelog: $it" } }
			.getOrNull()
			?.filter { it.version > MINCHAT_VERSION }

		UpdatePromptDialog(latestVersion, changelog).show()
		return CheckResult.UPDATE_FOUND
	}

	/** Performs an automatic update of the MinChat client. */
	fun performUpdate() {
		lateinit var job: Job

		Vars.ui.loadfrag.apply {
			show("Preparing to download...")
			setButton {
				job.cancel()
				Dialogs.info("The update has been aborted.")
			}

			job = Minchat.launch {
				runCatching {
					// Firstly, determine where the mod is located
					val file = Vars.mods.getMod(Minchat.javaClass)?.file?.file()
						?: error("Cannot locate the MinChat mod file.")
					if (file.isDirectory) error("MinChat mod file is a directory. This is not supported.")

					// Then, fetch the latest version and the list of releases
					val latestVersion = Minchat.githubClient.getLatestStableVersion().toString()
					val releases = Minchat.githubClient.getReleases()

					// Then, try to find the former among the latter.
					// If not found, fall back to the latest version.
					val latestRelease = releases.find {
						(it.tag.removePrefix("v").trim() == latestVersion) && it.assets.any {
							it.name.startsWith("minchat-client", true)
								&& it.name.endsWith(".jar", true)
						}
					} ?: releases.firstOrNull()

					val modAsset = latestRelease?.assets?.find { it.name.startsWith("minchat-client", true) }
						?: error("Cannot locate a suitable mod file. Please, try again later.")

					val tmpFilePath = file.resolveSibling("minchat-download-tmp")
					RandomAccessFile(tmpFilePath, "rw").use { tmpFile ->
						if (Minchat.isConnected) {
							Minchat.gateway.disconnect()
							Minchat.client.account = null
						}

						setText("Connecting to server...")
						// Download the asset and overwrite the mod file
						httpClient.prepareGet(modAsset.downloadUrl).execute {
							val length = it.contentLength() ?: error("what???")
							tmpFile.setLength(length)
							val channel = it.bodyAsChannel()
							var read = 0L

							setText("Updating. Please wait...")

							while (!channel.isClosedForRead) {
								val packet = channel.readRemaining(DEFAULT_BUFFER_SIZE.toLong())
								while (!packet.isEmpty) {
									val bytes = packet.readBytes()
									tmpFile.write(bytes)

									read += bytes.size
									setProgress(read.toFloat() / length)
								}
							}

							if (read != length) {
								tmpFile.close()
								tmpFilePath.delete()
								throw Exception("Download failed. Please try again.")
							}
						}

						hide()
						RestartPromptDialog().show()
					}
					// After this, new MinChat classes can no longer be loaded.
					tmpFilePath.copyTo(file, overwrite = true)
					tmpFilePath.delete()
				}.onFailure { exception ->
					hide()
					if (exception is CancellationException) return@onFailure

					Dialogs.info("""
						Failed to update.
						
						Reason: ${exception.userReadable()}
					""".trimIndent())
				}
			}
		}
	}

	inner class UpdatePromptDialog(val latestVersion: BuildVersion, val changelog: List<ChangelogEntry>?) : AbstractModalDialog() {
		init {
			header.apply {
				addTable(Style.surfaceBackground) {
					margin(Style.layoutMargin)
					addLabel("""
						A new MinChat version is available!
						
						Current version: $MINCHAT_VERSION
						Latest version: $latestVersion
					""".trimIndent(), Style.Label).pad(Style.layoutPad)
				}.fillX().pad(Style.layoutPad).row()

				if (!MINCHAT_VERSION.isInterchangeableWith(latestVersion)) addTable(Style.surfaceBackground) {
					margin(Style.layoutMargin)

					if (!latestVersion.isCompatibleWith(MINCHAT_VERSION)) {
						addLabel("The current version is incompatible with the official MinChat server!", wrap = true)
							.pad(Style.layoutPad)
							.growX().row()
						addLabel("You will likely be unable to chat until you update!", wrap = true)
							.color(Color.red).pad(Style.layoutPad)
							.growX()
					} else {
						addLabel("The current version may not be fully compatible with the official MinChat server.", wrap = true)
							.pad(Style.layoutPad)
							.growX()
					}
				}.fillX().pad(Style.layoutPad).row()

				// changelog
				addTable(Style.surfaceBackground) {
					top().margin(Style.layoutMargin)

					var showChangelog = false
					textToggle({ if (showChangelog) "Hide changelog" else "Show changelog" }, Style.ActionToggleButton) {
						showChangelog = !showChangelog
					}.grow().pad(Style.layoutPad)
						.margin(Style.buttonMargin)
						.row()

					addMinTable {
						clip = true
						hider(hideVertical = { !showChangelog }, hideHorizontal = { !showChangelog }) {
							margin(Style.layoutMargin)

							limitedScrollPane(limitW = false, limitH = false) {
								addChangelog()
							}.grow().maxWidth(800f)
						}.grow()
					}.maxHeight(500f).growX()
				}.fillX().pad(Style.layoutPad).row()

				addTable(Style.surfaceBackground) {
					margin(Style.layoutMargin)

					addLabel("Do you want to update now?").pad(Style.layoutPad)
				}.fillX().pad(Style.layoutPad).row()

				action("[green]Update") {
					hide()
					performUpdate()
				}
			}
		}

		private fun Table.addChangelog() {
			changelog ?: return

			for (entry in changelog.sortedByDescending { it.version }) {
				val desc = entry.description.lines().map { "[grey]|[] $it" }.joinToString("\n")

				addTable(Style.surfaceInner) {
					left()
					addLabel("version ${entry.version}", align = Align.center)
						.growX().pad(Style.layoutPad)
						.row()

					addLabel(desc, align = Align.left)
						.fillX().pad(Style.layoutPad)
						.padLeft(Style.layoutPad + 10f)
						.row()
				}.fillX().pad(Style.layoutPad)
					.margin(Style.layoutMargin)
					.row()
			}
		}
	}

	enum class CheckResult { NO_UPDATE, UPDATE_FOUND, ERROR }

	inner class RestartPromptDialog : AbstractModalDialog() {
		init {
			header.addTable(Style.surfaceBackground) {
				val begin = System.currentTimeMillis()

				margin(Style.layoutMargin)

				addLabel("MinChat has been updated. Restart now?")
					.pad(Style.layoutPad).row()
				addLabel({ "Restarting automatically in ${
					5 - (System.currentTimeMillis() - begin) / 1000
				}..." })
					.pad(Style.layoutPad)

				update {
					if (scene != null && System.currentTimeMillis() - begin >= 5000) {
						Core.app.exit()
					}
				}
			}

			action("[green]Restart") {
				Core.app.exit()
			}
		}
	}
}
