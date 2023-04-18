package io.minchat.client.ui

import arc.graphics.g2d.Draw
import arc.input.KeyCode
import arc.math.geom.Vec2
import arc.scene.*
import arc.scene.event.*
import arc.scene.style.Drawable
import arc.util.Tmp
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
 *
 * Important: despite being a [Group], this element doesn't use the [children]
 * property. An attempt to add a child will throw an exception. Any modification
 * of that property will be ignored.
 */
class RecyclerGroup<Data, E: Element>(
	val adapter: Adapter<Data, E>
) : Group() {
	val links = LinkedList<Link>()
	/**
	 * Position of the first visible element in [adapter.dataset].
	 * May be invalid if the underlying dataset was changed but not invalidated.
	 */
	var firstShownPosition = 0
	/**
	 * Vertical scroll offset relative to the top of the first visible element.
	 * Lower value means the recycler was scrolled down more.
	 *
	 * 0 means that the top element is perfectly visible.
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
	/**
	 * When the user does a fling gesture, this variable
	 * indicates the remaining time of the fling effect.
	 */
	var flingTimer = 0f

	var background: Drawable? = null
	/** Additional empty space around this element's borders. */
	val padding = Padding(0f, 0f, 0f, 0f)
	/** Additional empty space around each element. */
	val elementMargin = Padding(0f, 0f, 0f, 0f)

	val elementPool = object : Pool<E>() {
		override fun newObject() = adapter.createElement()

		override fun obtain(): E {
			while (true) {
				if (free <= 0) return newObject().also {
					// Necessary to set its scene.
					addChild(it)
				}

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
		touchable = Touchable.enabled

		// Flick & pan listener
		addListener(object : ElementGestureListener() {
			override fun pan(event: InputEvent?, x: Float, y: Float, deltaX: Float, deltaY: Float) {
				relativeScrollOffset -= deltaY
			}

			override fun fling(event: InputEvent?, velocityX: Float, velocityY: Float, button: KeyCode?) {
				if (button != KeyCode.mouseLeft && button != null) return

				flingTimer = 1f
				scrollSpeed -= velocityY
			}
		})

		// Scroll listener
		addListener(object : InputListener() {
			override fun touchDown(event: InputEvent?, x: Float, y: Float, pointer: Int, button: KeyCode?): Boolean {
				if (button == KeyCode.mouseLeft || button == null) {
					scrollSpeed = 0f
					flingTimer = 0f
				}
				return false
			}

			override fun scrolled(event: InputEvent?, x: Float, y: Float, amountX: Float, amountY: Float): Boolean {
				relativeScrollOffset -= amountY * 10f
				return true
			}

			override fun mouseMoved(event: InputEvent?, x: Float, y: Float): Boolean {
				requestScroll()
				return false
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
		val startHeight = if (links.size < adapter.dataset.size) {
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
				max(element.prefHeight, 5f)
			)
			element.y -= element.prefHeight - padding.top
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

		val firstElementDataIndex = firstShownPosition.coerceIn(dataset.indices)

		// Firstly, free all links and clear the link list
		links.forEach {
			it.free()
		}
		links.clear()

		if (dataset.isEmpty()) return

		// Then, recreate the link list.
		// This may allocate more elements if needed.
		// To avoid overhead, we validate elements right away
		var dataIndex = firstElementDataIndex
		var heightOccupied = 0f
		while (dataIndex < dataset.lastIndex) {
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
		if (links.isEmpty()) {
			computedSize.setZero()
			return
		}

		computedSize.x = links.maxOf {
			it.element.prefWidth
		} + padding.left + padding.right + elementMargin.left + elementMargin.right

		computedSize.y = links.sumOf {
			it.element.prefHeight.toDouble() + elementMargin.top + elementMargin.bottom
		}.toFloat() + padding.top + padding.bottom

		background?.let { background ->
			computedSize.set(
				min(computedSize.x, background.minWidth),
				min(computedSize.y, background.minHeight)
			)
		}
	}

	override fun act(delta: Float) {
		super.act(delta)

		if (links.isEmpty()) {
			relativeScrollOffset = 0f
			scrollSpeed = 0f
			return
		}

		val dataset = adapter.dataset

		relativeScrollOffset += scrollSpeed * flingTimer * delta
		flingTimer -= delta
		if (flingTimer <= 0f) {
			scrollSpeed = 0f
			flingTimer = 0f
		}

		val topLink = links.first
		if (relativeScrollOffset > 0f) {
			while (relativeScrollOffset > 0f) {
				if (firstShownPosition <= 0) {
					relativeScrollOffset = 0f
					adapter.topReached()
					break
				}

				// Add a new link at the top
				val element = elementPool.obtain()
				val data = dataset[--firstShownPosition]
				val link = Link(element, data)

				adapter.updateElement(element, data, firstShownPosition)
				element.validate()
				links.addFirst(link)
				invalidate()

				relativeScrollOffset -= link.prefHeight
			}
		} else if (relativeScrollOffset < -topLink.height) {
			// Height is used here instead of prefHeight because these elements are already valid
			var currentTopLink = topLink
			do {
				if (firstShownPosition > dataset.lastIndex - links.size) {
					relativeScrollOffset = -currentTopLink.height
					adapter.bottomReached()
					break
				}

				// Remove the topmost link
				currentTopLink = links.removeFirst()
				currentTopLink.free()
				invalidate()
				relativeScrollOffset += currentTopLink.height
				firstShownPosition++

				val newTopLink = links.peek() ?: break
			} while (relativeScrollOffset < -newTopLink.height)
		}

		// At the end of this process, the scroll offset must remain in the correct range
		val bottomLinkHeight = links.peekLast()?.height ?: 0f
		relativeScrollOffset = relativeScrollOffset.coerceIn(-bottomLinkHeight..0f)

		// Ensure there's no free space at the end
		// If there is, add more links.
		// If there are invisible elements at the end, remove them
		var occupiedHeight = relativeScrollOffset
		val availableHeight = height - padding.top - padding.bottom

		val iterator = links.listIterator()
		for (link in iterator) {
			if (occupiedHeight > availableHeight + link.prefHeight) {
				iterator.remove()
				invalidate()
			} else {
				// Using prefHeight because some elements may have an invalid size.
				occupiedHeight += link.prefHeight
			}
		}

		while (occupiedHeight < availableHeight && dataset.size > firstShownPosition + links.size) {
			val element = elementPool.obtain()
			val data = dataset[firstShownPosition + links.size]
			val link = Link(element, data)

			adapter.updateElement(element, data, firstShownPosition + links.size)
			element.validate()

			links.addLast(link)
			invalidate()

			occupiedHeight += link.prefHeight
		}

		validate()

		links.forEach {
			it.element.act(delta)
		}
	}

	override fun draw() {
		applyTransform(computeTransform())

		background?.let { background ->
			Draw.color(color.r, color.g, color.b, color.a * parentAlpha)
			background.draw(0f, 0f, width, height)
			Draw.color()
		}

		Draw.flush()
		if (clipBegin(
			padding.left,
			padding.top,
			width - padding.right - padding.left,
			height - padding.bottom - padding.top
		)) {
			for (link in links) {
				val element = link.element

				element.x += element.translation.x
				element.y += element.translation.y - relativeScrollOffset.toInt()
				element.draw()
				element.x -= element.translation.x
				element.y -= element.translation.y - relativeScrollOffset.toInt()
			}

			Draw.flush()
			clipEnd()
		}

		resetTransform()
	}

	override fun hit(x: Float, y: Float, touchable: Boolean): Element? {
		// First, try to hit one of the links
		val scrollOffset = relativeScrollOffset.toInt()
		for (link in links) {
			val element = link.element
			val position = element.parentToLocalCoordinates(Tmp.v1.set(x, y + scrollOffset))

			element.hit(position.x, position.y, touchable)?.let { return it }
		}

		if (x in 0f..width && y in 0f..height) {
			return this
		}
		return null
	}

	override fun notify(event: SceneEvent?, capture: Boolean): Boolean {
		// Notify the links
		for (link in links) {
			link.element.notify(event, capture)
		}
		return super.notify(event, capture)
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

		/** Called when the bottom of the list is reached and scrolled beyond. */
		open fun bottomReached() {}

		/** Called when the top of the list is reached and scrolled beyond. */
		open fun topReached() {}

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
		val height get() = element.height + elementMargin.top + elementMargin.bottom
		val prefHeight get() = element.prefHeight + elementMargin.top + elementMargin.bottom

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
