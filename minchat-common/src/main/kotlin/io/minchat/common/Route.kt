package io.minchat.common

import io.minchat.common.entity.*
import io.minchat.common.request.*

/** 
 * All offical MinChat server routes.
 *
 * Note that most POST route require
 * an authorization header (a user token).
 */
object Route {
	object Auth : MinchatRoute("auth") {
		/** 
		 * POST. 
		 * Body: [UserLoginRequest].
		 * Response: [UserLoginRequest.Response].
		 */
		val login = to("login")
		/** 
		 * POST. 
		 * Body: [UserRegisterRequest].
		 * Response: [UserRegisterRequest.Response].
		 */
		val register = to("register")
	}
	/** Accepts an {id} request parameter. */
	object User : MinchatRoute("user/{id}") {
		/** 
		 * GET. 
		 * Response: a [User] object.
		 */
		val fetch = to("")
		/**
		 * POST. Requires authorization.
		 * Body: [UserModifyRequest].
		 * Response: [UserModifyRequest.Response]
		 */
		val edit = to("edit")
		/**
		 * POST. Requires authorization.
		 * Body: [UserModifyRequest].
		 */
		val delete = to("delete")
	}
	/** Accepts an {id} request parameter. */
	object Message : MinchatRoute("message/{id}") {
		/** 
		 * GET. 
		 * Response: a [Message] object.
		 */
		val fetch = to("")
		/** 
		 * POST. Requires authorization.
		 * Body: [MessageEditRequest].
		 * Response: [MessageEditRequest.Response]
		 */
		val edit = to("edit")
		/** 
		 * POST. Requires authorization.
		 * Body: [MessageDeleteRequest].
		 */
		val delete = to("delete")
	}
	/** Accepts an {id} request parameter. */
	object Channel : MinchatRoute("channel/{id}") {
		/** 
		 * GET. 
		 * Response: a [Channel] object.
		 */
		val fetch = to("")
		/** 
		 * POST. Requires authorization.
		 * Body: [MessageCreateRequest]. 
		 * Response: a [Message] object.
		 */
		val send = to("send")
		/** 
		 * GET. 
		 * Response: a list of [Message] objects
		 *
		 * Query params:
		 * * {from} - timestamp of the oldest message that has to be listed
		 * * {to} - timestamp of the newest message thag has to be listed
		 *
		 * If there are too many messages matching the query, only the latest are listed.
		 */
		val messages = to("messages")

		/** 
		 * POST. Requires authorization and admin access.
		 * Body: [ChannelCreateRequest]. 
		 * Response: a [Channel] object.
		 */
		val create = to("new")
		/** 
		 * POST. Requires authorization and admin access. 
		 * Body: [ChannelEditRequest].
		 * Response: [ChannelEditRequest.Response]
		 */
		val edit = to("edit")
		/** 
		 * POST. Requires authorization and admin access.
		 * Body: [ChannelDeleteRequest]. 
		 */
		val delete = to("delete")
	}

	sealed class MinchatRoute(val name: String) {
		protected fun to(subroute: String) = when {
			subroute.isEmpty() -> "/api/$name"
			else -> "/api/$name/$subroute"
		}
	}
}
