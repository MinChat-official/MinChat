package io.minchat.server.util

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.minchat.server.databases.Users

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

/**
 * Throws an [AccessDeniedException] if an admin token is not provided.
 * Must be called within a transaction.
 */
fun ApplicationCall.requireAdmin() {
	if (!Users.isAdminToken(token())) accessDenied("admin-only route")
}

fun ApplicationResponse.statusOk() =
	status(HttpStatusCode.OK)
