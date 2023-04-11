package io.minchat.client.ui.dialog

import arc.scene.ui.Label
import com.github.mnemotechnician.mkui.extensions.dsl.*
import com.github.mnemotechnician.mkui.extensions.elements.*
import io.minchat.client.Minchat
import io.minchat.common.entity.Channel
import io.minchat.rest.entity.MinchatChannel
import kotlinx.coroutines.CoroutineScope
import kotlin.math.abs
import kotlin.random.Random
import io.minchat.client.misc.MinchatStyle as Style

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

		action("Close", ::hide)
		// It is assumed that the admin status of a user cannot change while they see this dialog.
		if (Minchat.client.canEditChannel(channel)) {
			action("Edit") {
				ChannelEditDialog().show()
			}
			action("Delete") {
				ChannelDeleteConfirmDialog().show()
			}
		}
	}

	inner class ChannelEditDialog : ModalDialog() {
		init {
			fields.addLabel("Editing channel #${channel.name}").row()

			val nameField = addField("Name", false) {
				it.length in Channel.nameLength
			}.also {
				it.content = channel.name
			}
			val descriptionField = addField("Description", false) {
				it.length in Channel.descriptionLength
			}.also {
				it.content = channel.description
			}

			action("Confirm") {
				hide()
				launchWithStatus("Editing channel #${channel.name}...") {
					runSafe {
						channel = channel.edit(
							newName = nameField.content,
							newDescription = descriptionField.content
						)
					}
				}
			}.disabled { !nameField.isValid || !descriptionField.isValid }
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
