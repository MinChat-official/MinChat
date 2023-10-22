package io.minchat.client.ui.tutorial

import io.minchat.client.ui.tutorial.Steps.*

object Tutorials {
	val welcome = newTutorial("welcome") {
		+ Delay(0.8f)
		+ ChangeDescriptionBundle("continue", true)

		+ Parallel(
			ChangeTitleBundle("1-title", true),
			ChangeDescription(""),
			Delay(0.8f)
		)
		+ ChangeDescriptionBundle("1-desc")
		+ ChangeDescriptionBundle("2-desc")

		+ Parallel(
			ChangeTitleBundle("3-title", true),
			ChangeDescription(""),
			Delay(0.8f)
		)
		+ ChangeDescriptionBundle("3-desc")
		+ ChangeDescriptionBundle("4-desc")
		+ ChangeDescriptionBundle("5-desc")

		+ Parallel(
			ChangeTitleBundle("6-title"),
			ChangeDescriptionBundle("6-desc")
		)
	}

	val authorization = newTutorial("authorization") {
		+ Delay(0.8f)
		+ Parallel(
			ChangeDescriptionBundle("1-desc"),
			Delay(2f)
		)
		+ ChangeDescriptionBundle("2-desc")
		+ ChangeDescriptionBundle("3-desc")
	}

	val directMessages = newTutorial("dms") {
		+ ChangeDescriptionBundle("1-desc")
		+ ChangeDescriptionBundle("2-desc")
		+ ChangeDescriptionBundle("3-desc")
		+ ChangeDescriptionBundle("4-desc")
		+ Parallel(
			ChangeTitleBundle("5-title"),
			ChangeDescriptionBundle("5-desc")
		)
		+ ChangeDescriptionBundle("6-desc")
		+ Parallel(
			ChangeTitleBundle("7-title"),
			ChangeDescriptionBundle("7-desc")
		)
		+ Parallel(
			ChangeTitleBundle("end-title"),
			ChangeDescriptionBundle("end-desc")
		)
	}
}
