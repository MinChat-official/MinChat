package io.minchat.client.ui.dialog

import arc.scene.ui.Dialog
import arc.scene.ui.layout.Table
import arc.util.Align
import com.github.mnemotechnician.mkui.extensions.dsl.*
import com.github.mnemotechnician.mkui.extensions.elements.cell
import io.minchat.client.misc.*
import io.minchat.client.ui.MinchatStyle
import kotlinx.coroutines.*
import kotlin.coroutines.EmptyCoroutineContext

// TODO: should this extend ModalDialog?
// They share a lot of logic and technically stat dialogs ARE modal.
abstract class AbstractStatDialog(
	parentScope: CoroutineScope
) : Dialog(), CoroutineScope {
	override val coroutineContext = parentScope.newCoroutineContext(EmptyCoroutineContext)
	/** A status string shown at the top. */
	@Volatile var status: String? = null

	/** A single-row table. */
	lateinit var headerTable: Table
	/** A table with 2 columns holding the stats of the user. */
	lateinit var statTable: Table
	/** A table containing rows of action buttons related to the dialog. Must only contain Tables. */
	lateinit var actionsTable: Table
	/** Rows of action buttons. The first one is the last added one. */
	val actionRows = mutableListOf<Table>()

	var minValueLabelWidth = 250f

	init {
		setFillParent(true)
		closeOnBack()
		titleTable.remove()
		buttons.remove()
		cont.cell()?.grow()

		cont.addLabel({ status.orEmpty() }, wrap = true)
			.color(MinchatStyle.red).fillX().row()

		cont.addTable {
			headerTable = this
		}.fillX().row()

		cont.addTable {
			statTable = this
		}.fillX().row()

		cont.addTable {
			actionsTable = this
			nextActionRow()
		}.fillX().row()
	}

	/** Adds a stat entry to the stat table. */
	inline fun addStat(name: String, crossinline value: () -> String?) {
		statTable.row()
		statTable.addTable(MinchatStyle.surfaceBackground) {
			margin(MinchatStyle.buttonMargin)
			addLabel(name, MinchatStyle.Label, align = Align.left)
				.grow().color(MinchatStyle.comment)
		}.pad(MinchatStyle.layoutPad).fill()

		statTable.addTable(MinchatStyle.surfaceBackground) {
			margin(MinchatStyle.buttonMargin)
			addLabel({ value() ?: "N/A" }, MinchatStyle.Label, align = Align.right, wrap = true)
				.grow().color(MinchatStyle.foreground)
				.minWidth(minValueLabelWidth)
		}.pad(MinchatStyle.layoutPad).growX().fillY()
	}

	/** Adds a stat entry to the stat table, using yes/no as the value. */
	@JvmName("addStatYesNo")
	@OverloadResolutionByLambdaReturnType
	inline protected fun addStat(name: String, crossinline value: () -> Boolean?): Unit =
		addStat(name) {
			value()?.let { if (it) "Yes" else "No" }
		}

	/** Removes all action rows and adds a default one. */
	protected fun clearActionRows() {
		actionsTable.clearChildren()
		actionRows.clear()
		nextActionRow()
		action("Close", action = ::hide)
	}

	/** Adds an action button to the specified row. */
	inline protected fun action(text: String, row: Int = 0, crossinline action: () -> Unit) =
		actionRows[row].textButton(text, MinchatStyle.ActionButton) {
			action()
		}.growX().uniformX().margin(MinchatStyle.buttonMargin).pad(MinchatStyle.layoutPad)

	/**
	 * Adds a new action row to [actionRows], affecting where [action] adds buttons to by default.
	 * The new row is added above the last one.
	 */
	protected fun nextActionRow() {
		actionsTable.cells.reverse()

		val cell = actionsTable.addTable()
			.growX().pad(MinchatStyle.layoutPad)
		actionsTable.row()
		actionRows.add(0, cell.get())

		actionsTable.cells.reverse()
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
