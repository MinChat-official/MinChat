package io.minchat.common

/** All offical MinChat server routes. */
object Route {
	object User : MinchatRoute("user") {
		val login = to("login")
		val register = to("register")
	}
	object Message : MinchatRoute("message")
	object Channel : MinchatRoute("channel") {
		val create = to("new")
		val messages = to("messages")
	}

	sealed class MinchatRoute(val name: String) {
		val fetch = to("")
		val edit = to("edit")
		val delete = to("delete")

		protected fun to(subroute: String) = when {
			subroute.isEmpty() -> "/api/$name"
			else -> "/api/$name/$subroute"
		}
	}
}
