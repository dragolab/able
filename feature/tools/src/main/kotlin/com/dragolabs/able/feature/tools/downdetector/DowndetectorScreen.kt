package com.dragolabs.able.feature.tools.downdetector

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.StarOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.dragolabs.able.core.data.ableViewModelFactory
import com.dragolabs.able.core.designsystem.component.AbleButton
import com.dragolabs.able.core.designsystem.component.AbleButtonFlow
import com.dragolabs.able.core.designsystem.component.AbleCard
import com.dragolabs.able.core.designsystem.component.AbleCardStyle
import com.dragolabs.able.core.designsystem.component.AbleDetailScaffold
import com.dragolabs.able.core.designsystem.component.AbleFilledTonalButton
import com.dragolabs.able.core.designsystem.component.AbleFilterChip
import com.dragolabs.able.core.designsystem.component.AbleHaptic
import com.dragolabs.able.core.designsystem.component.AbleIconButton
import com.dragolabs.able.core.designsystem.component.AbleTextButton
import com.dragolabs.able.core.designsystem.component.ActionRow
import com.dragolabs.able.core.designsystem.component.HelpButton
import com.dragolabs.able.core.designsystem.component.ListGroup
import com.dragolabs.able.core.designsystem.component.RadioRow
import com.dragolabs.able.core.designsystem.component.ReadableWidth
import com.dragolabs.able.core.designsystem.component.SectionHeader
import com.dragolabs.able.core.designsystem.component.listGroupItemShape
import com.dragolabs.able.core.designsystem.component.screenContentPadding
import com.dragolabs.able.core.designsystem.theme.AbleSpacing
import com.dragolabs.able.feature.tools.R
import com.dragolabs.able.feature.tools.common.startFirstAvailable
import java.util.Locale
import kotlinx.coroutines.launch

@Composable
fun DowndetectorRoute(onBack: () -> Unit, modifier: Modifier = Modifier) {
    val viewModel: DowndetectorViewModel = viewModel(
        factory = ableViewModelFactory { container -> DowndetectorViewModel(container.appContext) },
    )
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    DowndetectorScreen(
        state = state,
        onBack = onBack,
        onSelectCountry = viewModel::selectCountry,
        onQueryChange = viewModel::setQuery,
        onAddFavourite = viewModel::addFavourite,
        onRemoveFavourite = viewModel::removeFavourite,
        modifier = modifier,
    )
}

@Composable
fun DowndetectorScreen(
    state: DowndetectorUiState,
    onBack: () -> Unit,
    onSelectCountry: (String) -> Unit,
    onQueryChange: (String) -> Unit,
    onAddFavourite: (String) -> Unit,
    onRemoveFavourite: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val noBrowser = stringResource(R.string.downdetector_no_browser)
    val keyboard = LocalSoftwareKeyboardController.current
    var pickCountry by rememberSaveable { mutableStateOf(false) }

    // Able never fetches Downdetector itself: it only hands the URL to the user's browser.
    val open: (String) -> Unit = { url ->
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).addCategory(Intent.CATEGORY_BROWSABLE)
        if (!context.startFirstAvailable(intent)) scope.launch { snackbarHostState.showSnackbar(noBrowser) }
    }
    val openQuery = {
        state.site.statusUrl(state.query)?.let { keyboard?.hide(); open(it) }
        Unit
    }
    val canOpenQuery = DowndetectorSites.slugify(state.query).isNotEmpty()

    AbleDetailScaffold(
        title = stringResource(R.string.tools_downdetector_title),
        onBack = onBack,
        snackbarHostState = snackbarHostState,
        modifier = modifier,
        actions = {
            HelpButton(
                title = stringResource(R.string.downdetector_help_title),
                text = stringResource(R.string.downdetector_help_text),
            )
        },
    ) { padding ->
        ReadableWidth {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(screenContentPadding(padding)),
                verticalArrangement = Arrangement.spacedBy(AbleSpacing.lg),
            ) {
                AbleCard(
                    style = AbleCardStyle.Hero,
                    title = stringResource(R.string.downdetector_hero_title),
                    subtitle = stringResource(R.string.downdetector_hero_subtitle),
                    icon = Icons.Rounded.Public,
                    iconShape = MaterialShapes.Cookie9Sided,
                ) {
                    AbleButton(onClick = { open(state.site.homeUrl) }, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.AutoMirrored.Rounded.OpenInNew, contentDescription = null)
                        Text(
                            stringResource(R.string.downdetector_open_home, state.site.displayHost),
                            modifier = Modifier.padding(start = AbleSpacing.sm),
                        )
                    }
                }

                SectionHeader(stringResource(R.string.downdetector_country))
                ListGroup {
                    item { shape ->
                        ActionRow(
                            title = countryName(state.site.countryIso),
                            supportingText = stringResource(
                                if (state.countryChosen) R.string.downdetector_country_chosen else R.string.downdetector_country_detected,
                                state.site.displayHost,
                            ),
                            icon = Icons.Rounded.Public,
                            shape = shape,
                            onClick = { pickCountry = true },
                        )
                    }
                }

                SectionHeader(stringResource(R.string.downdetector_search))
                OutlinedTextField(
                    value = state.query,
                    onValueChange = onQueryChange,
                    label = { Text(stringResource(R.string.downdetector_search_label)) },
                    placeholder = { Text(stringResource(R.string.downdetector_search_placeholder)) },
                    leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                    supportingText = {
                        Text(
                            if (canOpenQuery) {
                                stringResource(R.string.downdetector_search_preview, state.site.statusUrl(state.query).orEmpty())
                            } else {
                                stringResource(R.string.downdetector_search_hint)
                            },
                        )
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Go),
                    keyboardActions = KeyboardActions(onGo = { openQuery() }),
                    modifier = Modifier.fillMaxWidth(),
                )
                AbleButtonFlow {
                    AbleFilledTonalButton(onClick = openQuery, enabled = canOpenQuery) {
                        Icon(Icons.AutoMirrored.Rounded.OpenInNew, contentDescription = null)
                        Text(stringResource(R.string.downdetector_open_status), modifier = Modifier.padding(start = AbleSpacing.sm), maxLines = 1)
                    }
                    AbleTextButton(onClick = { onAddFavourite(state.query) }, enabled = canOpenQuery, haptic = AbleHaptic.Confirm) {
                        Icon(Icons.Rounded.StarOutline, contentDescription = null)
                        Text(stringResource(R.string.downdetector_add_favourite), modifier = Modifier.padding(start = AbleSpacing.sm), maxLines = 1)
                    }
                }
                Text(
                    stringResource(R.string.downdetector_not_found_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                SectionHeader(stringResource(R.string.downdetector_favourites))
                if (state.favourites.isEmpty()) {
                    Text(
                        stringResource(R.string.downdetector_favourites_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    ListGroup {
                        state.favourites.forEach { name ->
                            item { shape ->
                                ActionRow(
                                    title = name,
                                    supportingText = state.site.displayHost,
                                    shape = shape,
                                    onClick = { state.site.statusUrl(name)?.let(open) },
                                    trailing = {
                                        AbleIconButton(onClick = { onRemoveFavourite(name) }, haptic = AbleHaptic.Reject) {
                                            Icon(
                                                Icons.Rounded.Close,
                                                contentDescription = stringResource(R.string.downdetector_remove_favourite, name),
                                            )
                                        }
                                    },
                                )
                            }
                        }
                    }
                }

                SectionHeader(stringResource(R.string.downdetector_suggestions))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(AbleSpacing.sm),
                    verticalArrangement = Arrangement.spacedBy(AbleSpacing.xs),
                ) {
                    state.suggestions.forEach { service ->
                        AbleFilterChip(
                            selected = false,
                            onClick = { open(state.site.statusUrlForSlug(service.slug)) },
                            label = { Text(service.name) },
                            haptic = AbleHaptic.Click,
                        )
                    }
                }
                Text(
                    stringResource(R.string.downdetector_privacy_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }

    if (pickCountry) {
        CountryDialog(
            selectedIso = state.site.countryIso,
            onSelect = { onSelectCountry(it); pickCountry = false },
            onDismiss = { pickCountry = false },
        )
    }
}

@Composable
private fun CountryDialog(selectedIso: String, onSelect: (String) -> Unit, onDismiss: () -> Unit) {
    val sites = DowndetectorSites.ALL
        .map { it to countryName(it.countryIso) }
        .sortedBy { it.second }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.downdetector_country_pick)) },
        text = {
            LazyColumn(
                modifier = Modifier.heightIn(max = 420.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                itemsIndexed(sites, key = { _, it -> it.first.countryIso }) { index, (site, name) ->
                    RadioRow(
                        title = name,
                        supportingText = site.displayHost,
                        selected = site.countryIso == selectedIso,
                        onSelect = { onSelect(site.countryIso) },
                        shape = listGroupItemShape(index, sites.size),
                    )
                }
            }
        },
        confirmButton = {
            AbleTextButton(onClick = onDismiss) { Text(stringResource(R.string.downdetector_close)) }
        },
    )
}

@Composable
private fun countryName(iso: String): String {
    val locale = LocalContext.current.resources.configuration.locales[0] ?: Locale.getDefault()
    return Locale.Builder().setRegion(iso).build().getDisplayCountry(locale).ifEmpty { iso }
}
