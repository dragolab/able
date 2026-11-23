package com.dragolabs.able.feature.settings

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BugReport
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.FontDownload
import androidx.compose.material.icons.rounded.Inventory2
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import com.dragolabs.able.core.designsystem.component.AbleTextButton
import com.dragolabs.able.core.designsystem.component.ActionRow
import com.dragolabs.able.core.designsystem.component.AbleDetailScaffold
import com.dragolabs.able.core.designsystem.component.InlineLoading
import com.dragolabs.able.core.designsystem.component.ListGroup
import com.dragolabs.able.core.designsystem.component.ReadableWidth
import com.dragolabs.able.core.designsystem.component.StatusBanner
import com.dragolabs.able.core.designsystem.component.StatusLevel
import com.dragolabs.able.core.designsystem.component.screenContentPadding
import com.dragolabs.able.core.designsystem.theme.AbleSpacing
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** A license text bundled in the APK assets (assets from every module are merged). */
private data class LicenseDocument(val labelRes: Int, val assetPath: String)

/** Third-party content bundled with Able (ABOUT-001). */
private enum class BundledComponent(
    val nameRes: Int,
    val detailRes: Int,
    val icon: ImageVector,
    val documents: List<LicenseDocument>,
) {
    GOOGLE_SANS_FLEX(
        R.string.settings_licenses_font_name,
        R.string.settings_licenses_font_detail,
        Icons.Rounded.FontDownload,
        listOf(LicenseDocument(R.string.settings_licenses_doc_ofl, "licenses/google_sans_flex_OFL.txt")),
    ),
    UAD_LIST(
        R.string.settings_licenses_uad_name,
        R.string.settings_licenses_uad_detail,
        Icons.Rounded.Inventory2,
        listOf(
            LicenseDocument(R.string.settings_licenses_doc_notice, "uad/NOTICE.md"),
            LicenseDocument(R.string.settings_licenses_doc_gpl, "uad/LICENSE-GPL-3.0.txt"),
        ),
    ),
    ACRA(
        R.string.settings_licenses_acra_name,
        R.string.settings_licenses_acra_detail,
        Icons.Rounded.BugReport,
        listOf(LicenseDocument(R.string.settings_licenses_doc_apache, APACHE_LICENSE_ASSET)),
    ),
    HAZE(
        R.string.settings_licenses_haze_name,
        R.string.settings_licenses_haze_detail,
        Icons.Rounded.Code,
        listOf(LicenseDocument(R.string.settings_licenses_doc_apache, APACHE_LICENSE_ASSET)),
    ),
    OSMDROID(
        R.string.settings_licenses_osm_name,
        R.string.settings_licenses_osm_detail,
        Icons.Rounded.Code,
        listOf(LicenseDocument(R.string.settings_licenses_doc_apache, APACHE_LICENSE_ASSET)),
    ),
    ANDROID_LIBRARIES(
        R.string.settings_licenses_libraries_name,
        R.string.settings_licenses_libraries_detail,
        Icons.Rounded.Code,
        listOf(LicenseDocument(R.string.settings_licenses_doc_apache, APACHE_LICENSE_ASSET)),
    ),
}

private const val APACHE_LICENSE_ASSET = "licenses/apache-2.0.txt"

/** "Open-source licenses" screen (ABOUT-001): bundled components and their license texts. */
@Composable
fun OpenSourceLicensesRoute(onBack: () -> Unit, modifier: Modifier = Modifier) {
    var open by rememberSaveable { mutableStateOf<String?>(null) }
    var openTitle by rememberSaveable { mutableStateOf("") }

    AbleDetailScaffold(
        title = stringResource(R.string.settings_about_licenses),
        onBack = onBack,
        modifier = modifier,
    ) { padding ->
        ReadableWidth {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = screenContentPadding(padding),
                verticalArrangement = Arrangement.spacedBy(AbleSpacing.md),
            ) {
                item(key = "intro") {
                    Text(
                        stringResource(R.string.settings_licenses_intro),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = AbleSpacing.xs),
                    )
                }
                BundledComponent.entries.forEach { component ->
                    item(key = component.name) {
                        Column(verticalArrangement = Arrangement.spacedBy(AbleSpacing.sm)) {
                            ListGroup {
                                item { shape ->
                                    ActionRow(
                                        title = stringResource(component.nameRes),
                                        shape = shape,
                                        supportingText = stringResource(component.detailRes),
                                        icon = component.icon,
                                    )
                                }
                                component.documents.forEach { doc ->
                                    item { shape ->
                                        val label = stringResource(doc.labelRes)
                                        ActionRow(
                                            title = label,
                                            shape = shape,
                                            onClick = {
                                                openTitle = label
                                                open = doc.assetPath
                                            },
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                item(key = "trademark") {
                    StatusBanner(
                        level = StatusLevel.INFO,
                        title = stringResource(R.string.settings_licenses_trademark_title),
                        message = stringResource(R.string.settings_licenses_trademark_body),
                    )
                }
            }
        }
    }

    open?.let { path ->
        LicenseTextDialog(title = openTitle, assetPath = path, onDismiss = { open = null })
    }
}

@Composable
private fun LicenseTextDialog(title: String, assetPath: String, onDismiss: () -> Unit) {
    val context = LocalContext.current
    var text by remember(assetPath) { mutableStateOf<String?>(null) }
    var failed by remember(assetPath) { mutableStateOf(false) }
    LaunchedEffect(assetPath) {
        val loaded = readAsset(context, assetPath)
        if (loaded == null) failed = true else text = loaded
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
        modifier = Modifier.padding(AbleSpacing.lg),
        title = { Text(title) },
        text = {
            Box(Modifier.fillMaxWidth().heightIn(max = 520.dp)) {
                val loaded = text
                when {
                    failed -> Text(stringResource(R.string.settings_licenses_read_failed))
                    loaded == null -> InlineLoading()
                    else -> SelectionContainer {
                        Text(
                            loaded,
                            fontFamily = FontFamily.Monospace,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.verticalScroll(rememberScrollState()),
                        )
                    }
                }
            }
        },
        confirmButton = { AbleTextButton(onClick = onDismiss) { Text(stringResource(R.string.settings_action_close)) } },
    )
}

private suspend fun readAsset(context: Context, path: String): String? = withContext(Dispatchers.IO) {
    runCatching { context.assets.open(path).bufferedReader().use { it.readText() } }.getOrNull()
}
