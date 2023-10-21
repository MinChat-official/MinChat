package io.minchat.common.util

import kotlin.reflect.KProperty

/** An observable variable. */
class Observable<T>(value: T) {
	private val lock = Any()

	var value = value
		set(newValue) {
			synchronized(lock) {
				field = newValue
				changeChain?.forEach { it() }
			}
		}
	private var changeChain: Observer? = null

	operator fun getValue(thisRef: Any?, property: KProperty<*>): T {
		return value
	}

	operator fun setValue(thisRef: Any?, property: KProperty<*>, value: T) {
		this.value = value
	}

	fun disposeObserver(disposed: Observer) {
		val chain = changeChain ?: return

		synchronized(lock) {
			if (disposed == chain) {
				// edge case: if the first node in the chain is the disposed observer, change the start of the chain.
				changeChain = chain.next
			} else {
				for (observer in chain) {
					if (observer.next == disposed) {
						val nextNext = observer.next?.next
						observer.next = nextNext // skip the next node
						break
					}
				}
			}
		}
	}

	fun listObservers() =
		changeChain?.toList().orEmpty()

	fun observe(callback: Observer.(T) -> Unit) {
		val observer = Observer(callback)

		synchronized(lock) {
			if (changeChain == null) {
				changeChain = observer
			} else {
				changeChain!!.last().next = observer
			}
		}
	}

	/** A chainable observer. Can be disposed by calling [dispose]. */
	inner class Observer(val callback: Observer.(T) -> Unit) : Iterable<Observer> {
		var next: Observer? = null

		override fun iterator() = iterator {
			var observer: Observer? = this@Observer
			while (observer != null) {
				yield(observer)
				observer = observer.next
			}
		}

		operator fun invoke() {
			callback(this, value)
		}

		fun dispose() {
			this@Observable.disposeObserver(this)
		}
	}
}
