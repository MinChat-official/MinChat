package io.minchat.cli.ui

import androidx.compose.runtime.*
import com.jakewharton.mosaic.layout.layout
import com.jakewharton.mosaic.modifier.Modifier
import com.jakewharton.mosaic.ui.*
import kotlinx.coroutines.*
import java.io.Reader

/**
 * Adds a text input component. When [focusCondition] returns true,
 * reads any text from [reader] as raw keyboard input and modifies the current
 * text.
 *
 * Whenever the text is modified, [onChange] is called. If it returns false,
 * the modification is discarded.
 *
 * Whenever the user presses the enter key, [onConfirm] is called and the
 * text field is reset. In this case, [onChange] is not invoked.
 *
 * If the user presses the ESC key or tries to erase more text than there is,
 * [onCancel] is called. If it's not provided, [onConfirm] is called instead.
 */
@Composable
fun TextField(
	reader: Reader,
	length: Int,
	color: Color? = null,
	background: Color? = null,
	clearOnConfirm: Boolean = true,
	focusCondition: () -> Boolean = { true },
	onChange: (String) -> Boolean = { true },
	onCancel: ((String) -> Unit)? = null,
	onConfirm: (String) -> Unit
) {
	val thisScope = currentRecomposeScope
	var currentInput by remember { mutableStateOf("") }


	val text = if (currentInput.length <= length) {
		currentInput.padEnd(length, '_')
	} else {
		"..." + currentInput.takeLast(length - 3)
	}

	Text(text, background = background, style = TextStyle.Underline, modifier = Modifier.layout {
		it.measure() // so dumb.
		layout(length, 1) {}
	})

	LaunchedEffect(Unit) {
		withContext(Dispatchers.IO) {
			var lastChar = '\u0000'
			while (true) {
				if (!focusCondition()) {
					delay(10L)
					continue
				}

				val nextChar = reader.read().toChar()

				when (nextChar) {
					// backspace
					'\b', '\u007F' -> {
						if (currentInput.length > 0) {
							val newInput = currentInput.dropLast(1)
							if (onChange(newInput)) currentInput = newInput
						} else {
							if (onCancel != null) onCancel(currentInput) else onConfirm(currentInput)
						}
					}
					// enter
					'\n', '\r' -> {
						// Windows uses CRLF, so it sends \r\n
						if (lastChar != '\r' || nextChar != '\n') {
							onConfirm(currentInput)
							if (clearOnConfirm) currentInput = ""
						}
					}
					// escape
					'\u001b' -> {
						if (onCancel != null) onCancel(currentInput) else onConfirm(currentInput)
						if (clearOnConfirm) currentInput = ""
					}
					// normal text: everything except control chars.
					else -> {
						if (nextChar.code >= 0x20) {
							val newInput = currentInput + nextChar
							if (onChange(newInput)) currentInput = newInput
						}
					}
				}

				lastChar = nextChar
				thisScope.invalidate()
			}
		}
	}
}
