package io.minchat.client.ui.dialog

import arc.scene.actions.Actions.action
import arc.scene.ui.*
import arc.scene.ui.layout.Table
import arc.util.Align
import com.github.mnemotechnician.mkui.extensions.dsl.*
import com.github.mnemotechnician.mkui.extensions.elements.hint
import io.minchat.client.ui.MinchatStyle as Style

/**
 * A dialog used to let the user input some data,
 * choose an action or confirm an action.
 */
abstract class AbstractModalDialog : Dialog() {
	/** A table atop all other tables. */
	lateinit var header: Table
	/** A table containing the fields of this dialog. If you use [addField], it will contain two columns. */
	lateinit var fields: Table
	/** A table containing actions of this modal dialog. */
	lateinit var actionsTable: Table
	val actionRows = mutableListOf<Table>()

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
			fields = this
		}.fillX().pad(Style.layoutPad).row()

		cont.addTable {
			actionsTable = this
		}.fillX().pad(Style.layoutPad).row()

		clearActionRows()
	}

	protected fun addField(hint: String, isPassword: Boolean, validator: TextField.TextFieldValidator) = run {
		fields.addTable(Style.surfaceBackground) {
			addLabel(hint, Style.Label, align = Align.left)
				.color(Style.comment)
				.grow()
		}.pad(Style.layoutPad)
			.margin(Style.layoutMargin)
			.padTop(Style.layoutPad + 5f)
			.fill()
			.left()

		fields.textField(style = Style.TextInput)
			.growX().pad(Style.layoutPad)
			.padTop(Style.layoutPad + 5f)
			.minWidth(250f)
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
	}

	/** Removes all action rows and adds a default one. */
	protected fun clearActionRows() {
		actionsTable.clearChildren()
		actionRows.clear()
		nextActionRow()
		action("Cancel", action = ::hide)
	}

	/** Adds an action to the specified action row. */
	protected inline fun action(text: String, row: Int = 0, crossinline action: () -> Unit) =
		actionRows[row].textButton(text, Style.ActionButton) { action() }
			.uniformX().growX().fillY()
			.pad(Style.layoutPad).margin(Style.buttonMargin)

	/**
	 * Adds a new action row to [actionRows], affecting where [action] adds buttons to by default.
	 * The new row is added above the last one.
	 */
	protected fun nextActionRow() {
		actionsTable.cells.reverse()

		val cell = actionsTable.addTable()
			.growX().pad(Style.layoutPad)
		actionsTable.row()
		actionRows.add(0, cell.get())

		actionsTable.cells.reverse()
	}
}
