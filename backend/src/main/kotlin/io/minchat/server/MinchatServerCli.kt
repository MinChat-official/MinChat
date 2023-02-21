package io.minchat.server

import kotlin.system.exitProcess
import kotlinx.coroutines.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.minchat.server.modules.*
import io.minchat.server.util.Log
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

	@Option(names = ["--exclude", "-e"], description = ["Prevent modules with the specified names from loading."])
	var excludedModules = listOf<String>()

	override fun run() = runBlocking {
		Log.info { "Launching a MinChat server on port $port" }

		val modules = listOf(
			UserModule()
		).filter { it.name !in excludedModules }

		val server = embeddedServer(Netty) {
			modules.forEach {
				it.onLoad(this)
			}
		}

		val context = Context(
			server = server,
			modules = modules
		)

		modules.forEach { it.afterLoad(context) }

		server.start(wait = true)

		Unit
	}
}
