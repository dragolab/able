# Able technical milestone 1: Architecture baseline, capability adapters & Pixel 8 Pro spikes

**Owner:** Drago Labs  
**Project:** Able Android Toolbox  
**Status:** In progress (partial implementation; verification blocked)  
**Target Device:** Google Pixel 8 Pro (Android 14+)  
**Duration / Scope:** Milestone 1 of v1 implementation

---

## 1. Goal and purpose

Milestone 1 establishes the engineering foundation of Able. Before constructing complex user flows and automation screens, this milestone sets up the clean architecture multi-module project, establishes local persistence and capability adapters, and resolves the highest-risk platform feasibility questions through targeted spikes on the Google Pixel 8 Pro reference device.

---

## 2. Scope and ordered work packages

### Phase 1: Project setup, tooling and design system baseline
1. **Repository & Build Setup:**
   - Initialize Git repository tracking with `.gitignore` and `.gitattributes` (UTF-8, LF line endings).
   - Configure Gradle build logic using Version Catalogs (`libs.versions.toml`).
   - Configure multi-module architecture:
     - `:app` (Application entry point, navigation graph, DI composition)
     - `:core:model` (Domain models, entities, immutable contracts)
     - `:core:common` (Result monad, dispatchers, extensions, logger)
     - `:core:designsystem` (Material 3 theme, Dynamic Color tokens, components, icons)
     - `:core:capability` (System adapters: BatteryManager, Shizuku, Permissions, SAF)
     - `:core:database` (Room database, DAOs, schema migrations, DataStore preferences)
     - `:feature:home` (Modular card dashboard)
     - `:feature:battery` (Profiles, monitor, analytics, hardware health)
     - `:feature:tools` (Torch, hotspot, shortcuts, storage, experimental tools)
     - `:feature:settings` (Appearance, language override, backup/restore, privacy)
2. **Localization & Theming:**
   - Set up Italian (`values-it/`) and English (`values/`) baseline string resources.
   - Implement `AbleTheme` with Material You dynamic color extraction (`dynamicLightColorScheme` / `dynamicDarkColorScheme`) and semantic fallback palette.
   - Implement Android 13+ `LocaleManager` integration for in-app language switching.

### Phase 2: Persistence and local architecture (ADR-009)
1. **Room Database & DAOs:**
   - Create Room database for `ActionExecutionEntity`, `BatterySessionEntity`, `ChargingSessionEntity`, `BatteryNoteEntity`, `ShortcutDefinitionEntity`, `ShortcutCollectionEntity`, `HomeCardConfigEntity`, `GpsPointEntity`, `DnsProfileEntity`, `CustomTileMappingEntity`, and `CustomWidgetConfigEntity`.
   - Implement database versioning strategy with exportSchema and migration verification tests.
2. **DataStore Preferences:**
   - Store user preferences: theme mode (System/Light/Dark), language override, monitor configuration, retention setting (30/90 days/manual), crash-report opt-in flag (ADR-015), haptic feedback switch (UX-003), guided-tour completion flag (UX-001).

### Phase 3: Hardware capability adapters and Shizuku bridge
1. **`CapabilityManager`:**
   - Detect device hardware features (`PackageManager.FEATURE_CAMERA_FLASH`, `FEATURE_TELEPHONY`, `FEATURE_TOUCHSCREEN`, `FEATURE_NFC`, `FEATURE_SENSOR_ACCELEROMETER`, `FEATURE_SENSOR_COMPASS`).
   - Determine battery presence and hardware level.
2. **`ShizukuAdapter` & Privileged Adapters:**
   - Bind to Shizuku IPC binder service.
   - Implement connection lifecycle listeners (Connected, Disconnected, DeadBinder).
   - Wrap privileged package inspection and command execution with timeout handling.
   - Implement `SecureSettingsAdapter` for testing read/write access to `Settings.Global` and `Settings.Secure` (for NFC, ADB, Private DNS).
3. **`SensorManagerAdapter` & Location Adapter:**
   - Implement lifecycle-aware sensor listener registration (accelerometer, magnetometer) adhering to ADR-022 (zero background overhead).
   - Implement standard Android location adapter with accuracy thresholds.
4. **`PermissionCoordinator`:**
   - Contextual permission request flows with rationales for `POST_NOTIFICATIONS`, `PACKAGE_USAGE_STATS`, `ACCESS_NOTIFICATION_POLICY`, `ACCESS_FINE_LOCATION`, `WRITE_SETTINGS`, and Accessibility.

### Phase 4: Critical Pixel 8 Pro feasibility spikes (Validation Gates)
1. **Spike 1: Battery Health APIs (BAT-HLT-001, BAT-HLT-002, R-015):**
   - Test `BatteryManager.EXTRA_CYCLE_COUNT` in `ACTION_BATTERY_CHANGED` on Pixel 8 Pro under Android 14+.
   - Test Shizuku / `BATTERY_STATS` access for State of Health (SoH) %, manufacturing date, first usage date, and charging policy.
   - Verify fallback behavior when HAL nodes are unpopulated.
2. **Spike 2: Double-Tap & Screen Lock (LOCK-001, R-001):**
   - Prototype Accessibility service gesture detection on Pixel Launcher and third-party launchers.
   - Verify `AccessibilityService.GLOBAL_ACTION_LOCK_SCREEN` on Pixel 8 Pro.
   - Document supported context boundaries and confirm fallback Quick Settings tile.
3. **Spike 3: Experimental Pixel Tools (PIX-001–PIX-006, R-014):**
   - Test AOD toggle viability on notification receipt and charging events.
   - Test Recents close-all invocation via Accessibility tree traversal.
   - Measure power draw of orientation sensor evaluation on screen wake.
4. **Spike 4: Background Profile Trigger Reliability (BAT-008, R-002):**
   - Test screen-off continuous delay detection (20-minute Notte trigger) using event broadcasts (`ACTION_SCREEN_OFF`, `ACTION_SCREEN_ON`) vs AlarmManager alarms.
   - Verify zero wake-lock overhead when phone is in deep sleep.
5. **Spike 5: Privileged System Toggles Validation (SYS-001–SYS-004, ADR-023, R-016):**
   - Validate direct state toggle of NFC via `WRITE_SECURE_SETTINGS` / Shizuku on Pixel 8 Pro.
   - Validate USB debugging (`adb_enabled`) and Private DNS (`private_dns_mode`, `private_dns_specifier`) toggling.
   - Verify graceful fallback to system settings intent when privileged permissions are missing.

### Phase 5: Adaptive Shell & TV Scaffold (ADR-008, NFR-TV-001)
1. **Responsive Mobile/Tablet Shell:**
   - Implement adaptive navigation: `NavigationBar` on compact phone; `NavigationRail` on expanded tablet.
   - Wire top app bar with Settings and Help actions.
2. **Android TV Remote Scaffold:**
   - Implement remote-first Compose for TV container (`androidx.tv.material3`).
   - Implement D-pad focus engine, high-contrast focus rings, and back key handling.

---

## 3. Milestone 1 deliverables and exit criteria

| Deliverable | Acceptance criteria | Verification |
| --- | --- | --- |
| **Project build & modules** | Multi-module project builds cleanly with Gradle; zero lint errors; minSdk 33, targetSdk 34+. | Unit & build check |
| **Material 3 Theme & Strings** | System/Light/Dark themes toggle cleanly; dynamic colors render on Pixel 8 Pro; Italian/English complete. | T-LOCALE-01 |
| **Adaptive Shell** | Shell renders correctly on compact phone, resized tablet window, and Android TV emulator. | T-UI-01, T-TV-01 |
| **Local Persistence** | Room DB and DataStore read/write entities with migrations; local data boundaries respected. | Unit & DB tests |
| **Shizuku & Capability Bridge** | Accurate detection of flash, battery, Shizuku connection state, and permissions without crashes. | T-CAP-01, T-SHIZUKU-01 |
| **Pixel 8 Pro Spike Report** | Recorded results for Cycle count, AOD, Double-tap lock, and Recents on physical Pixel 8 Pro. | Recorded spike log |

---

## 4. Transition to milestone 2

Upon passing all exit criteria of Milestone 1, development moves to **Milestone 2 (Core Toolbox & Modular Home)**:
- Full implementation of Modular Card Home dashboard (`:feature:home`).
- Battery profiles and automation engine (`:feature:battery`).
- Flashlight, hotspot, and shortcut creation tools (`:feature:tools`).
