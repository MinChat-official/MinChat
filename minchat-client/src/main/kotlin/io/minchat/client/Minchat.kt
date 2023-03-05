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
import com.github.mnemotechnician.mkui.extensions.groups.*
import io.minchat.client.ui.*
import io.minchat.rest.*
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

	val client = MinchatRestClient("http://127.0.0.1:8080")

	val chatFragment = ChatFragment(coroutineContext)
	val chatDialog by lazy { createBaseDialog(title = "Minchat", addCloseButton = true) {} }

	init {
		require(minchatInstance == null) { "Do not." }
		minchatInstance = this

		Events.on(EventType.ClientLoadEvent::class.java) {
			Vars.ui.menufrag.addButton("MinChat", Icon.terminal) {
				chatFragment.apply(chatDialog.cont)
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
					}
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
