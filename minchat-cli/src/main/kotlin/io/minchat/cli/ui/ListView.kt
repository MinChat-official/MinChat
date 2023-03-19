package io.minchat.cli.ui

import androidx.compose.runtime.Composable

/**
 * Displays [elements] by passing entries in it to the [display] function.
 */
@Composable
inline fun <T> ListView(
	elements: Iterable<T>,
	display: @Composable (T) -> Unit
) {
	elements.forEach { display(it) }
}

/**
 * Displays [elements] by passing entries in it to the [display] function.
 * Includes element indices.
 */
@Composable
inline fun <T> ListView(
	elements: Iterable<T>,
	display: @Composable (Int, T) -> Unit
) {
	elements.forEachIndexed { index, value -> display(index, value) }
}
