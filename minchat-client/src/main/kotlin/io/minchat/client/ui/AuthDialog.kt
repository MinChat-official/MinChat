package io.minchat.client.ui

import arc.scene.ui.TextField
import arc.util.Align
import com.github.mnemotechnician.mkui.extensions.dsl.*
import com.github.mnemotechnician.mkui.extensions.elements.content
import io.minchat.client.Minchat
import io.minchat.client.misc.then
import io.minchat.common.entity.User
import io.minchat.rest.entity.MinchatUser
import kotlinx.coroutines.CoroutineScope
import io.minchat.client.misc.MinchatStyle as Style

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
			} //.disabled { user != null }
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
		val usernameField: TextField
		val passwordField: TextField

		init {
			fields.addLabel("Enter your credentials:", Style.Label, align = Align.left)
				.growX().pad(Style.layoutPad)
				.row()

			usernameField = field("Username", false) {
				it.trim().length in User.nameLength
			}
			passwordField = field("Password", true) {
				it.length in User.passwordLength
			}

			action("Login") {
				val username = usernameField.content
				val password = passwordField.content
				hide()

				launchWithStatus("Logging in as $username...") {
					runSafe {
						Minchat.client.login(username, password)
					}
					// Update AuthDialog
					createActions()
				}
			}.disabled { !usernameField.isValid || !passwordField.isValid }.with {
				usernameField.then(passwordField)
				passwordField.then(it)
			}
		}
	}

	inner class RegisterDialog : ModalDialog() {
		val usernameField: TextField
		val nicknameField: TextField
		val passwordField: TextField
		val passwordConfirmField: TextField

		init {
			fields.addLabel("Create a new MinChat account:", Style.Label, align = Align.left)
				.growX().pad(Style.layoutPad)
				.row()
			
			usernameField = field("Username", false) {
				it.trim().length in User.nameLength
			}
			nicknameField = field("Nickname (can be empty)", false) {
				it.isEmpty() || it.isNotBlank()
			}
			passwordField = field("Confirm password", true) {
				it.length in User.passwordLength
			}
			passwordConfirmField = field("Password", true) {
				it == passwordField.content
			}

			action("Register") {
				val username = usernameField.content
				val nickname = nicknameField.content.takeIf { it.isNotBlank() }
				val password = passwordField.content
				hide()

				launchWithStatus("Registering as $username...") {
					runSafe {
						Minchat.client.register(username, nickname, password)
					}
					createActions()
				}
			}.disabled {
				!usernameField.isValid || !passwordField.isValid
					|| passwordField.content != passwordConfirmField.content
			}.with {
				usernameField.then(nicknameField)
				nicknameField.then(passwordField)
				passwordField.then(passwordConfirmField)
				passwordConfirmField.then(it)
			}
		}
	}
}

fun CoroutineScope.AuthDialog() = AuthDialog(this)
