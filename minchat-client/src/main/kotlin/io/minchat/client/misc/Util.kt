package io.minchat.client.misc

import io.ktor.client.network.sockets.*
import io.ktor.client.plugins.*
import kotlinx.coroutines.*

fun Throwable.userReadable() = when (this) {
	is ResponseException -> {
		val prefix = when {
			response.status.value == 429 -> "You are being rate limited"
			this is ClientRequestException -> "Client error"
			this is ServerResponseException -> "Server error"
			else -> "Unknown network error"
		}
		val midfix = when (this) {
			is ServerResponseException, is ClientRequestException -> {
				// extract the cached response text from the message
				toString().replace("""^.*Text: "(.+)".*""".toRegex(), "\$1")
			}
			else -> response.status.description
		}
		val postifx = "(code ${response.status.value})"

		"$prefix: $midfix. $postifx"
	}
	is ConnectTimeoutException -> {
		"Timed out. Check your internet connection and try again."
	}
	is java.net.ConnectException -> {
		"Could not connect to the server. Check your internet connection."
	}
	is RuntimeException -> {
		"Mod error: $message"
	}
	else -> "Unknown error: $this. Please, report to the developer."
}

fun Throwable.isImportant() = when (this) {
	is kotlinx.coroutines.CancellationException -> false
	else -> true
}

/** Same as [Job.invokeOnCompletion], but returns [this]. */
fun Job.then(handler: CompletionHandler) = this.also {
	invokeOnCompletion(handler)
}
