# Able code structure and UI conventions

**Status:** Implementation convention (2026-11-22). Supersedes the ad-hoc structure of the first implementation snapshot. Product decisions remain in [`decisions.md`](decisions.md).

## Toolchain

Verified against Google Maven / Maven Central on 2026-11-22.

| Item | Version | Note |
| --- | --- | --- |
| Android Gradle Plugin | 9.4.1 | Built-in Kotlin support; modules do not apply `org.jetbrains.kotlin.android`. Requires Gradle 9.6.0. |
| Kotlin | 2.3.21 | Compose compiler plugin and serialization plugin use the same version. |
| KSP | 2.3.14 | Room code generation (`room.generateKotlin=true`). |
| Compose BOM | 2026.11.01 | `material3` 1.5.0 (Material 3 Expressive), `adaptive` 1.3.0. Requires compileSdk 37. |
| SDK levels | compileSdk 37, minSdk 33, targetSdk 35 | targetSdk follows the product specification (v1 target Android 13+, targetSdk 35). |
| JDK | 17 bytecode; build with JDK 21 (Android Studio JBR) | |

## Module layering

```
:app ─────────────► :feature:* ─────► :core:designsystem ─► :core:model, :core:capability
  │                     │
  └─────────────────────┴──────────► :core:data ─► :core:database, :core:capability, :core:common, :core:model
```

| Module | Responsibility |
| --- | --- |
| `:core:model` | Pure Kotlin domain models (no Android types). |
| `:core:common` | `AbleResult`/`AbleError`, dispatchers, formatters. |
| `:core:database` | Room database, DAOs, entities, DataStore `UserPreferencesRepository`. |
| `:core:capability` | Android/Shizuku/sensor/permission adapters. They report capability honestly and never fake success. |
| `:core:data` | `AbleContainer` (manual DI), shared repositories used by more than one feature (battery status, storage summary, Home layout, action log, access snapshot), `ableViewModelFactory`. |
| `:core:designsystem` | Material 3 Expressive theme (`AbleTheme`), semantic colors, spacing, and every shared component. |
| `:feature:*` | Screens, ViewModels and feature-local data sources. Features never depend on each other. |
| `:app` | Application, adaptive shell and navigation, Accessibility service, Quick Settings tiles, widgets. |

## Dependency access

- The `Application` implements `AbleContainerOwner`. Code obtains dependencies through `context.ableContainer` (services, tiles, receivers) or `ableViewModelFactory { container -> ... }` (ViewModels).
- Never construct `UserPreferencesRepository`, `AbleDatabase`, adapters or `ShizukuAdapter.instance` inside a composable.
- Feature-specific data sources (for example the installed-app catalog or storage scanner) live in the feature module and are constructed in its ViewModel factory from container dependencies.

## Screen pattern

Each screen has three parts:

1. `XxxViewModel` exposing `val uiState: StateFlow<XxxUiState>` (built with `stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), initial)`) and plain functions for user intents. One-off results (snackbar messages, launch requests) are modelled as nullable state fields cleared by a `consumeXxx()` call, or a `Channel` collected in the route.
2. `XxxRoute(...)` – stateful entry point: obtains the ViewModel, collects state with `collectAsStateWithLifecycle()`, handles Android launchers (`rememberLauncherForActivityResult`), and forwards to the screen.
3. `XxxScreen(state, onEvent...)` – stateless, preview-friendly UI.

Entry points called from `:app` navigation:

| Module | Entry points |
| --- | --- |
| `:feature:home` | `HomeRoute(onOpenSettings, onOpenBattery, onOpenApps, onOpenTools, onOpenTool: (ToolId) -> Unit)` |
| `:feature:battery` | `BatteryRoute(onOpenSettings, onOpenApps)` |
| `:feature:apps` | `AppsRoute(onOpenSettings)` |
| `:feature:tools` | `ToolsRoute(onOpenSettings, onOpenTool: (ToolId) -> Unit)`, `ToolDetailRoute(toolId: ToolId, onBack)` |
| `:feature:settings` | `SettingsRoute(onBack)`, `OnboardingRoute(onFinished)` |

Deep links (tiles, widgets): launch `MainActivity` with the string extra `route` = `home`, `battery`, `apps`, `tools`, `settings` or `tool/<ToolId.key>`.

## UI conventions (Material 3 Expressive)

- Wrap content in `AbleTheme` (already done by `MainActivity`). It uses `MaterialExpressiveTheme`, `MotionScheme.expressive()`, dynamic color with a fallback palette, and `AbleThemeExtras.semantic` success/warning colors.
- Top-level destinations use `AbleTopLevelScaffold` (large flexible top app bar, Settings action). Drill-down screens use `AbleDetailScaffold` (medium flexible top app bar with back).
- Content padding: `screenContentPadding(scaffoldPadding)`; cap single-column width on wide windows with `ReadableWidth` or use adaptive grids / list-detail panes.
- Shared components: `AbleCard` (Standard/Hero/Tonal/Outlined), `ActionTile`, `MetricTile`/`MetricGrid`, `ExpressiveIcon` (Material shapes such as `MaterialShapes.Cookie9Sided`), `StatusBanner`, `StatusPill`, `ListGroup` with `ActionRow`/`SwitchRow`/`RadioRow`, `ConfirmationDialog`, `LoadingState`, `EmptyState`, `UnsupportedState`, `PermissionRequiredState`, `SectionHeader`, `Modifier.focusRing()`.
- Expressive components from Material 3 1.5 (for example `LoadingIndicator`, `ButtonGroup`, `ToggleButton`, `FloatingToolbar`, `SplitButtonLayout`, flexible top app bars, `ShortNavigationBar`, `WideNavigationRail`) may be used directly; the opt-ins for `ExperimentalMaterial3Api` and `ExperimentalMaterial3ExpressiveApi` are configured in every UI module.
- Status is never conveyed by color alone: pair color with an icon and a label (`StatusLevel`).

## Product rules enforced in code

- All user-facing text, content descriptions, notifications and errors are string resources in `values/` (English) and `values-it/` (Italian). Keep Italian product terms (Normale, Notte, Risparmio, Emergenza, Pausa app).
- Request permissions only in the context of the feature that needs them; never in bulk at first launch.
- Read back state after privileged changes (Shizuku, `WRITE_SECURE_SETTINGS`, `WRITE_SETTINGS`) and report "applied", "rejected" or "not confirmed" honestly. Log Able-applied changes through `ActionLogRepository`.
- Unsupported hardware/API shows `UnsupportedState` or a disabled control with a reason; never a control that looks actionable without a route.
- Sensors and location run only while the screen is visible (lifecycle-aware collection or `DisposableEffect`).
- Every clickable element must be reachable and visibly focused with D-pad/keyboard (`focusRing`).
- No battery-saving claims, no "clean RAM" language, freeze/disable never automatic.
