package io.minchat.client.ui

import arc.scene.*
import arc.scene.style.*
import arc.scene.ui.*
import arc.scene.ui.layout.*
import arc.util.*
import com.github.mnemotechnician.mkui.extensions.dsl.*
import com.github.mnemotechnician.mkui.extensions.elements.*
import com.github.mnemotechnician.mkui.extensions.groups.*
import io.minchat.client.*
import io.minchat.client.misc.*
import io.minchat.client.misc.MinchatStyle as Style
import io.minchat.common.entity.*
import io.minchat.rest.entity.*
import kotlinx.coroutines.*

/**
 * A dialog allowing the user to manage their MinChat account.
 */
class AuthDialog(parentScope: CoroutineScope) : UserDialog(parentScope) {
	override var user: MinchatUser?
		get() = Minchat.client.selfOrNull()
		set(value) { 
			value?.let { Minchat.client.account?.user = it.data }
		}

	init {
		userLabel.setText {
			user?.tag ?: "[Not logged in]"
		}

		// login + register
		headerTable.row().addTable {
			textButton("LOG IN", Style.ActionButton) {
				LoginDialog().show()
			}.disabled { user != null }
				.pad(Style.layoutPad).margin(Style.buttonMargin)
				.uniformX().growX()

			textButton("REGISTER", Style.ActionButton) {
				RegisterDialog().show()
			}.disabled { user != null }
				.pad(Style.layoutPad).margin(Style.buttonMargin)
				.uniformX().growX()
		}.growX()
	}

	inner class LoginDialog : ModalDialog() {
		init {
			fields.addLabel("Enter your credentials:", Style.Label, align = Align.left)
				.growX().pad(Style.layoutPad)
				.row()

			// todo: move Users.nameLength to User.Companion and unhardcode these limits
			val usernameField = field("Username", false) { 
				it.trim().length in 3..64 
			}
			val passwordField = field("Password", true) {
				it.length in 8..40
			}

			action("Login") {
				val username = usernameField.content
				val password = passwordField.content
				hide()

				val statusString = "Logging in as $username..."
				status(statusString)
				
				launch {
					runSafe {
						Minchat.client.login(username, password)
					}
					// Update AuthDialog
					status(null, override = statusString)
					createActions()
				}
			}.disabled { !usernameField.isValid || !passwordField.isValid }
		}
	}

	inner class RegisterDialog : ModalDialog() {
		init {
			fields.addLabel("Create a new MinChat account:", Style.Label, align = Align.left)
				.growX().pad(Style.layoutPad)
				.row()
			
			// todo: move Users.nameLength to User.Companion and unhardcode these limits
			val usernameField = field("Username", false) { 
				it.isEmpty() || it.trim().length in 3..64 
			}
			val passwordField = field("Confirm password", true) {
				it.isEmpty() || it.length in 8..40
			}
			val passwordConfirmField = field("Password", true) {
				it.isEmpty() || it == passwordField.content
			}

			action("Register") {
				val username = usernameField.content
				val password = passwordField.content
				hide()

				val statusString = "Registering as $username..."
				status(statusString)

				launch {
					runSafe {
						Minchat.client.register(username, password)
					}
					// Update AuthDialog
					status(null, override = statusString)
					createActions()
				}
			}.disabled {
				!usernameField.isValid || !passwordField.isValid
					|| passwordField.content != passwordConfirmField.content
			}
		}
	}
}

fun CoroutineScope.AuthDialog() = AuthDialog(this)
