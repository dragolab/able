package com.dragolabs.able.core.data

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.dragolabs.able.core.capability.CapabilityManager
import com.dragolabs.able.core.capability.LocationAdapter
import com.dragolabs.able.core.capability.PermissionCoordinator
import com.dragolabs.able.core.capability.SecureSettingsAdapter
import com.dragolabs.able.core.capability.SensorManagerAdapter
import com.dragolabs.able.core.capability.ShizukuAdapter
import com.dragolabs.able.core.data.battery.BatteryProfileRepository
import com.dragolabs.able.core.database.AbleDatabase
import com.dragolabs.able.core.database.UserPreferencesRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.Dispatchers

/**
 * Manual dependency container (no DI framework in v1). The Application owns a single instance;
 * ViewModels, services, tiles and widgets obtain dependencies from it instead of constructing
 * repositories themselves.
 */
interface AbleContainer {
    val appContext: Context

    /** Process-wide scope for work that must outlive a screen (e.g. logging an outcome). */
    val applicationScope: CoroutineScope

    val database: AbleDatabase
    val preferences: UserPreferencesRepository

    val shizuku: ShizukuAdapter
    val capabilities: CapabilityManager
    val permissions: PermissionCoordinator
    val secureSettings: SecureSettingsAdapter
    val sensors: SensorManagerAdapter
    val location: LocationAdapter
    val screenLock: ScreenLockController

    val battery: BatteryStatusRepository

    /** User-editable battery profiles and their evaluation (owner decision 2026-11-22). */
    val batteryProfiles: BatteryProfileRepository
    val storage: StorageSummaryRepository
    val homeLayout: HomeLayoutRepository
    val actionLog: ActionLogRepository
    val capabilityStatus: CapabilityStatusRepository
}

/** Implemented by the Application class. */
interface AbleContainerOwner {
    val container: AbleContainer
}

val Context.ableContainer: AbleContainer
    get() = (applicationContext as AbleContainerOwner).container

/**
 * Bridge to the app-module Accessibility service, which feature modules cannot reference
 * directly (LOCK-001).
 */
interface ScreenLockController {
    /** True when the Able Accessibility service is enabled in Android settings. */
    fun isServiceEnabled(): Boolean

    /** Requests a lock through the Accessibility service. Returns Android's reported result. */
    fun lockNow(): Boolean
}

class DefaultAbleContainer(
    context: Context,
    override val screenLock: ScreenLockController,
) : AbleContainer {
    override val appContext: Context = context.applicationContext
    override val applicationScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override val database: AbleDatabase by lazy { AbleDatabase.getInstance(appContext) }
    override val preferences: UserPreferencesRepository by lazy { UserPreferencesRepository(appContext) }

    override val shizuku: ShizukuAdapter get() = ShizukuAdapter.instance
    override val capabilities: CapabilityManager by lazy { CapabilityManager(appContext) }
    override val permissions: PermissionCoordinator by lazy { PermissionCoordinator(appContext) }
    override val secureSettings: SecureSettingsAdapter by lazy { SecureSettingsAdapter(appContext, shizuku) }
    override val sensors: SensorManagerAdapter by lazy { SensorManagerAdapter(appContext) }
    override val location: LocationAdapter by lazy { LocationAdapter(appContext, permissions) }

    override val battery: BatteryStatusRepository by lazy { BatteryStatusRepository(appContext) }
    override val batteryProfiles: BatteryProfileRepository by lazy {
        BatteryProfileRepository(database.batteryProfileDao(), preferences, battery)
    }
    override val storage: StorageSummaryRepository by lazy { StorageSummaryRepository() }
    override val homeLayout: HomeLayoutRepository by lazy { HomeLayoutRepository(database.homeCardDao()) }
    override val actionLog: ActionLogRepository by lazy { ActionLogRepository(database.actionExecutionDao()) }
    override val capabilityStatus: CapabilityStatusRepository by lazy {
        CapabilityStatusRepository(capabilities, shizuku, permissions, secureSettings, screenLock)
    }
}

/**
 * ViewModel factory that hands the [AbleContainer] to a constructor lambda:
 *
 * ```
 * val vm: HomeViewModel = viewModel(factory = ableViewModelFactory { HomeViewModel(it.battery, ...) })
 * ```
 */
inline fun <reified VM : ViewModel> ableViewModelFactory(
    crossinline create: CreationExtras.(AbleContainer) -> VM,
): ViewModelProvider.Factory = viewModelFactory {
    initializer {
        val application = checkNotNull(this[APPLICATION_KEY]) { "Application missing from CreationExtras" }
        create(application.ableContainer)
    }
}
