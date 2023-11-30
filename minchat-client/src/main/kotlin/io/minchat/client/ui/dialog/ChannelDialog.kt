package io.minchat.client.ui.dialog

import arc.scene.ui.Label
import com.github.mnemotechnician.mkui.extensions.dsl.*
import com.github.mnemotechnician.mkui.extensions.elements.*
import io.minchat.client.Minchat
import io.minchat.client.misc.*
import io.minchat.common.entity.*
import io.minchat.rest.entity.*
import kotlinx.coroutines.*
import kotlin.math.abs
import kotlin.random.Random
import io.minchat.client.ui.MinchatStyle as Style

/**
 * A dialog showing the stats of a channel and allowing to modify it.
 */
class ChannelDialog(
	var channel: MinchatChannel,
	parentScope: CoroutineScope
) : AbstractStatDialog(parentScope) {
	lateinit var channelLabel: Label

	/** Either the name of the group the channel belongs to, or the name of the other participant if this is a DM channel. */
	private var groupName: String? = null

	init {
		header.addTable(Style.surfaceBackground) {
			margin(Style.buttonMargin)
			addLabel("#")
			addLabel({ channel.name })
				.with { channelLabel = it }
				.scaleFont(1.1f)
		}.minWidth(300f).growX().pad(Style.layoutPad)

		stat("Name") { channel.name }
		stat("ID") { channel.id.toString() }
		stat("Group") { groupName ?: "<resolving...>" }
		stat("Description") { channel.description }
		stat("View mode") { channel.viewMode.readableName }
		stat("Send mode") { channel.sendMode.readableName }

		if (Minchat.client.selfOrNull()?.canEditChannel(channel) == true) {
			action("Edit") {
				ChannelEditDialog().show()
			}
		}
		if (Minchat.client.selfOrNull()?.canDeleteChannel(channel) == true) {
			action("Delete") {
				ChannelDeleteConfirmDialog().show()
			}
		}

		// Resolve the channel group.
		launch {
			runSafe {
				when (val channel = channel) {
					is NormalMinchatChannel -> {
						groupName = when {
							channel.groupId == ChannelGroup.GLOBAL.id -> "<global>"
							else -> channel.getGroup()?.name ?: "<invalid>"
						}
					}
					is MinchatDMChannel -> {
						if (!Minchat.client.isLoggedIn) return@launch

						val otherUser = if (channel.user1id == Minchat.client.self().id) channel.user2id else channel.user1id
						groupName = Minchat.client.cache.getUser(otherUser)?.tag ?: "<invalid>"
					}
				}
			}
		}
	}

	inner class ChannelEditDialog : AbstractModalDialog() {
		val channel = this@ChannelDialog.channel

		init {
			header.addLabel("Editing channel #${channel.name}").row()

			// Common things
			val nameField = inputField("Name", default = channel.name) {
				it.length in Channel.nameLength
			}

			val descriptionField = inputField("Description", default = channel.description) {
				it.length in Channel.descriptionLength
			}

			val orderField = inputField("Order", default = channel.order.toString()) {
				it.toIntOrNull() != null
			}

			// Normal channel-specific things
			if (channel is NormalMinchatChannel) {
				val defaultGroup = groupName ?: "<keep same>"
				val groupNameField = inputField("Group, or null", default = defaultGroup) {
					it == defaultGroup || it.length in ChannelGroup.nameLength
				}

				val viewModeField = inputField("View mode", default = channel.viewMode.toString()) { mode ->
					Channel.AccessMode.values().any { it.name == mode.uppercase() }
				}

				val sendModeField = inputField("Send mode", default = channel.sendMode.toString()) { mode ->
					Channel.AccessMode.values().any { it.name == mode.uppercase() }
				}

				action("Confirm") {
					Dialogs.await("Editing channel #${channel.name}...") {
						val groupId = when {
							groupNameField.content == defaultGroup -> null
							groupNameField.content.lowercase() == "null" -> -1L
							else -> {
								val groups = Minchat.client.getAllChannelGroups()
								groups.firstOrNull { it.name.equals(groupNameField.content, true) }
									?.id
									?: error("No group with such name.")
							}
						}

						this@ChannelDialog.channel = channel.edit(
							newName = nameField.content,
							newDescription = descriptionField.content,
							newOrder = orderField.content.toInt(),
							newGroupId = groupId,
							newViewMode = viewModeField.content.uppercase().let(Channel.AccessMode::valueOf),
							newSendMode = sendModeField.content.uppercase().let(Channel.AccessMode::valueOf)
						)
					}.onSuccess(::hide)
				}.get().enabledWhenValid(nameField, descriptionField, orderField, groupNameField, viewModeField, sendModeField)
			}

			// DM-specific things
			if (channel is MinchatDMChannel) {
				action("Confirm") {
					hide()
					launchSafeWithStatus("Editing DM channel #${channel.name}...") {
						this@ChannelDialog.channel = channel.edit(
							newName = nameField.content,
							newDescription = descriptionField.content,
							newOrder = orderField.content.toInt()
						)
					}
				}.get().enabledWhenValid(nameField, descriptionField, orderField)
			}
		}
	}

	inner class ChannelDeleteConfirmDialog : AbstractModalDialog() {
		init {
			val confirmString = Random.nextBytes(8).map {
				abs(it % 27) + 'a'.code
			}.joinToString("") { it.toChar().toString() }

			header.addLabel("""
				Are you sure you want to delete this channel?
				
				Make sure you are, or else some people may get very upset.
				
				Type "$confirmString" to confirm your intentions.
			""".trimIndent(), wrap = true).fillX().row()

			val confirmField = inputField("Type $confirmString", false) {
				it == confirmString
			}

			action("Confirm") {
				hide()
				launchWithStatus("Deleting channel #${channel.name}...") {
					runSafe {
						channel.delete()
					}
				}
			}.disabled { !confirmField.isValid }
		}
	}
}

class ChannelCreateDialog : AbstractModalDialog() {
	init {
		header.addLabel("Create a new channel")

		val nameField = inputField("Name", false) {
			it.length in Channel.nameLength
		}

		val descriptionField = inputField("Description", false) {
			it.length in Channel.descriptionLength
		}

		val groupField = inputField("Group", false) {
			it.isEmpty() || it.length in ChannelGroup.nameLength
		}

		val orderField = inputField("Order", false) {
			it.toIntOrNull() != null
		}.apply { content = "0" }

		val viewModeField = inputField("View mode", false) { mode ->
			Channel.AccessMode.values().any { it.name == mode.uppercase() }
		}.apply { content = Channel.AccessMode.EVERYONE.toString() }

		val sendModeField = inputField("Send mode", false) { mode ->
			Channel.AccessMode.values().any { it.name == mode.uppercase() }
		}.apply { content = Channel.AccessMode.LOGGED_IN.toString() }

		action("Create") {
			hide()
			Dialogs.await("Creating channel #${nameField.content}...") {
				val group = when {
					groupField.content.isEmpty() -> null
					else -> Minchat.client.getAllChannelGroups()
						        .firstOrNull { it.name.equals(groupField.content, true) }
					        ?: run {
						        Dialogs.info("Channel group not found: ${groupField.content}")
						        return@await
					        }
				}

				Minchat.client.createChannel(
					name = nameField.content,
					description = descriptionField.content,
					groupId = group?.id,
					order = orderField.content.toInt(),
					viewMode = viewModeField.content.uppercase().let(Channel.AccessMode::valueOf),
					sendMode = sendModeField.content.uppercase().let(Channel.AccessMode::valueOf)
				)
			}.then {
				if (it != null) show() // Re-show dialog on exception
			}
		}.get().enabledWhenValid(nameField, descriptionField, orderField, groupField, viewModeField, sendModeField)
	}
}

fun CoroutineScope.ChannelDialog(channel: MinchatChannel) =
	ChannelDialog(channel, this)
