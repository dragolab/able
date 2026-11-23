package com.dragolabs.able.feature.apps

import com.dragolabs.able.feature.apps.data.AppArchiveRecord
import com.dragolabs.able.feature.apps.data.AppInstallState
import com.dragolabs.able.feature.apps.data.ArchivedAction
import com.dragolabs.able.feature.apps.data.InstalledApp
import com.dragolabs.able.feature.apps.data.PackageKnowledge
import com.dragolabs.able.feature.apps.data.RemovalLevel
import com.dragolabs.able.feature.apps.data.UadList
import org.junit.Assert.assertEquals
import org.junit.Test

class AppsFilteringTest {

    private fun app(pkg: String, label: String, system: Boolean = false, enabled: Boolean = true, installed: Boolean = true) =
        InstalledApp(pkg, label, "1", 1, system, enabled, installed, false, true, 0, 0)

    private val apps = listOf(
        app("com.example.mail", "Mail"),
        app("com.oem.bloat", "Bloat", system = true, enabled = false),
        app("com.oem.gone", "Gone", system = true, installed = false),
    )
    private val knowledge = mapOf(
        "com.oem.bloat" to PackageKnowledge("com.oem.bloat", UadList.OEM, RemovalLevel.RECOMMENDED, "Vendor weather widget", emptyList(), emptyList(), emptyList()),
    )
    private val archive = mapOf(
        "com.oem.bloat" to AppArchiveRecord("com.oem.bloat", "Bloat", ArchivedAction.DISABLED, 1, true, true, true),
        "com.vanished" to AppArchiveRecord("com.vanished", "Vanished", ArchivedAction.UNINSTALLED, 1, true, true, true),
    )
    private val items = buildItems(apps, knowledge, archive, protectedPackages = setOf("com.example.mail"))

    private fun names(filters: AppFilters) = filterItems(items, filters).map { it.packageName }.toSet()

    @Test
    fun includesArchiveOnlyRecords() {
        assertEquals(4, items.size)
        assertEquals(AppInstallState.NOT_FOUND, items.first { it.packageName == "com.vanished" }.state)
    }

    @Test
    fun filtersByState() {
        assertEquals(setOf("com.example.mail"), names(AppFilters(state = AppStateFilter.INSTALLED)))
        assertEquals(setOf("com.oem.bloat"), names(AppFilters(state = AppStateFilter.DISABLED)))
        assertEquals(setOf("com.oem.gone", "com.vanished"), names(AppFilters(state = AppStateFilter.UNINSTALLED)))
        assertEquals(setOf("com.oem.bloat", "com.vanished"), names(AppFilters(state = AppStateFilter.ABLE_ARCHIVE)))
    }

    @Test
    fun filtersByTypeProtectedListAndRemoval() {
        assertEquals(setOf("com.example.mail"), names(AppFilters(type = AppTypeFilter.USER)))
        assertEquals(setOf("com.example.mail"), names(AppFilters(protectedOnly = true)))
        assertEquals(setOf("com.oem.bloat"), names(AppFilters(lists = setOf(UadList.OEM))))
        assertEquals(setOf("com.oem.bloat"), names(AppFilters(removals = setOf(RemovalFilter.RECOMMENDED))))
        assertEquals(3, names(AppFilters(removals = setOf(RemovalFilter.NOT_LISTED))).size)
    }

    @Test
    fun searchesLabelPackageAndDescription() {
        assertEquals(setOf("com.example.mail"), names(AppFilters(query = "mai")))
        assertEquals(setOf("com.oem.gone"), names(AppFilters(query = "OEM.GONE")))
        assertEquals(setOf("com.oem.bloat"), names(AppFilters(query = "weather")))
    }

    @Test
    fun blocksSelfAndShizuku() {
        assertEquals(BlockReason.SELF, blockReasonFor("com.dragolabs.able", "com.dragolabs.able"))
        assertEquals(BlockReason.SHIZUKU, blockReasonFor(SHIZUKU_PACKAGE, "com.dragolabs.able"))
        assertEquals(null, blockReasonFor("com.oem.bloat", "com.dragolabs.able"))
    }
}
