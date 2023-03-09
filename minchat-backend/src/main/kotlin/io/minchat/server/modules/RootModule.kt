package io.minchat.server.modules

import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.minchat.common.*
import io.minchat.common.Route

class RootModule : MinchatServerModule() {
	override fun Application.onLoad() {
		routing {
			get(Route.Root.version) {
				call.respond(MINCHAT_VERSION)
			}
		}
	}
}
