package io.minchat.client.ui.dialog

import arc.util.Align
import com.github.mnemotechnician.mkui.extensions.dsl.*
import io.minchat.client.misc.*
import io.minchat.client.ui.MinchatStyle
import kotlinx.coroutines.*
import kotlin.coroutines.EmptyCoroutineContext

abstract class AbstractStatDialog(
	parentScope: CoroutineScope
) : AbstractModalDialog(), CoroutineScope {
	override val coroutineContext = parentScope.newCoroutineContext(EmptyCoroutineContext)
	/** A status string shown at the top. */
	@Volatile var status: String? = null

	override val closeButtonText get() = "Close"

	init {
		header.addLabel({ status.orEmpty() }, wrap = true)
			.color(MinchatStyle.red).fillX().row()

		nextActionRow()
	}

	/** Adds a stat entry to the body. */
	inline fun stat(name: String, crossinline value: () -> String?) {
		body.row()
		body.addTable(MinchatStyle.surfaceBackground) {
			margin(MinchatStyle.buttonMargin)
			addLabel(name, MinchatStyle.Label, align = Align.left)
				.grow().color(MinchatStyle.comment)
		}.pad(MinchatStyle.layoutPad).fill()

		body.addTable(MinchatStyle.surfaceBackground) {
			margin(MinchatStyle.buttonMargin)
			addLabel({ value() ?: "N/A" }, MinchatStyle.Label, align = Align.right, wrap = true)
				.grow().color(MinchatStyle.foreground)
				.minWidth(minValueLabelWidth)
		}.pad(MinchatStyle.layoutPad).growX().fillY()
	}

	/** Adds a stat entry to the stat table, using yes/no as the value. */
	@JvmName("statYesNo")
	@OverloadResolutionByLambdaReturnType
	inline protected fun stat(name: String, crossinline value: () -> Boolean?): Unit =
		stat(name) {
			value()?.let { if (it) "Yes" else "No" }
		}

	/**
	 * Sets the current status. If [override] is not null,
	 * changes the status only if the current status is equal to [override].
	 */
	protected fun status(newStatus: String?, override: String? = null) {
		if (override == null || status == override) {
			status = newStatus
		}
	}

	/**
	 * Executes [action] and catches any exception.
	 * If an exception is caught, updates the status accordingly.
	 */
	protected inline fun <R> runSafe(action: () -> R) =
		runCatching {
			action()
		}.onFailure { exception ->
			if (exception.isImportant()) {
				status(exception.userReadable())
			}
		}

	/** Executes [action] and sets a temporary status, then cancels the status. */
	protected inline fun launchWithStatus(status: String, crossinline action: suspend () -> Unit) = run {
		status(status)
		launch {
			action()
		}.then {
			status(null, override = status)
		}
	}

	/** Shortcut for `launchWithStatus { runSafe {... } }`. */
	protected inline fun launchSafeWithStatus(status: String, crossinline action: suspend () -> Unit) =
		launchWithStatus(status) {
			runSafe {
				action()
			}
		}
}
