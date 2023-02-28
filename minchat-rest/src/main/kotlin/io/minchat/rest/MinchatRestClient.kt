package io.minchat.rest

import io.ktor.http.*
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.*
import io.minchat.common.entity.*
import io.minchat.rest.service.*

class MinchatRestClient(
	val baseUrl: String,
	val httpClient: HttpClient = HttpClient(CIO)
) {
	var account: MinChatAccount? = null

	val userService = UserService(baseUrl, httpClient)
	val channelService = ChannelService(baseUrl, httpClient)
	val messageService = MessageService(baseUrl, httpClient)

	/** 
	 * Attempts to log into the providen MinChat account.
	 * Must be called before using most other methods.
	 *
	 * If the account doesn't exist and [register] is true,
	 * a new account is registered. Otherwise, an exception is thrown.
	 *
	 * Length ranges:
	 * * [username] - 3..40 characters (server-side)
	 * * [password] - 8..40 characters (client-side only)
	 */
	suspend fun login(username: String, password: String, register: Boolean = true) {
		account = try {
			userService.login(username, password)
		} catch (e: ClientRequestException) {
			// 400 bad request is a sign that the account doesn't exist
			if (!register || e.response.status.value != HttpStatusCode.BadRequest.value) {
				throw e
			}

			userService.register(username, password)
		}
	}

	/** Returns [account] or throws an exception if this client is not logged in. */
	fun account(): MinChatAccount =
		account ?: error("You must log in before doing tnis.")
	
	/**
	 * Fetches the most recent User instance representing this user from the server
	 * and updates [account].
	 */
	suspend fun updateAccount(): User =
		getUser(account().id).also {
			account?.user = it
		}

	// getX methods
	/** Fetches the user with the specified ID. */
	suspend fun getUser(id: Long) =
		userService.getUser(id)

	/** Fetches the user with the specified ID, returns null if it doesn't exist.. */
	suspend fun getUserOrNull(id: Long) = runCatching {
		getUser(id)
	}.getOrNull()

	/** Fetches the channel with the specified ID. */
	suspend fun getChannel(id: Long) =
		channelService.getChannel(id)

	/** Fetches the channel with the specified ID, returns null if it doesn't exist.. */
	suspend fun getChannelOrNull(id: Long) = runCatching {
		getChannel(id)
	}.getOrNull()

	/** Fetches the message with the specified ID. */
	suspend fun getMessage(id: Long) =
		messageService.getMessage(id)

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
	) = channelService.getMessages(channelId, fromTimestamp, toTimestamp)

	/** Sends a message in the specified channel. */
	suspend fun createMessage(channelId: Long, content: String) =
		channelService.createMessage(channelId, account().token, content)

	/** Creates a new channel. Requires a logged-in admin account. */
	suspend fun createMessage(name: String, description: String) =
		channelService.createChannel(
			name = name,
			description = description,
			token = account().token
		)
	
	// editX methods
	/** 
	 * Edits the user with the specified ID.
	 * Requires a registered account.
	 * Accounts of others can only be edited by admins.
	 */
	suspend fun editUser(id: Long, newUsername: String?) =
		userService.editUser(id, account().token, newUsername)

	/** Edits the currently logged-in account. */
	suspend fun editSelf(newUsername: String?) =
		editUser(account().id, newUsername).also {
			account().user = it
		}
	
	/** Edits the specified channel. Requires a logged-in admin account. */
	suspend fun editChannel(
		id: Long,
		newName: String? = null,
		newDescription: String? = null
	) = channelService.editChannel(id, account().token, newName, newDescription)
	
	/** 
	 * Edits the specified message.
	 * Requires a logged-in account.
	 * Non-admins can only edit their own messages.
	 */
	suspend fun editMessage(id: Long, newContent: String) =
		messageService.editMessage(id, account().token, newContent)
	
	// deleteX methodw
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
}
