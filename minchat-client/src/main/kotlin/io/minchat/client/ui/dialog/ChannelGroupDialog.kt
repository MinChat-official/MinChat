package io.minchat.client.ui.dialog

import com.github.mnemotechnician.mkui.extensions.dsl.addLabel
import io.minchat.client.Minchat
import io.minchat.client.misc.*
import io.minchat.common.entity.ChannelGroup
import io.minchat.rest.entity.MinchatChannelGroup
import kotlinx.coroutines.CoroutineScope

class ChannelGroupDialog(
	var group: MinchatChannelGroup,
	parentScope: CoroutineScope
) : AbstractStatDialog(parentScope) {
	init {
		header.addLabel("Channel group").row()

		stat("ID") { group.id.toString() }
		stat("Name") { group.name }
		stat("Description") { group.description }

		// Channel groups can only be edited by admins, so no need for canEdit() checks
		if (Minchat.client.selfOrNull()?.role?.isAdmin == true) {
			action("Edit") {
				ChannelGroupEditDialog().show()
			}

			action("Delete") {
				Dialogs.confirm("""
					Are you sure want to delete this group?
					Channels in it won't be deleted.
				""") {
					launchSafeWithStatus("Deleting this group...") {
						group.delete()
						hide()
					}
				}
			}
		}
	}

	inner class ChannelGroupEditDialog : AbstractModalDialog() {
		init {
			header.addLabel("Editing channel group #${group.name}").row()

			val nameField = inputField("Name", default = group.name) {
				it.length in ChannelGroup.nameLength
			}
			val descriptionField = inputField("Description", default = group.description) {
				it.length in ChannelGroup.descriptionLength
			}
			val orderField = inputField("Order", default = group.order.toString()) {
				it.toIntOrNull() != null
			}

			action("Confirm") {
				hide()
				launchSafeWithStatus("Editing this group...") {
					val newGroup = group.edit(
						newName = nameField.text,
						newDescription = descriptionField.text,
						newOrder = orderField.text.toInt()
					)
					this@ChannelGroupDialog.group = newGroup
				}
			}.get().enabledWhenValid(nameField, descriptionField, orderField)
		}
	}
}

class ChannelGroupCreateDialog : AbstractModalDialog() {
	init {
		header.addLabel("Creating a channel group").row()

		val nameField = inputField("Name") {
			it.length in ChannelGroup.nameLength
		}
		val descriptionField = inputField("Description") {
			it.length in ChannelGroup.descriptionLength
		}
		val orderField = inputField("Order") {
			it.toIntOrNull() != null
		}

		action("Confirm") {
			Dialogs.await("Creating the group...") {
				Minchat.client.createChannelGroup(nameField.text, descriptionField.text, orderField.text.toInt())
			}.then { hide() }
		}
	}
}

fun CoroutineScope.ChannelGroupDialog(group: MinchatChannelGroup) =
	ChannelGroupDialog(group, this)
