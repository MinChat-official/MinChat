package io.minchat.cli

import androidx.compose.runtime.*
import androidx.compose.runtime.snapshots.*
import com.jakewharton.mosaic.*
import io.minchat.cli.ui.*
import io.minchat.common.MINCHAT_VERSION
import io.minchat.rest.MinchatRestClient
import io.minchat.rest.entity.*
import io.minchat.rest.gateway.MinchatGateway
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.jline.terminal.TerminalBuilder
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
	var currentMode by mutableStateOf(MinchatMode.CHANNEL_CHOOSER)


	val messages = SnapshotStateList<MinchatMessage>()
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
				ChannelSelector()

				MessageList()
				Text("")

				ChatBox()
			}
		}

		client.coroutineContext[Job]?.join()
	}

	@Composable
	fun ChannelList() {
		if (availableChannels.isEmpty()) {
			Text("Loading channels...")
		} else if (currentMode == MinchatMode.CHANNEL_CHOOSER || currentMode == MinchatMode.COMMAND) {
			val indexPad = availableChannels.lastIndex.toString().length + 2
			val namePad = availableChannels.maxOf { it.name.length } + 1

			ListView(availableChannels) { index, channel ->
				Row {
					Text("${index + 1}.".padEnd(indexPad))
					Text("#${channel.name}".padEnd(namePad), color = Color.Blue)
					Text(" (id ")
					Text(channel.id.toString(), color = Color.Blue)
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
				"exit" to { exitProcess(0) },
				"help" to { shouldShowHelp = true }
			)

			Text("Type a command (or \"help\" to see them):")
			Row {
				TextField(
					terminalReader,
					length = 20,
					color = Color.Blue,
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
	fun ChannelSelector() {
		var isChannelInvalid by remember { mutableStateOf(false) }
		var statusStr by remember { mutableStateOf<String?>(null) }

		if (currentMode == MinchatMode.CHAT) Row {
			Text("Current channel: ")
			Text(currentChannel!!.name.let { "#$it" }, color = Color.Blue)
		} else if (currentMode == MinchatMode.CHANNEL_CHOOSER) Row {
			Text("Choose a channel: ")
			TextField(
				terminalReader,
				length = 40,
				color = if (isChannelInvalid) Color.Red else Color.Blue,
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
				currentChannel = availableChannels.find { it.name.equals(input, true) }

				if (currentChannel == null) {
					statusStr = "Unknown channel: #$input"
				} else {
					statusStr = null
					currentMode = MinchatMode.CHAT
				}
			}
		}

		if (currentMode == MinchatMode.CHANNEL_CHOOSER && statusStr != null) {
			Text(statusStr!!, Color.Red)
		}
	}

	@Composable
	fun MessageList() {
		Text("") // empty line
	}

	@Composable
	fun ChatBox() {
		if (currentMode == MinchatMode.CHAT) {
			val channel = currentChannel ?: return

			Text("Type your message. Press ESC to exit or ENTER to send it.")
		}
	}

	enum class MinchatMode(val color: Color) {
		/** The user can type a command. */
		COMMAND(Color.BrightBlue),
		/** The user can type a channel name. */
		CHANNEL_CHOOSER(Color.BrightYellow),
		/** The user can send messages in the current channel. */
		CHAT(Color.BrightGreen)
	}
}
