package io.minchat.client

import arc.Events
import arc.scene.ui.Label
import arc.util.Log
import com.github.mnemotechnician.mkui.extensions.dsl.*
import com.github.mnemotechnician.mkui.extensions.elements.cell
import com.github.mnemotechnician.mkui.extensions.groups.child
import com.github.mnemotechnician.mkui.extensions.runUi
import io.minchat.client.misc.*
import io.minchat.client.ui.chat.ChatFragment
import io.minchat.common.MINCHAT_VERSION
import io.minchat.rest.MinchatRestClient
import io.minchat.rest.gateway.MinchatGateway
import kotlinx.coroutines.*
import mindustry.Vars
import mindustry.game.EventType
import mindustry.gen.Icon
import mindustry.mod.Mod
import mindustry.ui.Styles
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private var minchatInstance: MinchatMod? = null
/** The only instance of this mod. */
val Minchat get() = minchatInstance 
	?: throw UninitializedPropertyAccessException("Minchat has not been initialised")
val MinchatDispatcher = newFixedThreadPoolContext(5, "minchat")

/**
 * The mai class of the MinChat mod. This class is a singleton:
 * use [Minchat] to access its only instance.
 */
class MinchatMod : Mod(), CoroutineScope {
	val rootJob = SupervisorJob()
	val exceptionHandler = CoroutineExceptionHandler { _, e -> 
 		Log.err("An exception has occurred in MinChat", e)
	}
 	override val coroutineContext = rootJob + exceptionHandler + MinchatDispatcher

	val timestampFormatter = DateTimeFormatter.ofPattern("yyyy.MM.dd HH:mm")
	val timezone = ZoneId.systemDefault()

	/**
	 * The main Minchat client used across the mod.
	 * If it has not been initialised yet ([isConnected] is false), an exception will be thrown upon access.
	 */
	@Volatile
	lateinit var client: MinchatRestClient
		private set
	/**
	 * The gateway used to receive events from the server.
	 * Accessing it will throw an exception if [client] hasn't been itialised yet.
	 *
	 * It's guaranteed that the gateway will be connected by the time it can be accessed.
	 */
	@Volatile
	lateinit var gateway: MinchatGateway
		private set
	/** Returns true if [client] is initialised and a connection is established. */
	val isConnected get() = ::client.isInitialized

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

		MinchatPluginHandler.onInit()

		Events.on(EventType.ClientLoadEvent::class.java) {
			Vars.ui.menufrag.addButton("MinChat", Icon.terminal) {
				if (isConnected) {
					showChatDialog()
				} else {
					// If not yet connected, show a loading fragment and connect
					val job = connectToDefault().then { exception ->
						runUi {
							if (exception != null && exception !is CancellationException) {
								Vars.ui.showErrorMessage(
									"Failed to connect to the Minchst server: ${exception.userReadable()}")
							} else if (exception == null) {
								showChatDialog()
							}
							Vars.ui.loadfrag.hide()
						}
					}

					Vars.ui.loadfrag.apply {
						show("Couldn't connect to the MinChat server before. Retrying...")
						setButton { job.cancel() }
					}
				}
			}

			launch {
				MinchatPluginHandler.onLoad()
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
							Use at your own risk.
						""".trimIndent())
					}.with { it.fireClick() }

					textButton("about us", Styles.togglet) {
						dialog.child<Label>(0).setText("""
							[Kotlin] MinChat client by Mnemotechnician
							
							Discord: @Mnemotechnician#9967
							Github: https://github.com/Mnemotechnician
						""".trimIndent())
					}
				}.marginBottom(60f).row()
			}.show()
		}

		connectToDefault()

	}

	fun showChatDialog() {
		chatFragment.apply(chatDialog.cont)
		chatFragment.onClose(chatDialog::hide)
		chatDialog.show()
	}

	/**
	 * Tries to connect to the given server.
	 * Overrides [client] and [gateway] upon success.
	 *
	 * @throws VersionMismatchException if the server's version is not compatible with the client's.
	 */
	fun connectToServer(url: String) = launch {
		val client = MinchatRestClient(url)

		val serverVersion = client.getServerVersion()
		if (!serverVersion.isCompatibleWith(MINCHAT_VERSION)) {
			Log.err("'$url' uses an incompatible version of MinChat: $serverVersion. The client uses $MINCHAT_VERSION.")
			throw VersionMismatchException(serverVersion, MINCHAT_VERSION)
		}
		if (!serverVersion.isInterchangableWith(MINCHAT_VERSION)) {
			val warning = "The version of '$url' may not be fully compatible with the client ($serverVersion vs $MINCHAT_VERSION)"

			Log.warn(warning)
			Vars.ui.showInfoToast("[!] Minchat warning: $warning", 5f)
		}

		this@MinchatMod.client = client
		gateway = MinchatGateway(client).also { it.connectIfNecessary() }

		MinchatPluginHandler.onConnect()
	}

	/**
	 * Connects to the default server.
	 * Overrides [client] upon success.
	 */
	fun connectToDefault() = run {
		// TODO: UNHARDCODE THIS!
		// this must be overridable + must be fetched from github by default
		connectToServer("https://vps76238.xxvps.net:8443")
	}

}
