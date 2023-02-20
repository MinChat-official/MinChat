package io.minchat.server

import kotlin.system.exitProcess
import kotlinx.coroutines.*
import picocli.CommandLine
import picocli.CommandLine.Command // these need to be imported one-by-one. otherwise kapt dies.
import picocli.CommandLine.Parameters
import picocli.CommandLine.Option

fun main(vararg args: String) {
	val exitCode = CommandLine(MainCommand()).execute(*args)
	exitProcess(exitCode)
}

@Command(
	name = "minchat-server",
	description = ["The root command of the MinChat server executable."],
	subcommands = [MinchatLauncher::class],
	mixinStandardHelpOptions = true
)
open class MainCommand

@Command(
	name = "launch",
	description = ["Launch the MinChat server."],
	mixinStandardHelpOptions = true
)
open class MinchatLauncher : Runnable {

	@Option(names = ["--port", "-p"], description = ["Port to run on. Defaults to 8080."])
	var port = 8080

	override fun run() = runBlocking {
		Log.info { "Launching a MinChat server on port $port" }

		TODO()
	}
}
