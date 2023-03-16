package io.minchat.common.event

import kotlinx.serialization.Serializable

/** A base class for all events. Only used for serialization purposes. */
@Serializable
sealed class Event
