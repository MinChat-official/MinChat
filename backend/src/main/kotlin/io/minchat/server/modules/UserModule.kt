package io.minchat.server.modules

import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.minchat.server.*

class UserModule : MinchatServerModule() {
	override fun Application.onLoad() {
		routing {
			get("/user") {
				call.respond("This is unfinished...")
			}
		}
	}
}
