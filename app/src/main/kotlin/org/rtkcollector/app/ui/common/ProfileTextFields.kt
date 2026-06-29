package org.rtkcollector.app.ui.common

import android.graphics.drawable.GradientDrawable
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.text.method.PasswordTransformationMethod
import android.view.KeyEvent as AndroidKeyEvent
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView

@Composable
fun ProfileSingleLineTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    readOnly: Boolean = false,
    isError: Boolean = false,
    secretHidden: Boolean = false,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        label?.let {
            Text(it, style = MaterialTheme.typography.labelLarge)
        }
        ProfileSingleLineEditText(
            value = value,
            onValueChange = onValueChange,
            readOnly = readOnly,
            isError = isError,
            secretHidden = secretHidden,
        )
    }
}

@Composable
private fun ProfileSingleLineEditText(
    value: String,
    onValueChange: (String) -> Unit,
    readOnly: Boolean,
    isError: Boolean,
    secretHidden: Boolean,
) {
    val latestOnValueChange by rememberUpdatedState(onValueChange)
    val focusManager = LocalFocusManager.current
    var savedSelection by remember { mutableStateOf(value.length to value.length) }
    val textColor = MaterialTheme.colorScheme.onSurface.toArgb()
    val backgroundColor = MaterialTheme.colorScheme.surface.toArgb()
    val borderColor = if (isError) {
        MaterialTheme.colorScheme.error.toArgb()
    } else {
        MaterialTheme.colorScheme.outline.toArgb()
    }
    AndroidView(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp),
        factory = { context ->
            EditText(context).apply {
                val density = context.resources.displayMetrics.density
                setPadding(
                    (12 * density).toInt(),
                    (8 * density).toInt(),
                    (12 * density).toInt(),
                    (8 * density).toInt(),
                )
                setSingleLine(true)
                inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
                imeOptions = EditorInfo.IME_ACTION_NEXT
                textSize = 16f
                setText(value)
                fun saveSelection() {
                    savedSelection = selectionStart.coerceAtLeast(0) to selectionEnd.coerceAtLeast(0)
                }
                fun restoreSelection() {
                    val start = savedSelection.first.coerceIn(0, text.length)
                    val end = savedSelection.second.coerceIn(0, text.length)
                    setSelection(start, end)
                }
                setOnKeyListener { _, keyCode, event ->
                    if (keyCode == AndroidKeyEvent.KEYCODE_TAB && event.action == AndroidKeyEvent.ACTION_DOWN) {
                        saveSelection()
                        focusManager.moveFocus(
                            if (event.isShiftPressed) {
                                FocusDirection.Previous
                            } else {
                                FocusDirection.Next
                            },
                        )
                        true
                    } else {
                        false
                    }
                }
                setOnFocusChangeListener { _, hasFocus ->
                    if (hasFocus) {
                        restoreSelection()
                    } else {
                        saveSelection()
                    }
                }
                addTextChangedListener(
                    object : TextWatcher {
                        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
                        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
                        override fun afterTextChanged(s: Editable?) {
                            val next = s?.toString().orEmpty()
                            if (next != value) {
                                latestOnValueChange(next)
                            }
                        }
                    },
                )
            }
        },
        update = { view ->
            view.setTextColor(textColor)
            view.backgroundTintList = null
            view.background = GradientDrawable().apply {
                val density = view.resources.displayMetrics.density
                setColor(backgroundColor)
                cornerRadius = 4 * density
                setStroke((1 * density).toInt().coerceAtLeast(1), borderColor)
            }
            view.transformationMethod = if (secretHidden) PasswordTransformationMethod.getInstance() else null
            view.isEnabled = true
            view.isFocusable = !readOnly
            view.isFocusableInTouchMode = !readOnly
            view.isCursorVisible = !readOnly
            view.setTextIsSelectable(readOnly)
            if (view.text.toString() != value) {
                savedSelection = savedSelection.first.coerceIn(0, value.length) to
                    savedSelection.second.coerceIn(0, value.length)
                view.setText(value)
                view.setSelection(savedSelection.first, savedSelection.second)
            }
        },
    )
}
