package io.minchat.client.plugin.impl

import arc.util.Log
import com.github.mnemotechnician.mkui.delegates.setting
import io.minchat.client.Minchat
import io.minchat.client.config.MinchatSettings
import io.minchat.client.plugin.MinchatPlugin
import io.minchat.rest.MinchatAccount
import kotlinx.coroutines.launch
import kotlinx.serialization.*
import kotlinx.serialization.json.Json

class AccountSaverPlugin : MinchatPlugin("account-saver") {
	private var minchatAccountJson by setting<String?>(null, MinchatSettings.prefix)

	override suspend fun onConnect() {
		Minchat.launch {
			val client = Minchat.client

			try {
				val account = loadUserAccount() ?: return@launch
				Log.info("Attempting to restore the logged-in MinChat account...")

				client.account = account
				if (client.validateCurrentAccount()) {
					client.updateAccount()
					Log.info("Successfully logged-in as ${client.account?.user?.username}!")
				} else {
					Log.err("The restored user account is not valid. Defaulting to anonymous.")
					client.account = null
				}
			} catch (e: Exception) {
				Log.err("Failed to restore user account: $e")
				Log.err("Defaulting to anonymous")
				client.account = null
			}
		}
	}

	fun saveUserAccount() {
		val account = Minchat.client.account ?: return
		val surrogate = MinchatAccount.Surrogate(account.user, account.token)
		minchatAccountJson = Json.encodeToString(surrogate)
	}

	fun loadUserAccount(): MinchatAccount? {
		val json = minchatAccountJson ?: return null
		val surrogate = Json.decodeFromString<MinchatAccount.Surrogate>(json)
		return MinchatAccount(surrogate.user, surrogate.token)
	}
}
