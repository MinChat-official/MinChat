package io.minchat.client.ui.dialog

import arc.scene.ui.Label
import arc.util.Align
import com.github.mnemotechnician.mkui.extensions.dsl.*
import com.github.mnemotechnician.mkui.extensions.elements.*
import io.minchat.client.Minchat
import io.minchat.rest.entity.MinchatUser
import kotlinx.coroutines.CoroutineScope
import java.time.Instant
import kotlin.random.Random
import io.minchat.client.misc.MinchatStyle as Style

/**
 * A dialog showing the stats of a user and allowing to modify it.
 */
abstract class UserDialog(
	parentScope: CoroutineScope
) : StatDialog(parentScope) {
	abstract var user: MinchatUser?
	lateinit var userLabel: Label

	init {
		headerTable.addTable(Style.surfaceBackground) {
			margin(Style.buttonMargin)
			addLabel({ user?.tag ?: "Invalid User" })
				.with { userLabel = it }
				.scaleFont(1.1f)
		}.growX().pad(Style.layoutPad)

		addStat("Username") { user?.username }
		addStat("ID") { user?.id?.toString() }
		addStat("Is admin") { user?.isAdmin }
		addStat("Is banned") { user?.isBanned }
		addStat("Messages sent") { user?.messageCount?.toString() }
		addStat("Last active") { user?.lastMessageTimestamp?.let(::formatTimestamp) }
		addStat("Registered") { user?.creationTimestamp?.let(::formatTimestamp) }

		createActions()
	}

	/** Clears [actionsTable] and fills it using [action]. */
	open fun createActions() {
		actionsTable.clearChildren()

		action("Close", ::hide)
		// only add the "edit" and "delete" options if the user can be modified
		if (user?.let(Minchat.client::canEditUser) ?: false) {
			action("Edit") {
				UserEditDialog().show()
			}.disabled { user == null }

			action("Delete") {
				UserDeleteConfirmDialog().show()
			}.disabled { user == null }
		}
	}

	/**
	 * Asynchronously fetches a new User object from the server
	 * and updates the current dialog.
	 *
	 * Does nothing if [user] is null.
	 */
	fun update() = run {
		val id = user?.id ?: return@run
		
		launchWithStatus("Updating. Please wait...") {
			runSafe {
				user = Minchat.client.getUserOrNull(id)
			}
		}
	}

	protected fun formatTimestamp(timestamp: Long) = run {
		val minutes = (System.currentTimeMillis() - timestamp) / 1000L / 60L
		if (minutes < 60 * 24) when {
			// If the user was active less than 24 hours ago, show a literal string
			minutes <= 0L -> "Just now"
			minutes == 1L -> "A minute ago"
			minutes in 2L..<60L -> "$minutes minutes ago"
			minutes in 60L..<120L -> "An hour ago"
			else -> "${minutes / 60} hours ago"
		} else {
			Instant.ofEpochMilli(timestamp)
				.atZone(Minchat.timezone)
				.let { Minchat.timestampFormatter.format(it) }
		}
	}

	inner class UserEditDialog : ModalDialog() {
		val user = this@UserDialog.user!!

		init {
			fields.addLabel("Editing user ${user.tag} (${user.username}).", align = Align.left, wrap = true)
				.fillX().row()

			val usernameField = addField("New nickname", false) {
				it.length in 3..40
			}.also {
				it.content = user.nickname ?: user.username
			}

			action("Confirm") {
				hide()
				launchWithStatus("Editing user ${user.username}...") {
					runSafe {
						// This will also update the minchat account
						this@UserDialog.user = user.edit(
							newNickname = usernameField.content
						)
					}
				}
			}.disabled { !usernameField.isValid }
		}
	}
	
	inner class UserDeleteConfirmDialog : ModalDialog() {
		val user = this@UserDialog.user!!

		init {
			val confirmNumber = Random.nextInt(10_000, 100_000).toString()
			fields.addLabel("""
				Are you sure you want to delete this user account?
				Type "$confirmNumber" to confirm your intention.
			""".trimIndent(), wrap = true).fillX().row()

			val confirmField = addField("Type $confirmNumber", false) {
				it == confirmNumber
			}

			action("Confirm") {
				hide()
				launchWithStatus("Deleting user ${user.tag}...") {
					runSafe {
						user.delete()
						Minchat.client.logout()
					}
				}
			}.disabled { !confirmField.isValid }
		}
	}
}

fun CoroutineScope.UserDialog(user: MinchatUser) = object : UserDialog(this) {
	@Volatile
	override var user: MinchatUser? = user
}
