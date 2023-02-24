package io.minchat.server.modules

import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.request.*
import io.ktor.server.routing.*
import io.ktor.server.util.*

class MessageModule : MinchatServerModule() {
	override fun Application.onLoad() {
		routing {
		}
	}
}
