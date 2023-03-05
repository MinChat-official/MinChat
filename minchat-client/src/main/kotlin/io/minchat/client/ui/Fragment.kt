package io.minchat.client.ui

import arc.scene.Element
import arc.scene.Group
import arc.scene.ui.*
import arc.scene.ui.layout.*
import com.github.mnemotechnician.mkui.extensions.elements.*
import com.github.mnemotechnician.mkui.extensions.groups.*
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.*

/**
 * Represents a reusable UI fragment.
 *
 * The same fragment can be applied multiple times, however, only one instance of the same fragment
 * can be present in the ui tree at the same time, applying the fragment again simply moves that
 * instance.
 *
 * @param Parent the element type to which this fragment can be applied.
 * @param Type the type of this fragment, aka the element this fragment adds to the target group.
 */
abstract class Fragment<Parent: Table, Type: Element>(
	override val coroutineContext: CoroutineContext
) : CoroutineScope {
	/**
	 * The current instance of this fragment.
	 * Null if this fragment hasn't been applied anywhere.
	 */
	private var instance: Type? = null

	/** Whether this fragment is applied to any group */
	val isApplied get() = instance.let { it != null && it.parent != null && it.scene != null }

	/**
	 * Applies this fragment to the target table, building it if necessary.
	 *
	 * If this fragment is currently applied somewhere else, it will be removed from its current parent.
	 *
	 * @param target the group to which this fragment is applied.
	 * @param fillTarget whether the fragment should fill the target group. Has no effect if the target is a table.
	 */
	@Suppress("UNCHECKED_CAST")
	fun apply(target: Parent): Cell<Type> {
		val element = instance 
			?: build().also { instance = it }

		element.parent?.let {
			if (target == it) {
				// do not re-add an element to the same group
				return (element.cell<Element>() as Cell<Type>).also {
					applied(it)
				}
			}

			it.removeChild(instance, true)
			it.invalidateHierarchy()
		}
		element.clearActions()

		return (target.add(instance) as Cell<Type>).also {
			applied(it)

			if (element is Group) {
				element.deepShrink()
				element.deepInvalidate()
			}
			element.pack()
			element.invalidateHierarchy()
		}
	}

	/**
	 * Builds this fragment. Only called once, when the fragment is applied for the first time.
	 *
	 * @return an element which will be added to the target groups.
	 */
	protected abstract fun build(): Type

	/**
	 * Called when this fragment is applied.
	 */
	protected open fun applied(cell: Cell<Type>) {
	}
}
