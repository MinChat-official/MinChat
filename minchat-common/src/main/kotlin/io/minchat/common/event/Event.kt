package io.minchat.common.event

import io.minchat.common.entity.*
import kotlinx.serialization.*

/** A base class for all events. Only used for serialization purposes. */
@Serializable
sealed class Event
