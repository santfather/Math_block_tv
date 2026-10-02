package com.mathgate.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import com.mathgate.R
import com.mathgate.ui.theme.GateAccent
import com.mathgate.ui.theme.GateOnBackground
import com.mathgate.ui.theme.GateSurface

/**
 * Small D-pad friendly controls for the parent screen (phase 7).
 *
 * Built on plain Compose foundation instead of the TV material components so the selected
 * and focused states are explicit and readable from a distance.
 */
@Composable
internal fun SelectableChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(12.dp)
    Box(
        modifier = modifier
            .widthIn(min = 96.dp)
            .onFocusChanged { focused = it.isFocused }
            .background(if (selected) GateAccent else GateSurface, shape)
            .border(
                width = if (focused) 3.dp else 2.dp,
                color = if (focused) GateOnBackground else Color.Transparent,
                shape = shape,
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = GateOnBackground,
            fontSize = 22.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
        )
    }
}

/** A labelled on/off switch rendered as a chip (phase 7). */
@Composable
internal fun ToggleChip(
    label: String,
    checked: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SelectableChip(
            label = stringResource(if (checked) R.string.toggle_on else R.string.toggle_off),
            selected = checked,
            onClick = onToggle,
        )
        Text(text = label, color = GateOnBackground, fontSize = 22.sp)
    }
}
