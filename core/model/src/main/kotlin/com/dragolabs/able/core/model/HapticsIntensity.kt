package com.dragolabs.able.core.model

import kotlinx.serialization.Serializable

/**
 * Strength of Able's haptic feedback (UX-003). [OFF] is equivalent to the legacy
 * "haptic feedback" switch turned off; the system touch-feedback setting always applies on top.
 */
@Serializable
enum class HapticsIntensity {
    OFF,
    LIGHT,
    NORMAL,
    STRONG,
}
