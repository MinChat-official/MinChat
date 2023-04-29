package io.minchat.client.ui.chat

import io.minchat.client.ui.RecyclerGroup
import io.minchat.rest.entity.MinchatMessage

class ChatRecyclerAdapter(
	val chat: ChatFragment
) : RecyclerGroup.Adapter<MinchatMessage, NormalMinchatMessageElement>() {
	override val dataset = mutableListOf<MinchatMessage>()

	override fun createElement() =
		NormalMinchatMessageElement(chat, null, true)

	override fun isReusable(element: NormalMinchatMessageElement) =
		true

	override fun updateElement(element: NormalMinchatMessageElement, data: MinchatMessage, position: Int) {
		element.message = data
	}

	/**
	 * Adds a message to the dataset.
	 */
	override fun addEntry(data: MinchatMessage) = true.also {
		dataset.add(data)
		datasetChanged()
	}

	/**
	 * Removes a message from the dataset.
	 */
	override fun removeEntry(data: MinchatMessage) =
		dataset.remove(data).also {
			datasetChanged()
		}

	/**
	 * Removes a message with the specified id.
	 */
	fun removeEntry(id: Long) =
		dataset.removeAll { it.id == id }.also {
			datasetChanged()
		}

	/**
	 * Clears all messages.
	 */
	fun clear() {
		dataset.clear()
		datasetChanged()
	}
}
