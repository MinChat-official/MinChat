package io.minchat.client.config

import arc.Core
import arc.scene.ui.TextField
import com.github.mnemotechnician.mkui.delegates.setting
import com.github.mnemotechnician.mkui.extensions.dsl.addSpace
import com.github.mnemotechnician.mkui.extensions.elements.hint
import io.minchat.client.Minchat
import io.minchat.client.misc.userReadable
import io.minchat.rest.MinchatRestClient
import kotlinx.coroutines.launch
import mindustry.Vars
import mindustry.gen.Icon
import mindustry.ui.dialogs.SettingsMenuDialog.SettingsTable

object MinchatSettings {
	const val prefix = "minchat"

	/** Whether to show a gui button on desktop too. */
	var guiButtonDesktop by setting(true, prefix)

	/** Whether custom server url should be used. */
	var useCustomUrl by setting(false, prefix)
	var customUrl by setting("", prefix)

	/**
	 * Creates all the necessary gui settings in the settings menu.
	 * Must be called only once.
	 */
	fun createSettings() {
		Vars.ui.settings.addCategory("MinChat", Icon.terminal) {
			if (!Vars.mobile) {
				it.checkPref("minchat.gui-button-desktop", true)
			}

			it.pref(SpacerSetting(50f))

			it.checkPref("minchat.use-custom-url", false) { enabled ->
				if (enabled == false) return@checkPref
				it.validateCustomUrl()
			}

			it.pref(ConditionalTextSetting("minchat.custom-url", "NONE", { !useCustomUrl }))
		}
	}

	private fun SettingsTable.validateCustomUrl() {
		if ("\\w://.+\\..+".toRegex() !in customUrl && customUrl.split(".").size != 4 && customUrl != "localhost") {
			Vars.ui.showErrorMessage("URL $customUrl is not a valid url. Example of a valid url: https://example.com:3378")
			useCustomUrl = false
			rebuild()
			return
		}

		// Try to ping the custom url
		Minchat.launch {
			try {
				Vars.ui.loadfrag.show("Please wait...")

				val client = MinchatRestClient(customUrl)
				val version = client.getServerVersion()

				Core.app.post {
					Vars.ui.showInfo("""
						Success. Server version is $version. You must restart mindustry in order to apply the change.
						
						Minchat developers are not responsible for any data you send to foreign servers.
					""".trimIndent())
				}
			} catch (e: Exception) {
				val message = """
					The server $customUrl cannot be reached.
					Make sure the URL is valid and you are connected to the internet.
					
					Reason: ${e.userReadable()}
				""".trimIndent()

				Core.app.post {
					Vars.ui.showErrorMessage(message)
					useCustomUrl = false
					rebuild()
				}
			} finally {
				Vars.ui.loadfrag.hide()
			}
		}
	}

	private class SpacerSetting(val height: Float) : SettingsTable.Setting("PLACEHOLDER") {
		override fun add(table: SettingsTable) {
			table.addSpace(height = height).row()
		}
	}

	private class ConditionalTextSetting(
		name: String,
		var def: String = "",
		val enabled: () -> Boolean,
		var changed: ((String) -> Unit)? = null
	) : SettingsTable.Setting(name) {
		override fun add(table: SettingsTable) {
			val field = TextField()
			field.hint = title
			field.update {
				field.text = Core.settings.getString(name, def)
				field.isDisabled = !enabled()
			}
			field.changed {
				if (!enabled()) return@changed

				Core.settings.put(name, field.text)
				changed?.invoke(field.text)
			}
			// copied from TextSetting.
			val prefTable = table.table().left().padTop(3f).get()
			prefTable.add(field).minWidth(300f).fillX()
			addDesc(prefTable)
			table.row()
		}
	}

}
