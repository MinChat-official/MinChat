package io.minchat.client.ui.tutorial

import arc.Core
import arc.util.Time
import arc.util.Timer.Task
import com.github.mnemotechnician.mkui.extensions.elements.content
import io.minchat.client.ui.tutorial.Tutorial.Step

class Steps {
	/** Special step that signifies that the tutorial is finished. This step is returned automatically. */
	object End : Step<Unit>() {
		override fun apply(dialog: TutorialDialog) {}
		override fun remove(dialog: TutorialDialog, data: Unit) {}
	}

	/** Waits some time, then automatically advances to the next step. */
	class Delay(val time: Float) : Step<Task>() {
		override fun apply(dialog: TutorialDialog): Task {
			return Time.runTask(time * 60) { dialog.advanceStep() }
		}

		override fun remove(dialog: TutorialDialog, data: Task) {
			data.cancel()
		}
	}

	/** Changes the title of the dialog. This class uses literal names - use ChangeTitleBundle for bundled names. */
	open class ChangeTitle(val newTitle: String, persistent: Boolean = false) : Step<CharSequence>(persistent) {
		override fun apply(dialog: TutorialDialog): CharSequence {
			return dialog.titleLabel.content.also {
				dialog.titleLabel.content = newTitle
			}
		}

		override fun remove(dialog: TutorialDialog, data: CharSequence) {
			dialog.titleLabel.content = data
		}
	}

	/** Changes the title of the dialog, using the bundle "minchat.tutorials.$tutorialName.$stepName". */
	open class ChangeTitleBundle(val name: String, persistent: Boolean = false) : Step<CharSequence>(persistent) {
		override fun apply(dialog: TutorialDialog): CharSequence {
			return dialog.titleLabel.content.also {
				val tutorial = dialog.tutorial
				dialog.titleLabel.content = Core.bundle.get("${tutorial.bundlePrefix}.$name")
			}
		}

		override fun remove(dialog: TutorialDialog, data: CharSequence) {
			dialog.titleLabel.content = data
		}
	}

	/** Changes the description of the dialog. This class uses literal names - use ChangeDescriptionBundle for bundled names. */
	open class ChangeDescription(val newDescription: String, persistent: Boolean = false) : Step<CharSequence>(persistent) {
		override fun apply(dialog: TutorialDialog): CharSequence {
			return dialog.descriptionLabel.content.also {
				dialog.descriptionLabel.content = newDescription
			}
		}

		override fun remove(dialog: TutorialDialog, data: CharSequence) {
			dialog.descriptionLabel.content = data
		}
	}

	/** Changes the description of the dialog, using the bundle "minchat.tutorials.$name.$step */
	open class ChangeDescriptionBundle(val name: String, persistent: Boolean = false) : Step<CharSequence>(persistent) {
		override fun apply(dialog: TutorialDialog): CharSequence {
			return dialog.descriptionLabel.content.also {
				val tutorial = dialog.tutorial
				dialog.descriptionLabel.content = Core.bundle.get("${tutorial.bundlePrefix}.$name")
			}
		}

		override fun remove(dialog: TutorialDialog, data: CharSequence) {
			dialog.descriptionLabel.content = data
		}
	}

	/** Executes all specified steps in parallel, with no delays. */
	open class Parallel(vararg val steps: Step<*>) : Step<List<Any?>>() {
		override fun apply(dialog: TutorialDialog): List<Any?> {
			val data = steps.map { it.apply(dialog) }
			return data
		}

		@Suppress("UNCHECKED_CAST")
		override fun remove(dialog: TutorialDialog, data: List<Any?>) {
			steps.zip(data).forEach { (step, data) ->
				if (!step.persistent) {
					(step as Step<Any?>).remove(dialog, data)
				}
			}
		}
	}
}
