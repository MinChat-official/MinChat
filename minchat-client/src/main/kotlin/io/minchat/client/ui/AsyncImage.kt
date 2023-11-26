package io.minchat.client.ui

import arc.Core
import arc.func.Cons
import arc.graphics.*
import arc.graphics.g2d.*
import arc.math.Mathf
import arc.scene.style.*
import arc.scene.ui.Image
import arc.util.Time
import io.minchat.client.misc.Log
import kotlinx.coroutines.*
import java.io.File
import kotlin.coroutines.CoroutineContext
import kotlin.math.*

/**
 * An implementation of [Image] that supports asynchronous image download.
 *
 * Currently only supports PNG images.
 */
open class AsyncImage(parentScope: CoroutineScope) : Image(), CoroutineScope {
	override val coroutineContext = SupervisorJob() + CoroutineExceptionHandler(::reportException)

	private var fetcherJob: Job? = null
	private var isFailed = true

	/** An optional image source string for debugging purposes. Must be set after [setImageAsync] has been called. */
	var imageSource: String? = null

	override fun draw() {
		if (isFailed && (drawable as? TextureRegionDrawable)?.name != "error") {
			drawable = Core.atlas.find("error").let(::TextureRegionDrawable)
		} else if (drawable == null) {
			// Draw a loading circle
			Draw.color(Color.white)

			val radius = min(width, height) / 2f
			// Curve start and end degrees
			val start = (Time.time * 5f) % 360f
			val end = start + 80f + (1 + Mathf.sin(Time.time / 40f) / 2f) * 100f

			val segments: Int = (6 * Math.cbrt(radius.toDouble()).toFloat() * (abs(end - start) / 360.0f)).toInt()
			val step = abs(end - start) / segments
			for (i in 0..<segments) {
				val theta = start + i * step
				val ox = radius * Mathf.cosDeg(theta) * 0.9f
				val oy = radius * Mathf.sinDeg(theta) * 0.9f

				Fill.rect(
					x + radius + ox,
					y + radius + oy,
					abs(end - start) * Mathf.degreesToRadians * radius / segments,
					radius * 0.1f,
					theta + 90f
				)
			}

			if (radius < 5f) {
				Log.warn { "AsyncImage's size is too small to draw a loading circle" }
			}
		}

		super.draw()
	}

	fun setFileAsync(provider: suspend () -> File) {
		setDrawable(null)
		launch {
			val file = provider()

			if (Core.assets.isLoaded(file.absolutePath)) {
				val texture = Core.assets.get<Texture>(file.absolutePath)
				setDrawable(TextureRegionDrawable(TextureRegion(texture)))
				imageSource = file.absolutePath
			} else {
				val task = Core.assets.load(file.absolutePath, Texture::class.java)
				val executor = async {
					while (!Core.assets.isLoaded(file.absolutePath)) {
						delay(100L)
					}
					val texture = Core.assets.get<Texture>(file.absolutePath)
					TextureRegionDrawable(TextureRegion(texture))
				}

				task.errored = Cons { e ->
					Log.error(e) { "Failed to load $file for AsyncImage" }
					executor.cancel()
				}

				setDrawableAsync(file.absolutePath) {
					executor.await()
				}
			}
		}
	}

	fun setDrawableAsync(source: String? = null, fetcher: suspend () -> Drawable) {
		drawable = null
		fetcherJob = launch {
			val image = fetcher()
			setDrawable(image)
		}
		imageSource = source
		invalidate()
	}

	override fun setDrawable(drawable: Drawable?) {
		super.setDrawable(drawable)

		fetcherJob?.cancel()
		isFailed = false
		imageSource = null
	}

	override fun setDrawable(region: TextureRegion) {
		super.setDrawable(region)
	}

	private fun reportException(coroutineContext: CoroutineContext, throwable: Throwable) {
		isFailed = true

		if (throwable !is CancellationException) Log.error(throwable) {
			buildString {
				append("An exception has occurred while trying to fetch ")
				append(imageSource ?: "(unknown image)")
				append(" inside AsyncImage")
			}
		}
	}
}

fun CoroutineScope.asyncImage() = AsyncImage(this)
