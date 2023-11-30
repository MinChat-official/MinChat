package io.minchat.server

import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.ratelimit.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.response.*
import io.minchat.common.*
import io.minchat.common.entity.User
import io.minchat.server.databases.*
import io.minchat.server.modules.*
import io.minchat.server.util.*
import kotlinx.coroutines.*
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction
import org.mindrot.jbcrypt.BCrypt
import picocli.CommandLine
import picocli.CommandLine.*
import java.io.File
import java.security.KeyStore
import kotlin.concurrent.thread
import kotlin.random.Random
import kotlin.system.exitProcess
import kotlin.time.Duration.Companion.seconds

/** All default MinChat backend modules. */
val defaultModules = mutableListOf(
	RootModule(),
	UserModule(),
	AuthModule(),
	ChannelModule(),
	ChannelGroupModule(),
	DMChannelModule(),
	MessageModule(),
	GatewayModule()
)

fun main(vararg args: String) {
	val exitCode = CommandLine(MainCommand()).execute(*args)
	exitProcess(exitCode)
}

@Command(
	name = "minchat-server",
	description = ["The root command of the MinChat server executable."],
	subcommands = [MinchatLauncher::class, DbManager::class],
	mixinStandardHelpOptions = true
)
open class MainCommand

@Command(
	name = "launch",
	description = ["Launch the MinChat server and wait for its termination."],
	mixinStandardHelpOptions = true
)
open class MinchatLauncher : Runnable {
	@Option(names = ["--port", "-p"], description = ["Port to run on. This port will accept HTTP connections. Defaults to 8080."])
	var port = 8080

	@Option(names = ["--https-port"], description = ["Port to use for the SSL connector. This port will accept HTTPS connections. Defaults to 8443."])
	var sslPort = 8443

	@Option(names = ["--exclude", "-e"], description = ["Prevent modules with the specified names from loading."])
	var excludedModules = listOf<String>()

	@Option(names = ["--data-location", "-d"], description = ["The directory in which MinChat will store its data. Defaults to ~/minchat."])
	var dataDir = File(System.getProperty("user.home").orEmpty()).resolve("minchat")

	@Option(names = ["--credentials"], description = ["The file containing SSL keystore credentials. Defaults to ~/minchat/.credentials.txt."])
	var credentialsFileOption: File? = null

	@Option(names = ["--log-level", "l"], description = ["The log level to use. Val's options are: lifecycle, debug, info, error"])
	var logLevel = "lifecycle"

	override fun run() = runBlocking {
		val context = launchServer()

		Runtime.getRuntime().addShutdownHook(thread(start = false) {
			context.globalLogger.info { "Good night." }
		})

		context.globalLogger.info { "MinChat server is running. Awaiting termination." }
		context.engine.stopServerOnCancellation().join()
	}

	suspend fun launchServer(): ServerContext {
		BaseLogger.logFile = dataDir.resolve("log/log-${BaseLogger.currentTimestamp()}.txt")
		BaseLogger.minLevel = BaseLogger.LogLevel.valueOf(logLevel.uppercase())
		BaseLogger.stdoutFormatter = { level, timestamp, prefix, message ->
			buildString {
				level.color.let {
					// set the color
					val r = (it and 0xff0000) shr 16
					val g = (it and 0xff00) shr 8
					val b = it and 0xff
					append("\u001B[38;2;${r};${g};${b}m")
				}
				append("[$timestamp][$level]")
				append("\u001B[38;2;113;60;186m")
				append("[$prefix]")
				append("\u001B[0m") // reset the color
				append(": $message")
			}
		}

		val logger = BaseLogger.getSawmill("Server")
		logger.lifecycle { "Data directory: ${dataDir.absolutePath}" }
		logger.info { "Log level: ${logLevel}" }
		logger.info { "Connecting to a database." }

		val dbFile = dataDir.also { it.mkdirs() }.resolve("data")
		Database.connect("jdbc:h2:${dbFile.absolutePath}", "org.h2.Driver")

		transaction {
			SchemaUtils.create(Channels, Messages, Users)
			SchemaUtils.createMissingTablesAndColumns(Channels, Messages, Users, ChannelGroups)
		}

		logger.lifecycle { "Loading the SSL key store." }

		// Array of [alias, password, privatePassword
		val keystoreFile = dataDir.resolve("keystore.jks")
		val credentials = (credentialsFileOption ?: dataDir.resolve(".credentials.txt"))
			.takeIf { it.exists() && it.isFile() }
			?.readText()
			?.lines()
			?.filter { it.isNotBlank() }
			?.takeIf { it.size == 3 }
		
		// Create an environment either with or without ssl configured
		val environment = applicationEngineEnvironment {
			connector {
				port = this@MinchatLauncher.port
			}

			if (!keystoreFile.exists() || keystoreFile.isDirectory()) {
				logger.error { "SSL keystore file ($keystoreFile) could not be found." }
			} else if (credentials == null) {
				val path = (credentialsFileOption ?: dataDir.resolve(".credentials.txt")).absolutePath

				logger.error { "SSL certificate file is either absent or malformed. HTTPS will be unavailable." }
				logger.error { "Make sure the file ($path) exists and contains the following 3 lines:" }
				logger.error { "key alias, key store password, private key password." }
			} else {
				val keyStore = KeyStore.getInstance("JKS")
				keyStore.load(keystoreFile.inputStream(), credentials[1].toCharArray())

				sslConnector(
					keyStore = keyStore,
					keyAlias = credentials[0],
					keyStorePassword = { credentials[1].toCharArray() },
					privateKeyPassword = { credentials[2].toCharArray() }
				) {
					port = sslPort
					keyStorePath = keystoreFile
				}
			}
		}

		logger.info { "Launching a MinChat server. Ports: http=$port, https=$sslPort." }

		val modules = defaultModules.filter { it.name !in excludedModules }

		val engine = embeddedServer(Netty, environment).apply {
			with(application) {
				install(ContentNegotiation) {
					json()
				}
				install(StatusPages) {
					exception<Throwable> { call, cause ->
						val message = cause.message?.takeIf { it.isNotBlank() }?.let { ": $it" }.orEmpty()

						when (cause) {
							is BadRequestException -> call.respondText(
								text = "400$message", status = HttpStatusCode.BadRequest)

							is IllegalInputException -> call.respondText(
								text = "Illegal input$message", status = HttpStatusCode.BadRequest)

							is AccessDeniedException -> call.respondText(
								text = "Access denied$message", status = HttpStatusCode.Forbidden)

							is EntityNotFoundException -> call.respondText(
								text = "Entity not found$message", status = HttpStatusCode.NotFound)

							is TooManyRequestsException -> call.respondText(
								text = "Too many requests$message", status = HttpStatusCode.NotFound)

							else -> {
								logger.error(cause) { "Exception thrown when processing $call" }
								call.respondText(text = "Server error (500): An abnormal exception was thrown while processing the request.",
									status = HttpStatusCode.InternalServerError)
							}
						}
					}
				}
				install(RateLimit) {
					global {
						requestKey { call ->
							call.tokenOrNull().orEmpty()
						}
						rateLimiter(limit = 10, refillPeriod = 5.seconds)
					}
				}

				modules.forEach {
					it.onLoad(this)
				}
			}
		}

		val context = ServerContext(
			engine = engine,
			modules = modules,
			dataDir = dataDir,
			dbFile = dbFile,
			globalLogger = logger
		)

		engine.start(wait = false)

		modules.forEach { it.afterLoad(context) }

		return context
	}
}

@Command(
	name = "manage",
	description = ["Manage the database. This also launches the server on a random port between 1025 and 3000."],
	mixinStandardHelpOptions = true
)
open class DbManager : Runnable {
	@Option(names = ["--data-location", "-d"], description = ["The directory in which MinChat will store its data. Defaults to ~/minchat."])
	var dataDir = File(System.getProperty("user.home").orEmpty()).resolve("minchat")

	override fun run() = runBlocking {
		val port = Random.nextInt(1025, 3000)
		// launch the server first
		val context = MinchatLauncher().also {
			it.port = port
			it.dataDir = dataDir
		}.launchServer()

		println("Options: (register) a user, open (sql) shell, (exit)")

		while (true) {
			when (prompt("option")) {
				"exit" -> break
				"register" -> {
					println("Creating a user...\n")
					val name = prompt("username")
					val password = prompt("password (type - to skip)").takeIf { it != "-" }
					val isAdmin = prompt("is admin (true/false)", { it.toBoolean() })
					
					val hash = password?.let {
						val salt = Constants.hashSaltPre
						BCrypt.hashpw(it, salt)
					} ?: "<THIS WILL BE REPLACED>"
					
					transaction {
						val row = Users.register(
							name = name,
							nickname = null,
							passwordHash = hash,
							if (isAdmin) User.RoleBitSet.ADMIN else User.RoleBitSet.REGULAR_USER
						)

						// if password generation was skipped, substitute the password hash in the db
						if (password == null) {
							Users.update({ Users.id eq row[Users.id] }) {
								// this is an invalid hash, and therefore nobody can log into unis account.
								it[Users.passwordHash] = "<no password hash>"
							}
						}

						println("Success. Info:\n")
						println("Username: $name")
						println(when (password) {
							null -> "No password (impossible to log into)"
							else -> "${password.length} Characters long password"
						})
						println(when (isAdmin) {
							true -> "Admin account"
							false -> "User account"
						})
						println("Id: ${row[Users.id]}")
						println("Token: ${row[Users.token]}")
						println()
					}
				}

				"sql" -> {
					println("Launching the H2 SQL shell.\n\n")
					org.h2.tools.Shell.main("-url", "jdbc:h2:file:${context.dbFile};ifexists=true")
				}

				else -> {
					println("Invalid option.")
					continue
				}
			}
		}
	}

	private fun prompt(promptStr: String) =
		prompt(promptStr) { it }

	private inline fun <T> prompt(promptStr: String, transform: (String) -> T?) = run {
		var value: T? = null
		while (value == null) {
			print("$promptStr > ")
			value = readln().trim().takeIf(String::isNotEmpty)?.let(transform)
		}
		value
	}
}
