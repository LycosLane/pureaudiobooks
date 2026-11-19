package com.lycoslane.pureaudiobooks.ui.common

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.isSpecified
import com.lycoslane.pureaudiobooks.R
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Displays readable text that can be scaled directly via a two-finger pinch gesture (0.8x..1.8x)
 * without altering surrounding layout geometry, and optionally opens a reader view on tap.
 */
@Composable
fun PinchZoomText(
    text: String,
    textScale: Float,
    onTextScaleChange: (Float) -> Unit,
    style: TextStyle,
    color: Color = Color.Unspecified,
    onClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val currentScale by rememberUpdatedState(textScale)
    val currentOnScale by rememberUpdatedState(onTextScaleChange)
    val currentOnClick by rememberUpdatedState(onClick)

    var localScale by remember { mutableFloatStateOf(textScale.coerceIn(0.8f, 1.8f)) }
    LaunchedEffect(textScale) {
        localScale = textScale.coerceIn(0.8f, 1.8f)
    }

    val scaledStyle = if (style.fontSize.isSpecified) {
        style.copy(
            fontSize = style.fontSize * localScale,
            lineHeight = if (style.lineHeight.isSpecified) {
                style.lineHeight * localScale
            } else {
                style.fontSize * localScale * 1.4f
            }
        )
    } else {
        style
    }

    Text(
        text = text,
        style = scaledStyle,
        color = color,
        modifier = modifier
            .semantics {
                val click = currentOnClick
                if (click != null) {
                    role = Role.Button
                    onClick {
                        click()
                        true
                    }
                }
            }
            .pointerInput(Unit) {
                val touchSlop = viewConfiguration.touchSlop
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                    val downPos = down.position
                    var maxPointers = 1
                    var moved = false
                    var didZoom = false
                    var activeScale = currentScale.coerceIn(0.8f, 1.8f)
                    do {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        val pressed = event.changes.filter { it.pressed }
                        if (pressed.size > maxPointers) {
                            maxPointers = pressed.size
                        }
                        if (pressed.size == 1 && !moved) {
                            val delta = pressed[0].position - downPos
                            if (delta.getDistance() > touchSlop) {
                                moved = true
                            }
                        }
                        if (pressed.size >= 2) {
                            val zoom = event.calculateZoom()
                            if (abs(zoom - 1f) > 0.002f) {
                                activeScale = (activeScale * zoom).coerceIn(0.8f, 1.8f)
                                localScale = activeScale
                                didZoom = true
                            }
                        }
                        if (didZoom) {
                            event.changes.forEach { it.consume() }
                        }
                    } while (event.changes.any { it.pressed })

                    if (didZoom) {
                        currentOnScale(activeScale)
                    } else if (maxPointers == 1 && !moved) {
                        currentOnClick?.invoke()
                    }
                }
            }
    )
}

/**
 * Fullscreen in-window reader overlay for audiobook descriptions with live pinch-to-zoom and reset.
 */
@Composable
fun TextReaderOverlay(
    title: String,
    subtitle: String?,
    text: String,
    textScale: Float,
    onTextScaleChange: (Float) -> Unit,
    onDismiss: () -> Unit
) {
    BackHandler(onBack = onDismiss)

    Surface(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        awaitPointerEvent().changes.forEach { it.consume() }
                    }
                }
            },
        color = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 8.dp)
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (!subtitle.isNullOrBlank()) {
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                TextButton(onClick = { onTextScaleChange(1.0f) }) {
                    Text("${(textScale * 100f).roundToInt()}%")
                }
                IconButton(onClick = onDismiss) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = stringResource(R.string.close)
                    )
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 14.dp)
            ) {
                PinchZoomText(
                    text = text,
                    textScale = textScale,
                    onTextScaleChange = onTextScaleChange,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(32.dp))
            }
        }
    }
}
