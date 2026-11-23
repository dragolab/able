package com.dragolabs.able.core.model

import kotlinx.serialization.Serializable

@Serializable
enum class HomeCardType {
    BATTERY_PROFILE,
    QUICK_ACTIONS,
    CAPABILITY_STATUS,
    STORAGE_SUMMARY,
    FAVORITE_SHORTCUTS,
    RECENT_WARNINGS,
    SENSORS_OVERVIEW,
    GEO_POINT_QUICK
}

/**
 * Modular card configuration for the Home dashboard (HOME-001, HOME-002, ADR-016).
 */
@Serializable
data class HomeCardConfig(
    val cardType: HomeCardType,
    val isVisible: Boolean = true,
    val displayOrder: Int
) {
    companion object {
        fun defaultCards(): List<HomeCardConfig> = listOf(
            HomeCardConfig(HomeCardType.BATTERY_PROFILE, isVisible = true, displayOrder = 0),
            HomeCardConfig(HomeCardType.QUICK_ACTIONS, isVisible = true, displayOrder = 1),
            HomeCardConfig(HomeCardType.CAPABILITY_STATUS, isVisible = true, displayOrder = 2),
            HomeCardConfig(HomeCardType.STORAGE_SUMMARY, isVisible = true, displayOrder = 3),
            HomeCardConfig(HomeCardType.FAVORITE_SHORTCUTS, isVisible = true, displayOrder = 4),
            HomeCardConfig(HomeCardType.RECENT_WARNINGS, isVisible = true, displayOrder = 5),
            HomeCardConfig(HomeCardType.SENSORS_OVERVIEW, isVisible = false, displayOrder = 6),
            HomeCardConfig(HomeCardType.GEO_POINT_QUICK, isVisible = false, displayOrder = 7)
        )
    }
}
