package io.minchat.rest

import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.websocket.*
import io.ktor.serialization.kotlinx.*
import io.ktor.serialization.kotlinx.json.*
import io.minchat.common.*
import io.minchat.common.entity.*
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
			contentConverter = KotlinxWebsocketSerializationConverter(Json {
				ignoreUnknownKeys = true
			})
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
	val channelGroupService = ChannelGroupService(baseUrl, httpClient)
	val messageService = MessageService(baseUrl, httpClient)

	var cache = CacheService(baseUrl, this)

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
		account ?: error("You must log in to do this.")
	
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
	/**
	 * Fetches the MinChat version of the server.
	 */
	suspend fun getServerVersion() =
		rootService.getServerVersion()

	/**
	 * Fetches the most recent data of the currently logged-in user,
	 * updates it in the cache, and returns it.
	 *
	 * For the non-rest version of this method, see [self].
	 */
	suspend fun getSelf() = run {
		updateAccount()
		self().also { cache.set(it.data) }
	}

	/**
	 * Fetches the user with the specified ID and updates it in the cache.
	 *
	 * For the caching version of this method, see [cache].
	 */
	suspend fun getUser(id: Long) =
		userService.getUser(id)
			.also(cache::set)
			.withClient(this)

	/**
	 * Fetches the user with the specified ID, returns null if it doesn't exist.
	 * This method also updates the [cache].
	 *
	 * For the caching version of this method, see [cache].
	 */
	suspend fun getUserOrNull(id: Long) = runCatching {
		getUser(id)
	}.getOrNull()

	/**
	 * Fetches the channel with the specified ID and updates the cache.
	 *
	 * For the caching version of this method, see [cache].
	 */
	suspend fun getChannel(id: Long) =
		channelService.getChannel(id)
			.also(cache::set)
			.withClient(this)

	/**
	 * Fetches the channel with the specified ID, returns null if it doesn't exist.
	 * This method also updates the cache.
	 *
	 * For the caching version of this method, see [cache].
	 */
	suspend fun getChannelOrNull(id: Long) = runCatching {
		getChannel(id)
	}.getOrNull()

	/**
	 * Fetches all channels registered on the server.
	 *
	 * For the caching approach, you can try `cache.channelCache.values`.
	 */
	suspend fun getAllChannels() =
		channelService.getAllChannels()
			.onEach(cache::set)
			.map { it.withClient(this) }

	/**
	 * Fetches all channels registered on the server.
	 *
	 * For the caching approach, try `cache.channelgroupCache.values`
	 */
	suspend fun getAllChannelGroups() =
		channelGroupService.getAllGroups()
			.onEach(cache::set)
			.map { it.withClient(this) }

	/**
	 * Fetches the message with the specified ID and updates the cache.
	 *
	 * For the caching version of this method, see [cache].
	 */
	suspend fun getMessage(id: Long) =
		messageService.getMessage(id).also {
			cache.set(it)
		}.withClient(this)

	/**
	 * Fetches the message with the specified ID, returns null if it doesn't exist.
	 * This method also updates the cache.
	 *
	 * For the caching version of this method, see [cache].
	 */
	suspend fun getMessageOrNull(id: Long) = runCatching {
		getMessage(id)
	}.getOrNull()

	/**
	 * Fetches the channel group with the specified ID, returns null if it doesn't exist.
	 * This method also updates the cache.
	 *
	 * For the caching version of this method, see [cache].
	 */
	suspend fun getChannelGroupOrNull(id: Long) = runCatching {
		getChannelGroup(id)
	}.getOrNull()

	/**
	 * Fetches the channel group with the specified ID and updates the cache.
	 *
	 * For the caching version of this method, see [cache].
	 */
	suspend fun getChannelGroup(id: Long) =
		channelGroupService.getGroup(id)
			.also(cache::set)
			.withClient(this)

	/** 
	 * Fetches messages from the specified channel.
	 * See [Route.Channel.messages] for more info.
	 *
	 * This method also updates all fetched messages in the cache.
	 */
	suspend fun getMessagesIn(
		channelId: Long,
		fromTimestamp: Long? = null,
		toTimestamp: Long? = null
	) = channelService.getMessages(channelId, fromTimestamp, toTimestamp).map {
		cache.set(it)
		it.withClient(this)
	}

	/** Sends a message in the specified channel and adds it to the cache. */
	suspend fun createMessage(channelId: Long, content: String, referencedMessageId: Long? = null) =
		channelService.createMessage(channelId, account().token, content, referencedMessageId)
			.also(cache::set)
			.withClient(this)

	/** Creates a new channel. Requires a logged-in admin account. */
	suspend fun createChannel(
		name: String,
		description: String,
		viewMode: Channel.AccessMode,
		sendMode: Channel.AccessMode,
		groupId: Long?,
		order: Int
	) =
		channelService.createChannel(
			name = name,
			description = description,
			sendMode = sendMode,
			viewMode = viewMode,
			order = order,
			groupId = groupId,
			token = account().token
		).also(cache::set).withClient(this)
	
	// editX methods
	/** 
	 * Edits the user with the specified ID.
	 * Requires a registered account.
	 * Accounts of others can only be edited by admins.
	 */
	suspend fun editUser(id: Long, newNickname: String?) =
		userService.editUser(id, account().token, newNickname)
			.also(cache::set)
			.withClient(this)

	/** Edits the currently logged-in account. */
	suspend fun editSelf(newNickname: String?) =
		editUser(account().id, newNickname).also {
			cache.set(it)
			account().user = it.data
		}
	
	/** Edits the specified channel. Requires a logged-in admin account. */
	suspend fun editChannel(
		id: Long,
		newName: String? = null,
		newDescription: String? = null,
		newViewMode: Channel.AccessMode? = null,
		newSendMode: Channel.AccessMode? = null,
		newGroupId: Long? = null,
		newOrder: Int? = null
	) = channelService.editChannel(id, account().token, newName, newDescription, newViewMode, newSendMode, newGroupId, newOrder)
		.also(cache::set)
		.withClient(this)

	/** Edits the specified group. Requires a logged-in admin account. */
	suspend fun editChannelGroup(
		id: Long,
		newName: String? = null,
		newDescription: String? = null,
		newOrder: Int? = null
	) = channelGroupService.editGroup(id, account().token, newName, newDescription, newOrder)
		.also(cache::set)
		.withClient(this)
	
	/** 
	 * Edits the specified message.
	 * Requires a logged-in account.
	 * Non-admins can only edit their own messages.
	 */
	suspend fun editMessage(id: Long, newContent: String) =
		messageService.editMessage(id, account().token, newContent)
			.also(cache::set)
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

	/** Deletes the specified channel group. Requires a logged-in admin account. */
	suspend fun deleteChannelGroup(id: Long) =
		channelGroupService.deleteGroup(id, account().token)

	/** Makes sure the current account (token) is valid by sending a request to the server. */
	suspend fun validateCurrentAccount(): Boolean {
		account ?: return false
		return rootService.validateToken(account!!.user.username, account!!.token)
	}

	// Admin-only
	/** Modifies the punishments of the user with the specified id. Returns the updated user. Requires admin rights. */
	suspend fun modifyUserPunishments(user: MinchatUser, newMute: User.Punishment?, newBan: User.Punishment?): MinchatUser {
		require(self().role.isAdmin) { "Only admins can modify user punishments." }

		return userService.modifyUserPunishments(account().token, user.id, newMute, newBan)
			.also(cache::set)
			.withClient(this)
	}
}
