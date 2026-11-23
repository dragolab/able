package com.dragolabs.able.search

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.clearText
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.SearchOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import com.dragolabs.able.core.designsystem.component.LocalAbleBottomBarPadding
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.SearchBarValue
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberSearchBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.dragolabs.able.R
import com.dragolabs.able.core.designsystem.component.EmptyState
import com.dragolabs.able.core.designsystem.component.ExpressiveIcon
import com.dragolabs.able.core.designsystem.component.SectionHeader
import com.dragolabs.able.core.designsystem.component.focusRing
import com.dragolabs.able.core.designsystem.component.listGroupItemShape
import com.dragolabs.able.core.designsystem.component.rememberAbleHaptics
import com.dragolabs.able.core.designsystem.theme.AbleSpacing
import com.dragolabs.able.core.designsystem.R as DsR

/**
 * App-wide options search (global search for settings, tools and screens).
 *
 * The keyboard opens on entry because the user explicitly asked to search. Results are grouped by
 * [SearchSection]; tapping one calls [onOpen] with a shell deep link accepted by
 * `routeFromDeepLink` ("battery", "settings", "tool/torch", ...). With [includeAnchors] the link
 * carries an in-screen anchor fragment (`battery#monitor.alerts`, see [SearchDeepLink]); enable it
 * only once navigation strips or handles the fragment.
 */
@Composable
fun GlobalSearchScreen(
    onBack: () -> Unit,
    onOpen: (deepLink: String) -> Unit,
    modifier: Modifier = Modifier,
    includeAnchors: Boolean = false,
) {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val haptics = rememberAbleHaptics()
    val keyboard = LocalSoftwareKeyboardController.current
    val store = remember(context) { RecentSearchStore(context) }
    var recents by remember { mutableStateOf(store.load()) }

    val textState = rememberTextFieldState()
    val query by remember { derivedStateOf { textState.text.toString() } }

    // Re-resolve localized text when the configuration (language) changes.
    val res = LocalResources.current
    val resolved = remember(configuration, res) {
        SearchIndex.entries.map { entry ->
            val title = res.getString(entry.titleRes)
            val sectionLabel = res.getString(entry.section.labelRes)
            val breadcrumb = entry.areaRes
                ?.let { res.getString(R.string.search_breadcrumb, sectionLabel, res.getString(it)) }
                ?: sectionLabel
            ResolvedEntry(
                entry = entry,
                title = title,
                breadcrumb = breadcrumb,
                fields = SearchFields(
                    title = title,
                    keywords = entry.keywordsRes?.let(res::getString).orEmpty(),
                    context = breadcrumb,
                ),
            )
        }
    }
    val groups = remember(query, resolved) { groupResults(SearchMatcher.rank(query, resolved) { it.fields }) }
    val popular = remember(resolved) { resolved.filter { it.entry.popular } }

    fun open(item: ResolvedEntry) {
        haptics.click()
        if (query.isNotBlank()) recents = store.add(query)
        keyboard?.hide()
        onOpen(if (includeAnchors) item.entry.deepLinkWithAnchor else item.entry.deepLink)
    }

    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
        keyboard?.show()
    }
    val listState = rememberLazyListState()
    LaunchedEffect(listState.isScrollInProgress) {
        if (listState.isScrollInProgress) keyboard?.hide()
    }

    val paneTitle = stringResource(R.string.search_title)
    Scaffold(
        modifier = modifier.semantics { this.paneTitle = paneTitle },
        // The persistent floating bar stays visible here too (SHELL-001): keep results above it.
        contentWindowInsets = WindowInsets(bottom = LocalAbleBottomBarPadding.current),
        topBar = {
            SearchTopBar(
                textState = textState,
                focusRequester = focusRequester,
                onBack = {
                    haptics.click()
                    keyboard?.hide()
                    onBack()
                },
                onSearch = { submitted ->
                    if (submitted.isNotBlank()) recents = store.add(submitted)
                    keyboard?.hide()
                },
                onClear = {
                    haptics.tick()
                    textState.clearText()
                    focusRequester.requestFocus()
                },
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.TopCenter) {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .widthIn(max = AbleSpacing.maxContentWidth)
                    .fillMaxSize()
                    .imePadding(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    start = AbleSpacing.screenGutter,
                    end = AbleSpacing.screenGutter,
                    top = AbleSpacing.sm,
                    bottom = AbleSpacing.xxl,
                ),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                when {
                    query.isBlank() -> idleContent(
                        recents = recents,
                        popular = popular,
                        onRecent = { recent ->
                            haptics.tick()
                            textState.setTextAndPlaceCursorAtEnd(recent)
                            focusRequester.requestFocus()
                        },
                        onClearRecents = {
                            haptics.confirm()
                            store.clear()
                            recents = emptyList()
                        },
                        onOpen = ::open,
                    )
                    groups.isEmpty() -> item(key = "empty") {
                        EmptyState(
                            title = stringResource(R.string.search_empty_title, query.trim()),
                            message = stringResource(R.string.search_empty_message),
                            icon = Icons.Rounded.SearchOff,
                            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                        )
                    }
                    else -> resultsContent(groups = groups, onOpen = ::open)
                }
            }
        }
    }
}

@Immutable
private data class ResolvedEntry(
    val entry: SearchEntry,
    val title: String,
    val breadcrumb: String,
    val fields: SearchFields,
)

private data class ResultGroup(
    val section: SearchSection,
    val results: List<RankedResult<ResolvedEntry>>,
)

/** Groups ranked results by section; sections are ordered by their best match (ties: index order). */
private fun groupResults(ranked: List<RankedResult<ResolvedEntry>>): List<ResultGroup> =
    ranked.groupBy { it.item.entry.section }
        .map { (section, results) -> ResultGroup(section, results) }
        .sortedWith(
            compareByDescending<ResultGroup> { it.results.first().match.score }.thenBy { it.section.ordinal },
        )

@Composable
private fun SearchTopBar(
    textState: TextFieldState,
    focusRequester: FocusRequester,
    onBack: () -> Unit,
    onSearch: (String) -> Unit,
    onClear: () -> Unit,
) {
    // Starts expanded: this screen *is* the expanded search, so the field accepts input at once.
    val searchBarState = rememberSearchBarState(initialValue = SearchBarValue.Expanded)
    Surface(color = MaterialTheme.colorScheme.surface) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))
                .padding(horizontal = AbleSpacing.screenGutter, vertical = AbleSpacing.sm),
            contentAlignment = Alignment.TopCenter,
        ) {
            Surface(
                shape = SearchBarDefaults.inputFieldShape,
                color = SearchBarDefaults.colors().containerColor,
                modifier = Modifier
                    .widthIn(max = AbleSpacing.maxContentWidth)
                    .fillMaxWidth(),
            ) {
                SearchBarDefaults.InputField(
                    textState,
                    searchBarState,
                    onSearch,
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester),
                    placeholder = {
                        Text(stringResource(R.string.search_placeholder), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    },
                    leadingIcon = {
                        IconButton(onClick = onBack) {
                            Icon(
                                Icons.AutoMirrored.Rounded.ArrowBack,
                                contentDescription = stringResource(DsR.string.ds_navigate_back),
                            )
                        }
                    },
                    trailingIcon = {
                        if (textState.text.isNotEmpty()) {
                            IconButton(onClick = onClear) {
                                Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.search_clear_query))
                            }
                        }
                    },
                )
            }
        }
    }
}

private fun LazyListScope.idleContent(
    recents: List<String>,
    popular: List<ResolvedEntry>,
    onRecent: (String) -> Unit,
    onClearRecents: () -> Unit,
    onOpen: (ResolvedEntry) -> Unit,
) {
    if (recents.isNotEmpty()) {
        item(key = "recent_header") {
            val clearCd = stringResource(R.string.search_recent_clear_cd)
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = AbleSpacing.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SectionHeader(
                    text = stringResource(R.string.search_recent_title),
                    modifier = Modifier.weight(1f).semantics { heading() },
                )
                TextButton(
                    onClick = onClearRecents,
                    modifier = Modifier.semantics { contentDescription = clearCd },
                ) {
                    Text(stringResource(R.string.search_recent_clear))
                }
            }
        }
        recents.forEachIndexed { index, recent ->
            item(key = "recent_$index") {
                val shape = listGroupItemShape(index, recents.size)
                val cd = stringResource(R.string.search_recent_item_cd, recent)
                ListItem(
                    onClick = { onRecent(recent) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(shape)
                        .focusRing(shape)
                        .semantics { contentDescription = cd },
                    colors = rowColors(),
                    leadingContent = { Icon(Icons.Rounded.History, contentDescription = null) },
                ) {
                    Text(recent, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
        item(key = "recent_note") {
            Text(
                text = stringResource(R.string.search_privacy_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = AbleSpacing.lg, vertical = AbleSpacing.xs),
            )
        }
    }
    if (popular.isNotEmpty()) {
        item(key = "popular_header") {
            SectionHeader(
                text = stringResource(R.string.search_suggestions_title),
                modifier = Modifier
                    .padding(top = AbleSpacing.lg, bottom = AbleSpacing.xs)
                    .semantics { heading() },
            )
        }
        popular.forEachIndexed { index, entry ->
            item(key = "popular_${entry.entry.id}") {
                ResultRow(
                    item = entry,
                    title = AnnotatedString(entry.title),
                    shape = listGroupItemShape(index, popular.size),
                    onClick = { onOpen(entry) },
                )
            }
        }
    }
}

private fun LazyListScope.resultsContent(
    groups: List<ResultGroup>,
    onOpen: (ResolvedEntry) -> Unit,
) {
    item(key = "count") {
        val total = groups.sumOf { it.results.size }
        Text(
            text = pluralStringResource(R.plurals.search_results_count, total, total),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .padding(horizontal = AbleSpacing.xs, vertical = AbleSpacing.xs)
                .semantics { liveRegion = LiveRegionMode.Polite },
        )
    }
    groups.forEach { group ->
        item(key = "section_${group.section.name}") {
            SectionHeader(
                text = stringResource(group.section.labelRes),
                modifier = Modifier
                    .padding(top = AbleSpacing.md, bottom = AbleSpacing.xs)
                    .semantics { heading() },
            )
        }
        group.results.forEachIndexed { index, ranked ->
            item(key = "result_${ranked.item.entry.id}") {
                val highlight = SpanStyle(
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                )
                val title = remember(ranked, highlight) {
                    highlighted(ranked.item.title, ranked.match.titleHighlights, highlight)
                }
                ResultRow(
                    item = ranked.item,
                    title = title,
                    shape = listGroupItemShape(index, group.results.size),
                    onClick = { onOpen(ranked.item) },
                )
            }
        }
    }
}

private fun highlighted(text: String, ranges: List<IntRange>, style: SpanStyle): AnnotatedString =
    buildAnnotatedString {
        append(text)
        ranges.forEach { range ->
            val start = range.first.coerceIn(0, text.length)
            val end = (range.last + 1).coerceIn(start, text.length)
            if (end > start) addStyle(style, start, end)
        }
    }

@Composable
private fun ResultRow(
    item: ResolvedEntry,
    title: AnnotatedString,
    shape: Shape,
    onClick: () -> Unit,
) {
    ListItem(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .focusRing(shape),
        colors = rowColors(),
        supportingContent = { Text(item.breadcrumb, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        leadingContent = {
            ExpressiveIcon(
                icon = item.entry.icon,
                shape = MaterialShapes.Cookie9Sided,
                size = 40.dp,
                iconSize = 22.dp,
            )
        },
        trailingContent = { Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null) },
    ) {
        Text(title, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun rowColors() = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
