package io.minchat.client.ui.dialog

import arc.scene.ui.Label
import com.github.mnemotechnician.mkui.extensions.dsl.*
import com.github.mnemotechnician.mkui.extensions.elements.*
import io.minchat.client.Minchat
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
) : StatDialog(parentScope) {
	lateinit var channelLabel: Label

	init {
		headerTable.addTable(Style.surfaceBackground) {
			margin(Style.buttonMargin)
			addLabel("#")
			addLabel({ channel.name })
				.with { channelLabel = it }
				.scaleFont(1.1f)
		}.minWidth(300f).growX().pad(Style.layoutPad)

		addStat("Name") { channel.name }
		addStat("ID") { channel.id.toString() }
		addStat("Description") { channel.description }
		addStat("View mode") { channel.viewMode.readableName }
		addStat("Send mode") { channel.sendMode.readableName }

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

		action("Close", action = ::hide)
	}

	inner class ChannelEditDialog : ModalDialog() {
		val channel = this@ChannelDialog.channel

		init {
			fields.addLabel("Editing channel #${channel.name}").row()

			// Common things
			val nameField = addField("Name", false) {
				it.length in Channel.nameLength
			}.also { it.content = channel.name }

			val descriptionField = addField("Description", false) {
				it.length in Channel.descriptionLength
			}.also { it.content = channel.description }

			val orderField = addField("Order", false) {
				it.toIntOrNull() != null
			}.also { it.content = channel.order.toString() }

			// Normal channel-specific things
			if (channel is NormalMinchatChannel) {
				val groupIdField = addField("Group ID", false) {
					it.toLongOrNull() != null
				}.also { it.content = channel.groupId.toString() }

				val viewModeField = addField("View mode", false) { mode ->
					Channel.AccessMode.values().any { it.name == mode.uppercase() }
				}.also { it.content = channel.viewMode.toString() }

				val sendModeField = addField("Send mode", false) { mode ->
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
				}.disabled { !nameField.isValid || !descriptionField.isValid }
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
				}
			}
		}
	}

	inner class ChannelDeleteConfirmDialog : ModalDialog() {
		init {
			val confirmString = Random.nextBytes(15).map {
				abs(it % 27) + 'a'.code
			}.joinToString("") { it.toChar().toString() }

			fields.addLabel("""
				Are you sure you want to delete this channel?
				
				Make sure you are, or else some people may get very upset.
				
				Type "$confirmString" to confirm your intentions.
			""".trimIndent(), wrap = true).fillX().row()

			val confirmField = addField("Type $confirmString", false) {
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
