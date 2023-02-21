package io.minchat.server.modules

import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.minchat.server.*
import io.minchat.common.Route

class UserModule : MinchatServerModule() {
	override fun Application.onLoad() {
		routing {
			get(Route.User.fetch) {
				call.respond("This is unfinished...")
			}
			get(Route.User.login) {
				call.respond("TODO...")
			}
			post(Route.User.register) {
				call.respond("TODO!")
			}
		}
	}
}
