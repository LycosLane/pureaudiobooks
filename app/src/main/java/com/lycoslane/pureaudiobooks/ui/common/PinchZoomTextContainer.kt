package com.lycoslane.pureaudiobooks.ui.common

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableIntState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.math.roundToInt

val LocalSuspendGlobalTextZoom = staticCompositionLocalOf<MutableIntState?> { null }

fun Modifier.pinchToZoomTextScale(
    textScale: Float,
    onTextScaleChange: (Float) -> Unit,
    onPreviewScale: ((Float?) -> Unit)? = null,
    enabled: Boolean = true
): Modifier = composed {
    val currentScale by rememberUpdatedState(textScale)
    val currentOnCommit by rememberUpdatedState(onTextScaleChange)
    val currentOnPreview by rememberUpdatedState(onPreviewScale)
    val currentEnabled by rememberUpdatedState(enabled)

    pointerInput(Unit) {
        awaitEachGesture {
            awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            if (!currentEnabled) return@awaitEachGesture
            var didZoom = false
            var activeScale = currentScale
            do {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                val pressed = event.changes.filter { it.pressed }
                if (pressed.size >= 2) {
                    val zoom = event.calculateZoom()
                    if (abs(zoom - 1f) > 0.002f) {
                        activeScale = (activeScale * zoom).coerceIn(0.80f, 1.80f)
                        didZoom = true
                        currentOnPreview?.invoke(activeScale)
                    }
                }
                if (didZoom) {
                    event.changes.forEach { it.consume() }
                }
            } while (event.changes.any { it.pressed })

            if (didZoom) {
                currentOnCommit(activeScale)
                currentOnPreview?.invoke(null)
            }
        }
    }
}

@Composable
fun GlobalTextZoomHost(
    textScale: Float,
    onTextScaleChange: (Float) -> Unit,
    content: @Composable () -> Unit
) {
    val baseDensity = LocalDensity.current
    val suspendCount = remember { mutableIntStateOf(0) }
    var previewScale by remember { mutableStateOf<Float?>(null) }
    var showBadge by remember { mutableStateOf(false) }

    val effectiveScale = (previewScale ?: textScale).coerceIn(0.80f, 1.80f)

    LaunchedEffect(previewScale) {
        if (previewScale != null) {
            showBadge = true
        } else if (showBadge) {
            delay(1_000L)
            showBadge = false
        }
    }

    CompositionLocalProvider(
        LocalSuspendGlobalTextZoom provides suspendCount,
        LocalDensity provides Density(
            density = baseDensity.density,
            fontScale = baseDensity.fontScale * effectiveScale
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pinchToZoomTextScale(
                    textScale = textScale,
                    onTextScaleChange = onTextScaleChange,
                    onPreviewScale = { previewScale = it },
                    enabled = suspendCount.intValue == 0
                )
        ) {
            content()

            AnimatedVisibility(
                visible = showBadge,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
                    .padding(top = 12.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.inverseSurface,
                    shadowElevation = 4.dp
                ) {
                    Text(
                        text = "${(effectiveScale * 100f).roundToInt()}%",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.inverseOnSurface,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                    )
                }
            }
        }
    }
}
