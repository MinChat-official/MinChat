package io.minchat.client.plugin.impl

import com.github.mnemotechnician.mkui.delegates.setting
import io.minchat.client.*
import io.minchat.client.config.MinchatSettings
import io.minchat.client.plugin.MinchatPlugin
import io.minchat.common.BaseLogger
import io.minchat.common.BaseLogger.Companion.getContextSawmill
import io.minchat.rest.MinchatAccount
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class AccountSaverPlugin : MinchatPlugin("account-saver") {
	private var minchatAccountJson by setting<String>("", MinchatSettings.prefix)
	private val logger = BaseLogger.getContextSawmill()

	override fun onInit() {
		subscribe<AuthorizationEvent> {
			if (it.manually) {
				saveUserAccount()
			}
		}
		subscribe<ConnectEvent> {
			if (Minchat.client.account == null) {
				restoreAccount()
			}
		}
	}

	suspend fun restoreAccount() {
		val client = Minchat.client

		try {
			val account = loadUserAccount() ?: return
			logger.info { "Attempting to restore the logged-in MinChat account..." }

			client.account = account
			if (client.validateCurrentAccount()) {
				client.updateAccount()
				logger.info { "Successfully logged-in as ${client.account?.user?.username}!" }

				ClientEvents.fire(AuthorizationEvent(Minchat.client, false))
			} else {
				logger.error { "The restored user account is not valid. Defaulting to anonymous." }
				client.account = null
			}
		} catch (e: Exception) {
			logger.error { "Failed to restore user account: $e" }
			logger.error { "Defaulting to anonymous" }
			client.account = null
		}
	}

	fun forgetAccount() {
		minchatAccountJson = ""
	}

	fun saveUserAccount() {
		val account = Minchat.client.account
		if (account == null) {
			minchatAccountJson = ""
		} else {
			val surrogate = MinchatAccount.Surrogate(account.user, account.token)
			minchatAccountJson = Json.encodeToString(surrogate)
		}
	}

	fun loadUserAccount(): MinchatAccount? {
		val json = minchatAccountJson.takeIf { it.isNotBlank() } ?: return null
		val surrogate = Json.decodeFromString<MinchatAccount.Surrogate>(json)
		return MinchatAccount(surrogate.user, surrogate.token)
	}
}
