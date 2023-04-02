package io.minchat.client.ui

import arc.scene.*

/**
 * A group that displays an arbitrarily large list of elements 
 * without a big performance overhead.
 *
 * This is achieved by reusing the same elements as they come
 * in and out of the viewport rather than allocating
 * many elements and storing them in-memory.
 *
 * Elements of this group must not store any data on their own,
 * as it can be overridden at any moment. Instead, the data
 * must be stored in lightweight data classes extending
 * [RecyclerGroup.DataEntry] and loaded from them as needed.
 *
 * @param E the type of elements stored in this group.
 * @param T the type of a data class storing data of induvidual entries
 */
// abstract class RecyclerGroup<T : DataEntry, E : Element> : MutableList<T> {
// 	/** 
// 	 * All data entries stored in this recycler. 
// 	 * NEVER modify manually without calling [onDatasetModified].
// 	 */
// 	val entries = ArrayList<T>()
// 	/** 
// 	 * A list of elements and data entries currently associated
// 	 * with them used by this recycler. NEVER modify manually.
// 	 */
// 	protected val elements = ArrayList<ElementEntry>()
// 	/** A list of unused but pooled elements. */
// 	private val unusedElements = ArrayList<ElementEntry>()
//
// 	val size get() = entries.size
// 	private var oldSize = size
//
// 	/** 
// 	 * Maximum count of elements this recycler is allowed to show on the screen. 
// 	 * Actual size will be as low as possible and will never exceed this number.
// 	 */
// 	var sizeLimit = 50
//
// 	/** A relative value. Doesn't represent the actual scroll depth. */
// 	var scrollAmount = 0f
// 		private set
// 	/** Positive is downwards, negative is upwards. */
// 	var scrollVelocity = 0f
// 		private set
// 	/** The topmost shown data entry relatively to which [scrollAmount] is calclated. */
// 	var topElementIndex get() = 
// 		elements.firstOrNull()?.let { entries.indexOf(it.data) }?.coerceAtLeast(0) ?: 0
// 	var bottomElementIndex get() = 
// 		elements.lastOrNull()?.let { entries.indexOf(it.data) }?.coerceAtLeast(0) ?: 0
// 
// 	/** Margin between induvidual elements. */
// 	var margin = 0f
//
// 	/** 
// 	 * Called when this group needs to allocated another element,
// 	 * right after [createData]. Must create a blank element 
// 	 * that will later be configured.
// 	 *
// 	 * If the element stores some data, it must be saved in the properties
// 	 * of [entry.data] whenever the state of the element is modified.
// 	 */
// 	protected abstract fun createElement(entry: ElementEntry): E
//
// 	/**
// 	 * Called when an element comes into the viewport,
// 	 * either because it was just added or because it was recycled.
// 	 *
// 	 * This methos must load data from the data entry into the element.
// 	 * Do not add any callbacks inside this method.
// 	 */
// 	protected abstract fun configureElement(data: T, element: E)
//
// 	override fun update(delta: Float) {
// 		super.update(delta)
// 		if (size == 0) return
// 	
// 		scrollAmount += scrollVelocity * delta
// 		if (scrollVelocity > 0) {
// 			if (canScrollDown() && scrollAmount > elements.first().height) {
// 				val element = elements.removeAt(0)
// 			
// 			}
// 		}
// 	}
//
// 	/**
// 	 * Must load the data from the element onto
// 	 */
// 	protected abatract fun recycle(
//
// 	fun canScrollUp() =
// 		topElementIndex > 0 || scrollAmount > 0
// 	fun canScrollDown() when {
// 		size == 0 -> false
// 		else -> bottomElementIndex < size - 1 || scrollAmount < elements.last().element.height
// 	}
//
// 	/** 
// 	 * Called whenever the dataset changes.
// 	 * Must update the list of elements accordingly.
// 	 */
// 	open fun onDatasetModified() {
// 		if (size < oldSize) {
// 			// find all unused elements and remove them, hoping update() will reuse them
// 			var iterator = elements.listIterator()
// 			for (element in iterator) {
// 				if (element.data !in entries) {
// 					iterator.remove()
// 					unusedElements.add(element)
// 				}
// 			}
// 		}
// 		oldSize = size
// 	}
// 
// 	/** Get the element entry at the specified position. */
// 	operator fun get(position: Int) =
// 		elements[position]
// 
// 	/** Add new data to this recycler. */
// 	fun add(data: T, position: Int = size - 1) {
// 		entries.add(position, data)
// 		onDatasetModified()
// 	}
//
// 	/** Remove existing data at the specified position. */
// 	fun removeAt(index: Int) = entries.removeAt(index).also {
// 		onDatasetModified()
// 	}
// 
// 	/** Remove the specified data. */
// 	fun remove(data: T): Boolean {
// 		val index = entries.indexOf(data)
//
// 		return (index != -1).also {
// 			if (it) removeAt(index)
// 		}
// 	}
//
// 	/** Replace the data at the specified position. */
// 	operator fun set(position: Int, data: T) {
// 		entries[position] = data
// 		onDatasetModified()
// 	}
//
// 	/** 
// 	 * Represents a data entry of a recycler group.
// 	 * This class should be extended to store element-specific data.
// 	 */
// 	open inner class DataEntry
//
// 	/**
// 	 * Represents a link between an element and a data entry
// 	 * __currently__ associated with it.
// 	 */
// 	open inner class ElementEntry(val element: E) {
// 		/**
// 		 * The data entry currently associated with this element.
// 		 *
// 		 * An element must __not__ store a reference to this value under
// 		 * any circumstances, as it can be changed when the element
// 		 * is reused. Instead, this property must be accessed every
// 		 * time the element needs to save some data.
// 		 */
// 		lateinit var data: T
// 			internal set
// 		var position = 0
// 			internal set
// 	}
// }
