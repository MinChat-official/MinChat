package io.minchat.common

import io.minchat.common.event.Event
import io.minchat.common.request.*

/** 
 * All offical MinChat server routes.
 *
 * Note that most POST route require
 * an authorization header (a user token).
 */
object Route {
	object Root : MinchatRoute(null) {
		/**
		 * GET.
		 * Response: [BuildVersion].
		 */
		val version = to("version")

		/**
		 * POST.
		 * Body: [TokenValidateRequest]
		 * Return code: 200 - valid token, 403 - invalid.
		 */
		val tokenValidate = to("token-validate")
	}

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
		val fetch = to()
		/**
		 * POST. Requires authorization.
		 * Body: [UserModifyRequest].
		 * Response: an updated [User] object.
		 */
		val edit = to("edit")
		/**
		 * POST. Requires authorization.
		 * Body: [UserModifyRequest].
		 */
		val delete = to("delete")
		/**
		 * POST. Requires authorization.
		 * Body: [UserModifyRequest].
		 * Response: an updated [User] object.
		 */
		val modifyPunishments = to("modify-punishments")
		/**
		 * GET.
		 * Query params: full (boolean, whether to return the full image or the scaled version).
		 * Response: a raw file containing the avatar of the user.
		 */
		val getImageAvatar = to("image-avatar")
		/**
		 * POST. Requires authorization.
		 * Body: a raw file containing the avatar of the user.
		 * Response: an updated [User] object.
		 */
		val uploadImageAvatar = to("upload-avatar")
	}
	/** Accepts an {id} request parameter. */
	object Message : MinchatRoute("message/{id}") {
		/** 
		 * GET. May require authorization if the message is in a restricted channel.
		 * Response: a [Message] object.
		 */
		val fetch = to()
		/** 
		 * POST. Requires authorization.
		 * Body: [MessageModifyRequest].
		 * Response: an updated [Message] object.
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
		 * Unlike most subroutes in this route, this one does not acceot an ID parameter.
		 * 
		 * Response: a list of [Channel] objects.
		 */
		val all = to("all").replace("/{id}", "")

		/** 
		 * GET. 
		 * Response: a [Channel] object.
		 */
		val fetch = to()
		/** 
		 * POST. Requires authorization.
		 * Body: [MessageCreateRequest]. 
		 * Response: a [Message] object.
		 */
		val send = to("send")
		/** 
		 * GET.
		 * May require authorization if the channel has restricted access.
		 * Response: a list of [Message] objects ordered from oldest to newest.
		 *
		 * Query params:
		 * * {from} - Unix timestamp. If present, messages sent before or at that moment are not listed.
		 * * {to} - Unix timestamp. If present, messages sent after that moment are not listed.
		 *
		 * Basically, all messages matching `from < message.timestamp <= to` are listed.
		 * If there are too many messages matching the query, only the latest ones are listed.
		 */
		val messages = to("messages")

		/** 
		 * POST. Requires authorization and admin access.
		 * Unlike most subroutes in this route, this one does not acceot an ID parameter.
		 *
		 * Body: [ChannelCreateRequest]. 
		 * Response: a [Channel] object.
		 */
		val create = to("new").replace("/{id}", "")
		/** 
		 * POST. Requires authorization and admin access. 
		 * Body: [ChannelModifyRequest].
		 * Response: an updated [Channel] object.
		 */
		val edit = to("edit")
		/** 
		 * POST. Requires authorization and admin access.
		 * Body: [ChannelDeleteRequest]. 
		 */
		val delete = to("delete")
	}

	object DMChannel : MinchatRoute("dm-channel") {
		/**
		 * GET. Requires authorization.
		 * Response: a map of type <[Long], List<[Channel]>> associated with the user.
		 */
		val all = to("all")

		/**
		 * POST. Requires authorization.
		 *
		 * Body: [DMChannelCreateRequest].
		 * Response: a new [Channel] object of DM type.
		 */
		val create = to("create")
	}

	/** Accepts an {id} request parameter. */
	object ChannelGroup : MinchatRoute("channel-group/{id}") {
		/**
		 * GET.
		 * Unlike most subroutes in this route, this one does not acceot an ID parameter.
		 *
		 * Response: a list of [ChannelGroup] objects.
		 */
		val all = to("all")

		/**
		 * GET.
		 * Response: a [ChannelGroup] object.
		 */
		val fetch = to()

		/**
		 * POST. Requires authorization and admin access.
		 * Unlike most subroutes in this route, this one does not acceot an ID parameter.
		 *
		 * Body: [ChannelCreateRequest].
		 * Response: a [Channel] object.
		 */
		val create = to("new").replace("/{id}", "")
		/**
		 * POST. Requires authorization and admin access.
		 * Body: [ChannelModifyRequest].
		 * Response: an updated [Channel] object.
		 */
		val edit = to("edit")
		/**
		 * POST. Requires authorization and admin access.
		 * Body: [ChannelDeleteRequest].
		 */
		val delete = to("delete")
	}

	object Gateway : MinchatRoute("gateway") {
		/**
		 * WebSocket.
		 * Query params: version - optional BuildVersion object.
		 * Client-to-server: none.
		 * Server-to-client: [Event] objects.
		 */
		val websocket = to()
	}

	sealed class MinchatRoute(val name: String?) {
		protected fun to(subroute: String = ""): String {
			val prefix = name?.let { "/$it" }.orEmpty()
			return when {
				subroute.isEmpty() -> "/api$prefix"
				else -> "/api$prefix/$subroute"
			}
		}
	}
}
