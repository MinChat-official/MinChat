package io.minchat.client.config

import com.github.mnemotechnician.mkui.delegates.setting
import mindustry.Vars
import mindustry.gen.Icon

object MinchatSettings {
	/** Whether to show a gui button on desktop too. */
	var guiButtonDesktop by setting(true, "minchat")

	/**
	 * Creates all the necessary gui settings in the settings menu.
	 * Must be called only once.
	 */
	fun createSettings() {
		val category = Vars.ui.settings.addCategory("MinChat", Icon.terminal) {
			if (!Vars.mobile) {
				it.checkPref("minchat.gui-button-desktop", true)
			}
		}
	}
}
