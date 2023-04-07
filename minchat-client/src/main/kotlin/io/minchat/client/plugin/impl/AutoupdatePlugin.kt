package io.minchat.client.plugin.impl

import arc.Core
import arc.graphics.Color
import arc.util.Log
import com.github.mnemotechnician.mkui.extensions.dsl.*
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.utils.io.core.*
import io.minchat.client.Minchat
import io.minchat.client.misc.userReadable
import io.minchat.client.plugin.MinchatPlugin
import io.minchat.client.ui.dialog.ModalDialog
import io.minchat.common.*
import kotlinx.coroutines.*
import mindustry.Vars
import java.io.RandomAccessFile
import io.minchat.client.misc.MinchatStyle as Style

class AutoupdatePlugin : MinchatPlugin("autoupdater") {
	val maxAttempts = 3
	val httpClient = HttpClient(CIO) {
		expectSuccess = true
		engine {
			requestTimeout = 0 // Disabled
		}
	}

	override suspend fun onLoad() {
		lateinit var latestVersion: BuildVersion
		var attempt = 1
		while (true) {
			try {
				latestVersion = Minchat.githubClient.getLatestStableVersion()
				break
			} catch (e: Exception) {
				if (attempt++ == maxAttempts) {
					Log.err("Failed to get latest version from GitHub after $maxAttempts attempts.", e)
					return
				}
			}
		}

		if (latestVersion <= MINCHAT_VERSION) return

		UpdatePromptDialog(latestVersion).show()
	}

	/** Performs an automatic update of the MinChat client. */
	fun performUpdate() {
		lateinit var job: Job

		Vars.ui.loadfrag.apply {
			show("Updating. Please wait...")
			setButton {
				job.cancel()
				SimpleInfoDialog("The update has been aborted.").show()
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
						Minchat.gateway.disconnect()
						Minchat.client.account = null

						// Download the asset and overwrite the mod file
						httpClient.prepareGet(modAsset.downloadUrl).execute {
							val length = it.contentLength() ?: error("what???")
							tmpFile.setLength(length)
							val channel = it.bodyAsChannel()
							var read = 0L

							while (!channel.isClosedForRead) {
								val packet = channel.readRemaining(DEFAULT_BUFFER_SIZE.toLong())
								while (!packet.isEmpty) {
									val bytes = packet.readBytes()
									tmpFile.write(bytes)

									read += bytes.size
									setProgress(read.toFloat() / length)
								}
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

					SimpleInfoDialog("""
						Failed to update.
						
						Reason: ${exception.userReadable()}
					""".trimIndent()).show()
				}
			}
		}
	}

	inner class UpdatePromptDialog(val latestVersion: BuildVersion) : ModalDialog() {
		init {
			fields.apply {
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
						addLabel("The current version is incompatible with the official MinChat server!")
							.pad(Style.layoutPad).row()
						addLabel("You will likely be unable to chat until you update!")
							.color(Color.red).pad(Style.layoutPad)
					} else {
						addLabel("The current version may not be fully compatible with the official MinChat server.")
							.pad(Style.layoutPad)
					}
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
	}

	inner class SimpleInfoDialog(val text: String) : ModalDialog() {
		init {
			fields.addTable(Style.surfaceBackground) {
				margin(Style.layoutMargin)

				addLabel(text, wrap = true)
					.fillX().pad(Style.layoutPad)
					.minWidth(300f)
			}.fillX()
		}
	}

	inner class RestartPromptDialog : ModalDialog() {
		init {
			fields.addTable(Style.surfaceBackground) {
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
