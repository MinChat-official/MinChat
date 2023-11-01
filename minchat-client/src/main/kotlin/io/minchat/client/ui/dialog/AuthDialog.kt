package io.minchat.client.ui.dialog

import arc.scene.ui.TextField
import arc.util.Align
import com.github.mnemotechnician.mkui.extensions.dsl.*
import com.github.mnemotechnician.mkui.extensions.elements.content
import io.minchat.client.*
import io.minchat.client.misc.then
import io.minchat.client.plugin.MinchatPluginHandler
import io.minchat.client.plugin.impl.AccountSaverPlugin
import io.minchat.client.ui.tutorial.Tutorials
import io.minchat.common.entity.User
import io.minchat.rest.entity.MinchatUser
import kotlinx.coroutines.CoroutineScope
import io.minchat.client.ui.MinchatStyle as Style

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
		header.row().addTable {
			textButton({ if (user == null) "LOG IN" else "CHANGE ACCOUNT" }, Style.ActionButton) {
				LoginDialog().show()
			}
				.pad(Style.layoutPad).margin(Style.buttonMargin)
				.uniformX().growX()

			textButton(if (user == null) "REGISTER" else "LOG OUT", Style.ActionButton) {
				if (user == null) {
					RegisterDialog().show()
				} else {
					Dialogs.confirm("Are you sure?") {
						Minchat.client.logout()
						ClientEvents.fireAsync(AuthorizationEvent(Minchat.client, true))
						MinchatPluginHandler.get<AccountSaverPlugin>()?.forgetAccount()
					}
				}
			}
				.pad(Style.layoutPad).margin(Style.buttonMargin)
				.uniformX().growX()
		}.growX()

		Tutorials.authorization.trigger()
	}

	inner class LoginDialog : AbstractModalDialog() {
		val usernameField: TextField
		val passwordField: TextField

		init {
			header.addLabel("Enter your credentials:", Style.Label, align = Align.left)
				.growX().pad(Style.layoutPad)
				.row()

			usernameField = inputField("Username", false) {
				it.trim().length in User.nameLength
			}
			passwordField = inputField("Password", true) {
				it.length in User.passwordLength
			}

			action("Login") {
				val username = usernameField.content
				val password = passwordField.content
				hide()

				launchWithStatus("Logging in as $username...") {
					runSafe {
						Minchat.client.login(username, password)
						ClientEvents.fire(AuthorizationEvent(Minchat.client, true))
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

	inner class RegisterDialog : AbstractModalDialog() {
		val usernameField: TextField
		val nicknameField: TextField
		val passwordField: TextField
		val passwordConfirmField: TextField

		init {
			header.addLabel("Create a new MinChat account:", Style.Label, align = Align.left)
				.growX().pad(Style.layoutPad)
				.row()
			
			usernameField = inputField("Username", false) {
				it.trim().length in User.nameLength
			}
			nicknameField = inputField("Nickname (can be empty)", false) {
				it.isEmpty() || it.isNotBlank()
			}
			passwordField = inputField("Confirm password", true) {
				it.length in User.passwordLength
			}
			passwordConfirmField = inputField("Password", true) {
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
						ClientEvents.fire(AuthorizationEvent(Minchat.client, true))
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
