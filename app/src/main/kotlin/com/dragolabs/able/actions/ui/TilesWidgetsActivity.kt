package com.dragolabs.able.actions.ui

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.service.quicksettings.TileService
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dragolabs.able.actions.CustomTileSlots
import com.dragolabs.able.core.data.ableContainer
import com.dragolabs.able.core.designsystem.theme.AbleTheme
import com.dragolabs.able.core.model.ThemeMode

/**
 * Standalone host for "Tiles & widgets", opened from an unassigned custom tile and from a
 * tile long-press (`QS_TILE_PREFERENCES`). In-app navigation uses [TilesAndWidgetsScreen].
 */
class TilesWidgetsActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val slot = intent.getIntExtra(EXTRA_SLOT, 0).takeIf { it in 1..CustomTileSlots.COUNT }
            ?: slotFromTileComponent(intent)
        setContent {
            AbleActivityTheme {
                TilesAndWidgetsContent(onBack = ::finish, initialSlot = slot)
            }
        }
    }

    private fun slotFromTileComponent(intent: Intent): Int? {
        if (intent.action != TileService.ACTION_QS_TILE_PREFERENCES) return null
        val component = intent.getParcelableExtra(Intent.EXTRA_COMPONENT_NAME, ComponentName::class.java) ?: return null
        val index = CustomTileSlots.services.indexOfFirst { it.name == component.className }
        return if (index >= 0) index + 1 else null
    }

    companion object {
        private const val EXTRA_SLOT = "com.dragolabs.able.extra.TILE_SLOT"

        /** Opens the screen; with a [slot], the action picker for that slot opens directly. */
        fun intent(context: Context, slot: Int? = null): Intent =
            Intent(context, TilesWidgetsActivity::class.java).apply {
                if (slot != null) putExtra(EXTRA_SLOT, slot)
            }
    }
}

/** Able theme for standalone activities, following the user's theme, color and haptics preferences. */
@Composable
fun AbleActivityTheme(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val flow = remember(context) { context.ableContainer.preferences.userPreferencesFlow }
    val prefs by flow.collectAsStateWithLifecycle(initialValue = null)
    val loaded = prefs
    AbleTheme(
        themeMode = loaded?.themeMode ?: ThemeMode.SYSTEM,
        dynamicColor = loaded?.useDynamicColor ?: true,
        hapticsEnabled = loaded?.hapticsEnabled ?: true,
    ) {
        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
            if (loaded != null) content()
        }
    }
}
