package io.minchat.server

import kotlin.system.exitProcess
import kotlinx.coroutines.*
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.BadRequestException
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.minchat.server.databases.*
import io.minchat.server.modules.*
import io.minchat.server.util.Log
import java.io.File
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction
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

	@Option(names = ["--data-location", "-d"], description = ["The directory in which MinChat will store its data. Defaults to ~/minchat."])
	var dataDir = File(System.getProperty("user.home").orEmpty()).resolve("minchat")

	override fun run() = runBlocking {
		Log.baseLogDir = dataDir
		Log.info { "Connecting to a database." }

		val dbFile = dataDir.also { it.mkdirs() }.resolve("data")
		Database.connect("jdbc:h2:${dbFile.absolutePath}", "org.h2.Driver")

		transaction {
			SchemaUtils.create(Channels, Messages, Users)
		}
		
		Log.info { "Launching a MinChat server on port $port." }

		val modules = listOf(
			UserModule(),
			AuthModule()
		).filter { it.name !in excludedModules }

		val server = embeddedServer(Netty, port = port) {
			install(ContentNegotiation) {
				json()
			}
			install(StatusPages) {
				exception<BadRequestException> { call, cause ->
					call.respondText(text = "400: $cause", status = HttpStatusCode.BadRequest)
				}
				exception<IllegalAccessException> { call, cause ->
					call.respondText(text = "403: $cause", status = HttpStatusCode.Forbidden)
				}
				exception<Throwable> { call, cause ->
					call.respondText(text = "500: $cause" , status = HttpStatusCode.InternalServerError)
				}
			}

			modules.forEach {
				it.onLoad(this)
			}
		}

		val context = Context(
			server = server,
			modules = modules,
			dataDir = dataDir,
			dbFile = dbFile
		)

		server.start(wait = false)

		modules.forEach { it.afterLoad(context) }

		// Keep waiting until the server terminates
		Log.info { "MinChat server is running. Awaiting termination." }
		server.stopServerOnCancellation().join()
		Log.info { "Good night." }
	}
}
