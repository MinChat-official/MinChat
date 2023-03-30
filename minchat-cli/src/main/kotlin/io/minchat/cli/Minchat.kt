package io.minchat.cli

import androidx.compose.runtime.*
import androidx.compose.runtime.snapshots.*
import com.jakewharton.mosaic.runMosaic
import com.jakewharton.mosaic.ui.*
import io.minchat.cli.ui.*
import io.minchat.common.MINCHAT_VERSION
import io.minchat.common.event.Event
import io.minchat.rest.MinchatRestClient
import io.minchat.rest.entity.*
import io.minchat.rest.event.*
import io.minchat.rest.gateway.MinchatGateway
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.jline.terminal.TerminalBuilder
import java.io.*
import kotlin.system.exitProcess

@Suppress("FunctionName")
class Minchat(
	val client: MinchatRestClient
) {
	val gateway = MinchatGateway(client)
	val terminalReader = TerminalBuilder.terminal().apply {
		enterRawMode()
	}.reader()

	val availableChannels = SnapshotStateList<MinchatChannel>()
	var currentChannel by mutableStateOf<MinchatChannel?>(null)
	val currentMessages = SnapshotStateList<MinchatMessage>()
	var currentMode by mutableStateOf(MinchatMode.COMMAND)

	suspend fun launch(): Unit = runMosaic {
		launch {
			// only fetch the channels once
			val newChannels = client.getAllChannels()
			Snapshot.withMutableSnapshot {
				availableChannels.addAll(newChannels)
			}
		}

		setContent {
			Column {
				Row {
					Text("MinChat v${MINCHAT_VERSION}", color = Color.Magenta)
					Text(" (Mode: ")
					Text(currentMode.toString(), color = currentMode.color)
					Text(")")
				}

				ChannelList()
				CommandInput()
				AuthLine()
				ChannelSelector()

				MessageList()

				ChatBox()
			}
		}

		gateway.connectIfNecessary()
		gateway.events
			.filterIsInstance<MinchatEvent<out Event>>()
			.onEach { event ->
				Snapshot.withMutableSnapshot {
					when (event) {
						is MinchatMessageCreate -> if (event.channelId == currentChannel?.id) {
							currentMessages.add(event.message)
						}

						is MinchatMessageModify -> if (event.channelId == currentChannel?.id) {
							val index = currentMessages.indexOfFirst { it.id == event.message.id }
							if (index != -1) {
								currentMessages[index] = event.message
							} else {
								System.err.println("Unknown message modified: ${event.message.id}")
							}
						}

						is MinchatMessageDelete -> {
							currentMessages.removeAll { it.id == event.messageId }
						}
					}
				}
			}
			.collect()

		// Wait for 1 second before exiting
		// This can only be reached due to an exception thrown somewhere
		delay(1000L)
	}

	@Composable
	@CompositionContextLocal
	fun ChannelList() {
		if (availableChannels.isEmpty()) {
			Text("Loading channels...")
		} else if (currentMode == MinchatMode.CHANNEL_CHOOSER || currentMode == MinchatMode.COMMAND) {
			val indexPad = availableChannels.lastIndex.toString().length + 2
			val namePad = availableChannels.maxOf { it.name.length } + 1

			ListView(availableChannels) { index, channel ->
				Row {
					Text("${index + 1}.".padEnd(indexPad))
					Text("#${channel.name}".padEnd(namePad), color = Color.BrightBlue)
					Text(" (id ")
					Text(channel.id.toString(), color = Color.BrightBlue)
					Text(")")
				}
			}
		}
	}

	@Composable
	fun CommandInput() {
		var shouldShowHelp by remember { mutableStateOf(false) }
		var lastErrorCommand by remember { mutableStateOf<String?>(null) }

		if (currentMode == MinchatMode.COMMAND) {
			val commands: Map<String, () -> Unit> = mapOf(
				"chat" to { currentMode = MinchatMode.CHANNEL_CHOOSER },
				"login" to { currentMode = MinchatMode.LOGIN },
				"register" to { currentMode = MinchatMode.REGISTER },
				"exit" to { exitProcess(0) },
				"help" to { shouldShowHelp = true }
			)

			Text("Type a command (or \"help\" to see them):")
			Row {
				TextField(
					terminalReader,
					length = 20,
					color = Color.BrightBlue,
					focusCondition = { currentMode == MinchatMode.COMMAND },
					onChange = { input ->
						lastErrorCommand = null
						input.all { it.isLetter() || it == '-' } // ensure the command only contains letters and dashes
					},
					onCancel = {
						shouldShowHelp = false
					}
				) { command ->
					shouldShowHelp = false
					val action = commands[command.lowercase()]

					if (action == null) {
						lastErrorCommand = command
					} else {
						lastErrorCommand = null
						action()
					}
				}
			}

			if (shouldShowHelp) {
				Text("Available commands:")
				ListView(commands.keys) { index, command ->
					Text("${index + 1}. $command", color = Color.BrightBlue)
				}
			}
			if (lastErrorCommand != null) {
				Text("Error: '$lastErrorCommand' is not a command.", color = Color.Red)
			}
		}
	}

	@Composable
	fun AuthLine() {
		if (currentMode == MinchatMode.LOGIN || currentMode == MinchatMode.REGISTER) {
			var error by remember { mutableStateOf<String?>(null) }
			var username by remember { mutableStateOf<String?>(null) }
			var password by remember { mutableStateOf<String?>(null) }
			/** 0 is username, 1 is password. */
			var field by remember { mutableStateOf(0) }

			fun cancel() {
				username = ""
				password = ""
				error = null
				field = 0
				currentMode = MinchatMode.COMMAND
			}

			Text("Enter your ${if (field == 0) "username" else "password"}")
			Row {
				TextField(
					terminalReader,
					length = 15,
					color = if (field == 0) Color.Cyan else null,
					clearOnConfirm = false,
					focusCondition = { field == 0 },
					onCancel = { cancel() }
				) {
					username = it
					field++
				}
				Text(" | ")
				TextField(
					terminalReader,
					length = 15,
					color = if (field == 1) Color.Cyan else null,
					clearOnConfirm = false,
					focusCondition = { field == 1 },
					onCancel = { cancel() }
				) {
					password = it
					field++

					client.launch {
						runCatching {
							// should be safe since the user can't change the mode here
							when (currentMode) {
								MinchatMode.LOGIN -> client.login(username!!, password!!)
								MinchatMode.REGISTER -> client.register(username!!, null, password!!)
								else -> error("What have you done...")
							}
						}.onFailure {
							error = it.message
							field = 0
						}.onSuccess { cancel() }
					}
				}
			}
			error?.let { Text(it, color = Color.Red) }
		}
	}

	@Composable
	fun ChannelSelector() {
		var isChannelInvalid by remember { mutableStateOf(false) }
		var statusStr by remember { mutableStateOf<String?>(null) }

		if (currentMode == MinchatMode.CHAT) Row {
			Text("Current channel: ")
			Text(currentChannel!!.name.let { "#$it" }, color = Color.BrightBlue)
		} else if (currentMode == MinchatMode.CHANNEL_CHOOSER) Row {
			Text("Choose a channel: ")
			TextField(
				terminalReader,
				length = 40,
				color = if (isChannelInvalid) Color.Red else Color.BrightBlue,
				focusCondition = { currentMode == MinchatMode.CHANNEL_CHOOSER },
				onChange = { input ->
					isChannelInvalid = (availableChannels.find { it.name.equals(input, true) }) == null
					true
				},
				onCancel = {
					isChannelInvalid = false
					statusStr = null
					currentMode = MinchatMode.COMMAND
				}
			) { input ->
				val channel = availableChannels.find { it.name.equals(input, true) }
				currentChannel = channel

				if (channel == null) {
					statusStr = "Unknown channel: #$input"
				} else {
					statusStr = null
					currentMode = MinchatMode.CHAT

					client.launch {
						val newMessages = channel.getAllMessages()

						currentMessages.clear()
						newMessages.onEach {
							// add messages one-by-one at the top
							currentMessages.add(0, it)
						}.collect()
					}
				}
			}
		}

		if (statusStr != null) {
			Text(statusStr!!, Color.Red)
		}
	}

	@Composable
	fun MessageList() {
		if (currentMode == MinchatMode.CHAT) {
			Text("") // empty line
			ListView(currentMessages) { message ->
				Column {
					// author
					Row {
						val color = when {
							message.author.id == client.account?.id -> Color.BrightGreen
							message.author.isAdmin -> Color.BrightMagenta // I just like pink~
							else -> Color.Magenta
						}
						Text("@")
						Text(message.author.username, color = color)
						Text("#")
						Text(message.author.discriminator.toString().padStart(4, '0'), color = Color.BrightBlack)
					}
					// content
					Text(message.content)
					Text("")
				}
			}
		}
	}

	@Composable
	fun ChatBox() {
		if (currentMode == MinchatMode.CHAT) {
			val channel = currentChannel ?: return
			var status by remember { mutableStateOf<String?>(null) }

			Text("Type your message. Press ENTER to send it or ESC to exit.")
			TextField(
				terminalReader,
				length = 80,
				focusCondition = { currentMode == MinchatMode.CHAT },
				onCancel = { currentMode = MinchatMode.CHANNEL_CHOOSER }
			) { input ->
				if (input.isEmpty()) return@TextField

				client.launch {
					status = "Sending..."

					runCatching {
						channel.createMessage(input)
					}.onSuccess { status = null }.onFailure {
						status = "Failed to send: $it"
					}
				}
			}

			status?.let {
				Text(it, color = Color.Red, style = TextStyle.Bold)
			}
		}
	}

	/**
	 * Doesn't work; unused.
	 */
	@Composable
	fun ErrorLog() {
		val list = remember {
			val list = SnapshotStateList<String>()
			val lastErrorBuilder = StringBuilder()

			// redirect all errors from System.err to this list
			object : OutputStream() {
				override fun write(b: Int) {
					lastErrorBuilder.append(b.toChar())
					// add to the list if the line has ended
					if (b == '\n'.code || b == '\r'.code) {
						list.add(lastErrorBuilder.toString())
						lastErrorBuilder.clear()
					}
				}
			}.let { System.setErr(PrintStream(it)) }
			list
		}

		// Display all error lines in a static list
		Static(list) { Text(it, color = Color.Red) }
	}


	enum class MinchatMode(val color: Color) {
		/** The user can type a command. */
		COMMAND(Color.BrightBlue),
		/** The user can enter their MinChat credentials. */
		LOGIN(Color.BrightCyan),
		/** The user can enter their MinChat credentials. */
		REGISTER(Color.BrightCyan),
		/** The user can type a channel name. */
		CHANNEL_CHOOSER(Color.BrightYellow),
		/** The user can send messages in the current channel. */
		CHAT(Color.BrightGreen)
	}
}
