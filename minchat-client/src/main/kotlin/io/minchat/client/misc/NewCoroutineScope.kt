package io.minchat.client.misc

import kotlinx.coroutines.*
import kotlin.coroutines.*

/** A utility class to quickly fork coroutine scopes. */
class NewCoroutineScope(
	parentScope: CoroutineScope,
	additionalContext: CoroutineContext = EmptyCoroutineContext
) : CoroutineScope {
	override val coroutineContext =
		parentScope.newCoroutineContext(additionalContext)
}

/** Creates a [NewCoroutineScope] from this scope. */
fun CoroutineScope.fork(additionalContext: CoroutineContext = EmptyCoroutineContext) =
	NewCoroutineScope(this, additionalContext)
