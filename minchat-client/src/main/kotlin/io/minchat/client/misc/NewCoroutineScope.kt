package io.minchat.client.misc

import kotlinx.coroutines.*
import kotlin.coroutines.*

/** A utility class to quickly fork coroutine scopes. */
class NewCoroutineScope(
	parentScope: CoroutineScope,
	additionalContext: CoroutineContext = EmptyCoroutineContext
) : CoroutineScope {
	override val coroutineContext = run {
		val job = additionalContext[Job]

		parentScope.newCoroutineContext(
			when {
				job == null -> Job(parentScope.coroutineContext[Job]) + additionalContext
				else -> additionalContext // do not add a new job if there already is one
			}
		)
	}
}

/** Creates a [NewCoroutineScope] from this scope. */
fun CoroutineScope.fork(additionalContext: CoroutineContext = EmptyCoroutineContext) =
	NewCoroutineScope(this, additionalContext)

