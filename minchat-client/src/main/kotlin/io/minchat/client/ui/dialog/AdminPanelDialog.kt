package io.minchat.client.ui.dialog

import arc.scene.ui.layout.Table
import com.github.mnemotechnician.mkui.extensions.dsl.*
import kotlinx.coroutines.delay
import io.minchat.client.ui.MinchatStyle as Style

class AdminPanelDialog : AbstractModalDialog() {
	init {
		// The body is a table with 3 columns
		body.addTable(Style.surfaceBackground) {
			defaults().uniform().fill()

			// Row 1
			actionButton("Create channel") {
				ChannelCreateDialog().show()
			}

			actionButton("Create group") {
				ChannelGroupCreateDialog().show()
			}
			row()

			// Row 2
			actionButton("View users") {
				Dialogs.TODO()
			}

			actionButton("Test loading") {
				Dialogs.await("testing") {
					delay(5000L)

					error("a")
				}
			}
			row()
		}.grow()
	}

	fun Table.actionButton(text: String, onClick: () -> Unit) = run {
		textButton(text, Style.InnerActionButton) {
			onClick()
		}.margin(Style.buttonMargin).pad(Style.layoutPad)
	}
}
