package io.minchat.server.util

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*

/** 
 * Returns the authorization header of this call,
 * or null if it's not providen or malformed.
 */
fun ApplicationCall.tokenOrNull() =
	request.header("Authorization")
		?.trimStart()
		?.takeIf { it.startsWith("Bearer ") }
		?.removePrefix("Bearer ")
		?.takeIf { it.isNotBlank() }

/** 
 * Returns the authorization header of this call. 
 * @throws AccessDeniedException if the header is absent or malformed.
 */
fun ApplicationCall.token() =
	tokenOrNull() ?: accessDenied("Incorrect or malformed token.")

fun ApplicationResponse.statusOk() =
	status(HttpStatusCode.OK)
