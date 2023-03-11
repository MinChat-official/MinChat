package io.minchat.client.ui

import arc.scene.*
import arc.scene.style.*
import arc.scene.ui.*
import arc.scene.ui.layout.*
import arc.util.*
import com.github.mnemotechnician.mkui.extensions.dsl.*
import com.github.mnemotechnician.mkui.extensions.elements.*
import com.github.mnemotechnician.mkui.extensions.groups.*
import io.minchat.client.misc.MinchatStyle as Style

/**
 * A dialog used to let the user input some data,
 * choose an action or confirm an action.
 */
abstract class ModalDialog : Dialog() {
	lateinit var fields: Table
	lateinit var actionTable: Table

	init {
		setFillParent(true)
		closeOnBack()

		titleTable.remove()
		buttons.remove()

		cont.addTable {
			fields = this
		}.fillX().pad(Style.layoutPad).fillX().row()

		cont.addTable {
			actionTable = this
			action("Cancel", ::hide)
		}.fillX().pad(Style.layoutPad).fillX()
	}
	

	protected fun field(hint: String, isPassword: Boolean, validator: TextField.TextFieldValidator) = 
		fields.textField(style = Style.TextInput)
			.growX().pad(Style.layoutPad)
			.minWidth(250f)
			.with {
				it.hint = hint
				it.validator = validator
				it.setAlignment(Align.left)
				it.setPasswordCharacter('*') // the bullet character is absent in the font
				it.setPasswordMode(isPassword)
			}
			.also { it.row() }
			.get()

	protected inline fun action(text: String, crossinline listener: () -> Unit) =
		actionTable.textButton(text, Style.ActionButton) { listener() }
			.uniformX().growX().fillY()
			.pad(Style.layoutPad).margin(Style.buttonMargin)
}
