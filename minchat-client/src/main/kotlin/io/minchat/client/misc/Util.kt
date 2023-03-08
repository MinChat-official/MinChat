package io.minchat.client.misc

import io.ktor.client.plugins.*
import kotlinx.coroutines.*

fun Throwable.userReadable() = when {
	this is ResponseException -> {
		val prefix = when (this) {
			is ClientRequestException -> "Client error"
			is ServerResponseException -> "Server error"
			else -> "Unknown network error"
		}
		val midfix = when (this) {
			is ServerResponseException -> {
				val regex = "Text: \"(.*)\"".toRegex()
				regex.find(message)!!.groupValues[1]
			}
			else -> response.status.description
		}
		val postifx = "Received status code ${response.status.value}"

		"$prefix: $midfix. $postifx"
	}
	this is java.net.ConnectException -> {
		"Could not connect to the server."
	}
	this is RuntimeException -> {
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
