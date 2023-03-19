package io.minchat.cli

import io.minchat.common.MINCHAT_VERSION
import io.minchat.rest.MinchatRestClient
import io.minchat.rest.entity.MinchatChannel
import kotlinx.coroutines.*
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
	lateinit var channels: List<MinchatChannel>

	val nameColumnWidth = 30

	override fun run() = runBlocking {
		// add the url protocol, if necessary
		if (!noFixHttp && !serverUrl.startsWith("http")) {
			serverUrl = "http://$serverUrl"
		}

		println("Connecting to $serverUrl.")
		
		// Check for compatibility
		val serverVersion = rest.getServerVersion()
		when {
			!MINCHAT_VERSION.isCompatibleWith(serverVersion) -> {
				println("The server version is incompatible with the client version:")
				println("$MINCHAT_VERSION vs $serverVersion")
				exitProcess(1)
			}
			!MINCHAT_VERSION.isInterchangableWith(serverVersion) -> {
				println("Caution: the server version is not the same as thge client version. Some issues may arise.")
				println("$MINCHAT_VERSION vs $serverVersion.")
				delay(1000L)
			}
			else -> {
				println("Server version: $serverVersion, client version: $MINCHAT_VERSION")
			}
		}

		println()
		Minchat(rest).launch()
	}
}
