package io.minchat.common.event

import kotlinx.serialization.*

/** A base class for all events. Only used for serialization purposes. */
@Serializable
sealed class Event {
	/**
	 * If not null, only the specified users should receive this event.
	 *
	 * Only valid on server-side. Always null on client-side.
	 */
	@Transient
	var recipients: LongArray? = null
}

/** Adds the specified users to [recipients] and returns this object. */
fun <T : Event> T.withRecipients(vararg ids: Long): T {
	recipients = if (recipients == null) {
		ids
	} else {
		recipients!! + ids
	}
	return this
}
