package io.minchat.client.ui.dialog

import com.github.mnemotechnician.mkui.extensions.dsl.*
import com.github.mnemotechnician.mkui.extensions.runUi
import io.minchat.client.misc.Log
import io.minchat.client.ui.MinchatStyle as Style

/**
 * A collection of utility methods to quickly show dialogs and not worry about any possible exceptions.
 *
 * Methods in this class can be called from any thread.
 */
object Dialogs {
	fun info(message: String, onClose: () -> Unit = {}) {
		runUi {
			SimpleInfoDialog(message, onClose).show()
		}
	}

	fun confirm(
		message: String,
		textNo: String = "No",
		textYes: String = "Yes",
		onCancel: () -> Unit = {},
		onConfirm: () -> Unit
	) {
		runUi {
			ConfirmDialog(message, textNo, textYes, onCancel, onConfirm).show()
		}
	}

	class SimpleInfoDialog(val text: String, onClose: () -> Unit = {}) : AbstractModalDialog() {
		override val addCloseAction = false

		init {
			body.addTable(Style.surfaceBackground) {
				margin(Style.layoutMargin)

				addLabel(text, wrap = true)
					.fillX().pad(Style.layoutPad)
					.minWidth(300f)
			}.fillX()

			action("OK") {
				try {
					onClose()
				} catch (e: Exception) {
					Log.error(e) { "Exception in SimpleInfoDialog's close listener." }
				}
				hide()
			}
		}
	}

	class ConfirmDialog(
		val message: String,
		val textNo: String = "No",
		val textYes: String = "Yes",
		val cancelListener: () -> Unit = {},
		val confirmListener: () -> Unit
	) : AbstractModalDialog() {
		override val addCloseAction = false

		init {
			action("[red]$textNo") {
				try {
					cancelListener()
				} catch (e: Exception) {
					Log.error(e) { "Exception in ConfirmDialog's cancel listener." }
				}
				hide()
			}
			action("[green]$textYes") {
				try {
					confirmListener()
				} catch (e: Exception) {
					Log.error(e) { "Exception in ConfirmDialog's confirm listener." }
				}
				hide()
			}

			body.addTable(Style.surfaceBackground) {
				margin(Style.layoutMargin)

				addLabel(message, wrap = true)
					.growX()
					.pad(Style.layoutPad).row()
			}.minWidth(300f).pad(Style.layoutPad)
		}
	}
}
