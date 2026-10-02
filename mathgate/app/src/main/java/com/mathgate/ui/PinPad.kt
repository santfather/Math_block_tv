package com.mathgate.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Button
import androidx.tv.material3.Text
import com.mathgate.R
import com.mathgate.core.PinPolicy
import com.mathgate.ui.theme.GateAccent
import com.mathgate.ui.theme.GateOnBackground
import com.mathgate.ui.theme.GateSurface

private val DigitRows = listOf(listOf(1, 2, 3), listOf(4, 5, 6), listOf(7, 8, 9))
private const val DefaultFocusDigit = 5

/**
 * PIN entry pad for the parental screens (phase 7): masked slots, D-pad navigation and the
 * numeric keys of the remote. The screen owning the pad decides what to do with the digits.
 */
@Composable
internal fun PinPad(
    pin: String,
    onDigit: (Int) -> Unit,
    onErase: () -> Unit,
    modifier: Modifier = Modifier,
    maxLength: Int = PinPolicy.MAX_LENGTH,
) {
    val defaultFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { defaultFocus.requestFocus() }

    Column(
        modifier = modifier.onPreviewKeyEvent { event ->
            if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
            when {
                event.key == Key.Backspace || event.key == Key.Delete -> {
                    onErase()
                    true
                }

                else -> digitForKey(event.key)?.let { digit ->
                    onDigit(digit)
                    true
                } ?: false
            }
        },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        PinSlots(length = pin.length, maxLength = maxLength)
        PinKeypad(defaultFocus = defaultFocus, onDigit = onDigit, onErase = onErase)
    }
}

@Composable
private fun PinSlots(length: Int, maxLength: Int) {
    Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        repeat(maxLength) { index ->
            val filled = index < length
            val shape = RoundedCornerShape(12.dp)
            Box(
                modifier = Modifier
                    .size(width = 56.dp, height = 72.dp)
                    .background(GateSurface, shape)
                    .border(3.dp, if (filled) GateAccent else GateOnBackground.copy(alpha = 0.25f), shape),
                contentAlignment = Alignment.Center,
            ) {
                if (filled) {
                    Text(text = "•", color = GateOnBackground, fontSize = 40.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun PinKeypad(
    defaultFocus: FocusRequester,
    onDigit: (Int) -> Unit,
    onErase: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        DigitRows.forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                row.forEach { digit ->
                    PinButton(
                        label = digit.toString(),
                        modifier = if (digit == DefaultFocusDigit) {
                            Modifier.focusRequester(defaultFocus)
                        } else {
                            Modifier
                        },
                        onClick = { onDigit(digit) },
                    )
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            PinButton(label = "0", onClick = { onDigit(0) })
            PinButton(
                label = stringResource(R.string.pin_erase),
                labelSize = 22.sp,
                onClick = onErase,
            )
        }
    }
}

@Composable
private fun PinButton(
    label: String,
    modifier: Modifier = Modifier,
    labelSize: androidx.compose.ui.unit.TextUnit = 30.sp,
    onClick: () -> Unit,
) {
    Button(
        onClick = onClick,
        modifier = modifier.size(width = 120.dp, height = 56.dp),
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
    ) {
        Text(text = label, fontSize = labelSize, fontWeight = FontWeight.Bold)
    }
}
