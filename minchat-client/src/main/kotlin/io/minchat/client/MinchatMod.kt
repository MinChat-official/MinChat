package io.minchat.client

import arc.*
import arc.util.*
import arc.scene.ui.*
import mindustry.mod.*
import mindustry.game.*
import mindustry.ui.*
import mindustry.ui.dialogs.*
import com.github.mnemotechnician.mkui.extensions.dsl.*
import com.github.mnemotechnician.mkui.extensions.groups.*
import kotlinx.coroutines.*

class MinchatMod : Mod() {
	val info = """
		MinChat is currently unfinished.
		Do not use.
	""".trimIndent()

	init {
		//when client load event is fired (that happens only once),
		Events.on(EventType.ClientLoadEvent::class.java) {
			//create a dialog,
			val dialog = BaseDialog("This is an example mod")
			//make the dialog close when the player presses escape / back key
			dialog.closeOnBack()
			 
			dialog.cont.apply {
				//add an info label
				addLabel(info).marginBottom(20f).row()
				
				//and a button group...
				buttonGroup {
					defaults().width(120f)
					
					//containing two buttons that put different messages on the label
					textButton("info", Styles.togglet) {
						dialog.cont.child<Label>(0).setText(info)
					}
					
					textButton("about us", Styles.togglet) {
						dialog.cont.child<Label>(0).setText("""
							[Kotlin] Mindustry Mod Template by Mnemotechnician
							
							Discord: @Mnemotechnician#9967
							Github: https://github.com/Mnemotechnician
						""".trimIndent())
					}
				}.marginBottom(60f).row()
				
				//and don't forget to add a close button to the dialog
				textButton("close") { dialog.hide() }.width(240f)
			}
			
			dialog.show()
		}
	}
	
}
