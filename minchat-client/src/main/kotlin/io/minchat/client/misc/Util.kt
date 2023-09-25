package io.minchat.client.misc

import arc.Core
import arc.input.KeyCode
import arc.scene.Element
import arc.scene.ui.TextField
import com.github.mnemotechnician.mkui.extensions.elements.content
import io.ktor.client.network.sockets.*
import io.ktor.client.plugins.*
import kotlinx.coroutines.*
import java.nio.channels.UnresolvedAddressException
import java.security.cert.*
import java.time.*
import java.time.format.DateTimeFormatter
import kotlin.math.*

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
				toString().replace("""^.*Text: "(.+)".*""".toRegex(), "\$1").let {
					if (it.length > 200) {
						it.substringBefore("Text: ") // the server has sent an HTML response or similar
					} else it
				}
			}
			else -> response.status.description
		}
		val postifx = "(code ${response.status.value})"

		//"$prefix: $midfix. $postifx"
		// TODO: do I need the prefix and the postfix?
		midfix
	}
	is HttpRequestTimeoutException, is ConnectTimeoutException -> {
		"Timed out. Check your internet connection and try again."
	}
	is java.net.ConnectException -> {
		"Could not connect to the server. Check your internet connection."
	}
	is UnresolvedAddressException -> {
		"Could not resolve the server. Check your internet connection or make sure you're connecting to a valid MinChat server."
	}
	is CertPathValidatorException -> {
		val causeString = when (cause) {
			is CertificateExpiredException -> "The server's encryption certificate has expired: ${cause!!.message}."
			else -> "The server's encryption certificate is invalid."
		}
		"$causeString\nPlease, report to the developer (or the server owner if you're using a custom server) as soon as possible."
	}
	is IllegalStateException -> {
		// Caused by calls to error()
		"Error: ${message}"
	}
	is RuntimeException -> {
		"Mod error: $this"
	}
	else -> "Unknown error: $this.\nPlease, report to the developer."
}

fun Throwable.isImportant() = when (this) {
	is kotlinx.coroutines.CancellationException -> false
	else -> true
}

/** Same as [Job.invokeOnCompletion], but returns [this]. */
fun Job.then(handler: CompletionHandler) = this.also {
	invokeOnCompletion(handler)
}

/**
 * When the user presses enter while typing in this field,
 * provided element is clicked and the text is trimmed.
 */
fun TextField.then(element: Element) = apply {
	keyDown(KeyCode.enter) {
		content = content.trim()

		element.fireClick()
		if (element is TextField) {
			Core.scene.setKeyboardFocus(element)
		}
	}
}

/** Converts the given epoch timestamp to a human-readable ISO-8601 string. */
fun Long.toTimestamp() =
	DateTimeFormatter.RFC_1123_DATE_TIME.format(
		Instant.ofEpochMilli(this).atZone(ZoneId.systemDefault()))

private val durationUnitMap = mapOf(
	"ms" to 1L,
	"s" to 1000L,
	"m" to 60_000L,
	"h" to 3600_000L,
	"d" to 3600_000L * 24
)
private val durationRegex = "(-?\\d+)\\s*(${durationUnitMap.keys.joinToString("|")})".toRegex()
/**
 * Converts a string of format {factor}{unit} to a duration in milliseconds:
 *
 * * 10m = 10 minutes (1000 * 10 * 60) ms
 * * 24h = 24 hours (1000 * 60 * 60 * 24) ms
 *
 * Supports milliseconds, seconds, minutes, hours, days.
 *
 * Returns null if the duration is invalid or is negative and [allowNegative] is false.
 */
fun String.parseUnitedDuration(allowNegative: Boolean = false): Long? {
	val (_, factor, unit) = durationRegex.find(trim())?.groupValues ?: return null

	val result = (factor.toLongOrNull() ?: return null) * durationUnitMap.getOrDefault(unit, 1)
	return if (result < 0 && !allowNegative) {
		null
	} else {
		result
	}
}

/**
 * Converts a duration in milliseconds to a united duration compatible with [parseUnitedDuration].
 *
 * This process may be lossy, e.g. converting 886_380_000 (10d 6h 13m) to a united duration will result in 10d.
 */
fun Long.toUnitedDuration(): String {
	val unit = durationUnitMap.entries.findLast { (_, value) ->
		value * 10 <= abs(this) // find the unit smaller than duration/10
	} ?: durationUnitMap.entries.first()

	return "${ceil(toDouble() / unit.value).toInt()} ${unit.key}"
}
