package io.minchat.client.ui.dialog

import arc.graphics.Color
import arc.graphics.g2d.*
import arc.scene.Element
import arc.util.Align
import com.github.mnemotechnician.mkui.extensions.dsl.*
import com.github.mnemotechnician.mkui.extensions.runUi
import io.minchat.client.Minchat
import io.minchat.client.misc.Log
import kotlinx.coroutines.*
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

	/** Asynchronously runs [action] and shows a dialog. Hides the dialog on success, and may show an error dialog on error. */
	inline fun await(
		message: String,
		showError: Boolean = true,
		cancellable: Boolean = true,
		crossinline action: suspend () -> Unit
	) {
		val job = Minchat.launch {
			try {
				action()
			} catch (e: CancellationException) {
				// nothing
			} catch (e: Exception) {
				Log.error(e) { "Exception in AwaitActionDialog's action" }
				if (showError) {
					info("""
						An error has occurred while processing the action.
						Check the logs for more detail.
					""".trimIndent())
				}
			}
		}

		runUi {
			if (!job.isCancelled && !job.isCompleted) {
				AwaitActionDialog(message, cancellable, job).show()
			}
		}
	}

	/** Equivalent to calling `info("This action is not implemented yet.")`. */
	fun TODO() {
		info("This action is not implemented yet.")
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

	class AwaitActionDialog(
		val message: String,
		val cancellable: Boolean = true,
		val job: Job
	) : AbstractModalDialog() {
		override val addCloseAction = false

		init {
			body.addTable(Style.surfaceBackground) {
				margin(Style.layoutMargin)

				addLabel(message, wrap = true)
					.growX()
					.pad(Style.layoutPad).row()

				// Loading bar animation
				val start = System.currentTimeMillis()
				add(object : Element() {
					override fun draw() {
						val progress = (System.currentTimeMillis() - start) / 1800f
						val progWidth = (width - 2 * Style.layoutMargin) * progress
						val offset = when {
							progress < 0.5 -> 0f
							else -> (width - 2 * Style.layoutMargin) * (progress - 0.5f)
						}

						val midX = getX(Align.center)
						val midY = getY(Align.center)

						Draw.color(Color.white)
						Style.surfaceInner.draw(midX, midY, width, height)

						Draw.color(Style.comment)
						Fill.rect(
							x + offset + Style.layoutMargin + progWidth / 2,
							midY,
							progWidth,
							height - 2 * Style.layoutMargin
						)
					}
				}).growX().minHeight(50f).pad(Style.layoutPad)

				if (cancellable) {
					action("Cancel") {
						job.cancel()
						hide()
					}
				}
			}.minWidth(300f).pad(Style.layoutPad)
		}

		override fun act(delta: Float) {
			if (!job.isActive) {
				hide()
			}

			super.act(delta)
		}
	}
}
