package io.minchat.client.ui.dialog

import arc.Core
import arc.graphics.Color
import arc.scene.style.TextureRegionDrawable
import arc.scene.ui.Label
import arc.scene.ui.layout.Table
import arc.util.*
import com.github.mnemotechnician.mkui.extensions.dsl.*
import com.github.mnemotechnician.mkui.extensions.elements.*
import io.ktor.utils.io.jvm.javaio.*
import io.minchat.client.Minchat
import io.minchat.client.misc.*
import io.minchat.client.ui.AsyncImage
import io.minchat.client.ui.MinchatStyle.buttonMargin
import io.minchat.client.ui.MinchatStyle.layoutMargin
import io.minchat.client.ui.MinchatStyle.layoutPad
import io.minchat.client.ui.chat.UserAvatarElement
import io.minchat.common.entity.*
import io.minchat.rest.entity.MinchatUser
import kotlinx.coroutines.*
import mindustry.Vars
import java.io.File
import java.time.Instant
import kotlin.random.Random
import kotlin.reflect.KMutableProperty0
import io.minchat.client.ui.MinchatStyle as Style

/**
 * A dialog showing the stats of a user and allowing to modify it.
 */
abstract class UserDialog(
	parentScope: CoroutineScope
) : AbstractStatDialog(parentScope) {
	abstract var user: MinchatUser?
	lateinit var userLabel: Label

	init {
		// utility function
		fun User.Punishment?.toExplanation() =
			this?.let {
				val time = if (expiresAt == null) "Forever" else "Until ${expiresAt!!.toTimestamp()}"
				val reason = " (${reason ?: "no reason specified"})"
				"$time$reason"
			} ?: "No"

		header.addTable(Style.surfaceBackground) {
			margin(Style.buttonMargin)

			add(UserAvatarElement(
				{ user?.id },
				{ user?.avatar },
				true
			)).size(48f).padRight(layoutPad).apply {
				get().clicked {
					val name = user?.displayName ?: return@clicked
					val avatar = get().drawable ?: return@clicked

					Dialogs.imageView("Viewing avatar: $name", avatar)
				}
			}

			addLabel({ user?.tag ?: "Invalid User" })
				.with { userLabel = it }
				.scaleFont(1.1f)
		}.growX().pad(layoutPad)

		stat("Nickname") { user?.nickname }
		stat("ID") { user?.id?.toString() }
		stat("Role") { user?.role?.readableName }
		stat("Banned") { user?.let { it.ban.toExplanation() } }
		stat("Muted") { user?.let { it.mute.toExplanation() } }
		stat("Messages sent") { user?.messageCount?.toString() }
		stat("Last active") { user?.lastMessageTimestamp?.let(::formatTimestamp) }
		stat("Registered") { user?.creationTimestamp?.let(::formatTimestamp) }

		createActions()
	}

	/** Clears [actionsTable] and fills it using [action]. */
	open fun createActions() {
		val self = Minchat.client.selfOrNull()
		clearActionRows()

		action("Edit") {
			UserEditDialog().show()
		}.disabled {
			self == null || user?.let { self.canEditUser(it) } != true
		}

		action("Delete") {
			UserDeleteConfirmDialog().show()
		}.disabled {
			self == null || user?.let { self.canDeleteUser(it) } != true
		}

		nextActionRow()
		if (self != null && user?.let { self.canModifyUserPunishments(it) } == true) {
			action("Punishments") {
				AdminPunishmentsDialog().show()
			}.disabled { user == null }
		}

		action("Message") {
			DMCreationDialog().show()
		}.disabled { user == null || self == null }
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
				createActions()
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

	inner class UserEditDialog : AbstractModalDialog() {
		val user = this@UserDialog.user!!
		var newAvatar = user.avatar
			set(value) {
				field = value
				avatarElement.avatar = value
			}

		lateinit var avatarElement: UserAvatarElement

		init {
			header.addLabel("Editing user ${user.tag} (${user.displayName}).", align = Align.left, wrap = true)
				.fillX().row()

			// Avatar preview & change button
			body.addTable(Style.surfaceBackground) {
				margin(layoutMargin)

				add(UserAvatarElement(user.id, user.avatar, true))
					.maxSize(128f)
					.apply { avatarElement = get() }

				// Actions - to the left of the avatar
				addTable {
					top()
					textButton("Change", Style.InnerActionButton) {
						Dialogs.choices(
							"What to change your avatar to?",
							"Mindustry icon" to { IconAvatarChangeDialog().show() },
							"Image" to { ImageAvatarChangeDialog().show() },
							"Nothing" to {
								Dialogs.confirm("Are you sure you want to reset your avatar?") {
									newAvatar = null
								}
							},
							cancellable = true
						)
					}.margin(buttonMargin).growX()
				}.growX()
			}.pad(layoutPad).fillX()
				.colspan(2)
				.row()

			val usernameField = inputField("New nickname", default = user.nickname ?: user.username) {
				it.length in 3..40
			}

			action("Confirm") {
				hide()
				launchSafeWithStatus("Editing user ${user.username}...") {
					if (usernameField.content != user.nickname) {
						this@UserDialog.user = user.edit(
							newNickname = usernameField.content
						)
					}

					if (newAvatar != user.avatar) when (val avatar = newAvatar) {
						// If an icon avatar, call the icon avatar route.
						// If a local one, call the image avatar route. Otherwise, it's an error.
						null -> user.setIconAvatar(null)
						is User.Avatar.IconAvatar -> user.setIconAvatar(avatar.iconName)
						is User.Avatar.LocalAvatar -> {
							Vars.ui.loadfrag.apply {
								show("Uploading avatar...")
								setButton { cancel(CancellationException("Avatar upload was cancelled.")) }

								user.uploadAvatar(avatar.file.inputStream().toByteReadChannel()) {
									setProgress(it)
								}

								hide()
							}
						}
						is User.Avatar.ImageAvatar -> {
							error("Cannot set avatar to a non-local image avatar!")
						}
					}
				}
			}.disabled { !usernameField.isValid }
		}

		inner class IconAvatarChangeDialog : AbstractModalDialog() {
			lateinit var iconsTable: Table

			init {
				header.addLabel("Choose an icon.").fillX().row()

				// A search bar
				body.textField("", Style.TextInput) {
					rebuildWithCriteria(it.trim().replace(' ', '-').takeIf { it.isNotEmpty() })
				}.fillX()
					.pad(layoutPad)
					.apply { get().hint = "Type to search" }
					.row()

				body.addTable(Style.surfaceBackground) {
					// The icon list
					limitedScrollPane(limitW = false, limitH = true) {
						margin(layoutMargin)
						iconsTable = this
					}.pad(layoutPad)
						.fillX()
						.height(Core.graphics.height / 3f)
						.row()
				}.margin(layoutMargin).pad(layoutPad)
					.fillX()
					.row()

				rebuildWithCriteria(null)
			}

			fun rebuildWithCriteria(queryString: String?) {
				iconsTable.clear()

				val icons = Core.atlas.regions.toList().filter {
					it.name.endsWith("-ui")
					&& (queryString == null || it.name.contains(queryString, true))
				}.distinctBy {
					it.name.removeSuffix("-ui")
				}

				val iconsPerRow = (Core.graphics.width / 64).coerceAtLeast(2);
				for (icon in icons) {
					val image = iconsTable.addImage(TextureRegionDrawable(icon), scaling = Scaling.fill)
						.size(48f)
						.pad(layoutPad)
						.rowPer(iconsPerRow)
						.get()

					image.clicked {
						newAvatar = User.Avatar.IconAvatar(icon.name)
						hide()
					}
				}
			}
		}

		inner class ImageAvatarChangeDialog : AbstractModalDialog() {
			var file: File? = null
			lateinit var image: AsyncImage

			init {
				header.addLabel("Choose an new image.").fillX().row()

				body.addTable(Style.surfaceBackground) {
					margin(layoutMargin)
					add(AsyncImage(this@UserDialog).also {
						image = it
						it.setFileAsync { user.getImageAvatar(true)!! }
					})
						.maxSize(256f)
						.minSize(48f)
						.apply { get() }
				}.pad(layoutPad).row()

				action("Confirm") {
					newAvatar = User.Avatar.LocalAvatar(file!!)
					hide()
				}.disabled { file == null || !image.isLoaded }

				nextActionRow()
				action("Choose file") {
				}
			}
		}
	}
	
	inner class UserDeleteConfirmDialog : AbstractModalDialog() {
		val user = this@UserDialog.user!!

		init {
			val confirmNumber = Random.nextInt(10_000, 100_000).toString()
			header.addLabel("""
				Are you sure you want to delete user "${user.nickname}?
				Type "$confirmNumber" to confirm your intention.
			""".trimIndent(), wrap = false).fillX().row()

			val confirmField = inputField("Type $confirmNumber", false) {
				it == confirmNumber
			}

			action("Confirm") {
				hide()
				launchSafeWithStatus("Deleting user ${user.tag}...") {
					user.delete()
					if (user.id == Minchat.client.account?.user?.id) {
						Minchat.client.logout()
					}
				}
			}.disabled { !confirmField.isValid }
		}
	}

	inner class AdminPunishmentsDialog : AbstractModalDialog() {
		val user = this@UserDialog.user!!
		var newMute = user.mute
		var newBan = user.ban

		init {
			update()

			action("Save") {
				launchSafeWithStatus("Updating...") {
					hide()
					val newUser = Minchat.client.modifyUserPunishments(user, newMute, newBan)
					this@UserDialog.user = newUser
				}
			}.disabled { user.mute == newMute && user.ban == newBan}
		}

		fun update() {
			body.clearChildren()
			addPunishmentView(
				"Ban",
				"banned",
				{ newBan },
				{ AddPunishmentDialog(::newBan).show() }
			)
			addPunishmentView(
				"Mute",
				"muted",
				{ newMute },
				{ AddPunishmentDialog(::newMute).show() }
			)
		}

		private inline fun addPunishmentView(
			name: String,
			nameWithSuffix: String,
			getter: () -> User.Punishment?,
			crossinline action: () -> Unit
		) {
			body.addTable(Style.surfaceBackground) {
				defaults().left()

				addLabel("$name status").pad(layoutPad).row()

				val punishment = getter()
				if (punishment == null) {
					addLabel("This user is not $nameWithSuffix.")
						.color(Color.green)
						.pad(layoutPad)
						.row()
				} else {
					addLabel("This user is $nameWithSuffix")
						.color(Color.yellow)
						.pad(layoutPad).padBottom(0f)
					row()
					addLabel("    Expires: ${punishment.expiresAt?.toTimestamp() ?: "never"}")
					row()
					addLabel("    Reason: ${punishment.reason ?: "none"}")
					row()
				}

				textButton("MODIFY", Style.InnerActionButton) { action() }
					.fillX()
					.pad(layoutPad).margin(buttonMargin)
			}.margin(layoutMargin).pad(layoutPad).fillX().row()
		}

		inner class AddPunishmentDialog(val property: KMutableProperty0<User.Punishment?>) : AbstractModalDialog() {
			val punishment = property.get()

			init {
				body.addLabel("You are modifying a punishment value of the user ${user.displayName}!", wrap = true)
					.pad(layoutPad)
					.fillX()
					.row()

				val duration = inputField("Duration (forever, 10m, 20h, 10d)", false) {
					it.equals("forever", true) || it.parseUnitedDuration() != null
				}
				punishment?.expiresAt?.let {
					duration.content = (it - System.currentTimeMillis()).toUnitedDuration()
				}

				val reason = inputField("Reason", false) { true }
				punishment?.reason?.let { reason.content = it }

				action("Change") {
					try {
						val expires = System.currentTimeMillis() + duration.content.parseUnitedDuration()!!
						property.set(User.Punishment(expires, reason.content.takeIf { it.isNotBlank() }))

						this@AddPunishmentDialog.hide()
						this@AdminPunishmentsDialog.update()
					} catch (e: Exception) {
						status("Error: $e")
					}
				}.disabled { !duration.isValid }
			}
		}
	}

	inner class DMCreationDialog : AbstractModalDialog() {
		val user = this@UserDialog.user!!

		init {
			header.apply {
				addLabel("Creating a DM channel").row()
				addLabel("With ${user.displayName}").row()
				addLabel("(${user.tag})").row()
			}

			val nameField = inputField("Channel name", false) {
				it.length in Channel.nameLength
			}
			val descriptionField = inputField("Description", false) {
				it.length in Channel.descriptionLength
			}
			val orderField = inputField("Order", false) {
				it.toIntOrNull() != null
			}.also { it.content = "1" }

			action("Create") {
				hide()
				launchSafeWithStatus("Creating DM channel...") {
					val channel = user.createDMChannel(nameField.content, descriptionField.content, orderField.content.toInt())

					// Set the channel as active and reload ui
					Minchat.chatFragment.apply {
						currentChannel = channel
						updateChatUi()
					}
				}
			}
		}
	}
}

fun CoroutineScope.UserDialog(user: MinchatUser) = object : UserDialog(this) {
	@Volatile
	override var user: MinchatUser? = user
}
