package io.minchat.client.ui

import arc.scene.*
import arc.scene.ui.*
import arc.scene.ui.layout.*
import com.github.mnemotechnician.mkui.extensions.dsl.*
import com.github.mnemotechnician.mkui.extensions.elements.*
import com.github.mnemotechnician.mkui.extensions.groups.*

class AccountDialog : Dialog() {
	init {
		setFillParent(true)
		closeOnBack()
		titleTable.remove()
		cont.cell()?.grow()
	}
}
