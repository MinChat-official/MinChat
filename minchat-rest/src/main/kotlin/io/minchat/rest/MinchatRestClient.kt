package io.minchat.rest

import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.websocket.*
import io.ktor.serialization.kotlinx.*
import io.ktor.serialization.kotlinx.json.*
import io.minchat.common.AbstractLogger
import io.minchat.rest.entity.*
import io.minchat.rest.ratelimit.*
import io.minchat.rest.service.*
import kotlinx.coroutines.*
import kotlinx.serialization.json.Json

/** An [AbstractLogger] used by the rest module. Must be initialized on startup. */
lateinit var MinchatRestLogger: AbstractLogger

class MinchatRestClient(
	val baseUrl: String,
	dispatcher: CoroutineDispatcher = Dispatchers.Default
) : CoroutineScope {
	override val coroutineContext = SupervisorJob() + dispatcher

	val httpClient = HttpClient(CIO) {
		expectSuccess = true
		install(WebSockets) {
			contentConverter = KotlinxWebsocketSerializationConverter(Json)
		}
		install(ClientRateLimit) {
			limiter = GlobalBucketRateLimiter()
			//retryOnRateLimit = true
		}
		install(ContentNegotiation) { 
			json(Json { ignoreUnknownKeys = true })
		}
	}

	var account: MinchatAccount? = null
	val isLoggedIn get() = account != null

	val rootService = RootService(baseUrl, httpClient)
	val userService = UserService(baseUrl, httpClient)
	val channelService = ChannelService(baseUrl, httpClient)
	val messageService = MessageService(baseUrl, httpClient)

	/** 
	 * Attempts to log into the provided Minchat account.
	 * Must be called before using most other methods.
	 *
	 * Length ranges:
	 * * [username] - 3..40 characters (server-side)
	 * * [password] - 8..40 characters (client-side only)
	 */
	suspend fun login(username: String, password: String) {
		account = userService.login(username, password)
	}

	/**
	 * Attempts to create a new Minchat account and log into it.
	 * Can be called instead of [login].
	 *
	 * Length ranges:
	 * * [username], [nickname] - 3..40 characters (server-side)
	 * * [password] - 8..40 characters (client-side only)
	 */
	suspend fun register(username: String, nickname: String?, password: String) {
		account = userService.register(username, nickname, password)
	}

	/** Logs out of the current account. */
	fun logout() {
		account = null
	}

	/** Returns [account] or throws an exception if this client is not logged in. */
	fun account(): MinchatAccount =
		account ?: error("You must log in before doing tnis.")
	
	/** Returns the currently logged-in account without updating it. */
	fun self() = account().user.withClient(this)
	
	/** 
	 * Returns the currently logged-in account without updating it, 
	 * or null if this client is not loggec in.
	 */
	fun selfOrNull() = account?.user?.withClient(this)
	
	/**
	 * Fetches the most recent User instance representing this user from the server
	 * and updates [account].
	 */
	suspend fun updateAccount() =
		getUser(account().id).also {
			account?.user = it.data
		}

	// getX methods
	/** Fetches the MinChat version of the server. */
	suspend fun getServerVersion() =
		rootService.getServerVersion()

	/** Fetches the most recent data of the currently logged-in user and returns it. */
	suspend fun getSelf() = run {
		updateAccount()
		self()
	}

	/** Fetches the user with the specified ID. */
	suspend fun getUser(id: Long) =
		userService.getUser(id).withClient(this)

	/** Fetches the user with the specified ID, returns null if it doesn't exist.. */
	suspend fun getUserOrNull(id: Long) = runCatching {
		getUser(id)
	}.getOrNull()

	/** Fetches the channel with the specified ID. */
	suspend fun getChannel(id: Long) =
		channelService.getChannel(id).withClient(this)

	/** Fetches the channel with the specified ID, returns null if it doesn't exist.. */
	suspend fun getChannelOrNull(id: Long) = runCatching {
		getChannel(id)
	}.getOrNull()

	/** Fetches all channels registered on the server. */
	suspend fun getAllChannels() =
		channelService.getAllChannels()
			.map { it.withClient(this) }

	/** Fetches the message with the specified ID. */
	suspend fun getMessage(id: Long) =
		messageService.getMessage(id).withClient(this)

	/** Fetches the message with the specified ID, returns null if it doesn't exist.. */
	suspend fun getMessageOrNull(id: Long) = runCatching {
		getMessage(id)
	}.getOrNull()

	/** 
	 * Fetches messages from the specified channel.
	 * See [ChannelService.getMessages] for more info.
	 */
	suspend fun getMessagesIn(
		channelId: Long,
		fromTimestamp: Long? = null,
		toTimestamp: Long? = null
	) = channelService.getMessages(channelId, fromTimestamp, toTimestamp).map {
		it.withClient(this)
	}

	/** Sends a message in the specified channel. */
	suspend fun createMessage(channelId: Long, content: String) =
		channelService.createMessage(channelId, account().token, content)
			.withClient(this)

	/** Creates a new channel. Requires a logged-in admin account. */
	suspend fun createChannel(name: String, description: String) =
		channelService.createChannel(
			name = name,
			description = description,
			token = account().token
		).withClient(this)
	
	// editX methods
	/** 
	 * Edits the user with the specified ID.
	 * Requires a registered account.
	 * Accounts of others can only be edited by admins.
	 */
	suspend fun editUser(id: Long, newNickname: String?) =
		userService.editUser(id, account().token, newNickname)
			.withClient(this)

	/** Edits the currently logged-in account. */
	suspend fun editSelf(newNickname: String?) =
		editUser(account().id, newNickname).also {
			account().user = it.data
		}
	
	/** Edits the specified channel. Requires a logged-in admin account. */
	suspend fun editChannel(
		id: Long,
		newName: String? = null,
		newDescription: String? = null
	) = channelService.editChannel(id, account().token, newName, newDescription)
		.withClient(this)
	
	/** 
	 * Edits the specified message.
	 * Requires a logged-in account.
	 * Non-admins can only edit their own messages.
	 */
	suspend fun editMessage(id: Long, newContent: String) =
		messageService.editMessage(id, account().token, newContent)
			.withClient(this)
	
	// deleteX methods
	/**	
	 * Deleted the user with the specified ID.
	 * Requires a registered account.
	 * Accounts of others can only be deleted by admins.
	 */
	suspend fun deleteUser(id: Long) =
		userService.deleteUser(id, account().token)

	/** Deletes the currently logged-in account. */
	suspend fun deleteSelf() =
		deleteUser(account().id).also {
			account = null
		}
	
	/** Deletes the specified channel. Requires a logged-in admin account. */
	suspend fun deleteChannel(id: Long) =
		channelService.deleteChannel(id, account().token)
	
	/** 
	 * Deletes the specified message.
	 * Requires a logged-in account.
	 * Non-admins can only edit their own messages.
	 */
	suspend fun deleteMessage(id: Long) =
		messageService.deleteMessage(id, account().token)

	// canX methods
	/**
	 * Returns true if the currently logged-in user can edit the specified user.
	 *
	 * True is returned if the current user is an admin or is the same user as
	 * the provided user.
	 */
	fun canEditUser(user: MinchatUser) =
		selfOrNull()?.let {
			it.id == user.id || it.isAdmin
		} ?: false

	/**
	 * Returns true if and only if the currently logged-in user is an admin.
	 */
	@Suppress("UNUSED_PARAMETER")
	fun canEditChannel(channel: MinchatChannel) =
		selfOrNull()?.isAdmin ?: false

	/**
	 * Returns true if the currently logged-in user can edit the specified message.
	 *
	 * True is returned if the current user is an admin or is the author of the message.
	 */
	fun canEditMessage(message: MinchatMessage) =
		selfOrNull()?.let {
			it.isAdmin || message.author.id == it.id
		} ?: false

	/** Makes sure the current account (token) is valid by sending a request to the server. */
	suspend fun validateCurrentAccount(): Boolean {
		account ?: return false
		return rootService.validateToken(account!!.user.username, account!!.token)
	}
}
