package io.minchat.cli

import io.minchat.rest.*
import io.minchat.rest.entity.*
import kotlin.system.exitProcess
import kotlinx.coroutines.*
import picocli.CommandLine
import picocli.CommandLine.Command // these need to be imported one-by-one. otherwise kapt dies.
import picocli.CommandLine.Option
import picocli.CommandLine.Parameters
import kotlin.math.*

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
	@Parameters(description = ["MinChat server url to conntect to."])
	lateinit var serverUrl: String

	@Option(names = ["--no-fix-http", "-n"], description = ["Do not add the missing 'http://' protocol to the url"])
	var noFixHttp = false

	val rest by lazy { MinchatRestClient(serverUrl) }
	lateinit var channels: List<MinchatChannel>

	/** A string to the color of the terminal output. */
	private val reset = "\u001B[0m"
	private val grey = color(0x777777)
	private val red = color(0xff3333)
	private val green = color(0x33dd33)
	private val blue = color(0x7766ff)

	override fun run() = runBlocking {
		// add the url protocol, if neccessary
		if (!noFixHttp && !serverUrl.startsWith("http")) {
			serverUrl = "http://$serverUrl"
		}

		println("Connecting to $serverUrl.")
		selectChannelUi()
	}

	/** Launches the "select channel" terminal ui. Blocks until the user exits. */
	suspend fun selectChannelUi() {
		while (true) {
			channels = rest.getAllChannels()

			println(reset)
			if (rest.account != null) {
				println("Logged in as ${rest.account().user.username}.\n")
			}

			println("${green}Available channels:$blue\n")
			val namePad = channels.maxOf { it.name.length }.coerceAtMost(20)

			channels.forEachIndexed { index, channel ->
				val prefix = index.toString().padStart(4, ' ')
				print("$prefix. #${channel.name.padEnd(namePad, ' ')}")
				println(" (${channel.id}) [${channel.description}]")
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
					println("This command accepts $range args.")
					null
				}
			}

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
						
						println("\n\nChannel: #${channel.name}")
						chatUI(channel.id)
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
	suspend fun chatUI(channelId: Long) {
		TODO()
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
