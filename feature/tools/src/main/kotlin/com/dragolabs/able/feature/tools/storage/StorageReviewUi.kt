package com.dragolabs.able.feature.tools.storage

import android.content.Context
import android.text.format.Formatter
import android.net.Uri
import android.util.LruCache
import android.util.Size
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.InsertDriveFile
import androidx.compose.material.icons.rounded.AudioFile
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.FolderZip
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.dragolabs.able.core.designsystem.component.AbleFilterChip
import com.dragolabs.able.core.designsystem.component.AbleTextButton
import com.dragolabs.able.core.designsystem.component.focusRing
import com.dragolabs.able.core.designsystem.theme.AbleSpacing
import com.dragolabs.able.feature.tools.R
import com.dragolabs.able.feature.tools.common.SelectableRow
import com.dragolabs.able.feature.tools.common.SelectionSlot
import com.dragolabs.able.feature.tools.common.highlightMatches
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.DateFormat
import java.util.Date
import com.dragolabs.able.core.designsystem.R as DsR

/** One labelled group of filter chips. */
@Composable
private fun FilterGroup(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(AbleSpacing.xs)) {
        Text(title, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(AbleSpacing.sm),
            verticalArrangement = Arrangement.spacedBy(0.dp),
        ) { content() }
    }
}

/** Type, size, age, location and sort filters of the review list (STO-001). */
@Composable
internal fun ReviewFilters(
    state: StorageUiState,
    actions: StorageActions,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val filters = state.filters
    var customOpen by rememberSaveable { mutableStateOf(false) }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(AbleSpacing.md)) {
        FilterGroup(stringResource(R.string.storage_filter_type)) {
            AbleFilterChip(
                selected = filters.kinds.isEmpty(),
                onClick = actions.onClearKinds,
                label = { Text(stringResource(R.string.storage_filter_all_types), maxLines = 1) },
            )
            FileKind.entries.forEach { kind ->
                AbleFilterChip(
                    selected = kind in filters.kinds,
                    onClick = { actions.onToggleKind(kind) },
                    leadingIcon = { Icon(kind.icon(), contentDescription = null, modifier = Modifier.size(18.dp)) },
                    label = { Text(stringResource(kind.labelRes), maxLines = 1) },
                )
            }
        }
        FilterGroup(stringResource(R.string.storage_filter_size)) {
            SizeThreshold.entries.forEach { size ->
                val label = when (size) {
                    SizeThreshold.ANY -> stringResource(R.string.storage_size_any)
                    SizeThreshold.CUSTOM -> if (filters.size == SizeThreshold.CUSTOM) {
                        stringResource(R.string.tools_storage_size_chip, Formatter.formatShortFileSize(context, filters.minSizeBytes))
                    } else {
                        stringResource(R.string.storage_size_custom)
                    }
                    else -> stringResource(R.string.tools_storage_size_chip, Formatter.formatShortFileSize(context, size.bytes))
                }
                AbleFilterChip(
                    selected = filters.size == size,
                    onClick = { if (size == SizeThreshold.CUSTOM) customOpen = true else actions.onSizeChange(size) },
                    label = { Text(label, maxLines = 1) },
                )
            }
        }
        FilterGroup(stringResource(R.string.storage_filter_age)) {
            AgeThreshold.entries.forEach { age ->
                AbleFilterChip(
                    selected = filters.age == age,
                    onClick = { actions.onAgeChange(age) },
                    label = {
                        Text(
                            when (val days = age.days) {
                                null -> stringResource(R.string.storage_age_any)
                                365 -> stringResource(R.string.storage_age_year)
                                else -> stringResource(R.string.storage_age_days, days)
                            },
                            maxLines = 1,
                        )
                    },
                )
            }
        }
        if (state.source == StorageSource.MediaLibrary) {
            FilterGroup(stringResource(R.string.storage_filter_location)) {
                FileLocation.entries.forEach { location ->
                    AbleFilterChip(
                        selected = filters.location == location,
                        onClick = { actions.onLocationChange(location) },
                        label = { Text(stringResource(location.labelRes), maxLines = 1) },
                    )
                }
            }
        }
        FilterGroup(stringResource(R.string.storage_filter_sort)) {
            FileSort.entries.forEach { sort ->
                AbleFilterChip(
                    selected = filters.sort == sort,
                    onClick = { actions.onSortChange(sort) },
                    label = { Text(stringResource(sort.labelRes), maxLines = 1) },
                )
            }
        }
    }
    if (customOpen) {
        CustomSizeDialog(
            initialMb = filters.customSizeMb,
            onConfirm = { mb -> customOpen = false; actions.onCustomSize(mb) },
            onDismiss = { customOpen = false },
        )
    }
}

@Composable
private fun CustomSizeDialog(initialMb: Long, onConfirm: (Long) -> Unit, onDismiss: () -> Unit) {
    var text by rememberSaveable { mutableStateOf(initialMb.toString()) }
    val value = text.trim().toLongOrNull()?.takeIf { it in 0..FileFilters.MAX_CUSTOM_MB }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.storage_custom_size_title)) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { new -> text = new.filter(Char::isDigit).take(7) },
                label = { Text(stringResource(R.string.storage_custom_size_label)) },
                singleLine = true,
                isError = value == null,
                supportingText = if (value == null) {
                    { Text(stringResource(R.string.storage_custom_size_invalid)) }
                } else {
                    null
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            AbleTextButton(onClick = { value?.let(onConfirm) }, enabled = value != null, modifier = Modifier.focusRing()) {
                Text(stringResource(DsR.string.ds_confirm))
            }
        },
        dismissButton = {
            AbleTextButton(onClick = onDismiss, modifier = Modifier.focusRing()) { Text(stringResource(DsR.string.ds_cancel)) }
        },
    )
}

/** "N files · size" plus Select all; selection count and actions live in the selection bar. */
@Composable
internal fun ReviewResultsHeader(state: StorageUiState, onSelectAll: () -> Unit) {
    if (!state.scanned || state.items.isEmpty()) return
    val context = LocalContext.current
    val visible = state.visibleItems
    val total = Formatter.formatShortFileSize(context, visible.sumOf { it.sizeBytes })
    Column(verticalArrangement = Arrangement.spacedBy(AbleSpacing.xs)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                if (visible.size == state.items.size) {
                    stringResource(R.string.tools_storage_results, visible.size, total)
                } else {
                    stringResource(R.string.storage_results_filtered, visible.size, state.items.size, total)
                },
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.weight(1f),
            )
            if (visible.isNotEmpty() && state.selected.size < visible.size) {
                AbleTextButton(onClick = onSelectAll, modifier = Modifier.focusRing()) { Text(stringResource(R.string.tools_storage_select_all)) }
            }
        }
        if (state.truncated) {
            Text(
                stringResource(R.string.storage_results_truncated, state.items.size),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
internal fun ReviewFileRow(
    item: ReviewFile,
    query: String,
    selected: Boolean,
    enabled: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val dateFormat = remember { DateFormat.getDateInstance(DateFormat.MEDIUM) }
    val date = if (item.lastModifiedEpochMs > 0) dateFormat.format(Date(item.lastModifiedEpochMs)) else stringResource(R.string.tools_storage_date_unknown)
    val meta = stringResource(R.string.tools_storage_item_meta, Formatter.formatShortFileSize(context, item.sizeBytes), date)
    SelectableRow(
        selected = selected,
        onToggle = onToggle,
        slot = if (enabled) SelectionSlot.CHECKBOX else SelectionSlot.BUSY,
        shape = MaterialTheme.shapes.medium,
        modifier = modifier,
        leading = { FileThumbnail(item) },
        headline = { Text(highlightMatches(item.displayName, query), maxLines = 2, overflow = TextOverflow.Ellipsis) },
        supporting = {
            Text(meta, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val path = item.relativePath?.trimEnd('/')
            if (!path.isNullOrEmpty()) {
                Text(
                    stringResource(R.string.storage_file_location, stringResource(item.kind.labelRes), path),
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        },
    )
}

/** Cached image/video thumbnails, decoded at the row size off the main thread. */
private object ThumbnailCache {
    private val cache = LruCache<String, ImageBitmap>(120)

    fun cached(uri: String): ImageBitmap? = cache.get(uri)

    suspend fun load(context: Context, uri: String, sizePx: Int): ImageBitmap? {
        cache.get(uri)?.let { return it }
        return withContext(Dispatchers.IO) {
            runCatching {
                context.contentResolver.loadThumbnail(Uri.parse(uri), Size(sizePx, sizePx), null).asImageBitmap()
            }.getOrNull()?.also { cache.put(uri, it) }
        }
    }
}

/** Thumbnail for images and videos when Android provides one; a type icon otherwise. */
@Composable
private fun FileThumbnail(item: ReviewFile, size: Dp = 48.dp) {
    val context = LocalContext.current.applicationContext
    val sizePx = with(LocalDensity.current) { size.roundToPx() }
    val shape = MaterialTheme.shapes.small
    val thumbnail by produceState(initialValue = ThumbnailCache.cached(item.uriString), item.uriString, item.hasThumbnail) {
        if (value == null && item.hasThumbnail) value = ThumbnailCache.load(context, item.uriString, sizePx)
    }
    val bitmap = thumbnail
    if (bitmap != null) {
        Image(
            bitmap = bitmap,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.size(size).clip(shape),
        )
    } else {
        Box(
            modifier = Modifier
                .size(size)
                .clip(shape)
                .background(MaterialTheme.colorScheme.surfaceContainerHighest),
            contentAlignment = Alignment.Center,
        ) {
            Icon(item.kind.icon(), contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private fun FileKind.icon(): ImageVector = when (this) {
    FileKind.IMAGE -> Icons.Rounded.Image
    FileKind.VIDEO -> Icons.Rounded.Movie
    FileKind.AUDIO -> Icons.Rounded.AudioFile
    FileKind.DOCUMENT -> Icons.Rounded.Description
    FileKind.ARCHIVE -> Icons.Rounded.FolderZip
    FileKind.OTHER -> Icons.AutoMirrored.Rounded.InsertDriveFile
}
