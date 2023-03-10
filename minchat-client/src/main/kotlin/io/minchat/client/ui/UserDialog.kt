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
import io.minchat.rest.entity.*
import java.time.Instant
import kotlin.coroutines.EmptyCoroutineContext
import kotlinx.coroutines.*
import mindustry.ui.Styles

/** A dialog showing the stats of a user. */
abstract class UserDialog(
	parentScope: CoroutineScope
) : Dialog(), CoroutineScope {
	abstract var user: MinchatUser?
	/** A status string shown at the top. */
	@Volatile var status: String? = null

	override val coroutineContext = parentScope.newCoroutineContext(EmptyCoroutineContext)

	/** A single-row table. */
	lateinit var headerTable: Table
	lateinit var userLabel: Label
	/** A table with 2 columns holding the stats of the user. */
	lateinit var statsTable: Table
	/** A table containing buttons related to the dialog. */
	lateinit var actionsTable: Table

	init {
		setFillParent(true)
		closeOnBack()
		titleTable.remove()
		buttons.remove()
		cont.cell()?.grow()

		cont.addLabel({ status.orEmpty() })
			.color(Style.red).row()

		cont.addTable {
			headerTable = this
			
			addTable(Style.surfaceBackground) {
				margin(Style.buttonMargin)
				addLabel({ user?.tag ?: "Invalid User" })
					.with { userLabel = it }
					.scaleFont(1.1f)
			}.growX().pad(Style.layoutPad)
		}.fillX().row()

		cont.addTable {
			statsTable = this

			addStat("ID") { user?.id?.toString() }
			addStat("Is admin") { user?.isAdmin }
			addStat("Is banned") { user?.isBanned }
			addStat("Messages sent") { user?.messageCount?.toString() }
			addStat("Last active") { user?.lastMessageTimestamp?.let(::formatTimestamp) }
			addStat("Registered") { user?.creationTimestamp?.let(::formatTimestamp) }
		}.fillX().row()

		cont.addTable {
			actionsTable = this
			createActions()
		}.fillX().row()
	}

	/** Clears [actionsTable] and fills it using [addAction]. */
	open fun createActions() {
		actionsTable.clearChildren()

		addAction("Close", ::hide)
		// only add the "edit" and "delete" options if the user can be modified
		if (Minchat.client.account?.user?.let { it.isAdmin || it.id == user?.id } ?: false) {
			addAction("Edit") { mindustry.Vars.ui.showInfo("TODO") }
			addAction("Delete") { mindustry.Vars.ui.showInfo("TODO") }
		}
	}

	/** Adds a stat entry to the stats table. */
	inline fun addStat(name: String, crossinline value: () -> String?) {
		statsTable.row()
		statsTable.addTable(Style.surfaceBackground) {
			margin(Style.buttonMargin)
			addLabel(name, Style.Label, align = Align.left)
				.grow().color(Style.comment)
		}.pad(Style.layoutPad).fill()

		statsTable.addTable(Style.surfaceBackground) {
			margin(Style.buttonMargin)
			addLabel({ value() ?: "N/A" }, Style.Label, align = Align.right)
				.grow().color(Style.foreground)
		}.pad(Style.layoutPad).growX().fillY()
	}

	/** Adds a stat entry to the stats table, using yes/no as the value. */
	@JvmName("addStatYesNo")
	@OverloadResolutionByLambdaReturnType
	inline fun addStat(name: String, crossinline value: () -> Boolean?): Unit =
		addStat(name) {
			value()?.let { if (it) "Yes" else "No" }
		}
	
	/** Adds an action button to the buttons table. */
	inline fun addAction(text: String, crossinline action: () -> Unit) =
		actionsTable.textButton(text, Style.ActionButton) {
			action()
		}.growX().uniformX().margin(Style.buttonMargin).pad(Style.layoutPad)

	/**
	 * Asynchronously fetches a new User object from the server
	 * and updates the current dialog.
	 *
	 * Does nothing if [user] is null.
	 */
	fun update() = run {
		val id = user?.id ?: return@run
		val newStatus = "Updating. Please wait..."
		status(newStatus)
		
		launch {
			runSafe {
				user = Minchat.client.getUserOrNull(id)
			}.onSuccess {
				status(null, override = newStatus)
			}
		}
	}
	
	/**
	 * Sets the current status. If [override] is not null,
	 * changes the status only if the current status is equal to [override].
	 */
	protected fun status(newStatus: String?, override: String? = null) {
		if (override == null || status == override) {
			status = newStatus
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

	/**
	 * Executes [action] and catches any exception.
	 * If an exception is catched, updates the status accordingly.
	 */
	protected inline fun <R> runSafe(action: () -> R) =
		runCatching {
			action()
		}.onFailure { exception ->
			if (exception.isImportant()) {
				status("An error has occurred: ${exception.userReadable()}")
			}
		}
}

fun CoroutineScope.UserDialog(user: MinchatUser) = object : UserDialog(this) {
	@Volatile
	override var user: MinchatUser? = user
}
