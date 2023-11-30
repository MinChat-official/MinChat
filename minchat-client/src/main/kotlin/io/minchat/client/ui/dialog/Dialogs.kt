package io.minchat.client.ui.dialog

import arc.graphics.Color
import arc.graphics.g2d.*
import arc.scene.Element
import arc.util.Align
import com.github.mnemotechnician.mkui.extensions.dsl.*
import com.github.mnemotechnician.mkui.extensions.runUi
import io.minchat.client.MinchatDispatcher
import io.minchat.client.misc.userReadable
import io.minchat.client.ui.MinchatStyle.layoutMargin
import io.minchat.common.BaseLogger
import io.minchat.common.BaseLogger.Companion.getContextSawmill
import kotlinx.coroutines.*
import io.minchat.client.ui.MinchatStyle as Style

/**
 * A collection of utility methods to quickly show dialogs and not worry about any possible exceptions.
 *
 * Methods in this class can be called from any thread.
 *
 * The CoroutineScope of this class totally ignores any failed jobs within itself.
 */
object Dialogs : CoroutineScope {
	override val coroutineContext = SupervisorJob() + MinchatDispatcher + CoroutineExceptionHandler { _, _ -> }

	val logger = BaseLogger.getContextSawmill()

	fun info(message: String, onClose: () -> Unit = {}) {
		runUi {
			SimpleInfoDialog(message, onClose).show()
		}
	}

	fun error(exception: Exception, message: String? = null, onClose: () -> Unit = {}) {
		runUi {
			SimpleInfoDialog("""
				${message ?: "An error has occurred!"}
				
				[red]${exception.userReadable()}[]
				[lightgray]Type: ${exception.javaClass.simpleName}
			""".trimIndent(), onClose).show()
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
	): Job {
		val job = launch {
			try {
				action()
			} catch (e: CancellationException) {
				throw e
			} catch (e: Exception) {
				logger.error(e) { "Exception in AwaitActionDialog's action" }
				if (showError) {
					error(e, "An error has occurred.".trimIndent())
				}
				throw e // for the job to be marked as failed
			}
		}

		runUi {
			if (!job.isCancelled && !job.isCompleted) {
				AwaitActionDialog(message, cancellable, job).show()
			}
		}

		return job
	}

	fun choices(
		message: String,
		vararg choiceList: String,
		cancellable: Boolean = true,
		onSelect: (Int) -> Unit
	) {
		runUi {
			ChoiceDialog(message, choiceList, cancellable, onSelect).show()
		}
	}

	fun choices(
		message: String,
		vararg choiceList: Pair<String, (Int) -> Unit>,
		cancellable: Boolean = true
	) {
		choices(message, *choiceList.map { it.first }.toTypedArray(), cancellable = cancellable) {
			choiceList[it].second(it)
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
				margin(layoutMargin)

				addLabel(text, wrap = true)
					.fillX().pad(Style.layoutPad)
					.minWidth(300f)
			}.fillX()

			action("OK") {
				try {
					onClose()
				} catch (e: Exception) {
					logger.error(e) { "Exception in SimpleInfoDialog's close listener." }
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
					logger.error(e) { "Exception in ConfirmDialog's cancel listener." }
				}
				hide()
			}
			action("[green]$textYes") {
				try {
					confirmListener()
				} catch (e: Exception) {
					logger.error(e) { "Exception in ConfirmDialog's confirm listener." }
				}
				hide()
			}

			body.addTable(Style.surfaceBackground) {
				margin(layoutMargin)

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
				margin(layoutMargin)

				addLabel(message, wrap = true)
					.growX()
					.pad(Style.layoutPad).row()

				// Loading bar animation
				val start = System.currentTimeMillis()
				add(object : Element() {
					override fun draw() {
						val progress = ((System.currentTimeMillis() - start) / 1800f) % 1f
						val progWidth = (width - 2 * layoutMargin) * progress * 2
						val offset = when {
							progress < 0.5 -> 0f
							else -> (width - 2 * layoutMargin) * (progress - 0.5f) * 2
						}

						val midX = getX(Align.center)
						val midY = getY(Align.center)

						Draw.color(Color.white)
						Style.surfaceInner.draw(x, y, width, height) // this draws at (0, 0, width, height)

						Draw.color(Style.comment)
						if (clipBegin(x + layoutMargin, y + layoutMargin, width - 2 * layoutMargin, height - 2 * layoutMargin)) {
							Fill.rect( // ... but this draws at (-width/2, -height/2, width/2, height/2) ?!
								x + offset + layoutMargin + progWidth / 2,
								midY,
								progWidth,
								height - 2 * layoutMargin
							)
							clipEnd()
						}
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

	class ChoiceDialog(
		val message: String,
		val choices: Array<out String>,
		val cancellable: Boolean,
		val onSelect: (Int) -> Unit
	) : AbstractModalDialog() {
		override val addCloseAction get() = false

		init {
			if (message.isNotBlank()) header.addTable(Style.surfaceBackground) {
				margin(layoutMargin)

				addLabel(message, wrap = true)
					.fillX().pad(Style.layoutPad)
					.minWidth(300f)
			}

			if (cancellable) {
				nextActionRow()
				action("Cancel") {
					hide()
				}
			}

			for (i in choices.indices.reversed()) {
				nextActionRow()
				action(choices[i]) {
					onSelect(i)
					hide()
				}
			}
		}
	}
}
