package com.dragolabs.able.feature.tools.fakegps

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.dragolabs.able.core.designsystem.component.rememberAbleHaptics
import com.dragolabs.able.feature.tools.R
import kotlinx.coroutines.launch
import kotlin.math.hypot
import kotlin.math.roundToInt

/**
 * In-app joystick that moves the fake position. Deflection is reported in screen axes (x right,
 * y down, -1..1); the knob springs back to the centre on release. TalkBack users get custom
 * actions that nudge the position north/east/south/west instead of dragging.
 */
@Composable
internal fun FakeGpsJoystick(
    onMove: (x: Float, y: Float) -> Unit,
    onNudge: (bearingDegrees: Double) -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptics = rememberAbleHaptics()
    val scope = rememberCoroutineScope()
    val currentOnMove by rememberUpdatedState(onMove)
    val knob = remember { Animatable(Offset.Zero, Offset.VectorConverter) }
    val padSize = 136.dp
    val knobSize = 56.dp
    val radiusPx = with(LocalDensity.current) { ((padSize - knobSize) / 2).toPx() }

    val description = stringResource(R.string.fakegps_joystick_cd)
    val north = stringResource(R.string.fakegps_joystick_north)
    val east = stringResource(R.string.fakegps_joystick_east)
    val south = stringResource(R.string.fakegps_joystick_south)
    val west = stringResource(R.string.fakegps_joystick_west)

    Box(
        modifier = modifier
            .size(padSize)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.88f), CircleShape)
            .semantics {
                contentDescription = description
                customActions = listOf(
                    CustomAccessibilityAction(north) { onNudge(0.0); true },
                    CustomAccessibilityAction(east) { onNudge(90.0); true },
                    CustomAccessibilityAction(south) { onNudge(180.0); true },
                    CustomAccessibilityAction(west) { onNudge(270.0); true },
                )
            }
            .pointerInput(radiusPx) {
                detectDragGestures(
                    onDragStart = { haptics.tick() },
                    onDragEnd = {
                        currentOnMove(0f, 0f)
                        scope.launch { knob.animateTo(Offset.Zero, spring()) }
                    },
                    onDragCancel = {
                        currentOnMove(0f, 0f)
                        scope.launch { knob.animateTo(Offset.Zero, spring()) }
                    },
                ) { change, drag ->
                    change.consume()
                    var next = knob.value + drag
                    val distance = hypot(next.x, next.y)
                    if (distance > radiusPx) next *= radiusPx / distance
                    scope.launch { knob.snapTo(next) }
                    currentOnMove(next.x / radiusPx, next.y / radiusPx)
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            modifier = Modifier
                .offset { IntOffset(knob.value.x.roundToInt(), knob.value.y.roundToInt()) }
                .size(knobSize),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primary,
            shadowElevation = 4.dp,
        ) {}
    }
}
