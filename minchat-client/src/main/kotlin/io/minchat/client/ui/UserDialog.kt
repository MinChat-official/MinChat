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

		cont.addTable(Style.surfaceBackground) {
			margin(Style.layoutMargin)
			headerTable = this

			addLabel({ user?.tag ?: "Invalid User" }).color(Style.green).scaleFont(1.5f)
		}.fillX().pad(Style.layoutPad).row()

		addLabel("Info", align = Align.left)
			.color(Style.foreground).fillX().colspan(2)

		cont.addTable {
			statsTable = this

			addStat("ID") { user?.id?.toString() ?: "N/A" }
			addStat("Is admin") { user?.isAdmin ?: false }
			addStat("Is banned") { user?.isBanned ?: false }
			addStat("Messages sent") { user?.messageCount?.toString() ?: "N/A" }
			addStat("Last active") {
				user?.lastMessageTimestamp?.let(::formatTimestamp) ?: "N/A" 
			}
			addStat("Registered") { 
				user?.creationTimestamp?.let(::formatTimestamp) ?: "N/A"
			}
		}.fillX().row()

		cont.addTable {
			actionsTable = this
			
			addAction("Close", ::hide)
		}.fillX().row()
	}

	/** Adds a stat entry to the stats table. */
	inline fun addStat(name: String, crossinline value: () -> String) {
		statsTable.row()
		statsTable.addTable(Style.surfaceBackground) {
			margin(10f)
			addLabel(name, StatLabelStyle, align = Align.left)
				.grow().color(Style.comment)
		}.pad(Style.layoutPad).fill()

		statsTable.addTable(Style.surfaceBackground) {
			margin(10f)
			addLabel(value, StatLabelStyle, align = Align.right)
				.grow().color(Style.foreground)
		}.pad(Style.layoutPad).fill()
	}

	/** Adds a stat entry to the stats table, using yes/no as the value. */
	@JvmName("addStatYesNo")
	@OverloadResolutionByLambdaReturnType
	inline fun addStat(name: String, crossinline value: () -> Boolean): Unit =
		addStat(name, { if (value()) "Yes" else "No" })
	
	/** Adds an action button to the buttons table. */
	inline fun addAction(text: String, crossinline action: () -> Unit) {
		actionsTable.textButton(text, ActionButtonStyle) {
			action()
		}.growX().uniformX().margin(10f).pad(Style.layoutPad)
	}

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
	
	object StatLabelStyle : Label.LabelStyle(Styles.defaultLabel) {
		init {
			// background = Style.surfaceBackground
		}
	}

	object ActionButtonStyle : TextButton.TextButtonStyle(Styles.defaultt) {
		init {
			up = Style.surfaceBackground
			down = Style.surfaceDown
			over = Style.surfaceOver
		}
	}
}

fun CoroutineScope.UserDialog(user: MinchatUser) = object : UserDialog(this) {
	@Volatile
	override var user: MinchatUser? = user
}
