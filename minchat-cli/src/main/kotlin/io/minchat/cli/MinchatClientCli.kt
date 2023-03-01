package io.minchat.cli

import io.minchat.rest.*
import io.minchat.rest.entity.*
import kotlin.system.exitProcess
import kotlinx.coroutines.*
import picocli.CommandLine
import picocli.CommandLine.Command // these need to be imported one-by-one. otherwise kapt dies.
import picocli.CommandLine.Option
import picocli.CommandLine.Parameters

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

	val rest by lazy { MinchatRestClient(serverUrl) }
	lateinit var channels: List<MinchatChannel>

	override fun run() = runBlocking {
		// channels = rest.getAllChannels()
		println("Connected to $serverUrl.")
	}

	/** Launches the "select channel" terminal ui. Blocks until the user exits. */
	suspend fun selectChannelUi() {
		TODO()
	}

	/** Launches the chat terminal ui. Blocks until the user exits. */
	suspend fun chatUI(channelId: Long) {
		TODO()
	}
}
