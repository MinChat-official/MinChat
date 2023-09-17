package io.minchat.client.config

import arc.Core
import arc.graphics.Color
import arc.scene.ui.TextField
import arc.scene.ui.layout.Table
import arc.util.Align
import com.github.mnemotechnician.mkui.delegates.setting
import com.github.mnemotechnician.mkui.extensions.dsl.*
import com.github.mnemotechnician.mkui.extensions.elements.*
import com.github.mnemotechnician.mkui.extensions.runUi
import io.minchat.client.Minchat
import io.minchat.client.misc.MinchatStyle.buttonMargin
import io.minchat.client.misc.MinchatStyle.layoutMargin
import io.minchat.client.misc.MinchatStyle.layoutPad
import io.minchat.client.misc.userReadable
import io.minchat.client.ui.tutorial.*
import io.minchat.rest.*
import kotlinx.coroutines.launch
import kotlinx.serialization.*
import kotlinx.serialization.json.Json
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

			it.pref(SpacerSetting(50f))

			it.pref(TableSetting {
				textButton("Check for updates") {

				}.uniformX()
				textButton("Tutorials") {
					showTutorialsDialog()
				}.uniformX()
			})
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

				runUi {
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

				runUi {
					Vars.ui.showErrorMessage(message)
					useCustomUrl = false
					rebuild()
				}
			} finally {
				Vars.ui.loadfrag.hide()
			}
		}
	}

	fun showTutorialsDialog() = createBaseDialog("Tutorials", addCloseButton = true) {
		val tutorials = try {
			Tutorials::class.java
				.declaredFields
				.filter { Tutorial::class.java.isAssignableFrom(it.type) }
				.map {
					it.isAccessible = true
					it.get(Tutorials) as Tutorial
				}
		} catch (e: Exception) {
			addLabel("Failed to get the tutorial list\n\n$e")
			return@createBaseDialog
		}

		scrollPane {
			addLabel("Name")
			addLabel("Seen")
			addLabel("Actions")
				.colspan(2).fill()
				.align(Align.center).row()

			for (tutorial in tutorials) {
				addTable {
					addLabel(tutorial.name)
						.pad(layoutPad).row()

					addLabel(tutorial.initialTitle)
						.color(Color.gray)
						.pad(layoutPad).row()
				}.margin(layoutMargin)

				addLabel({ if (tutorial.isSeen) "[green]+" else "[red]-" })
					.scaleFont(2f)
					.pad(layoutPad)

				textButton("reset") {
					tutorial.isSeen = false
				}.pad(layoutPad).margin(buttonMargin)

				textButton("show") {
					tutorial.show()
				}.pad(layoutPad).margin(buttonMargin).row()
			}
		}.grow()
	}.show()

	private class SpacerSetting(val height: Float) : SettingsTable.Setting("PLACEHOLDER") {
		override fun add(table: SettingsTable) {
			table.addSpace(height = height).row()
		}
	}

	private class TableSetting(
		val builder: Table.() -> Unit
	) : SettingsTable.Setting("PLACEHOLDER") {
		override fun add(table: SettingsTable) {
			// copied from TextSetting.
			val prefTable = table.table().left().padTop(3f).fillX().get()
			prefTable.builder()
			table.row()
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
