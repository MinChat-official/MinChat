package io.minchat.client.ui.tutorial

import io.minchat.client.ui.tutorial.Tutorial.Step

open class TutorialBuilder(val name: String) {
	val steps = mutableListOf<Step<*>>()

	/** Adds the specified step. */
	open operator fun Step<*>.unaryPlus() = steps.add(this)

	/** Builds the tutorial and returns it. */
	open fun build() = Tutorial(name, steps)
}

fun newTutorial(name: String, block: TutorialBuilder.() -> Unit): Tutorial {
	val builder = TutorialBuilder(name)
	block(builder)
	return builder.build()
}
