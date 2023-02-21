package io.minchat.server.modules

import io.ktor.server.application.*
import io.minchat.server.*
import io.minchat.server.util.Log

abstract class MinchatServerModule {
	lateinit var context: Context

	open val name get() = this::class.java.simpleName ?: "anonymous-module"

	@JvmName("onLoadPublic")
	fun onLoad(application: Application) {
		require(this::context.isInitialized.not()) { "Module $name has already been loaded!" }

		Log.info { "Loading module $name." }

		with(application) { onLoad() }
	}

	@JvmName("afterLoadPublic")
	suspend fun afterLoad(context: Context) {
		require(this::context.isInitialized.not()) { "Module $name has already been post-loaded!" }
		this.context = context

		Log.info { "After-loading module $name." }

		with(context) { afterLoad() }
	}

	/** Sets up routes and other stuff. */
	abstract protected fun Application.onLoad()

	/** Post-processes the context, if neccesary. */
	open protected suspend fun Context.afterLoad() {}

	override fun toString() =
		"Module(name = $name)"
}
