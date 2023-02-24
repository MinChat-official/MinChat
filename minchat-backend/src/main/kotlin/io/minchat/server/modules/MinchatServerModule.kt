package io.minchat.server.modules

import io.ktor.server.application.*
import io.minchat.server.*
import io.minchat.server.util.Log
import org.jetbrains.exposed.sql.*

abstract class MinchatServerModule {
	lateinit var context: Context

	val name = createServiceName()

	@JvmName("onLoadPublic")
	fun onLoad(application: Application) {
		require(::context.isInitialized.not()) { "Module '$name' has already been loaded!" }

		Log.info { "Loading module '$name'." }

		with(application) { onLoad() }
	}

	@JvmName("afterLoadPublic")
	suspend fun afterLoad(context: Context) {
		require(::context.isInitialized.not()) { "Module '$name' has already been post-loaded!" }
		this.context = context

		Log.lifecycle { "After-loading module '$name'." }

		with(context) { afterLoad() }
	}

	/** Sets up routes and other stuff. */
	abstract protected fun Application.onLoad()

	/** Post-processes the context, if neccesary. */
	open protected suspend fun Context.afterLoad() {}

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

	override fun toString() =
		"Module(name = $name)"
}
