package com.dragolabs.able.actions

import android.content.ComponentName
import android.content.Context
import android.service.quicksettings.TileService
import com.dragolabs.able.core.data.ableContainer
import com.dragolabs.able.core.database.entity.CustomTileMappingEntity
import com.dragolabs.able.core.model.CustomTileMapping
import com.dragolabs.able.tile.CustomTile1Service
import com.dragolabs.able.tile.CustomTile2Service
import com.dragolabs.able.tile.CustomTile3Service
import com.dragolabs.able.tile.CustomTile4Service
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Action currently bound to a custom tile slot, with the user's optional label. */
data class TileBinding(val action: CatalogAction, val customLabel: String? = null)

/**
 * Custom Quick Settings tile slots (CUST-001). Mappings live in `custom_tile_mappings`; a slot
 * is cleared by storing an empty action id, because the DAO has no delete query.
 */
object CustomTileSlots {
    const val COUNT = 4

    val slots: List<Int> = (1..COUNT).toList()

    val services: List<Class<out TileService>> = listOf(
        CustomTile1Service::class.java,
        CustomTile2Service::class.java,
        CustomTile3Service::class.java,
        CustomTile4Service::class.java,
    )

    fun component(context: Context, slot: Int): ComponentName = ComponentName(context, services[slot - 1])

    suspend fun binding(context: Context, slot: Int): TileBinding? {
        val mapping = context.ableContainer.database.customTileDao().getTileBySlot(slot) ?: return null
        val action = ActionCatalog.find(mapping.actionId) ?: return null
        return TileBinding(action, mapping.customLabel?.takeIf { it.isNotBlank() })
    }

    /** Slot -> mapping for every assigned slot. */
    fun observe(context: Context): Flow<Map<Int, CustomTileMapping>> =
        context.ableContainer.database.customTileDao().getAllTiles().map { list ->
            list.map { it.toDomain() }.filter { it.actionId.isNotBlank() }.associateBy { it.slotIndex }
        }

    suspend fun assign(context: Context, slot: Int, actionId: String?) {
        require(slot in 1..COUNT) { "Unknown tile slot $slot" }
        context.ableContainer.database.customTileDao().insertOrUpdateTile(
            CustomTileMappingEntity(slotIndex = slot, actionId = actionId.orEmpty(), customLabel = null, customIconKey = null),
        )
        TileService.requestListeningState(context.applicationContext, component(context, slot))
    }
}
