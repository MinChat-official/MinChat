package io.minchat.client.ui.dialog

import arc.scene.ui.Label
import com.github.mnemotechnician.mkui.extensions.dsl.*
import com.github.mnemotechnician.mkui.extensions.elements.*
import io.minchat.client.Minchat
import io.minchat.client.misc.enabledWhenValid
import io.minchat.common.entity.Channel
import io.minchat.rest.entity.*
import kotlinx.coroutines.CoroutineScope
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
	}

	inner class ChannelEditDialog : AbstractModalDialog() {
		val channel = this@ChannelDialog.channel

		init {
			header.addLabel("Editing channel #${channel.name}").row()

			// Common things
			val nameField = inputField("Name", false) {
				it.length in Channel.nameLength
			}.also { it.content = channel.name }

			val descriptionField = inputField("Description", false) {
				it.length in Channel.descriptionLength
			}.also { it.content = channel.description }

			val orderField = inputField("Order", false) {
				it.toIntOrNull() != null
			}.also { it.content = channel.order.toString() }

			// Normal channel-specific things
			if (channel is NormalMinchatChannel) {
				val groupIdField = inputField("Group ID", false) {
					it.toLongOrNull() != null
				}.also { it.content = channel.groupId?.toString() ?: "-1" }

				val viewModeField = inputField("View mode", false) { mode ->
					Channel.AccessMode.values().any { it.name == mode.uppercase() }
				}.also { it.content = channel.viewMode.toString() }

				val sendModeField = inputField("Send mode", false) { mode ->
					Channel.AccessMode.values().any { it.name == mode.uppercase() }
				}.also { it.content = channel.sendMode.toString() }

				action("Confirm") {
					hide()
					launchSafeWithStatus("Editing channel #${channel.name}...") {
						this@ChannelDialog.channel = channel.edit(
							newName = nameField.content,
							newDescription = descriptionField.content,
							newOrder = orderField.content.toInt(),
							newGroupId = groupIdField.content.toLong(),
							newViewMode = viewModeField.content.uppercase().let(Channel.AccessMode::valueOf),
							newSendMode = sendModeField.content.uppercase().let(Channel.AccessMode::valueOf)
						)
					}
				}.get().enabledWhenValid(nameField, descriptionField, orderField, groupIdField, viewModeField, sendModeField)
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

fun CoroutineScope.ChannelDialog(channel: MinchatChannel) =
	ChannelDialog(channel, this)
