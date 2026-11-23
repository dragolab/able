package com.dragolabs.able.tile

import android.graphics.drawable.Icon
import android.service.quicksettings.Tile
import com.dragolabs.able.R
import com.dragolabs.able.actions.ActionCatalog
import com.dragolabs.able.actions.ActionIds
import com.dragolabs.able.actions.CustomTileSlots
import com.dragolabs.able.actions.TileBinding
import com.dragolabs.able.actions.ui.TilesWidgetsActivity

/** Built-in tile permanently bound to one catalog action; keeps its manifest label. */
abstract class FixedActionTileService(private val actionId: String) : BaseTileService() {
    override suspend fun binding(): TileBinding? = ActionCatalog.find(actionId)?.let { TileBinding(it) }
}

/** Torch (TORCH-002). */
class TorchTileService : FixedActionTileService(ActionIds.TORCH)

/** NFC (SYS-001). */
class NfcTileService : FixedActionTileService(ActionIds.NFC)

/** Auto brightness (SYS-002). */
class AutoBrightnessTileService : FixedActionTileService(ActionIds.AUTO_BRIGHTNESS)

/** USB debugging (SYS-003). */
class UsbAdbTileService : FixedActionTileService(ActionIds.USB_DEBUGGING)

/** Wireless debugging (SYS-003). */
class WirelessAdbTileService : FixedActionTileService(ActionIds.WIRELESS_DEBUGGING)

/** Private DNS (SYS-004). */
class PrivateDnsTileService : FixedActionTileService(ActionIds.PRIVATE_DNS)

/** Screen lock (LOCK-002). */
class ScreenLockTileService : FixedActionTileService(ActionIds.SCREEN_LOCK)

/**
 * Customizable tile slot (CUST-001): shows the label, icon and live state of the action the user
 * assigned in "Tiles & widgets". An unassigned slot shows the Able mark and opens the setup.
 */
abstract class CustomSlotTileService(private val slot: Int) : BaseTileService() {
    override val labelFollowsAction: Boolean = true

    override suspend fun binding(): TileBinding? = CustomTileSlots.binding(this, slot)

    override fun renderUnbound(tile: Tile) {
        tile.label = getString(R.string.tiles_slot_label, slot)
        tile.icon = Icon.createWithResource(this, R.drawable.ic_tile_able)
        tile.state = Tile.STATE_INACTIVE
        tile.subtitle = getString(R.string.tiles_slot_setup)
        tile.updateTile()
    }

    override fun onUnboundClick() {
        startActivityAndCollapseCompat(TilesWidgetsActivity.intent(this, slot), requestCode = slot)
    }
}

class CustomTile1Service : CustomSlotTileService(1)

class CustomTile2Service : CustomSlotTileService(2)

class CustomTile3Service : CustomSlotTileService(3)

class CustomTile4Service : CustomSlotTileService(4)
