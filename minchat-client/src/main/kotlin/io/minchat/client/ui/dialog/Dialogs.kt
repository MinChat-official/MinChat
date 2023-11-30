package io.minchat.client.ui.dialog

import arc.Core
import arc.graphics.Color
import arc.graphics.g2d.*
import arc.input.KeyCode.scroll
import arc.scene.Element
import arc.scene.event.*
import arc.scene.style.Drawable
import arc.scene.ui.Image
import arc.util.*
import com.github.mnemotechnician.mkui.extensions.dsl.*
import com.github.mnemotechnician.mkui.extensions.elements.updateLast
import com.github.mnemotechnician.mkui.extensions.runUi
import io.minchat.client.MinchatDispatcher
import io.minchat.client.misc.userReadable
import io.minchat.client.ui.MinchatStyle.layoutMargin
import io.minchat.client.ui.MinchatStyle.layoutPad
import io.minchat.client.ui.MinchatStyle.surfaceBackground
import io.minchat.common.BaseLogger
import kotlinx.coroutines.*
import kotlin.math.*
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

	fun imageView(message: String?, drawable: Drawable) {
		runUi {
			ImageViewDialog(message, drawable).show()
		}
	}

	/** Equivalent to calling `info("This action is not implemented yet.")`. */
	fun TODO() {
		info("This action is not implemented yet.")
	}

	class SimpleInfoDialog(val text: String, onClose: () -> Unit = {}) : AbstractModalDialog() {
		override val addCloseAction = false

		init {
			body.addTable(surfaceBackground) {
				margin(layoutMargin)

				addLabel(text, wrap = true)
					.fillX().pad(layoutPad)
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

			body.addTable(surfaceBackground) {
				margin(layoutMargin)

				addLabel(message, wrap = true)
					.growX()
					.pad(layoutPad).row()
			}.minWidth(300f).pad(layoutPad)
		}
	}

	class AwaitActionDialog(
		val message: String,
		val cancellable: Boolean = true,
		val job: Job
	) : AbstractModalDialog() {
		override val addCloseAction get() = false

		init {
			body.addTable(surfaceBackground) {
				margin(layoutMargin)

				addLabel(message, wrap = true)
					.growX()
					.pad(layoutPad).row()

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
				}).growX().minHeight(50f).pad(layoutPad)

				if (cancellable) {
					action("Cancel") {
						job.cancel()
						hide()
					}
				}
			}.minWidth(300f).pad(layoutPad)
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
			if (message.isNotBlank()) header.addTable(surfaceBackground) {
				margin(layoutMargin)

				addLabel(message, wrap = true)
					.fillX().pad(layoutPad)
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

	class ImageViewDialog(
		val message: String?,
		val drawable: Drawable
	) : AbstractModalDialog() {
		lateinit var image: Image

		var scaleSpeed: Double = 1 / 50.0
		var currentScaleStep = 0
		var minSize = 128f
		var maxSize = min(Core.graphics.height, Core.graphics.width) / 1.5f
		private var currentSize = drawable.imageSize().coerceIn(minSize..maxSize)
		private val imageSize = drawable.imageSize().coerceIn(minSize, maxSize)

		init {
			if (!message.isNullOrBlank()) header.addTable(surfaceBackground) {
				addLabel(message, wrap = true)
					.growX()
					.minWidth(100f)
					.pad(layoutPad)
			}.fillX().margin(layoutMargin)

			body.addTable(surfaceBackground) {
				touchable = Touchable.enabled

				wrapper({ currentSize }, { currentSize }) {
					addImage(drawable, scaling = Scaling.fill)
						.grow()
						.apply { image = get() }
				}.grow()
					.minSize(minSize)
					.maxSize(maxSize)

				// Zooming with the mouse wheel
				updateLast {
					val scroll = Core.input.axis(scroll)
					if (scroll != 0f) {
						resizeStep(scroll.toInt() * 2)
					}
				}

				// Zooming with pinching
				addListener(object : ElementGestureListener() {
					override fun zoom(event: InputEvent?, initialDistance: Float, distance: Float) {
						val zoom = (distance - initialDistance).toInt() / 20
						resizeStep(zoom)
					}
				})
			}.fillX().margin(layoutMargin)

			nextActionRow()
			action("+") { resizeStep(20) }
			action("-") { resizeStep(-20) }
		}

		fun resizeStep(step: Int) {
			val newStep = currentScaleStep + step
			val newSize = (imageSize * exp(newStep * scaleSpeed)).toFloat().coerceIn(minSize, maxSize)

			if (newSize != currentSize) {
				currentScaleStep = (log(newSize.toDouble() / imageSize, Math.E) / scaleSpeed).toInt();
				currentSize = newSize
			}
		}
	}
}
