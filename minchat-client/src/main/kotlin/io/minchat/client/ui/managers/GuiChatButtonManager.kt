package io.minchat.client.ui.managers

import arc.*
import arc.scene.ui.*
import arc.scene.ui.layout.Table
import arc.util.*
import com.github.mnemotechnician.mkui.extensions.dsl.addTable
import com.github.mnemotechnician.mkui.extensions.elements.cell
import io.minchat.client.Minchat
import mindustry.Vars
import mindustry.game.EventType
import mindustry.ui.Styles

object GuiChatButtonManager {
	val button by lazy {
		TextButton("MinChat", Styles.defaultt).apply {
			label.setWrap(false)
			clicked {
				Minchat.showChatDialog()
			}
		}
	}
	private var lastPlacedContainer: Table? = null

	fun init() {
		Events.on(EventType.WorldLoadEndEvent::class.java) {
			Core.app.post {
				lastPlacedContainer?.remove()

//				if (Vars.mobile) {
//					TODO()
//				} else if (MinchatSettings.guiButtonDesktop) {
				val target = Vars.ui.hudGroup.find<Table>("minimap/position")

				if (target == null) {
					Log.err("Failed to find an appropriate table to place the MinChat chat button.")
					return@post
				}
				// Now things get a little tricky: we need the button to be on the top,
				// so we need to rearrange the table cells.
				val cell = target.addTable {
					add(button)
				}.fillX()

				// Increase the colspan of the position label and align it to the right
				target.find<Label>("position").cell()?.apply {
					val oldColspan = Reflect.get<Int>(this, "colspan")
					colspan(oldColspan + 1).right()
				}

				lastPlacedContainer = cell.get()
				target.cells.remove(cell)
				target.cells.insert(0, cell) // First row, first colum.
				target.layout()
//				}
			}
		}
	}
}
