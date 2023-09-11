package io.minchat.client.ui.tutorial

import arc.graphics.Color
import arc.input.KeyCode
import arc.scene.ui.*
import arc.util.*
import com.github.mnemotechnician.mkui.extensions.dsl.*
import com.github.mnemotechnician.mkui.extensions.elements.scaleFont
import io.minchat.client.misc.MinchatStyle
import io.minchat.client.misc.MinchatStyle.ActionButton
import io.minchat.client.misc.MinchatStyle.buttonMargin
import io.minchat.client.misc.MinchatStyle.layoutMargin
import io.minchat.client.misc.MinchatStyle.layoutPad
import io.minchat.client.misc.MinchatStyle.surfaceBackground

class TutorialDialog(val tutorial: Tutorial) : Dialog() {
	var step = 0
	val currentStep get() = tutorial.getStep(step)
	var previousStepData: Any? = null

	lateinit var titleLabel: Label
	lateinit var descriptionLabel: Label

	private var nextButtonCooldown = 60f

	init {
		background = MinchatStyle.black(1)

		setFillParent(true)

		titleTable.remove()
		buttons.clearChildren()

		cont.addTable {
			addTable(surfaceBackground) {
				addLabel(tutorial.name, MinchatStyle.Label)
					.fillX()
					.pad(layoutPad)
					.with { titleLabel = it }
					.scaleFont(1.15f)
					.row()

				hider(
					hideVertical = { descriptionLabel.text.isBlank() },
					hideHorizontal = { descriptionLabel.text.isBlank() }
				) {
					addLabel("", MinchatStyle.Label, wrap = true)
						.color(Color.lightGray.mul(1.1f))
						.width(300f)
						.fillY()
						.pad(layoutPad)
						.with { descriptionLabel = it }
				}.row()
			}.margin(layoutMargin).pad(layoutPad).row()

			addTable(surfaceBackground) {
				textButton({
					if (step != tutorial.steps.size - 1) "NEXT" else "DONE"
				}, ActionButton) {
					advanceStep()
				}.disabled { nextButtonCooldown >= 0 }
					.pad(layoutPad)
					.margin(buttonMargin).row()
			}.fillX().margin(layoutMargin).pad(layoutPad).row()

			update { nextButtonCooldown -= Time.delta }
		}

		keyDown {
			if (it == KeyCode.enter || it == KeyCode.escape || it == KeyCode.back) {
				advanceStep()
			}
		}

		applyThisStep()
	}

	fun advanceStep() {
		step++
		nextButtonCooldown = 40f
		applyThisStep()
	}

	@Suppress("UNCHECKED_CAST")
	private fun applyThisStep() {
		if (step >= 1) {
			try {
				val previous = tutorial.getStep(step - 1) as Tutorial.Step<Any?>

				if (!previous.persistent) {
					previous.remove(this, previousStepData)
				}
			} catch (e: Exception) {
				Log.warn("Step $step of tutorial ${tutorial.name} could no be un-applied: $e")
			}
		}

		if (currentStep == Steps.End) {
			previousStepData = null
			hide()
		} else {
			previousStepData = currentStep.apply(this)
		}
	}
}