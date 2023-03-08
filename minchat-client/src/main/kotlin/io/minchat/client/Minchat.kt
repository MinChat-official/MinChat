package io.minchat.client

import arc.*
import arc.util.*
import arc.scene.ui.*
import mindustry.Vars
import mindustry.mod.*
import mindustry.game.*
import mindustry.gen.*
import mindustry.ui.*
import mindustry.ui.dialogs.*
import com.github.mnemotechnician.mkui.extensions.dsl.*
import com.github.mnemotechnician.mkui.extensions.elements.*
import com.github.mnemotechnician.mkui.extensions.groups.*
import io.minchat.client.ui.*
import io.minchat.rest.*
import java.time.*
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.*

private var minchatInstance: MinchatMod? = null
/** The only instance of this mod. */
val Minchat get() = minchatInstance 
	?: throw UninitializedPropertyAccessException("Minchat has not been initialised")
val MinchatDispatcher = newFixedThreadPoolContext(5, "minchat")

class MinchatMod : Mod(), CoroutineScope {
	val rootJob = SupervisorJob()
	val exceptionHandler = CoroutineExceptionHandler { _, e -> 
 		Log.err("An exception has occurred in MinChat", e)
	}
 	override val coroutineContext = rootJob + exceptionHandler + MinchatDispatcher

	val timestampFormatter = DateTimeFormatter.ofPattern("yyyy.MM.dd HH:mm")
	val timezone = ZoneId.systemDefault()

	// TODO: UNHARDCODE THIS!
	// this must be overridable + must be fetched from github by default
	//
	// one way to achieve this is to make it a lateinit var,
	// try to load the default url asynchronously at startup and, if failed, explicitly
	// retry the next time the user opens the minchst dialog/window
	// however, this shouldn't be done if it's overriden in the settings
	var client = MinchatRestClient("https://vps76238.xxvps.net:8443")

	val chatFragment by lazy { ChatFragment(this) }
	val chatDialog by lazy { createDialog(title = "") {
		it.setFillParent(true)
		it.setBackground(Styles.none)
		it.titleTable.remove()
		cell()?.grow()?.pad(10f)
	} }

	init {
		require(minchatInstance == null) { "Do not." }
		minchatInstance = this

		Events.on(EventType.ClientLoadEvent::class.java) {
			Vars.ui.menufrag.addButton("MinChat", Icon.terminal) {
				chatFragment.apply(chatDialog.cont)
				chatFragment.onClose(chatDialog::hide)
				chatDialog.show()
			}
		}

		Events.on(EventType.ClientLoadEvent::class.java) {
			createBaseDialog("MinChat", addCloseButton = true) {
				val dialog = this

				addLabel("").marginBottom(20f).row()
				buttonGroup {
					defaults().width(120f)
					textButton("info", Styles.togglet) {
						dialog.child<Label>(0).setText("""
							MinChat is currently unfinished.
							Do not use.
						""".trimIndent())
					}.with { it.fireClick() }

					textButton("about us", Styles.togglet) {
						dialog.child<Label>(0).setText("""
							[Kotlin] Mindustry Mod Template by Mnemotechnician
							
							Discord: @Mnemotechnician#9967
							Github: https://github.com/Mnemotechnician
						""".trimIndent())
					}
				}.marginBottom(60f).row()
			}.show()
		}
	}
}
