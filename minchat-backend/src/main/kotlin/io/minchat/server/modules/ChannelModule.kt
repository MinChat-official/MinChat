package io.minchat.server.modules

import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.request.*
import io.ktor.server.routing.*
import io.ktor.server.util.*
import io.minchat.common.*
import io.minchat.common.Route
import io.minchat.common.request.*
import io.minchat.common.entity.*
import io.minchat.server.databases.*
import io.minchat.server.util.*
import kotlin.system.measureTimeMillis
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.experimental.*

class ChannelModule : MinchatServerModule() {
	override fun Application.onLoad() {
		routing {
			get(Route.Channel.fetch) {
				val id = call.parameters.getOrFail<Long>("id")

				newSuspendedTransaction {
					call.respond(Channels.getById(id))
				}
			}
			get(Route.Channel.messages) {
				val id = call.parameters.getOrFail<Long>("id")

				val fromTimestamp = call.request.queryParameters["from"]?.toLong() ?: 0L
				val toTimestamp = call.request.queryParameters["to"]?.toLong() ?: System.currentTimeMillis()
				
				newSuspendedTransaction {
					val channel = Channels.getById(id)

					lateinit var messages: List<Message>
					measureTimeMillis { //todo remove after testing
						// using innerJoin to improve performance
						(Messages innerJoin Users).select {
							not(Messages.isDeleted) and
								(Messages.channel eq id) and
								(Messages.timestamp greater fromTimestamp) and
								(Messages.timestamp lessEq toTimestamp)
						}
							.orderBy(Messages.id to SortOrder.DESC)
							.limit(20) // this will take the 20 last messages in a descending order
							.toList()
							.reversed() // this makes the order correct
							.map {
								Messages.createEntity(
									row = it,
									author = Users.createEntity(it),
									channel = channel
								)
							}
							.let { messages = it }
					}.let { Log.debug { "Message query took $it ms" } } 

					call.respond(messages)
				}
			}

			post(Route.Channel.send) {
				TODO()
			}

			// Admin-only
			post(Route.Channel.create) {
				TODO()
			}

			post(Route.Channel.edit) {
				TODO()
			}

			post(Route.Channel.delete) {
				TODO()
			}
		}
	}
}
