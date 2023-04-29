package io.minchat.client.config

import com.github.mnemotechnician.mkui.delegates.setting

object MinchatSettings {
	/** Whether to show a gui button on desktop too. */
	var guiButtonDesktop by setting(false, "minchat")

	/**
	 * Creates all the necessary gui settings in the settings menu.
	 * Must be called only once.
	 */
	fun createSettings() {

	}
}
