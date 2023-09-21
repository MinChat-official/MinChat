package io.minchat.server.modules

import io.ktor.server.application.*
import io.minchat.common.entity.User
import io.minchat.server.*
import io.minchat.server.databases.Users
import io.minchat.server.util.*
import org.jetbrains.exposed.sql.*
import java.time.*
import java.time.format.DateTimeFormatter

abstract class MinchatServerModule {
	lateinit var server: ServerContext

	val name = createServiceName()

	private val illegalCharRegex = """[^a-zA-Z0-9\-_()\[\]]""".toRegex()

	@JvmName("onLoadPublic")
	fun onLoad(application: Application) {
		require(::server.isInitialized.not()) { "Module '$name' has already been loaded!" }

		Log.info { "Loading module '$name'." }

		with(application) { onLoad() }
	}

	@JvmName("afterLoadPublic")
	suspend fun afterLoad(context: ServerContext) {
		require(::server.isInitialized.not()) { "Module '$name' has already been post-loaded!" }
		this.server = context

		Log.lifecycle { "After-loading module '$name'." }

		with(context) { afterLoad() }
	}

	/** Sets up routes and other stuff. */
	abstract protected fun Application.onLoad()

	/** Post-processes the context, if neccesary. */
	open protected suspend fun ServerContext.afterLoad() {}

	/** Creates an [Op] that uses [commom] if [isAdmin] is true, or `common and userOnly` otherwise. */
	inline protected fun opWithAdminAccess(
		isAdmin: Boolean,
		crossinline common: SqlExpressionBuilder.() -> Op<Boolean> = { Op.TRUE },
		crossinline userOnly: SqlExpressionBuilder.() -> Op<Boolean>
	): SqlExpressionBuilder.() -> Op<Boolean> = run {
		var op = Op.build(common)
		if (!isAdmin) {
			op = op and Op.build(userOnly)
		}
		{ op }
	}

	/** Generates the name of this service on initialisation. */
	protected open fun createServiceName() =
		this::class.java.simpleName?.removeSuffix("Module")?.let { name ->
			buildString {
				var hasDash = true
				for (char in name) {
					if (!hasDash && char.isUpperCase()) {
						append('-')
						hasDash = true
					} else if (char.isLowerCase()) {
						hasDash = false
					}
					append(char.lowercase())
				}
			}
		} ?: "anonymous-module"

	/** Converts the given string to a conventional name string. */
	fun String.nameConvention() =
		trim().replace(illegalCharRegex, "-")

	override fun toString() =
		"Module(name = $name)"


	/**
	 * Checks if the user has punishments (mute/ban) that may prevent them from performing actions.
	 *
	 * If the user has expired punishments, removes them (requires a transaction context).
	 *
	 * Otherwise throws [AccessDeniedException] with the corresponding reason
	 * unless the corresponding check parameter is set to false.
	 *
	 * @return either the same [User] instance or a copy with removed punishments.
	 */
	fun User.checkAndUpdateUserPunishments(checkMute: Boolean = true, checkBan: Boolean = true): User {
		fun Long.asTimestamp() =
			DateTimeFormatter.RFC_1123_DATE_TIME.format(
				Instant.ofEpochMilli(this).atZone(ZoneId.systemDefault()))

		var removeBan = false
		var removeMute = false
		var scheduledException: String? = null
		mute?.let { mute ->
			if (mute.isExpired) {
				removeMute = true
			} else if (checkMute) {
				val expires = when {
					mute.expiresAt == null -> "is permanent"
					else -> "expires at ${mute.expiresAt!!.asTimestamp()}"
				}
				scheduledException = "User ${username} is muted. The mute $expires."
			}
		}
		ban?.let { ban ->
			if (ban.isExpired) {
				removeBan = true
			} else if (checkBan) {
				val expires = when {
					ban.expiresAt == null -> "is permanent"
					else -> "expires at ${ban.expiresAt!!.asTimestamp()}"
				}
				scheduledException = "User ${username} is banned. The ban $expires."
			}
		}

		if (!removeMute && !removeBan) {
			scheduledException?.let { accessDenied(it) }
			return this
		}

		Users.update(where = { Users.id eq id }) {
			if (removeBan) {
				it[Users.bannedUntil] = -1
				it[Users.banReason] = null
			}
			if (removeMute) {
				it[Users.mutedUntil] = -1
				it[Users.muteReason] = null
			}
		}
		scheduledException?.let { accessDenied(it) }

		return copy(
			ban = if (removeBan) null else ban,
			mute = if (removeMute) null else mute
		)
	}
}
