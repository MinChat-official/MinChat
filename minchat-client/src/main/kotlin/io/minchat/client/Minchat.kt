package io.minchat.client

import arc.Events
import arc.scene.ui.Label
import com.github.mnemotechnician.mkui.delegates.setting
import com.github.mnemotechnician.mkui.extensions.dsl.*
import com.github.mnemotechnician.mkui.extensions.elements.cell
import com.github.mnemotechnician.mkui.extensions.groups.child
import com.github.mnemotechnician.mkui.extensions.runUi
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.minchat.client.config.*
import io.minchat.client.misc.*
import io.minchat.client.plugin.MinchatPluginHandler
import io.minchat.client.plugin.impl.AccountSaverPlugin
import io.minchat.client.ui.*
import io.minchat.client.ui.chat.ChatFragment
import io.minchat.client.ui.managers.*
import io.minchat.common.*
import io.minchat.rest.*
import io.minchat.rest.gateway.MinchatGateway
import kotlinx.coroutines.*
import mindustry.Vars
import mindustry.game.EventType
import mindustry.mod.Mod
import mindustry.ui.Styles
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.concurrent.thread

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
		if (e !is CancellationException) {
			logger.error(e) { "An exception has occured in MinChat" }
		}
	}
 	override val coroutineContext = rootJob + exceptionHandler + MinchatDispatcher

	val timestampFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy.MM.dd HH:mm")
	val timezone: ZoneId = ZoneId.systemDefault()

	val dataDir = Vars.dataDirectory.file().resolve("minchat").also { it.mkdir() }
	val cacheDir = dataDir.resolve("cache").also { it.mkdir() }

	val logger = BaseLogger.getContextSawmill()

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
	val isConnected get() = ::client.isInitialized && ::gateway.isInitialized && gateway.isConnected

	val chatFragment by lazy { ChatFragment(this) }
	private val chatDialog by lazy { createDialog(title = "") {
		it.setFillParent(true)
		it.setBackground(Styles.none)
		it.titleTable.remove()
		cell()?.grow()?.pad(10f)

		it.shown {
			ClientEvents.fireAsync(ChatDialogEvent(false))
		}
		it.hidden {
			ClientEvents.fireAsync(ChatDialogEvent(true))
		}
	} }

	/** The main MinChat GitHub client used across the mod. */
	val githubClient = MinchatGithubClient()

	init {
		require(minchatInstance == null) { "Do not." }
		minchatInstance = this
		LoggerManager.init()

		Runtime.getRuntime().addShutdownHook(thread(start = false, block = ::onShutdown))

		runUi {
			MinchatPluginHandler.onInit()
		}

		Events.on(EventType.ClientLoadEvent::class.java) {
			Vars.ui.menufrag.addButton("MinChat", R.chat) {
				showChatDialog()
			}

			ClientEvents.fireAsync(LoadEvent)

			MinchatSettings.createSettings()
			MinchatBackgroundHandler.start()
		}

		Events.on(EventType.ClientLoadEvent::class.java) {
			var dontShowInfoAgain by setting(false, MinchatSettings.prefix)

			if (!dontShowInfoAgain) createBaseDialog("MinChat", addCloseButton = true) {
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
							
							Discord: @Mnemotechnician
							Github: https://github.com/Mnemotechnician
						""".trimIndent())
					}
				}.marginBottom(60f).row()

				check("don't show again") {
					dontShowInfoAgain = it
				}.row()
			}.show()
		}

		MinchatKeybinds.registerDefaultKeybinds()
		GuiChatButtonManager.init()
		UnreadsManager.load()

		launch {
			delay(100L) // TODO: possible race condition
			connectToDefault()
			client.fileCache.loadFromDrive()
		}
	}

	/**
	 * Shows the chat dialog.
	 * May show a loading screen if the server hasn't been reached yet.
	 */
	fun showChatDialog() {
		if (chatDialog.isShown) return

		if (isConnected) {
			chatFragment.apply(chatDialog.cont)
			chatFragment.onClose(chatDialog::hide)
			chatDialog.show()
		} else {
			// If not yet connected, show a loading fragment and connect
			val job = launch {
				connectToDefault()
			}.then { exception ->
				runUi {
					if (exception != null && exception !is CancellationException) {
						Vars.ui.showErrorMessage(
							"Failed to connect to the Minchat server: ${exception.userReadable()}")
					} else if (exception == null) {
						chatFragment.apply(chatDialog.cont)
						chatFragment.onClose(chatDialog::hide)
						chatDialog.show()
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

	/**
	 * Tries to connect to the given server.
	 * Overrides [client] and [gateway] upon success.
	 *
	 * @throws VersionMismatchException if the server's version is not compatible with the client's.
	 */
	suspend fun connectToServer(url: String) {
		val client = MinchatRestClient(url, cacheDir.absolutePath)

		val serverVersion = client.getServerVersion()
		if (!serverVersion.isCompatibleWith(MINCHAT_VERSION)) {
			logger.error { "'$url' uses an incompatible version of MinChat: $serverVersion. The client uses $MINCHAT_VERSION." }
			throw VersionMismatchException(serverVersion, MINCHAT_VERSION)
		}
		if (!serverVersion.isInterchangeableWith(MINCHAT_VERSION)) {
			val warning = "The version of '$url' may not be fully compatible with the client ($serverVersion vs $MINCHAT_VERSION)"

			logger.warn { warning }
			if (Vars.clientLoaded) {
				Vars.ui.showInfoToast("[!] Minchat warning: $warning", 5f)
			}
		}

		this@MinchatMod.client = client
		MinchatPluginHandler.get<AccountSaverPlugin>()?.restoreAccount()
		gateway = MinchatGateway(client).also { it.connectIfNecessary() }

		ClientEvents.fire(ConnectEvent(url))
	}

	/**
	 * Connects to the default server.
	 * Overrides [client] upon success.
	 */
	suspend fun connectToDefault() {
		val url = when {
			MinchatSettings.useCustomUrl -> {
				logger.warn { "Connecting to a custom URL. Minchat developers are not responsible for any data sent to foreign servers." }
				MinchatSettings.customUrl
			}
			else -> githubClient.getDefaultUrl()
		}
		connectToServer(url)
	}

	/**
	 * Suspends until a connection to the MinChat server gets established.
	 *
	 * All coroutines depending on [Minchat.client] or [Minchat.gateway] must invoke this method first.
	 */
	suspend fun awaitConnection() {
		while (!isConnected) {
			delay(50)
		}
	}

	private fun onShutdown() {
		if (isConnected) {
			client.fileCache.saveToDrive()
		}
	}
}
