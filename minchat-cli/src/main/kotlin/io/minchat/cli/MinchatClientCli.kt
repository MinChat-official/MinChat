package io.minchat.cli

import io.minchat.common.MINCHAT_VERSION
import io.minchat.rest.*
import io.minchat.rest.entity.MinchatChannel
import io.minchat.rest.gateway.MinchatGateway
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.toList
import picocli.CommandLine
import picocli.CommandLine.*
import kotlin.system.exitProcess

fun main(vararg args: String) {
	val exitCode = CommandLine(MainCommand()).execute(*args)
	exitProcess(exitCode)
}

@Command(
	name = "minchat-client",
	description = ["The root command of the MinChat cli client executable."],
	subcommands = [CliClientLauncher::class],
	mixinStandardHelpOptions = true
)
open class MainCommand

@Command(
	name = "launch",
	description = ["Launch the MinChat server and wait for its termination."],
	mixinStandardHelpOptions = true
)
open class CliClientLauncher : Runnable {
	@Parameters(description = ["MinChat server url to connect to."], index = "0")
	lateinit var serverUrl: String

	@Option(names = ["--no-fix-http", "-n"], description = ["Do not add the missing 'http://' protocol to the url"])
	var noFixHttp = false

	val rest by lazy { MinchatRestClient(serverUrl) }
	val gateway by lazy { MinchatGateway(rest) }
	lateinit var channels: List<MinchatChannel>

	/** A string to the color of the terminal output. */
	private val reset = "\u001B[0m"
	// colors
	private val grey = color(0x777777)
	private val red = color(0xff3333)
	private val green = color(0x33dd33)
	private val blue = color(0x7766ff)

	private val adminColor = color(0x8741cc)
	private val userColor = color(0x419ecc)
	private val selfColor = color(0x77e693)

	override fun run() = runBlocking {
		// add the url protocol, if neccessary
		if (!noFixHttp && !serverUrl.startsWith("http")) {
			serverUrl = "http://$serverUrl"
		}

		println("Connecting to $serverUrl.")
		
		// Check for compatibility
		val serverVersion = rest.getServerVersion()
		when {
			!MINCHAT_VERSION.isCompatibleWith(serverVersion) -> {
				println("${red}The server version is incompatible with the client version:")
				println("$MINCHAT_VERSION vs $serverVersion")
				exitProcess(1)
			}
			!MINCHAT_VERSION.isInterchangableWith(serverVersion) -> {
				println("${red}Caution: the server version is not the same as the client version. Some issues may arise.")
				println("$MINCHAT_VERSION vs $serverVersion.$reset")
			}
			else -> {
				println("Server version: $serverVersion, client version: $MINCHAT_VERSION")
			}
		}

		selectChannelUi()
	}

	/** Launches the "select channel" terminal ui. Blocks until the user exits. */
	suspend fun selectChannelUi() {
		gateway.connectIfNecessary()

		while (true) {
			channels = rest.getAllChannels()

			println(reset)
			if (rest.account != null) {
				println("Logged in as ${rest.account().user.username} (id ${rest.account().id}).\n")
			}

			println("${green}Available channels:$blue\n")
			val namePad = channels.maxOfOrNull { it.name.length }?.coerceAtMost(20) ?: 0

			channels.forEachIndexed { index, channel ->
				val prefix = (index + 1).toString().padStart(4, ' ')
				print("$prefix. #${channel.name.padEnd(namePad, ' ')}")
				println(" (id ${channel.id}) [${channel.description}]")
			}
			println("""
				$grey
				-----------------------------------$green
				Available commands:
				* login (username) (password)
				* login-token (id) (token)
				* register (username) (password)
				* connect (channel name/id)
				* exit$grey
				--- admin only: -------------------$green
				* create (name) (description...)
				* edit (id) (name) (description...)
				* delete (id)$grey
				-----------------------------------$reset
			""".trimIndent())

			val command = prompt("command")
			val split = command.split(" ")

			// returns null on mismatch
			fun requireArgs(range: IntRange): Unit? = when {
				(split.size - 1) in range -> Unit
				else -> {
					println("${red}This command accepts $range args.")
					null
				}
			}

			// this all is extremely dumb, I know.
			// but I don't want to create a complex system for this.
			try {
				when (split.firstOrNull().orEmpty().lowercase()) {
					"login" -> {
						requireArgs(2..2) ?: continue
						runSafe({ "Failed to log-in" }) {
							rest.login(split[1], split[2])
						}
					}
					"login-token" -> {
						requireArgs(2..2) ?: continue
						rest.account = MinchatAccount(
							user = rest.getUser(split[1].toLong()).data,
							token = split[2]
						)
					}
					"register" -> {
						requireArgs(2..2) ?: continue
						runSafe({ "Failed to register" }) {
							rest.register(split[1], split[2])
						}
					}
					"connect" -> {
						requireArgs(1..1) ?: continue
						val channel = channels.find {
							it.name.equals(split[1], true)
						} ?: run {
							val id = split[1].toLong()
							channels.find { it.id == id }
						}

						if (channel == null) {
							println("No such channel.")
							continue
						}
						
						chatUI(channel)
					}
					"exit" -> {
						break
					}
					"create" -> {
						requireArgs(1..1000) ?: continue
						val name = split[1]
						// 2 parts + 2 spaces
						val description = command.drop(split[0].length + name.length + 2)
						
						println("Creating a channel with the name $name and description: $description")
						runSafe({ "Failed to create the channel" }) {
							rest.createChannel(name, description)
						}
					}
					"edit" -> {
						requireArgs(2..1000) ?: continue
						val id = split[1].toLong()
						val newName = split[2]
						val newDescription = split.getOrNull(3)

						rest.editChannel(id, newName, newDescription)
					}
					"delete" -> {
						requireArgs(1..1)
						val id = split[1].toLong()
						rest.deleteChannel(id)
					}
					else -> {
						println("${red}Invalid command: ${split.firstOrNull()}")
					}
				}
			} catch (e: Exception) {
				println("${red}Failure: $e")
			}
		}
	}

	/** Launches the chat terminal ui. Blocks until the user exits. */
	suspend fun chatUI(channel: MinchatChannel) {
		while (true) {
			"\n\nChannel: #${channel.name}".let {
				println(it)
				println("-".repeat(it.trim().length))
			}

			print("Fetching messages...")

			val nameColumnWidth = 30

			val messages = channel.getAllMessages(limit = 50)
				.toList()
				.reversed()

			print("\r${" ".repeat(40)}\r") // clear the line

			messages.forEach { message ->
				val color = when {
					message.author.id == rest.account?.id -> selfColor
					message.author.isAdmin -> adminColor
					else -> userColor
				}

				print(color)
				print(message.author.tag.let {
					it.takeIf { it.length < nameColumnWidth }?.padEnd(nameColumnWidth, ' ')
						?: it.take(nameColumnWidth - 3) + "..."
				})
				print("| $reset")
				// pad every line othwe than the first one with spaces and a pipe symbol
				message.content.lines().let {
					println(it[0])

					it.drop(1).map {
						"$color...${" ".repeat(nameColumnWidth - 3)}| $reset$it"
					}.forEach(::println)
				}
			}

			// TODO: I will need to implement a websocket connection.
			// to correctly print the newly received messages, I will need
			// to move the cursor to the left and 2 lines up, then print
			// the message, and then return.
			println("${grey}Type :q to quit, :r to refresh${
				when (rest.isLoggedIn) {
					true -> ", any other text to send a message."
					false -> "; ${red}you can't send messages until you log in."
				}
			}")
			val input = prompt("").trim()

			when {
				input == ":q" -> break
				input == ":r" || input == "" -> continue
				input.startsWith(":") -> {
					println("Invalid command: ${input.drop(1)}.")
					continue
				}
			}
			
			if (rest.account != null) {
				channel.createMessage(input.replace("\\n", "\n"))
			} else {
				println("${red}You must log in before doing this!")
			}
		}	
	}

	/** A string to set the color of the terminal output. */
	private fun color(color: Int) = run {
		val r = (color and 0xff0000) shr 16
		val g = (color and 0xff00) shr 8
		val b = color and 0xff
		"\u001B[38;2;${r};${g};${b}m"
	}

	/** Run the function or show an error message. */
	private inline fun runSafe(crossinline failureText: () -> String, block: () -> Unit) {
		runCatching(block).onFailure {
			println(red + failureText() + " ($it)")
		}
	}

	/** Prompt the user for string input. */
	private fun prompt(promptStr: String) =
		prompt(promptStr) { it }

	/** Prompt the user for arbitrary input. */
	private inline fun <T> prompt(promptStr: String, transform: (String) -> T?) = run {
		var value: T? = null
		while (value == null) {
			print("$green$promptStr > ${color(0xbbbbcc)}")
			value = readln().trim().takeIf(String::isNotEmpty)?.let(transform)
		}
		value
	}
}
