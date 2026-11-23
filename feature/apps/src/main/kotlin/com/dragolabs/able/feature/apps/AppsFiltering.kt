package com.dragolabs.able.feature.apps

import com.dragolabs.able.feature.apps.data.AppArchiveRecord
import com.dragolabs.able.feature.apps.data.AppInstallState
import com.dragolabs.able.feature.apps.data.InstalledApp
import com.dragolabs.able.feature.apps.data.PackageKnowledge
import com.dragolabs.able.feature.apps.data.RemovalLevel

/**
 * Builds the full row list: every package PackageManager reports plus Able-archive records
 * for packages it no longer reports ([AppInstallState.NOT_FOUND]).
 */
internal fun buildItems(
    apps: List<InstalledApp>,
    knowledge: Map<String, PackageKnowledge>,
    archive: Map<String, AppArchiveRecord>,
    protectedPackages: Set<String>,
): List<AppListItem> {
    val known = HashSet<String>(apps.size * 2)
    val items = ArrayList<AppListItem>(apps.size + archive.size)
    for (app in apps) {
        known += app.packageName
        val info = knowledge[app.packageName]
        items += AppListItem(
            packageName = app.packageName,
            label = app.label,
            isSystem = app.isSystem,
            state = app.state,
            removal = info?.removal,
            list = info?.list,
            isProtected = app.packageName in protectedPackages,
            inAbleArchive = app.packageName in archive,
            searchDescription = info?.description?.lowercase().orEmpty(),
        )
    }
    for (record in archive.values) {
        if (record.packageName in known) continue
        val info = knowledge[record.packageName]
        items += AppListItem(
            packageName = record.packageName,
            label = record.label,
            isSystem = record.isSystem,
            state = AppInstallState.NOT_FOUND,
            removal = info?.removal,
            list = info?.list,
            isProtected = record.packageName in protectedPackages,
            inAbleArchive = true,
            searchDescription = info?.description?.lowercase().orEmpty(),
        )
    }
    return items
}

/** Applies search (label, package, UAD description) and all filters. */
internal fun filterItems(items: List<AppListItem>, filters: AppFilters): List<AppListItem> {
    val query = filters.query.trim().lowercase()
    return items.filter { item ->
        matchesState(item, filters.state) &&
            when (filters.type) {
                AppTypeFilter.ALL -> true
                AppTypeFilter.USER -> !item.isSystem
                AppTypeFilter.SYSTEM -> item.isSystem
            } &&
            (!filters.protectedOnly || item.isProtected) &&
            (filters.lists.isEmpty() || item.list in filters.lists) &&
            (filters.removals.isEmpty() || item.removalFilter() in filters.removals) &&
            (
                query.isEmpty() ||
                    item.label.lowercase().contains(query) ||
                    item.packageName.lowercase().contains(query) ||
                    item.searchDescription.contains(query)
                )
    }
}

private fun matchesState(item: AppListItem, filter: AppStateFilter): Boolean = when (filter) {
    AppStateFilter.ALL -> true
    AppStateFilter.INSTALLED -> item.state == AppInstallState.INSTALLED
    AppStateFilter.DISABLED -> item.state == AppInstallState.DISABLED
    AppStateFilter.UNINSTALLED -> item.state == AppInstallState.UNINSTALLED_FOR_USER || item.state == AppInstallState.NOT_FOUND
    AppStateFilter.ARCHIVED_BY_SYSTEM -> item.state == AppInstallState.ARCHIVED_BY_SYSTEM
    AppStateFilter.ABLE_ARCHIVE -> item.inAbleArchive
}

internal fun AppListItem.removalFilter(): RemovalFilter = when (removal) {
    RemovalLevel.RECOMMENDED -> RemovalFilter.RECOMMENDED
    RemovalLevel.ADVANCED -> RemovalFilter.ADVANCED
    RemovalLevel.EXPERT -> RemovalFilter.EXPERT
    RemovalLevel.UNSAFE -> RemovalFilter.UNSAFE
    null -> RemovalFilter.NOT_LISTED
}

/** Packages Able never changes: itself, the Shizuku manager it depends on, and the core framework. */
internal fun blockReasonFor(packageName: String, ownPackage: String): BlockReason? = when (packageName) {
    ownPackage -> BlockReason.SELF
    SHIZUKU_PACKAGE -> BlockReason.SHIZUKU
    "android", "com.android.systemui", "com.android.settings", "com.android.shell" -> BlockReason.CORE_SYSTEM
    else -> null
}

internal const val SHIZUKU_PACKAGE = "moe.shizuku.privileged.api"
