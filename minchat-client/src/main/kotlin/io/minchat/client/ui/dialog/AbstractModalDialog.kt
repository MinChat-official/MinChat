package io.minchat.client.ui.dialog

import arc.scene.actions.Actions.action
import arc.scene.ui.*
import arc.scene.ui.layout.Table
import arc.util.Align
import com.github.mnemotechnician.mkui.extensions.dsl.*
import com.github.mnemotechnician.mkui.extensions.elements.*
import io.minchat.client.ui.MinchatStyle as Style

/**
 * A dialog used to let the user input some data,
 * choose an action or confirm an action.
 */
abstract class AbstractModalDialog : Dialog() {
	/** A table atop all other tables. */
	lateinit var header: Table
	/** A table containing the fields of this dialog. Normally contains two columns. */
	lateinit var body: Table
	/** A table containing actions of this modal dialog. */
	lateinit var actionsTable: Table
	val actionRows = mutableListOf<Table>()

	open val addCloseAction get() = true
	open val closeButtonText get() = "Cancel"
	var minValueLabelWidth = 250f

	init {
		setFillParent(true)
		closeOnBack()

		titleTable.remove()
		buttons.remove()

		cont.addTable {
			left().defaults().growX()
			header = this
		}.fillX().row()

		cont.addTable {
			body = this
		}.fillX().pad(Style.layoutPad).row()

		cont.addTable {
			actionsTable = this
		}.fillX().padTop(Style.layoutPad).row()

		clearActionRows()
	}

	protected fun inputField(
		hint: String,
		isPassword: Boolean = false,
		default: String = "",
		validator: TextField.TextFieldValidator
	) = run {
		body.row()

		body.addTable(Style.surfaceBackground) {
			addLabel(hint, Style.Label, align = Align.left)
				.color(Style.comment)
				.grow()
		}.pad(Style.layoutPad)
			.margin(Style.layoutMargin)
			.padTop(Style.layoutPad + 5f)
			.fill()
			.left()

		body.textField(style = Style.TextInput)
			.growX().pad(Style.layoutPad)
			.padTop(Style.layoutPad + 5f)
			.minWidth(minValueLabelWidth)
			.fill()
			.with {
				it.hint = "<none>"
				it.validator = validator
				it.setAlignment(Align.left)
				it.setPasswordCharacter('*') // the bullet character is absent in the font
				it.setPasswordMode(isPassword)
			}
			.also { it.row() }
			.get()
			.also { it.content = default }
	}

	/** Removes all action rows and adds a default one. */
	protected fun clearActionRows() {
		actionsTable.clearChildren()
		actionRows.clear()
		nextActionRow()

		if (addCloseAction) {
			action(closeButtonText, action = ::hide)
		}
	}

	/** Adds an action to the specified action row. */
	protected inline fun action(text: String, row: Int = 0, crossinline action: () -> Unit) =
		actionRows[row].textButton(text, Style.ActionButton) { action() }
			.uniformX().growX()
			.fill()
			.pad(Style.layoutPad)
			.margin(Style.buttonMargin)

	/**
	 * Adds a new action row to [actionRows], affecting where [action] adds buttons to by default.
	 * The new row is added above the last one.
	 */
	protected fun nextActionRow() {
		actionsTable.cells.reverse()

		val cell = actionsTable.addTable()
			.growX()
		actionsTable.row()
		actionRows.add(0, cell.get())

		actionsTable.cells.reverse()
	}
}
