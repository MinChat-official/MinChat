package io.minchat.client.ui

import arc.input.KeyCode
import arc.math.Mathf
import arc.math.geom.Vec2
import arc.scene.Element
import arc.scene.event.*
import arc.util.pooling.Pool
import java.util.*
import javax.naming.OperationNotSupportedException
import kotlin.math.*

/**
 * A group that displays data as a list of elements of the same type.
 *
 * This group is meant to display a large or even infinite number of elements.
 * This is possible because this group uses the minimum possible number of elements,
 * removing and recycling elements that are no longer visible.
 */
class RecyclerGroup<Data, E: Element>(
	val adapter: Adapter<Data, E>
) : Element() {
	val links = LinkedList<Link>()
	var firstShownPosition = 0
	/**
	 * Scroll offset relative to the top of the first visible element.
	 *
	 * If this value is positive, the next element will be shown on the next frame
	 * and this value will change to negative.
	 * If its absolute value is greater than
	 * the height of the first visible element, then the top element will be freed on
	 * the next frame.
	 *
	 * If it's already negative, then the top of the first visible element is invisible.
	 */
	var relativeScrollOffset = 0f
	var scrollSpeed = 0f

	/** Additional empty space around this element's borders. */
	val padding = Padding(0f, 0f, 0f, 0f)
	/** Additional empty space around each element. */
	val elementMargin = Padding(0f, 0f, 0f, 0f)

	val elementPool = object : Pool<E>() {
		override fun newObject() = adapter.createElement()

		override fun obtain(): E {
			while (true) {
				if (free <= 0) return newObject()

				val element = super.obtain()
				if (adapter.isReusable(element)) return element
			}
		}
	}

	var datasetInvalid: Boolean = true
		private set
	private val computedSize = Vec2()

	init {
		adapter.parent = this

		addCaptureListener(object : ElementGestureListener() {
			override fun fling(event: InputEvent?, velocityX: Float, velocityY: Float, button: KeyCode?) {
				if (button != KeyCode.mouseLeft) return

				scrollSpeed += velocityY
			}
		})
	}

	/**
	 * Adds a data entry to this recycler.
	 * May throw an exception if this recycler's adapter doesn't support adding new data.
	 */
	fun addEntry(data: Data) =
		adapter.addEntry(data)

	/**
	 * Removes a data entry from this recycler.
	 * May throw an exception if this recycler's adapter doesn't support removing data.
	 */
	fun removeEntry(data: Data) =
		adapter.removeEntry(data)

	/** Forces this recycler to reinterpret the dataset on the next frame. */
	fun invalidateDataset() {
		datasetInvalid = true
		invalidate()
	}

	override fun layout() {
		if (datasetInvalid) {
			datasetInvalid = false
			layoutDataset()
		}

		computeSize()

		// Firstly, we need to determine how much height the elements want to know
		// where to start laying them out
		val startHeight = if (links.size >= adapter.dataset.size) {
			// Shortcut: all space will be taken
			height - padding.top - padding.bottom
		} else {
			links.sumOf {
				it.element.prefHeight.toDouble() + elementMargin.top + elementMargin.bottom
			}.toFloat().coerceAtMost(height - padding.top - padding.bottom)
		}

		// Lay elements out on the screen as they go in the link list
		// Scroll offset is ignored here as it is only applied during drawing and event processing.
		var heightOccupied = 0f
		val availableWidth = width - padding.left - padding.right

		links.forEach {
			val element = it.element

			element.x = padding.left + elementMargin.left
			element.y = padding.bottom + startHeight - heightOccupied + elementMargin.top
			element.setSize(
				availableWidth - padding.left - padding.right - elementMargin.left - elementMargin.right,
				max(element.prefHeight - elementMargin.top - elementMargin.bottom, 5f)
			)
			element.validate()

			heightOccupied += element.prefHeight + elementMargin.top + elementMargin.bottom
		}
	}

	/**
	 * Forcibly updates all shown elements.
	 *
	 * Additionally, updates [firstShownPosition].
	 */
	fun layoutDataset() {
		val dataset = adapter.dataset

		firstShownPosition = firstShownPosition.coerceIn(0, dataset.lastIndex)

		val firstLink = links.first
		val firstElementDataIndex = dataset.indexOfFirst { it == firstLink.data }.let {
			if (it == -1) 0 else it
		}

		// Firstly, free all links and clear the link list
		links.forEach {
			it.free()
		}
		links.clear()

		if (dataset.isEmpty()) return

		// Then, recreate the link list.
		// This may allocate more elements if needed.
		// To avoid overhead, we lay elements out right away
		var dataIndex = firstElementDataIndex
		var heightOccupied = 0f
		while (true) {
			val data = dataset[dataIndex]
			val element = elementPool.obtain()

			adapter.updateElement(element, data, dataIndex)

			element.validate()
			heightOccupied += element.prefHeight

			val link = Link(element, data)
			links.add(link)

			if (heightOccupied >= height) {
				break
			} else {
				dataIndex++
			}
		}
	}

	/** Updates [computedSize]. */
	protected fun computeSize() {
		computedSize.x = links.maxOf {
			it.element.prefWidth
		} + padding.left + padding.right + elementMargin.left + elementMargin.right

		computedSize.y = links.sumOf {
			it.element.prefHeight.toDouble() + elementMargin.top + elementMargin.bottom
		}.toFloat() + padding.top + padding.bottom
	}

	override fun act(delta: Float) {
		super.act(delta)

		if (links.isEmpty()) {
			relativeScrollOffset = 0f
			scrollSpeed = 0f
			return
		}

		val dataset = adapter.dataset

		relativeScrollOffset += scrollSpeed * delta
		scrollSpeed = Mathf.lerpDelta(scrollSpeed, 0f, 0.9f)

		if (abs(scrollSpeed) < 0.01f) scrollSpeed = 0f

		val topLink = links.first
		if (relativeScrollOffset > 0f) {
			if (firstShownPosition > 0) {
				// Add a new link at the top
				val element = elementPool.obtain()
				val data = dataset[--firstShownPosition]
				val link = Link(element, data)

				adapter.updateElement(element, data, firstShownPosition)
				links.addFirst(link)
				invalidate()
			} else {
				relativeScrollOffset = 0f
			}
		} else if (relativeScrollOffset < 0f) {
			if (firstShownPosition < dataset.lastIndex - links.size) {
				if (relativeScrollOffset < -topLink.element.height - elementMargin.top - elementMargin.bottom) {
					// Remove the topmost link
					links.removeFirst().free()
					firstShownPosition++
					invalidate()
				}
			} else {
				relativeScrollOffset = 0f
			}
		}

		if (links.size > dataset.size) {
			// Ensure there's no free space at the end.
			// If there is, add more links.
			// If there are invisible elements at the end, remove them
			var occupiedHeight = relativeScrollOffset
			val availableHeight = height - padding.top - padding.bottom

			val iterator = links.listIterator()
			for (link in iterator) {
				if (occupiedHeight > availableHeight) {
					iterator.remove()
					invalidate()
				} else {
					occupiedHeight += link.element.prefHeight + elementMargin.top + elementMargin.bottom
				}
			}

			if (occupiedHeight < availableHeight && dataset.size > firstShownPosition + links.size) {
				val element = elementPool.obtain()
				val data = dataset[firstShownPosition + links.size]
				val link = Link(element, data)

				adapter.updateElement(element, data, firstShownPosition + links.size)

				links.addLast(link)
				invalidate()
			}
		}

		validate()
	}

	override fun getMinWidth() = computedSize.x

	override fun getMinHeight() = computedSize.y

	abstract class Adapter<Data, T : Element> {
		internal var parent: RecyclerGroup<Data, T>? = null

		/**
		 * The dataset of this adapter.
		 * When the underlying list is changed, [datasetChanged] must be called.
		 */
		abstract val dataset: List<Data>

		/**
		 * Called when the parent group needs to allocate a new element.
		 *
		 * This function mustn't assign any data to the element in any way.
		 * The element will immediately be passed to [updateElement] which should
		 * do that.
		 *
		 * If the returned element has some listeners, they must be set up here.
		 */
		abstract fun createElement() : T

		/**
		 * Called when the parent group needs to display new data and
		 * there is a recycling candidate.
		 *
		 * If this function returns false for all candidates, a new element will be allocated by
		 * invoking [createElement]. Otherwise, this element will be passed to [updateElement].
		 */
		abstract fun isReusable(element: T): Boolean

		/**
		 * Must update the element in accordance with the provided data entry.
		 *
		 * This function mustn't add any listeners to the element.
		 */
		abstract fun updateElement(element: T, data: Data, position: Int)

		/** Throw an [OperationNotSupportedException] by default. */
		open fun addEntry(data: Data): Boolean =
			throw OperationNotSupportedException("$this does not support adding new data.")

		/** Throw an [OperationNotSupportedException] by default. */
		open fun removeEntry(data: Data): Boolean =
			throw OperationNotSupportedException("$this does not support removing data.")

		open fun datasetChanged() {
			parent?.datasetInvalid = true
		}
	}

	/**
	 * Represents a link between an element and a data entry.
	 */
	inner class Link(
		var element: E,
		var data: Data
	) {
		override fun toString(): String {
			return "Link(element=$element, data=$data)"
		}

		/** Frees the element. */
		fun free() {
			elementPool.free(element)
		}
	}

	inner class Padding(top: Float, bottom: Float, left: Float, right: Float) {
		var top = top
			set(value) {
				field = value
				invalidate()
			}
		var bottom = bottom
			set(value) {
				field = value
				invalidate()
			}
		var left = left
			set(value) {
				field = value
				invalidate()
			}
		var right = right
			set(value) {
				field = value
				invalidate()
			}

		fun set(top: Float, bottom: Float, left: Float, right: Float) {
			this.top = top
			this.bottom = bottom
			this.left = left
			this.right = right
		}

		fun set(all: Float) =
			set(all, all, all, all)

		override fun toString(): String {
			return "Padding(top=$top, bottom=$bottom, left=$left, right=$right)"
		}
	}
}
