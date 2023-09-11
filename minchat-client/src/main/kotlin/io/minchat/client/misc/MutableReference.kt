package io.minchat.client.misc

typealias Mut<T> = MutableReference<T>

data class MutableReference<T>(var value: T) {
	override fun toString() = value.toString()
}
