package io.minchat.client.ui.tutorial

import com.github.mnemotechnician.mkui.delegates.*
import com.github.mnemotechnician.mkui.extensions.runUi

/**
 * A tutorial that can be shown to the user.
 *
 * @param steps steps of this tutorial. Unless otherwise necessary, [getStep] should be used instead of direct access.
 */
open class Tutorial(
	val name: String,
	val steps: List<Step<*>>
) {
	val bundlePrefix = "minchat.tutorial.$name"
	val initialTitle by bundle(bundlePrefix)

	var isSeen by setting(false, bundlePrefix)
	private var isLoaded = false

	/** Loads any assets associated with this tutorial, if needed. */
	protected open fun load() {
		steps.forEach(Step<*>::load)
	}

	/** Shows this tutorial if it hasn't been shown before. Does nothing otherwise. */
	fun trigger() {
		if (!isSeen) show()
	}

	/**
	 * Shows this tutorial to the user and marks it as shown.
	 *
	 * This method calls Core.app.post to allow calling itself while building the ui.
	 */
	fun show() {
		runUi {
			if (!isLoaded) load()

			val dialog = TutorialDialog(this)
			dialog.show()

			isSeen = true
		}
	}

	/** Returns a step of this tutorial, or Step.End if [index] >= [steps].size. */
	fun getStep(index: Int) = when {
		index >= steps.size -> Steps.End
		index < 0 -> throw IndexOutOfBoundsException("$index < 0")
		else -> steps[index]
	}

	/**
	 * Represents a step of a tutorial.
	 *
	 * @param persistent if true, this step should not be un-done.
	 */
	abstract class Step<T>(val persistent: Boolean = false) {
		/** Applies this step to the given tutorial dialog, returns its own data. */
		abstract fun apply(dialog: TutorialDialog): T

		/** Reverts any changes made by the [apply] method of this step. */
		abstract fun remove(dialog: TutorialDialog, data: T)

		/** If necessary, this method should load/defer loading of any necessary assets. */
		open fun load() {}
	}
}
