package com.dragolabs.able.widget

import android.content.Context
import androidx.annotation.DrawableRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.action.Action
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.clickable
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxHeight
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.semantics.contentDescription
import androidx.glance.semantics.semantics
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.dragolabs.able.R
import com.dragolabs.able.actions.ActionCatalog
import com.dragolabs.able.actions.ActionRoute
import com.dragolabs.able.actions.ActionRunner
import com.dragolabs.able.actions.ActionState
import com.dragolabs.able.actions.CatalogAction
import com.dragolabs.able.actions.ReportMode

/** One rendered widget slot: a catalog action with its live state and tap behavior. */
data class WidgetSlot(
    val label: String,
    val status: String?,
    @DrawableRes val iconRes: Int,
    val active: Boolean?,
    val available: Boolean,
    val onClick: Action?,
)

private val ActionIdKey = ActionParameters.Key<String>("able_action_id")

/**
 * Resolves how a widget slot behaves: actions that can run in place use a callback; actions
 * that need a screen (missing access, tools) start that screen directly (ADR-023).
 */
suspend fun buildWidgetSlot(context: Context, action: CatalogAction): WidgetSlot {
    val state = runCatching { action.readState(context) }
        .getOrElse { ActionState(active = null, available = false, iconRes = action.iconRes) }
    val click: Action? = when (val route = action.route(context)) {
        ActionRoute.RunInPlace -> actionRunCallback<RunCatalogActionCallback>(actionParametersOf(ActionIdKey to action.id))
        is ActionRoute.OpenScreen -> actionStartActivity(route.intent)
        is ActionRoute.Unsupported -> route.infoIntent?.let { actionStartActivity(it) }
    }
    return WidgetSlot(
        label = context.getString(action.titleRes),
        status = state.status(context),
        iconRes = state.iconRes,
        active = state.active,
        available = state.available,
        onClick = click,
    )
}

/** Runs a catalog action from a widget tap and reports the read-back outcome with a toast. */
class RunCatalogActionCallback : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val action = ActionCatalog.find(parameters[ActionIdKey]) ?: return
        if (action.route(context) == ActionRoute.RunInPlace) {
            ActionRunner.execute(context, action, ReportMode.ALWAYS)
        } else {
            // Access changed since the widget was drawn: a background tap cannot open a screen,
            // so re-render; the next tap opens the screen the action needs.
            ActionRunner.toast(context, context.getString(R.string.action_result_needs_screen, context.getString(action.titleRes)))
            ActionRunner.refreshSurfaces(context)
        }
    }
}

/** Grid of action slots; `null` entries are empty slots that open [emptyClick]. */
@Composable
fun ActionSlotGrid(
    context: Context,
    slots: List<WidgetSlot?>,
    columns: Int,
    emptyClick: Action?,
) {
    val rows = slots.chunked(columns)
    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(GlanceTheme.colors.widgetBackground)
            .cornerRadius(28.dp)
            .padding(6.dp),
    ) {
        rows.forEachIndexed { rowIndex, row ->
            if (rowIndex > 0) Spacer(GlanceModifier.height(6.dp))
            Row(
                modifier = GlanceModifier.fillMaxWidth().defaultWeight(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                row.forEachIndexed { index, slot ->
                    if (index > 0) Spacer(GlanceModifier.width(6.dp))
                    SlotCell(
                        context = context,
                        slot = slot,
                        emptyClick = emptyClick,
                        modifier = GlanceModifier.defaultWeight().fillMaxHeight(),
                    )
                }
            }
        }
    }
}

@Composable
private fun SlotCell(context: Context, slot: WidgetSlot?, emptyClick: Action?, modifier: GlanceModifier) {
    val colors = GlanceTheme.colors
    val container: ColorProvider
    val content: ColorProvider
    when {
        slot == null -> { container = colors.surfaceVariant; content = colors.onSurfaceVariant }
        !slot.available -> { container = colors.surfaceVariant; content = colors.outline }
        slot.active == true -> { container = colors.primary; content = colors.onPrimary }
        else -> { container = colors.secondaryContainer; content = colors.onSecondaryContainer }
    }
    val label = slot?.label ?: context.getString(R.string.tiles_widget_empty_slot)
    val description = listOfNotNull(label, slot?.status).joinToString(", ")
    val click = if (slot == null) emptyClick else slot.onClick
    Column(
        modifier = modifier
            .background(container)
            .cornerRadius(20.dp)
            .padding(horizontal = 4.dp, vertical = 6.dp)
            .semantics { contentDescription = description }
            .let { if (click != null) it.clickable(click) else it },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Image(
            provider = ImageProvider(slot?.iconRes ?: R.drawable.ic_tile_able),
            contentDescription = null,
            modifier = GlanceModifier.size(24.dp),
            colorFilter = ColorFilter.tint(content),
        )
        Spacer(GlanceModifier.height(4.dp))
        Text(
            text = label,
            style = TextStyle(fontSize = 12.sp, color = content, textAlign = TextAlign.Center),
            maxLines = 1,
        )
    }
}
