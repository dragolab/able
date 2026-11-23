# Able Android Toolbox Product Specification

**Owner:** Drago Labs  
**Version:** 0.18  
**Status:** Working draft; product interview in progress  
**Last updated:** 22 November 2026

## Purpose

Able is an Android toolbox for quick device actions, app and background management, battery profiles, storage cleanup, flashlight control, and home-screen shortcuts. This document records the agreed product direction and technical constraints so it can guide implementation with AI. Items marked as open decisions will be settled through the ongoing interview.

## Product decisions so far

- Product name: **Able**.
- Product owner: **Drago Labs**.
- First reference device: **Google Pixel 8 Pro**.
- Intended Android range: **Android 13 and newer**.
- Distribution target: **Google Play and sideloaded APK** (Phone, Tablet, Android TV / Google TV).
- Software licensing & IP: **GNU General Public License v3.0 (GPLv3)** for source code; project name 'Able', 'Drago Labs', and brand/logo assets remain proprietary intellectual property of Drago Labs, reserved by a `NOTICE.md` file. Revised 2026-11-22 (ADR-011): the owner decided to publish now; the public repository is **https://github.com/dragolab/able** and the GPLv3 `LICENSE` and `NOTICE.md` are added at publication. `Able_Documentation_Pack.zip` stays local and git-ignored.
- Versioning (confirmed 2026-11-22, ADR-035): Able is in **beta 0.5**; the next APK is **0.6** (`versionName 0.6.0-beta`, `versionCode 6`). Beta releases use `0.<minor>.<patch>-beta` with `versionCode` incremented by one per published APK.
- Design direction: **Material You / Material 3**, adapting to Android dynamic colors and available window space. The user confirmed system dynamic colors as Able's visual palette.
- Implementation stack: **100% native Kotlin and Jetpack Compose** across all targets, utilizing `androidx.compose.material3.adaptive` for adaptive phone/tablet windows and Compose for TV (`androidx.tv:tv-material`, `androidx.tv:tv-foundation`) for Android TV remote-first navigation.
- Form factors: graphical support for **phones, tablets, and Android TV / Google TV**. Layouts and navigation adapt to available window size and input method.
- Android TV / Google TV: complete remote-first experience is in v1, with Google Play TV distribution as a target.
- Languages in the first release: **Italian and English**. Default to the system language, with an in-app override in Settings.
- Theme behavior: default to the Android system theme; Settings also offers **Light** and **Dark** overrides.
- Main navigation: bottom bar with **Home, Batteria, App, Strumenti**.
- Home: fully modular card-based dashboard. All sections (Battery & Active Profile, Quick Actions, Capability Status, Storage Summary, Favorite Shortcuts, Recent Actions/Warnings) are distinct cards that can be freely reordered, hidden, shown, and reset to defaults.
- Home default priority: battery level, active profile, and quick actions are placed at the top in the default layout; critical capability and automation warnings remain prominent regardless of card order.
- Cloud sync: **out of scope for v1**. Configuration transfer uses a user-managed local export/import file.
- Battery triggers default **off** (confirmed 2026-11-22, ADR-005): every automatic trigger is disabled on a fresh install. Nothing activates until the user enables each trigger individually.
- **Custom battery profiles** (owner decision 2026-11-22, v0.18, ADR-005 revised): profiles are user-creatable and editable. The built-ins Normale, Notte, Risparmio, Emergenza, and Pausa app are editable defaults that cannot be deleted and can be reset. Each profile has its own triggers (battery below a threshold with separate activate/reset thresholds and an only-unplugged option; schedule with days of the week; screen off for N minutes; charging; manual), all disabled by default. **Several profiles can be active at once**; when active profiles set different actions for the same app, **the most restrictive action wins** (Osserva < Limita in background < Arresta quando esco), protected apps are always excluded, and the UI shows which profile each applied action comes from. This replaces the earlier fixed priority Emergenza → Risparmio → Notte → Normale.
- Battery page: balanced overview of active profile, affected apps, and usage summary.
- Activity Launcher and Shortcut Maker are separate tools. From an activity detail, the user can choose **Create shortcut** and continue in the same shortcut editor used by Shortcut Maker.
- Activity Launcher should show only activities that Android allows Able to launch normally. Do not expose hidden/private components as a default feature.
- Battery automation may be both automatic and manual. The user should choose an intervention level for each app.
- Once a user reviews and enables a battery profile, its configured actions run automatically at the trigger, with a notification and action log; freezing/disabling apps remains manual.
- Able should suggest common protected apps, such as alarms, messaging, health, and wearable companions; the user confirms and can edit the list.
- Hotspot automation should optionally react to a user-selected Bluetooth device, such as a car system.
- Pixel ToolBox candidate features (AOD for notifications/charging, Recents close-all, screen-off fingerprint, ringtone tile, orientation camera launch) are included in v1 as experimental capabilities, conditional on Pixel 8 Pro feasibility verification; unsupported states are reported honestly.
- Battery diagnostics and hardware health suite confirmed for v1: personal idle-drain baseline (BASE-001/002), app impact review, before/after profile comparison, charging session diary, anomaly checklist, battery health history, session event notes, and aBattery-inspired hardware health (cycle count via Android 14+ public API; SoH %, design vs full capacity, manufacture/first-use dates, charging policy via Shizuku/system access).
- Storage cleanup presets: default thresholds identify large files >100 MB (filters: 50MB, 100MB, 500MB, 1GB) and old downloads >30 days (filters: 7, 30, 90 days); deletions require explicit user selection and Android Storage Access Framework confirmation.
- Shortcut Maker collections: deleting a collection prompts the user to either move contained shortcuts to 'Unsorted' or delete them, preventing unintended deletion of shortcut definitions and custom icon assets.
- GPS coordinate tool: user-initiated location capture (latitude, longitude, altitude, accuracy), searchable catalog with tags and notes, quick share (geo URI, text, map links), offline-first storage.
- Physical measurement tools: spirit level / inclinometer with 2D bubble and haptic 0° feedback, screen ruler calibrated via display DPI with fine-tuning slider, digital compass.
- System Quick Settings tiles & toggles: NFC toggle following `pcolby/nfc-quick-settings` (basic mode opens NFC settings; advanced mode toggles directly when `WRITE_SECURE_SETTINGS` was granted via ADB; Shizuku as a third route; revised 2026-11-22), Auto-brightness toggle (via `WRITE_SETTINGS`), USB & Wireless Debugging toggles (wireless debugging details open the Wireless debugging page directly, revised 2026-11-22), and Private DNS quick switch (Off, Automatic, custom hostname).
- Universal Action Catalog, customizable tiles & widgets: user-assignable Quick Settings tile slots and multi-action customizable home-screen AppWidgets (via Glance/Compose) allowing users to map any action or combination of actions from Able's Action Catalog. Tile icons come from a curated Able icon set (ADR-027).
- App management package visibility (confirmed 2026-11-22, ADR-028): Able lists the full installed-package set using `QUERY_ALL_PACKAGES`. This works for sideloaded builds; the Google Play permissions declaration is decided at release time, and a Play build flavor without the permission may be needed.
- Package knowledge base (confirmed 2026-11-22, ADR-026): Able bundles a snapshot of the Universal Android Debloater Next Generation (UAD-ng) list (`uad_lists.json`, GPL-3.0) with attribution in About, refreshed manually per release. It provides per-package descriptions, list, removal level, dependencies, and filters.
- Diagnostics (confirmed 2026-11-22, ADR-015): crash reports use the open-source ACRA library in a user-reviewed mode. After a crash Able shows the report and the user chooses whether to send it by email to Drago Labs (`info.dragolabs@gmail.com`). No third-party server, no automatic upload, and no feature-use analytics in v1.
- Drago Labs website: `https://dragolabs.it` (confirmed 2026-11-22).
- Brand identity (confirmed 2026-11-22, ADR-027): Able gets its own logo, adaptive and monochrome launcher icon, Quick Settings tile icon set, and a more distinctive expressive visual style. The open-source "Essentials" app by sameerasw is inspiration only; its branding is not copied. Brand assets remain Drago Labs IP (ADR-011).
- Guidance and feel (confirmed 2026-11-22): skippable guided tour after onboarding, contextual "?" help on advanced or risky options, haptic feedback with an off switch, predictive back, and expressive motion (UX-001..UX-004).
- Owner requests of 22 November 2026 (spec v0.17):
  - **Command shell tool** (CMD-001, ADR-029), aShell-like, running commands as the Shizuku shell user. The owner labelled the request "SHELL-001"; that ID already identifies the app shell/navigation requirement, so the tool is recorded as **CMD-001** to keep IDs stable.
  - **Double tap on empty home-screen space** to turn the screen off (LOCK-003), with the transparent home widget as fallback; platform-dependent (see the Double tap section).
  - **App lock** (APPLOCK-001, ADR-030) using the system biometric/credential prompt from Able's own activity; no overlay windows.
  - **Button shortcuts** (KEY-001, ADR-031) mapping volume-key gestures to Able catalog actions, apps, or activities; power-key remapping is unsupported for third-party apps.
  - **App-wide options search** (SEARCH-001, ADR-032) from the top bar of every primary destination.
  - **Motion and haptics revisions** (UX-003, UX-004), **readable card headers and human-formatted system values** (NFR-ACC-002).
  - **APK backup before uninstalling a user app** (APP-004 revised, ADR-033) after a verified restore failure on the Android 16 emulator.
- Owner decisions and requests of 22 November 2026 (spec v0.18):
  - **Versioning** (ADR-035): current beta 0.5; next APK 0.6 (`0.6.0-beta`, `versionCode 6`).
  - **Public repository** (ADR-011 revised): https://github.com/dragolab/able, GPLv3 `LICENSE` plus `NOTICE.md` reserving the Able and Drago Labs names and logos; ABOUT-001 links to it.
  - **Custom battery profiles, concurrent active profiles, most-restrictive-wins** (BAT-001, BAT-002, BAT-003 revised; ADR-005 revised); Battery Status and Diagnosis screens redesigned (adaptive UI spec).
  - **Background permissions management** (APP-007, ADR-036), NoMoreBackground-like, through Shizuku AppOps.
  - **RAM and processes tool** (MEM-001, ADR-037), Shappky-inspired, in Strumenti next to Storage. This overrides the earlier "avoid clean RAM" principle only as far as offering a manual, honest tool; automatic "RAM cleaning" remains excluded (BAT-007 revised).
  - **NFC tile** three routes (SYS-001 revised): basic (settings), advanced (`WRITE_SECURE_SETTINGS` via ADB), Shizuku.
  - **Wireless debugging details** open the Wireless debugging page directly with fallbacks (SYS-003 revised).
  - **Real hotspot control via Shizuku** following Delta (HOT-001, HOT-002 revised; ADR-038).
  - **Precise home double-tap mode** with a launcher-only accessibility overlay that replays non-matching touches (LOCK-003 revised, ADR-039), after the light watcher proved unreliable on the Pixel.
  - **Navigation re-select returns to the section start** (SHELL-001 revised, ADR-040).
  - **Container-transform glitches on small tool cards are a defect** (UX-004 acceptance criteria extended).
  - **Rebrand requested** (BRAND-001): redesign in progress, options proposed in `docs/brand/branding-options.html`, decision pending (ADR-027 revised).

## Goals

1. Make common device actions accessible without navigating through many system settings.
2. Help users identify and selectively limit apps that matter to their battery use.
3. Provide reversible, understandable advanced actions through optional Shizuku access.
4. Make the flashlight and shortcut tools useful from the Home screen and Quick Settings.
5. Explain permission requirements and Android limitations before asking users to enable them.

## Product principles

- **User control:** no unannounced app freezing, disabling, or removal.
- **Reversible actions:** record changes and provide a clear undo or restore path where Android permits it.
- **Selective battery controls:** do not kill every background process on a timer. Automatic "RAM cleaning" is excluded; a manual RAM and processes tool (MEM-001) is allowed when its copy stays honest (owner decision 2026-11-22): Android manages memory, stopped apps may restart, and no battery-saving or "boost" claim is made.
- **Honest battery claims:** show measured observations or system-reported data; do not promise a fixed percentage of battery savings.
- **Permission transparency:** advanced features remain optional and explain why each access is needed.
- **Local-first:** keep profiles, app rules, settings, and logs on-device by default. Configuration export/import is explicit and user-controlled; cloud sync remains out of scope. Crash diagnostics leave the device only when the user reviews a report and chooses to send it by email; there is no automatic upload and no feature-use analytics in v1.
- **Platform-aware UI:** show when an action is unavailable on a device or Android version instead of silently failing.

## Information architecture

### Bottom navigation

| Destination | Main content |
| --- | --- |
| Home | Battery state, active profile, permission status, and customizable quick actions |
| Batteria | Battery overview, profiles, app-level rules, and available diagnostics |
| App | Background controls and app management |
| Strumenti | Torcia, storage, RAM and processes (next to storage), Shortcut Maker, Activity Launcher, command shell, app lock, button shortcuts, hotspot, and system utilities |

Home cards should be reorderable and hideable. Settings, help, and the app-wide options search (SEARCH-001) can be opened from the top app bar of every primary destination. Selecting the navigation item of the section that is already shown returns to that section's start: nested screens are popped, detail panes closed, the list scrolled to the top, and the first tab selected (SHELL-001, revised 2026-11-22, ADR-040).

### Adaptive layouts and form factors

- Adapt layouts to the current app window size rather than assuming that a device is always a phone, tablet, or TV. Support portrait/landscape, split-screen, and resizable windows where available.
- **Phone:** compact single-column layout, bottom navigation, reachable primary actions, and clear drill-down screens.
- **Tablet / wide window:** use additional space for two-pane list/detail layouts, side navigation where appropriate, and denser summaries without stretching phone-sized controls across the screen.
- **Android TV / Google TV:** provide a remote-first experience navigable with D-pad and Select/Back. Use visible focus states, large readable controls, safe margins, and no touch-only gestures. Prioritize the tools that can sensibly operate on a TV; do not imply that a phone-oriented capability is available there.
- Detect device features and runtime capabilities before presenting actions. Hide irrelevant tools or show them as unavailable with a short reason. Examples: battery monitoring only when the device exposes a battery; torch controls only when flash hardware exists; hotspot controls only when supported by the OS/device; screen-lock gestures only on suitable touch devices. Never infer capability solely from the form-factor label.
- Keep feature availability distinct from layout: a tablet may have a torch and battery, while a TV usually will not. Available features should remain usable on any form factor that exposes the required capability.

### Language and theme settings

- Translate all user-facing strings, accessibility labels, onboarding, permission explanations, notifications, and empty/error states into Italian and English in the first release.
- Initial language follows the Android system language. In Able Settings offer **System default**, **Italiano**, and **English**; persist a manual override until the user selects System default again.
- Where supported, integrate with Android's per-app language settings so the system Settings page and Able's own selector stay consistent.
- Initial theme follows the system. Offer **System**, **Light**, and **Dark** in Able Settings, applying the choice throughout the app and its widgets where supported.

### Home dashboard

The Home screen is organized as a fully modular, card-based dashboard. Every major information block and tool shortcut is encapsulated in a configurable Material 3 card.

**Default card ordering:**

1. **Battery state & active profile:** battery percentage, charging state, current active profile, and quick profile switcher.
2. **Quick actions:** customizable action buttons (such as flashlight toggle, screen lock, hotspot, or profile trigger).
3. **Capability & permission status:** connection state for Shizuku, Accessibility, Usage access, and notification permissions.
4. **Recent actions and warnings:** log of recent profile transitions, manual force-stops, or rejected actions.
5. **Storage summary:** available/used storage overview with direct link to the Storage tool.
6. **Favorite shortcuts:** user-pinned shortcuts or shortcut collections for quick access.

**Customization and safety rules:**

- **Full modularity:** the user can freely reorder cards, hide cards that are not needed, and re-enable them from a Home customization sheet or Settings.
- **Reset to default:** a one-tap action restores the recommended card order and visibility.
- **Safety persistence:** critical alerts (such as automation failure, revoked permissions required by an active profile, or Shizuku disconnect) are rendered through a high-priority safety card or persistent banner so they cannot be accidentally buried by card reordering.
- **Responsive adaptation:** on compact phone screens, cards are displayed in a single scrollable column; on wide/tablet screens, cards adapt into a 2- or 3-column responsive grid; on Android TV, cards are presented in a D-pad navigable grid with visible focus states. Card order and visibility preferences persist locally and are included in configuration export.

## Feature requirements

### Battery and background management

#### Profiles (revised 2026-11-22, v0.18)

Profiles are user-creatable and editable (BAT-001, ADR-005 revised). Able ships five built-in profiles as **editable defaults**: they can be edited and reset to their default configuration but not deleted. User-created profiles can be renamed, duplicated, and deleted (deletion asks for confirmation and first deactivates the profile).

| Built-in profile | Default trigger (disabled until the user enables it) | Default behavior |
| --- | --- | --- |
| Normale | None (always the baseline when no other profile is active) | No app restrictions. Show system battery information and optional suggestions. |
| Notte | Schedule 00:30–07:30 every day, and screen off for 20 minutes | Apply each selected app's configured background action. Suggest alarm, calling, messaging, health, and wearable apps for the protected list. |
| Risparmio | Battery below 25% while unplugged, reset above 35% or on charging | Apply selected app actions and offer Android Battery Saver. |
| Emergenza | Battery below 10% while unplugged, reset above 20% or on charging | Apply the user's more restrictive selected-app rules and offer Android Battery Saver. |
| Pausa app | Manual | Show the selected apps and let the user stop them with one action. |

**Trigger types (each profile, each trigger individually enabled, all disabled by default):**

- **Battery below:** activation threshold and a higher reset threshold (hysteresis; the editor rejects a reset threshold not above the activation threshold), with an **only while unplugged** option (when on, charging ends the trigger).
- **Schedule:** start and end time and the days of the week; a window crossing midnight belongs to the start day.
- **Screen off for N minutes:** activates after the screen has been off continuously for N minutes; waking the screen ends this trigger.
- **Charging:** active while the device is charging.
- **Manual:** the user activates or deactivates the profile from Battery, Home, a tile, a widget, or a shortcut; a manual activation stays until the user ends it.

A profile is active while any of its enabled triggers is true (logical OR). On a fresh install every trigger is off and only Normale is effective.

**Concurrent profiles and conflict resolution (BAT-002 revised):** several profiles can be active at the same time. For each app Able computes the effective action across all active profiles: **the most restrictive action wins**, ordered Osserva < Limita in background < Arresta quando esco. Protected apps are always excluded from every profile. The UI shows, for each app action applied, the profile(s) it comes from, and the Status screen lists all active profiles with the trigger that activated each. When a profile ends, Able recomputes the effective action from the remaining active profiles and reverts only changes it applied and can attribute to itself.

**History note:** v0.17 and earlier used a fixed priority Emergenza → Risparmio → Notte → Normale with one effective profile and Notte's two hard-coded triggers. That rule is superseded on 2026-11-22; the built-in defaults above preserve the earlier thresholds and times.

#### Per-app intervention levels

Each app can have a distinct action for each profile:

1. **Osserva:** show available usage and system information without changing behavior.
2. **Limita in background:** use a supported Android app restriction or an advanced Shizuku operation.
3. **Arresta quando esco:** after a configurable delay, force-stop the selected app after it leaves the foreground. Explain that notifications, alarms, synchronization, and push delivery may stop until the user opens it again.
4. **Congela o disattiva:** advanced, explicit, reversible action. Keep separate from default automatic profiles and explain that the app becomes unavailable until restored.

Users need a protected-app list, per-profile exclusions, an action log, and a restore screen. The UI must distinguish “suspended” from “stopped”: suspending an app can block user interaction without preventing its background work.

#### Battery diagnostics

- Show Android-provided battery level, charging state, temperature, and app-use information where available.
- Mark estimates and incomplete data clearly.
- Optional Shizuku diagnostics may read additional system battery information; validate availability and accuracy on Pixel 8 Pro before promising per-app drain or wake-lock details.
- Provide a controlled comparison or history view, but do not claim that an action saved battery unless a reliable before/after measurement supports it.
- Do not enable global aggressive Doze timing or radio/sensor shutdown by default.

#### Live battery monitor

Provide an optional live monitor in the Battery section, with user-selectable outputs: an ongoing notification, a home-screen widget, or both. The monitor service must have a visible on/off control and use a sampling strategy that does not needlessly wake the phone.

The monitor should record separate screen-on and screen-off sessions and show:

| State | Metrics |
| --- | --- |
| Screen on | Duration, battery percentage used, and estimated active drain per hour |
| Screen off | Duration, battery percentage used, deep-sleep and awake time and percentages when accessible, and estimated idle drain per hour |
| Charging | Battery percentage, temperature, current, voltage, estimated charging power, and estimated time to full when reported by Android |

User controls should include:

- Start or stop the monitor service.
- Reset statistics on charger connection, at a chosen battery percentage, or after reboot; each reset rule is optional.
- Set an idle-drain alert threshold.
- Choose Celsius or Fahrenheit.
- Show battery percentage in the ongoing notification and optionally include charging current/power.
- Choose compact or detailed notification/widget layouts.
- Choose local battery-history retention: **30 days**, **90 days (default)**, or **until manually deleted**; provide **Delete history now**.

Persist per-session summaries, not an indefinite high-frequency stream of raw readings. Discard temporary samples after calculating each session summary. When retention expires or the user deletes history, also delete the idle-drain baseline derived from that history; keep profiles, app rules, and other configuration. Standard configuration export continues to exclude battery/usage history.

Battery percentage rates are estimates over the monitor's recorded interval. Current, voltage, and wattage depend on hardware and can be missing or approximate. Deep-sleep and awake statistics may require Shizuku access to BatteryStats and must be shown as unavailable when the device does not expose them.

The notification is the live surface. The widget is a convenient summary, not a guaranteed real-time display: Android limits scheduled widget refreshes, so update it in response to battery events or user interaction and keep periodic refreshes infrequent. The persistent monitor must remain opt-in because frequent sampling and a foreground service can themselves use battery.

### Double tap to lock or turn off screen

- Goal: double tap to turn off the display and lock the device both on the launcher home screen and on the lock screen, without a screen-off widget, as in the referenced "Double Tap Screen Off" app.
- Route: the explicitly enabled Able Accessibility service detects the gesture and calls `GLOBAL_ACTION_LOCK_SCREEN`; clearly explain setup and unlock behavior.
- Home-screen and lock-screen detection are both in scope (LOCK-001, revised 2026-11-22), but remain device-, launcher-, and system-dependent. Each context is tested separately and reported as supported or blocked.
- **Home-screen context (LOCK-003, added 2026-11-22):** a double tap on *empty* home-screen space turns the screen off. The Accessibility service observes touchscreen events without consuming them, so the launcher keeps receiving every touch; taps on icons, widgets, folders, docks, and search bars are ignored. Able recognizes the launcher as the foreground window and decides "empty space" from the accessibility window/node tree at the tap position.
- **Platform status (checked 22 Nov 2026):** the public `AccessibilityServiceInfo.setMotionEventSources()` (API 34) and `onMotionEvent()` deliver touchscreen events to the service, but the [`onMotionEvent` reference](https://developer.android.com/reference/android/accessibilityservice/AccessibilityService#onMotionEvent(android.view.MotionEvent)) states that events from those sources "are not sent to the rest of the system", so this route would consume launcher touches and is not acceptable. Non-consuming "observed" motion-event sources exist in AOSP for Android 15+ (`setObservedMotionEventSources`), but at the check date it is marked as a flagged test API and is not part of the public SDK reference. LOCK-003 is therefore **platform-dependent**: Able enables the non-consuming route only where a supported public API (or a verified device behavior) exists, and otherwise offers the fallback.
- **Fallback:** a transparent, resizable Able home-screen widget that the user places over empty home-screen space; a double tap on the widget turns the screen off. The widget is the documented route where non-consuming observation is unavailable.
- **Modes (revised 2026-11-22, ADR-039):** the home-screen context offers two modes the user chooses between.
  - **Light (Leggera):** the existing watcher that infers double taps from accessibility event timing only, without intercepting touches. Owner observation on the Pixel 8 Pro (2026-11-22): unreliable (missed and false detections). It stays available, labelled "may miss taps".
  - **Precise (Precisa):** while the launcher home screen is the foreground window, and only then, the Accessibility service shows a full-screen transparent accessibility overlay (`TYPE_ACCESSIBILITY_OVERLAY`) over the launcher. The overlay receives every touch and classifies it as tap, double tap, swipe, or long press. A double tap on empty space turns the screen off; every other touch is **replayed** to the launcher: a tap or long press through `dispatchGesture()` (or `ACTION_CLICK`/`ACTION_LONG_CLICK` on the node under the touch when that is more reliable), and a swipe as a dispatched gesture path. The overlay excludes the system gesture areas (status bar, navigation/gesture bar and back-gesture edges, using the system gesture insets) so system navigation is never intercepted. The overlay is removed immediately when the launcher leaves the foreground (app opened, Recents, notification shade, lock screen, keyboard shown).
  - **Trade-offs disclosed in setup and "?" help:** replayed touches arrive at the launcher later (a single tap must wait for the double-tap window to expire before it is replayed), drags and widget scrolling may feel less smooth, multi-touch gestures (pinch) and some launcher gestures may not replay faithfully, and a launcher update can change behavior. Able measures and shows the replay delay in the self-test. The mode is part of the Play Accessibility declaration (R-022) and depends on R-032.
  - **Reference app:** the referenced double-tap app was studied only through its manifest (declared services, overlay/accessibility configuration); no decompilation or code reuse.
- Do not claim that the Pixel 8 Pro can detect a double tap from every lock-screen state until tested; if a launcher or the OS blocks detection in a context, Able says so and offers alternatives.
- A Quick Settings tile and a home-screen shortcut remain reliable alternatives (LOCK-002).

### Command shell

Confirmed by the owner on 2026-11-22 (CMD-001, ADR-029). An aShell-like tool in Strumenti for advanced users:

- Runs commands typed by the user as the **Shizuku shell user** (ADB shell UID); never as root and never through any other route. Unavailable, with an explanation and a Shizuku setup route, when Shizuku is missing, stopped, or not granted.
- Streams standard output and error live, shows the running state, and offers **Stop** (terminates the process) at any time. Shows the exit status when the command ends.
- **History** of executed commands is stored locally only, is excluded from Android Auto Backup / device-to-device transfer and from `.ablebackup` export, and can be cleared.
- **Saved snippets:** the user can save, name, edit, and delete reusable commands. Selecting a snippet places it in the input field; it never runs by itself.
- **Copy and share output** through the Android clipboard and share sheet, only after a user action.
- **Dangerous commands** (for example package uninstall/disable/clear, `rm`, settings writes, reboot, `wm`/density changes, `svc` power/data changes) show a warning that names the risk and require an explicit confirmation before running. The pattern list is a proposal (ADR-034) and is advisory: a command not matched is not described as safe.
- **Never auto-runs:** no command runs from a profile, trigger, schedule, tile, widget, button shortcut, import, or snippet without the user pressing Run in the shell screen.
- **Action log:** each run records the command text, start time, exit status (or stopped/failed), and duration in the Able action log; command output is never written to the action log.

### App lock

Confirmed by the owner on 2026-11-22 (APPLOCK-001, ADR-030):

- The user selects apps to protect. When a protected app comes to the foreground, the Able Accessibility service detects it and Able opens its own lock activity, which shows the **system** authentication prompt (`BiometricPrompt` with `BIOMETRIC_STRONG` or `DEVICE_CREDENTIAL`). Able draws **no overlay windows** (no `SYSTEM_ALERT_WINDOW`, no accessibility overlay).
- On successful authentication the user returns to the protected app. On cancel, error, or back, Able sends the user to the Android Home screen.
- **Grace period:** Immediately (default), 1, 5, or 15 minutes after a successful unlock; every protected app re-locks when the screen turns off.
- Requires a device screen lock (PIN, pattern, or password). Without one the feature cannot be enabled and Able offers the Android security settings route.
- **Honest limits** shown in setup and in contextual help: app lock is not OS-level enforcement and can be bypassed by disabling the Accessibility service or by apps that are already open in split-screen/picture-in-picture; Recents previews of protected apps may still show content; the protected app may be visible for a moment before the prompt appears; the feature depends on Accessibility being enabled; on devices with Android Advanced Protection active, third-party (non-accessibility-tool) accessibility services are blocked, so app lock is unavailable there (see the platform note below).
- App lock is user-started configuration; it never locks Able's own recovery screens or Android Settings in a way that prevents disabling it.

### Button shortcuts

Confirmed by the owner on 2026-11-22 (KEY-001, ADR-031):

- Gestures: **volume up + down pressed together**, **double press volume up**, **double press volume down**, **long press volume up**, **long press volume down**.
- Targets: Able Action Catalog entries (torch, NFC, tools such as spirit level or compass, screen lock, ringer mode, and other catalog actions), camera launch, any launchable app, or an exported activity that Android reports as launchable (ACT-001 rules).
- The Accessibility service filters key events (`FLAG_REQUEST_FILTER_KEY_EVENTS`) and **consumes only gestures that match an enabled mapping**; other presses reach the system. Because a key event cannot be consumed after the fact, a key with a double/long-press mapping may delay its single-press volume step until the gesture is decided; Able discloses this (R-028).
- Works while the screen is on. Screen-off key events are not generally delivered to accessibility services, so screen-off use is not promised; where a device delivers them, Able reports it only after verification.
- **Unsupported:** power-key remapping. Android does not deliver the power key to third-party accessibility services; Able lists it as unsupported rather than offering it.
- Holding both volume keys is also Android's system accessibility shortcut; Able's "pressed together" gesture must trigger on a short press and must not block the system shortcut.
- Destructive actions (package changes, shell commands) cannot be bound to a button gesture.

### Options search

Confirmed by the owner on 2026-11-22 (SEARCH-001, ADR-032):

- A search action in the top app bar of every primary destination (Home, Batteria, App, Strumenti) opens an app-wide options search.
- The index covers tools, battery options, apps features, settings, tiles & widgets, and Home customization, with Italian and English titles and keywords; a query in either language finds the option.
- Selecting a result opens the destination screen (and, where possible, scrolls to and highlights the option); unavailable features appear with their unavailable reason instead of a dead link.
- Recent searches are stored locally only, can be cleared, and are excluded from `.ablebackup` export.
- The index is bundled with the app; searching makes no network request and never changes state.

### Experimental Pixel and system tools (Pixel ToolBox features)

These features are included in v1 as experimental capabilities subject to an upfront technical feasibility gate on Google Pixel 8 Pro. Each feature is disabled by default, explicitly opt-in, clearly labeled as experimental/device-dependent, and capability-gated:

- **Always On Display on new notifications:** temporarily wake or activate AOD when a high-priority notification arrives, using available system settings or Shizuku/Accessibility routes.
- **Always On Display while charging:** activate AOD automatically upon connecting to power and return to the previous state when disconnected.
- **Close all apps in Recents:** trigger a system recents cleanup action via verified Accessibility or Shizuku access.
- **Screen-off fingerprint unlock:** assess enabling or expediting screen-off biometric recognition if platform/firmware APIs on Pixel 8 Pro allow it.
- **Custom ringtone-mode Quick Settings tile:** toggle device ringer modes (Normal, Vibrate, Silent) via Android's `TileService` and Notification Policy access.
- **Camera quick launch based on device orientation:** evaluate device orientation immediately when waking the display and launch the camera if held in a designated posture, reading sensors only during the wake event to avoid continuous background drain.

If Android security restrictions, private APIs, or firmware limitations block any of these operations on Pixel 8 Pro or other devices, Able must report the feature as unsupported or failed rather than crashing or simulating success.

### Flashlight

- Toggle torch on/off.
- Show brightness levels supported by the device; offer level control on Android 13+ when the hardware exposes it.
- Provide saved brightness shortcuts, SOS, Morse, and interval/BPM modes.
- Provide a Quick Settings tile.
- Volume-button torch shortcuts are provided through Button shortcuts (KEY-001) with optional Accessibility access.
- Report when the phone's flash hardware does not support intensity control.

### Hotspot

Revised 2026-11-22 (HOT-001, HOT-002, ADR-038): Able implements **real hotspot control through Shizuku**, following the approach of the Delta app (Shizuku-backed calls to the system tethering and Wi-Fi SoftAP APIs, which are system APIs not available to ordinary apps):

- **On/off:** start and stop Wi-Fi tethering; the state shown is read back from the system (off, starting, on, stopping, failed with reason).
- **SoftAP configuration:** SSID, password (show/hide), security type (WPA2, WPA3/transition, open where allowed), band (2.4 GHz, 5 GHz, 6 GHz, or combined where the device supports it), hidden SSID, maximum connected clients, auto-off when idle (with timeout where the API exposes it), and MAC randomization where supported. Only the options the device reports as supported are shown.
- **Connected clients:** list of connected devices (name or MAC, connection time where available) and, where supported, a device blocklist.
- **QR code** to join the hotspot, shown only after a user action; the password is never written to logs, crash reports, or the standard backup.
- A Quick Settings tile toggles the hotspot.
- Optional Bluetooth rule: turn hotspot on when a selected paired device connects and off when it disconnects.
- **Without Shizuku** Able shows the hotspot state it can read, opens Android's tethering settings for changes, and states that direct control needs Shizuku.
- **Honest unsupported states:** each option that a device or Android build rejects or does not expose is shown unavailable with the reason; every change is read back and a rejected change shows the actual value. Because the APIs are system/hidden APIs, they may change or break after an Android update (R-030); Able detects missing methods at runtime and reports "not supported on this Android build" instead of crashing.

### App background controls and app management

- List installed user and system apps with search, categories, and per-app detail.
- Show background-control state, protected status, and recent Able actions.
- Support manual force-stop and optional automatic rules for selected apps.
- List the full installed-package set using `QUERY_ALL_PACKAGES` (ADR-028); filter by state (enabled, disabled, uninstalled for user, archived), user/system, UAD list, and removal level (APP-006).
- With Shizuku, disable/enable and uninstall-for-user system or user packages. Every removed or disabled package is listed in an Able **archive** that offers restore (`cmd package install-existing` for uninstalled-for-user packages, `pm enable` for disabled packages). On Android 15+ offer Android's native app archiving where available (APP-004).
- **APK backup before uninstalling a user app (APP-004 revised 2026-11-22, ADR-033).** Observed on 2026-11-22 on the Android 16 / API 36.1 emulator: `pm uninstall -k --user 0` on a user (non-system) app keeps its data, but Android deletes the APK, so `cmd package install-existing` later fails; system apps restore correctly because their APK stays on the system partition. Therefore, before uninstalling a user app for the user, Able copies its APK file(s) (base and splits) into Able's private storage, shows the backup size, and restores from that backup through a Shizuku package-install session. If the backup fails (no space, unreadable APK), uninstall proceeds only after an explicit confirmation that restore will not be possible from Able. Without a backup, Able offers reinstall from the original store (data kept where Android retained it). Backups can be reviewed and deleted from the Able archive, and the space used is shown.
- Package knowledge base (ADR-026): show the bundled UAD-ng description, list (Oem, Aosp, Google, Carrier, Misc), removal level (Recommended, Advanced, Expert, Unsafe), dependencies and needed-by relations, and links. Packages absent from the snapshot are labelled "no information", not "safe".
- Show warnings driven by the UAD removal level before system app removal or disabling, use separate confirmations, and keep an action history and recovery guidance.
- Do not present memory use as evidence that an app is draining battery. The manual RAM and processes tool (MEM-001) uses the honest copy rules below; "clean RAM" or "boost" claims remain excluded.

### Background permissions management

Added 2026-11-22 (APP-007, ADR-036), modelled on NoMoreBackground (all apps' background settings in one place):

- Per app and in bulk (multi-select, "select all user apps", filter by user/system), the user can **restrict** or **allow** background execution through Shizuku AppOps: `RUN_ANY_IN_BACKGROUND` (the "Allow background usage" switch of Android's app battery settings) and `RUN_IN_BACKGROUND` (legacy background-service limits for older target SDKs). Optionally the user can also set the app's **standby bucket** (for example `rare` or `restricted`) where the device supports it.
- Every change is **read back**; the list shows the actual AppOps mode and bucket, and a mismatch or rejection is shown as failed with the actual value.
- Every change is logged in the action log with prior and new value; the user can restore the prior values of a bulk change.
- **Warnings** explain that restricted apps may deliver notifications late, miss alarms, stop syncing, or stop background playback. **Protected apps** (BAT-003) and system/core packages are excluded from bulk selection; changing a system package needs a separate confirmation with the UAD removal-level warning (APP-006).
- Without Shizuku the tool is unavailable for changes; Able explains why and offers Android's per-app battery settings.
- These are manual changes; battery profiles keep using their own Limita in background action (BAT-003), which may use the same AppOps route.

### RAM and processes

Added 2026-11-22 (MEM-001, ADR-037), inspired by Shappky; in Strumenti next to Storage. Owner decision: this tool is allowed even though the product previously avoided "clean RAM" language; automatic RAM cleaning stays excluded.

- **RAM summary:** total, available, and used memory and the low-memory threshold from `ActivityManager.MemoryInfo`, with a chart and a text equivalent.
- **Running apps and processes** with memory per app/process (PSS/RSS where readable) through Shizuku (for example `dumpsys meminfo` / `dumpsys activity processes`); without Shizuku only the summary is shown with an explanation.
- **Force-stop** an app (`am force-stop`) or **kill** its background processes (`am kill`), each after a confirmation that says what will happen; results are read back (process still present or not) and logged.
- **Block list (Lista di blocco):** a user-curated list of apps the user stops together with one confirmed action. Protected apps, the launcher, the keyboard, Able itself, and core system processes cannot be added and are never stopped by the bulk action.
- **Honest copy:** Able states that Android manages memory itself, that free RAM is not a goal, that stopped apps may restart and may then use more battery, and that notifications, alarms, and sync may stop for force-stopped apps. Able makes no battery-saving, speed, or "boost" claims and shows memory before/after only as a measurement.
- Nothing in this tool runs automatically: no schedule, trigger, or profile can start it.

### Storage and cleanup

- Show free and used storage with categories where Android exposes them.
- Find large files with a default threshold of >100 MB and quick filter chips for 50 MB, 100 MB, 500 MB, and 1 GB.
- Find old downloads with a default threshold of >30 days and quick filter chips for 7, 30, and 90 days.
- Present items for review with name, path, size, date, and thumbnail/preview where supported.
- Clean Able's own cache directly.
- Storage visualization (STO-003): used/free overview chart, breakdown by category (apps, images, video, audio, documents, downloads, other) where Android exposes it via `StorageStatsManager` / MediaStore, and largest folders/files. Any deletion from the visualization follows the STO-001 review-before-delete rules.
- Use Android's Storage Access Framework (SAF) / MediaStore for user-selected files and folders; require explicit user confirmation before any deletion.
- System-wide cache cleanup (STO-002): with Shizuku, an explicit user action trims caches of all apps (for example `pm trim-caches`) and reports the actual freed space measured before/after. Without Shizuku, Able opens Android's storage settings and explains why it cannot clear other apps' caches. Warn that cleanup can take time and that apps rebuild caches.
- Do not promise direct access to other apps' private data directories on modern Android.

### Shortcut Maker

Keep this as a separate tool from Activity Launcher. The Shortcut Maker editor is in scope for v1 implementation, modelled on the referenced Shortcut Maker app (SHORT-001..SHORT-003). Support shortcut creation for:

- Apps and launchable app activities.
- Android settings or system actions that can be launched safely.
- User-selected files and folders.
- Websites.
- Able actions such as a selected battery profile, flashlight mode, or screen-lock action.

Before creating a shortcut, offer a preview and editable label/icon. Icon options should include the app's available icon, text/emoji, and a user-selected image. Provide label visibility and size options, icon size, favorites, creation history, and collections/folders if supported reliably by the launcher. When an existing non-empty collection is deleted, Able prompts the user to choose whether to move contained shortcuts to an 'Unsorted' collection or delete them permanently, ensuring custom configurations and icon assets are never lost unintentionally.

Android/launcher confirmation is expected when pinning a shortcut. Able cannot guarantee that every launcher preserves a custom icon exactly.

### Activity Launcher

- Keep the category separate from Shortcut Maker.
- Search activities grouped by installed app.
- Show only activities that Android reports as normally launchable.
- Activity details should offer **Open** and **Create shortcut**.
- **Create shortcut** opens the shared Shortcut Maker editor, then requests pinning through Android.
- Show a clear unavailable state when a component is private, disabled, or blocked by Android.

### GPS coordinate saver and catalog

A location utility designed for quick bookmarking, organizing, and sharing points of interest:

- **User-initiated capture:** requests location fix only when the user explicitly triggers capture or opens the GPS screen (requires contextual `ACCESS_FINE_LOCATION`). Never runs background location tracking.
- **Rich point metadata:** records latitude, longitude, altitude (where available), accuracy estimate in meters, and capture timestamp.
- **Catalog & organization:** allows assigning custom names/labels, category tags (e.g. Parking, Trekking, Work, Photography), and text notes.
- **Offline-first:** points are stored in local persistence and do not require network connectivity to save or search.
- **Sharing & map launch:** one-tap actions to copy formatted coordinates (decimal, DMS), generate standard `geo:` intent to open in the user's preferred mapping app (Google Maps, OpenStreetMap, OsmAnd), or share as a shareable link.
- **Backup inclusion:** saved points and tags are included in `.ablebackup` exports under the user data section.

### Physical measurement tools

Integrated real-world measurement utilities taking advantage of onboard device sensors:

- **Spirit level / Inclinometer:**
  - 2D circular bubble level for flat surface leveling and dual-axis pitch/roll angle meters for vertical alignment.
  - Live numerical degree readout with 0.1° resolution.
  - Zero-reference calibration button (allowing relative angle measurements).
  - Subtle haptic feedback when perfectly level (0° ± 0.5°).
- **Screen ruler:**
  - Visual centimeter/millimeter and inch scale rendered along screen edges.
  - Automatically scaled using physical display DPI (`DisplayMetrics.xdpi` and `ydpi`).
  - Interactive fine-tuning calibration slider (e.g., against a standard physical card).
- **Digital compass:**
  - Heading readout in degrees with cardinal directions.
  - Magnetic north vs True north toggle where location declination is available.
  - Sensor accuracy indicator and figure-8 calibration guidance.
- **Resource discipline:** Sensor listeners are registered only while the measurement screen is active in the foreground and immediately unregistered on pause/stop. Unavailable sensors (e.g. on Android TV) show a clear unsupported state.

### Advanced system toggles

Quick-access tiles and in-app switches for system and developer settings, implementing a two-tier access architecture (direct toggle when privileged; settings intent fallback when unprivileged):

- **NFC Quick Settings tile (follows `pcolby/nfc-quick-settings`, GPL-3.0; revised 2026-11-22):**
  - Basic mode (no special access): the tile and in-app switch open Android's NFC settings page.
  - Advanced mode: when the user has granted `WRITE_SECURE_SETTINGS` to Able via ADB (`pm grant <able package> android.permission.WRITE_SECURE_SETTINGS`, shown in setup with copy), the tile toggles NFC directly through the system `NfcAdapter` enable/disable calls that require that permission.
  - Shizuku route: when `WRITE_SECURE_SETTINGS` is not granted but Shizuku is connected, Able toggles NFC through Shizuku (or offers to grant `WRITE_SECURE_SETTINGS` to itself through Shizuku).
  - The tile reflects the real adapter state (off, turning on, on, turning off) from `NfcAdapter` and the state-change broadcast; a failed toggle shows the actual state.
- **Auto-brightness tile:**
  - Directly toggles `Settings.System.SCREEN_BRIGHTNESS_MODE` between Automatic and Manual.
  - Requests `WRITE_SETTINGS` ("Modify system settings") through standard Android user consent flow.
- **USB and Wireless Debugging toggles:**
  - Dedicated Quick Settings tiles and in-app switches to enable/disable USB Debugging (`adb_enabled`) and Wireless Debugging.
  - Direct toggling supported via Shizuku / `WRITE_SECURE_SETTINGS`; fallback opens Developer Options.
  - In-app screen displays current ADB connection state, IP address, and port when wireless debugging is active.
  - **Details (revised 2026-11-22):** the "details" action opens Android's **Wireless debugging** page directly (pairing code, paired devices). Fallbacks in order: the Wireless debugging settings fragment route, then Developer options, then Android Settings, each with a short explanation; the route used is detected at runtime because it is not a public settings action.
- **Private DNS quick switcher:**
  - Quick Settings tile cycling between Off, Automatic, and configured custom provider.
  - In-app manager allows adding, renaming, and selecting DNS-over-TLS hostnames (e.g., Cloudflare, AdGuard, NextDNS, Google, Quad9).
  - Direct switching via `WRITE_SECURE_SETTINGS` / Shizuku; fallback opens Private DNS settings.

### Customizable Quick Settings tiles and multi-action widgets

Extensible shortcut interfaces allowing users to control Able features directly from Android's system shade and home screen:

- **Customizable Quick Settings tiles:**
  - Exposes multiple declared Android `TileService` instances (Custom Tile 1, 2, 3, 4).
  - Users can bind each tile slot to any action from the unified `AbleAction` catalog (e.g. Flashlight, Battery Profile switch, Screen Lock, Hotspot toggle, NFC, Auto-brightness, GPS Quick-Save, Private DNS cycle).
  - Tiles reflect real-time state and dynamic labels of the assigned action.
  - Tile icons come from a curated Able icon set (BRAND-001); users can create custom tiles from any Action Catalog entry.
- **Multi-action customizable home-screen widgets:**
  - Built with Jetpack Glance/Compose for fluid resizing and Material You dynamic color support.
  - Flexible widget grid templates (e.g., 2x1 quick toggle bar, 4x1 status bar, 4x2 dashboard).
  - Users customize each slot independently by selecting an action from the catalog.
  - Supports combining multiple diverse actions in a single compact home-screen widget (e.g. Flashlight + Profile Pausa + GPS Save + Storage Clean).
  - Widget configurations persist across device reboots and are included in configuration backup.
  - Users can create custom widgets from the Action Catalog.

### Guidance, feedback, and brand identity

Confirmed by the product owner on 2026-11-22:

- **Guided tour (UX-001):** after first-run onboarding, a skippable tour explains each section (Home, Batteria, App, Strumenti, Settings) and its main options. It can be replayed from Settings → Help.
- **Contextual help (UX-002):** advanced or risky options show a "?" info button that opens a dialog explaining what the option does, its risks, and how to undo it.
- **Haptic feedback (UX-003, revised 2026-11-22):** confirmations, toggles, and completed actions give haptic feedback, and so do sliders (a light tick at each step, a confirm on release), buttons, chips, switches, and list rows. A Settings switch turns it off (default on); Able respects the system haptics setting. The haptics map is in the adaptive UI specification.
- **Predictive back and expressive motion (UX-004, revised 2026-11-22):** support Android's predictive back gesture. Primary-destination transitions slide horizontally in the direction of the tab order (moving to a tab on the right slides content in from the right) with a very light spring overshoot. Opening a card or menu uses a container transform like launching an app: the card expands into the full-screen destination, and back or predictive back shrinks it back into the card. With the "Remove animations" accessibility setting, transitions are instant or reduced to a fade. **Defect recorded 2026-11-22 (v0.18):** the container transform glitches on small tool cards (jumps, wrong return position); it must be fixed: no jumps, shrink back into the originating card, works with predictive back, and falls back to a fade when the originating card is not visible.
- **Readability (NFR-ACC-002, revised 2026-11-22):** concise primary text, progressive disclosure for long explanations (for example compact access-checklist rows with expandable explanations, SET-002), 48dp minimum touch targets, WCAG AA contrast, TalkBack labels, and collapsing headers that never overlap content. Card headers never squeeze titles: status pills and "?" buttons sit beside the title only when the title fits, otherwise under it, and a title is never forced into one-word lines. Raw system values (epoch timestamps, enum or integer codes, raw byte counts) are always formatted for humans (localized date/time, named state, KB/MB/GB), with the raw value available as secondary text where useful.
- **Brand identity (BRAND-001, ADR-027):** Able's own logo, adaptive and monochrome launcher icon, in-app logo, and Quick Settings tile icon set, consistent in light/dark themes and with dynamic color. The "Essentials" app by sameerasw is visual inspiration only. **Status 2026-11-22 (v0.18):** the owner asked for a rebrand; the redesign is in progress, options are proposed in [`docs/brand/branding-options.html`](../brand/branding-options.html), and the decision is pending. No option is accepted until the owner chooses.

### Additional toolbox candidates

- Action history with undo/restore.
- Permission and capability checklist.
- Quick Settings tiles and widgets for selected Able actions.
- Guided cleanup with review-before-delete.
- Profile preview showing affected apps before activation.
- Export/import for user configurations (required for v1; detailed below).

### Battery diagnostics, analytics, and hardware health suite

These capabilities are confirmed for v1. They expand Able's battery diagnostics while maintaining local-first storage, honest reporting without false causality claims, and battery-conscious design:

1. **Personal idle-drain baseline (BASE-001, BASE-002):** compare a user's screen-off drain against their own prior nights, grouped by comparable duration and charge range. Show “higher than your usual range,” not a diagnosis or a universal good/bad score.
2. **App impact review (BAT-REV-001):** after a user selects an app to observe, summarize the system-reported background activity available to Able, its confidence/coverage, and provide a direct link to Android's battery settings. Suggest reversible interventions only when supported by evidence.
3. **Before/after profile review (BAT-REV-002):** compare similar observation windows before and after a user-enabled rule. Label results as an observed change, account for differences in screen-on time/charging, and never claim causal savings from simple correlation.
4. **Charging session diary (BAT-CHG-001):** record start/end, temperature profile, supported current/power readings, and flag unusually warm sessions using a configurable threshold without changing charging behavior.
5. **Battery anomaly checklist (BAT-CHK-001):** guide the user through recent high drain, weak signal, hotspot use, screen-on time, and background apps; link to relevant Android settings instead of promising hidden system toggles.
6. **Hardware battery health and lifecycle (aBattery capabilities) (BAT-HLT-001, BAT-HLT-002):**
   - **Public API (Android 14+):** Read `BatteryManager.EXTRA_CYCLE_COUNT` from `Intent.ACTION_BATTERY_CHANGED` to display battery cycle counts without privileged permissions.
   - **Advanced hardware health via Shizuku/System APIs:** Query State of Health (SoH) percentage, design capacity vs current full charge capacity (in mAh), battery manufacture date, first usage date, and charging policy.
   - **Honest capability gating:** Where an OEM, kernel, or security layer (such as Samsung Knox or unpopulated HAL) blocks or returns unpopulated values, clearly display "unavailable on this device" rather than guessing or fabricating numbers.
7. **Battery event notes (BAT-NOTE-001):** let the user annotate a session (travel, hotspot, navigation, gaming) so later comparisons have context, stored locally and excluded from export unless deliberately selected.

#### Detail: personal idle-drain baseline

**Status:** Accepted for v1 (BASE-001, BASE-002).  
**Goal:** Help the user notice that the phone's screen-off battery loss differs from its own normal pattern, without comparing it to an arbitrary universal score or claiming a specific app caused the change.

**Measurement rules (to validate on Pixel 8 Pro):**

- Use the existing opt-in battery monitor data; do not create a second always-running service.
- Compute the baseline from battery level, screen state, and charging state only; the baseline itself does not require Usage access or Shizuku.
- A candidate valid session is screen-off, unplugged, has continuous start/end readings, and is long enough to reduce whole-percent rounding noise. Proposed minimum: four hours; validate against Pixel readings.
- Exclude or mark partial sessions that include charging, reboot, monitor interruption, large gaps, or substantial screen-on time.
- Show “collecting baseline” until there are enough comparable nights. Initial calibration: at least five valid sessions in the last fourteen days; adjust only after observing data quality.
- Compare each valid night to a rolling personal median and observed range, grouping similar duration and starting battery ranges. Display number of samples and a confidence/coverage label.
- Alert only when the session is clearly outside the user's own range and meets a configurable minimum difference. Do not hard-code an unvalidated percentage threshold.
- Do not enable baseline alerts automatically. Once the baseline is established, show a one-time, dismissible in-app offer to enable alerts. Choosing “Not now” leaves alerts off; the user can enable them later in Battery settings. Do not repeat the offer automatically after dismissal.
- Request notification permission only after the user chooses to enable alerts. If enabled, send at most one alert after a qualifying night/session is complete. Let users disable alerts, inspect history, or delete local measurements.

**Baseline screen:** baseline status (collecting / established / insufficient data), latest valid night (% per hour and duration), personal median/range, number of comparable sessions, a short trend chart, and a “possible factors to review” checklist. The baseline works without Usage access/Shizuku. App-level attribution is a separate, opt-in diagnostic that may request Usage access and/or Shizuku; otherwise link to Android Battery settings. The anomaly itself never triggers force-stop, freeze, disable, or other app action.

**Context diagnostics:** the baseline itself uses only battery level, screen state, and charging state. Temperature, hotspot activity, or network-reception context is an optional, separate diagnostic the user enables when investigating an anomaly. Collect only signals Android exposes under the access already granted; request any additional permission in context, never during first-run setup. Missing signals remain unavailable and do not affect the baseline. Context follows the same local-retention rule and remains excluded from standard configuration export.

**Open design choices:** calibrate a meaningful outlier after Pixel measurements and decide whether the factor checklist shows suggestions by default or only when opened. All records remain local and outside standard configuration export.

| ID | Status | Requirement | Acceptance condition |
| --- | --- | --- | --- |
| BASE-001 | Proposed; not yet v1 scope | After a personal idle-drain baseline is established, offer the user an explicit choice to enable anomaly notifications. | No prompt appears before calibration. The offer is dismissible and shown once; alerts stay off until the user opts in. Notification permission is requested only after opt-in. If enabled, send at most one alert after a completed valid session that exceeds the calibrated personal range. |
| BASE-002 | Proposed; not yet v1 scope | Let the user separately opt in to contextual signals when investigating an idle-drain anomaly. | The baseline works without context signals. Context is off until explicitly enabled; unavailable or permission-blocked signals are labelled and do not block the baseline. Any required permission is requested only when the user enables that context source. |

## Detailed product and implementation specification

This section turns the agreed product direction into requirements that can be implemented and reviewed. Requirement IDs are stable references for future issues and acceptance tests. **MUST** means required for v1; **SHOULD** means expected unless a platform limitation is documented; **MAY** means optional or a later release. Any feature that depends on Shizuku, Accessibility, a hardware sensor, a launcher, or an undocumented system API must report its capability state and must not claim success until the operation is confirmed.

### V1 scope and exclusions

**V1 includes:** responsive Able Home, phone/tablet/TV layouts, Italian and English, system/light/dark themes, user-creatable battery profiles with concurrent activation and the monitor, optional app background controls, background permissions management through Shizuku AppOps, a manual RAM and processes tool, double-tap screen lock on home and lock screen where feasible (including double tap on empty home-screen space or the transparent widget fallback), command shell via Shizuku, app lock with the system authentication prompt, volume-key button shortcuts, app-wide options search, flashlight, Shizuku-based hotspot control and SoftAP configuration where supported, app/activity browsing with the UAD-ng package knowledge base and Able archive/restore, shortcut creation with the Shortcut Maker editor, storage visualization and guided storage cleanup including Shizuku system-wide cache trim, user-reviewed crash reports, guided tour, contextual help, haptics, predictive back, Able brand assets, and configuration export/import.

**V1 does not promise:** a fixed battery-life improvement; root-only operations; clearing another app's private files or data without Android-mediated access; unsupported hidden activities; modifying Android's global power-management internals; OS-level app-lock enforcement; power-key remapping or screen-off key shortcuts; automatic "RAM cleaning" or any memory/battery "boost" claim; or identical behavior across every OEM, launcher, TV, and Android build. Any request that Android rejects must be shown as unavailable or failed, not simulated as success.

### Functional requirements

#### Shell, Home, and settings

| ID | Priority | Requirement | Acceptance condition |
| --- | --- | --- | --- |
| SHELL-001 | MUST | Provide Home, Battery, Apps, and Tools destinations, plus Settings and contextual help. Selecting the navigation item of the section already shown returns to that section's start (revised 2026-11-22, ADR-040). | Every destination is reachable using the current form factor's primary input; Back returns to the prior screen without discarding edits silently; re-selecting the current section's item (bottom bar, rail, or TV drawer) pops nested screens to the section root, closes detail panes, scrolls the root list to the top, and selects the first tab; if the section is already at its start, re-selecting only scrolls to the top; a dirty form still shows the discard/keep prompt before leaving; selecting a different section keeps its previous back stack as before. |
| HOME-001 | MUST | Show battery level, active profile, and primary quick actions at the top of the default Home layout. | On fresh install and default layout, battery/profile and primary quick action cards appear first without switching tabs; on wider windows the same default hierarchy is preserved. |
| HOME-002 | MUST | Provide a modular card system where all dashboard cards and quick actions can be freely reordered, hidden, shown, and reset to default. | Changes persist after process restart; users can hide/show any card, reorder them, and restore the default layout via a single action; hidden actions do not reappear unexpectedly. |
| HOME-003 | MUST | Surface broken automations, missing permissions, and disconnected Shizuku access prominently regardless of card order. | A failed or paused action or missing critical access displays a high-priority safety card or persistent banner with a direct route to details or recovery, even if non-critical cards are reordered or hidden. |
| SET-001 | MUST | Offer system/Italian/English language and system/light/dark theme controls. | Selection persists across restart; selecting system language/theme restores OS-following behavior. |
| SET-002 | MUST | Show a capability/permission checklist grouped by feature, with compact rows and expandable explanations (revised 2026-11-22). | Each row shows the access name and current state in one compact line with an action to enable or revoke it; expanding the row reveals the purpose and consequences; no permission is requested before the related feature is selected. |
| PRIV-001 | MUST | Offer optional, user-reviewed crash diagnostics using the open-source ACRA library: after a crash Able shows the report and the user chooses whether to send it by email to Drago Labs (revised 2026-11-22). | The crash dialog is offered only if the Settings → Privacy opt-in is on; nothing is sent without the per-report user action (the user reviews the report, and the Android email composer to `info.dragolabs@gmail.com` opens only after the user chooses Send); no third-party server, no automatic upload, and no feature-use analytics; reports exclude account identifiers, message/file contents, hotspot credentials, and the installed-app list; turning the opt-in off stops offering reports and deletes stored unsent reports. |
| ABOUT-001 | MUST | Provide Settings → About links to the configured Able GitHub repository, Drago Labs website, and support email, plus third-party attributions (including UAD-ng). | Website opens `https://dragolabs.it`; email opens a compose intent to `info.dragolabs@gmail.com`; GitHub opens `https://github.com/dragolab/able` (configured 2026-11-22; previously "to be configured"); if an external handler is unavailable, Able offers to copy the URL/address and reports the outcome; About shows the UAD-ng attribution, license, and snapshot commit. |
| UX-001 | MUST | After first-run onboarding, offer a skippable guided tour of each section and its main options, replayable from Settings → Help. | Tour starts only after onboarding, can be skipped or exited at any step without side effects, never requests a permission, is localized, is D-pad operable on TV, and can be replayed from Settings → Help; skipping is remembered. |
| UX-002 | MUST | Show a "?" info button on advanced or risky options that opens an explanation dialog (what it does, risks, how to undo). | Every option marked advanced/risky (Shizuku actions, disable/uninstall/archive, system toggles, cache trim, experimental tools) has the button; the dialog is reachable by touch, TalkBack, and D-pad; opening it changes no state. |
| UX-003 | MUST | Give haptic feedback for confirmations, toggles, actions, sliders (step ticks and release confirm), buttons, chips, switches, and list rows, with a Settings switch (default on) that respects the system haptics setting (revised 2026-11-22). | Each component fires the pattern defined in the haptics map (adaptive UI spec) once per interaction; a slider ticks once per step crossed (not per pixel) and confirms once on release; turning the Able switch off or disabling system touch feedback suppresses all of them; devices without a vibrator show no error. |
| UX-004 | MUST | Support the predictive back gesture and expressive motion: primary-destination transitions slide horizontally in the tab-order direction with a very light spring overshoot; opening cards/menus uses a container transform (card expands to full screen; back and predictive back shrink it into the card); respect the "Remove animations" accessibility setting (revised 2026-11-22). | Moving to a tab further right slides content in from the right and vice versa; overshoot is barely perceptible and settles without oscillation; a card-opened destination expands from and returns to that card's bounds, and predictive back previews the shrink; predictive back falls back to standard back on unsupported versions; dirty-form back rules still apply; with "Remove animations" enabled, transitions are instant or a plain fade; on small tool cards (Strumenti grid, Home quick actions) the transform has no position or size jump at start or end, the content does not flash or overlap during the transform, back and predictive back shrink into the originating card's current bounds (also after scroll or rotation), a cancelled predictive back restores the full screen without a jump, and when the originating card is not visible the return uses a fade (defect criteria added 2026-11-22). |
| SEARCH-001 | MUST | Provide an app-wide options search reachable from the top bar of every primary destination, indexing tools, battery options, apps features, settings, tiles & widgets, and Home customization with Italian and English keywords; recent searches stored locally (added 2026-11-22, ADR-032). | The search action is present on Home, Batteria, App, and Strumenti; a query in Italian or English matches titles and keywords in both languages; selecting a result opens its destination (highlighting the option where possible); unavailable features show their reason; recent searches persist locally, can be cleared, and are excluded from export; no network request; searching changes no state; no results shows an empty state; TalkBack and D-pad can operate search on phone, tablet, and TV. |
| BRAND-001 | MUST | Provide Able brand assets: adaptive and monochrome launcher icon, in-app logo, and a curated Quick Settings tile icon set. Status 2026-11-22: rebrand requested by the owner; redesign in progress; options proposed in `docs/brand/branding-options.html`; decision pending. | Launcher icon renders as adaptive and themed (monochrome) icon; logo and tile icons are legible in light, dark, and dynamic color themes; assets are original Drago Labs work and not copied from inspiration apps. |

#### Battery profiles and app background controls

| ID | Priority | Requirement | Acceptance condition |
| --- | --- | --- | --- |
| BAT-001 | MUST | Provide user-creatable and editable battery profiles. Normale, Notte, Risparmio, Emergenza, and Pausa app are built-in editable defaults that cannot be deleted and can be reset; each profile has its own triggers (battery below with activate/reset thresholds and only-unplugged option, schedule with days, screen off for N minutes, charging, manual), all disabled by default (revised 2026-11-22, ADR-005). | On a fresh install every trigger of every profile is disabled and only Normale is effective; the user can create, rename, duplicate, edit, and delete custom profiles (delete asks for confirmation and deactivates first); built-ins offer Reset to default and no Delete; each trigger is enabled individually; a reset threshold not above the activation threshold is rejected with a message; a schedule without any day selected cannot be enabled; active profiles and the trigger that activated each are visible; users can pause automation; profile names are unique and localized built-in names follow the UI language unless renamed. |
| BAT-002 | MUST | Allow several profiles to be active at once and resolve per-app conflicts so that the most restrictive action wins (Osserva < Limita in background < Arresta quando esco); protected apps are always excluded; the UI shows the source profile of each applied action (revised 2026-11-22; replaces the fixed priority Emergenza → Risparmio → Notte → Normale). | With two or more active profiles, each app's effective action equals the most restrictive of its actions across active profiles; a protected app gets no action from any profile; the app list and Status screen show the source profile(s) of each effective action; activating or ending a profile applies only the resulting differences and is idempotent (repeating an event changes nothing); ending a profile reverts only Able-attributed changes not still required by another active profile; Normale is shown as effective only when no other profile is active. |
| BAT-003 | MUST | Let users set an intervention level and profile exclusions for each app in each profile, including user-created profiles (revised 2026-11-22). | Before enabling a restrictive level, show affected apps and likely notification/sync impact; protected apps are excluded from every profile until the user explicitly changes that status; the app detail lists each profile's action for that app and which one is currently effective and why. |
| BAT-004 | MUST | Offer Observe as the no-change default and separate manual actions from automatic rules. | First-run setup makes no app restriction; user confirms a preview before activating automated changes. |
| BAT-005 | MUST | Record every Able-applied app-state change with app, prior state when retrievable, new state, trigger, time, and outcome. | History distinguishes applied, skipped, failed, reverted, and unknown state; restore is offered only when safe and supported. |
| BAT-006 | MUST | Handle charging, reboot, app update, Shizuku loss, and permission revocation without silently leaving Able's state inconsistent. | On resume/reconnect, Able reconciles actual state, reports conflicts, and offers a safe recovery path; it does not repeatedly retry a rejected operation. |
| BAT-007 | MUST | Never use automatic "RAM cleaning" or force-stop every app as a default battery policy. A manual, honest RAM and processes tool (MEM-001) is allowed (revised 2026-11-22, owner decision). | The standard profile path changes only explicitly selected apps and explains that restrictions can interrupt user-visible behavior; no profile, trigger, or schedule stops apps because of memory use; the MEM-001 tool runs only on user action and makes no battery-saving or "boost" claim. |
| BAT-008 | MUST | After the user reviews and enables a profile, execute its preconfigured actions automatically at each trigger without asking for confirmation again. | Each transition creates an action-log entry and a notification summarizing applied/skipped/failed actions; notification delivery failure does not suppress the configured action. Freeze/disable actions cannot run automatically. The user can pause the profile or automation at any time. |

**Profile transition rules (revised 2026-11-22):** each profile is active while any of its enabled triggers is true. Built-in defaults: Risparmio activates below 25% while unplugged and resets above 35% or on charging; Emergenza activates below 10% while unplugged and resets above 20% or on charging; Notte uses a schedule (00:30–07:30, every day) and screen off for 20 minutes, and stays active while charging. All triggers are disabled by default. A battery-below trigger uses separate activation and reset thresholds so user edits cannot make it oscillate; with only-unplugged on, charging ends it. A screen-off trigger ends when the screen wakes. A manual activation stays until the user ends it. Pausa app is manual by default and never scheduled implicitly. When the set of active profiles changes, Able recomputes the per-app effective action (most restrictive wins, protected apps excluded) and applies only the differences. Superseded rule (v0.17): one effective profile chosen by the fixed priority Emergenza → Risparmio → Notte → Normale.

#### Battery monitor and diagnostics

| ID | Priority | Requirement | Acceptance condition |
| --- | --- | --- | --- |
| MON-001 | MUST | Show screen-on and screen-off intervals separately, with duration and percentage change. | Incomplete intervals after reboot or monitor interruption are marked partial, not presented as full sessions. |
| MON-002 | MUST | Calculate active/idle drain per hour only across a valid elapsed interval. | Zero-duration, charging transitions, missing readings, and percentage rounding produce “not enough data” instead of divide-by-zero or misleading rates. |
| MON-003 | MUST | Show deep-sleep/awake time only if the selected data source supplies it. | Unavailable metrics are labelled unavailable and are never fabricated from screen-off duration. |
| MON-004 | MUST | Allow optional persistent notification and home-screen widget independently or together. | The user can stop the monitor from Able and from its notification; widget staleness is indicated or corrected on the next permitted refresh. |
| MON-005 | MUST | Provide reset on plug-in, selected battery threshold, and reboot as separate optional rules. | Each rule can be enabled/disabled and has a visible current setting; one reset event is not counted twice. |
| MON-006 | MUST | Show temperature and supported charging values with units and an accuracy note. | Unsupported current, voltage, wattage, or time-to-full is omitted or marked unavailable; values are not described as laboratory-accurate. |
| MON-007 | MUST | Alert when idle drain exceeds the user's threshold only after a minimum valid sample window. | Alert includes measurement window and action to inspect data; it does not identify an app as the cause without evidence. |
| MON-008 | MUST | Keep continuous monitoring opt-in and battery-conscious. | Monitoring starts only after an explicit user action, has a clear persistent indicator when required, and uses the least frequent sampling that supports displayed metrics. |
| MON-009 | MUST | Let users control local battery-history retention and delete history immediately. | Default is 90 days; alternatives are 30 days and until manually deleted. Expiration/deletion removes session summaries and derived idle-drain baseline, but preserves profiles and app rules. High-frequency readings are temporary and not retained as long-term history. |
| BASE-001 | MUST | After a personal idle-drain baseline is established, offer the user an explicit choice to enable anomaly notifications. | No prompt appears before calibration. The offer is dismissible and shown once; alerts stay off until the user opts in. Notification permission is requested only after opt-in. If enabled, send at most one alert after a completed valid session that exceeds the calibrated personal range. |
| BASE-002 | MUST | Let the user separately opt in to contextual signals when investigating an idle-drain anomaly. | The baseline works without context signals. Context is off until explicitly enabled; unavailable or permission-blocked signals are labelled and do not block the baseline. Any required permission is requested only when the user enables that context source. |
| BAT-REV-001 | MUST | Summarize system-reported background activity for an observed app, showing confidence and links to Android battery settings. | Summarizes available background time/foreground time without claiming direct causality; links to system App Info; does not require root. |
| BAT-REV-002 | MUST | Provide before/after profile comparison for similar observation windows. | Compares metrics across matching duration/screen-off conditions; labels differences as observed correlations without promising guaranteed savings. |
| BAT-CHG-001 | MUST | Maintain a local charging session diary recording duration, start/end battery %, temperature, and power metrics. | Logs start/stop of charging; flags sessions exceeding user-set or safe thermal threshold; does not alter charging hardware behavior. |
| BAT-CHK-001 | MUST | Provide an interactive battery anomaly checklist to troubleshoot excessive drain. | Guided flow covering weak signal, hotspot, screen-on time, and heavy background apps; links directly to relevant Android system settings without requiring continuous background polling. |
| BAT-NOTE-001 | MUST | Allow users to annotate battery sessions with custom notes and context tags. | Notes and tags (travel, gaming, hotspot, navigation) are stored locally with the session; deleted automatically upon session expiry or manual deletion; excluded from standard backup unless selected. |
| BAT-HLT-001 | MUST | Read and display battery charge cycle count using Android 14+ public APIs where supported. | Reads `EXTRA_CYCLE_COUNT` from `ACTION_BATTERY_CHANGED`; shows cycle count or an honest 'unavailable on this device hardware' status if unsupported by HAL. |
| BAT-HLT-002 | SHOULD | Provide advanced hardware battery health data (State of Health %, capacity in mAh, manufacture date, first use date, charging policy) via Shizuku/system access. | Displays health metrics when Shizuku or system access is granted and hardware exposes them; gracefully reports unavailable on restricted OEM layers (e.g. Knox or unpopulated kernel nodes) without crashing or fabricating numbers. |

#### Double tap, flashlight, hotspot, storage, and shortcuts

| ID | Priority | Requirement | Acceptance condition |
| --- | --- | --- | --- |
| LOCK-001 | MUST | Offer double tap to turn off/lock the screen both on the launcher home screen and on the lock screen, using an explicitly enabled Accessibility service and a supported lock mechanism (revised 2026-11-22; the home-screen context is detailed by LOCK-003). | Setup explains every required access; home-screen and lock-screen contexts can be enabled and tested separately; a test action confirms lock behavior per context; if the launcher or OS prevents gesture detection in a context, Able reports that context as unsupported, presents supported alternatives, and does not claim support for it. |
| LOCK-002 | SHOULD | Offer a Quick Settings tile and a pinned Able shortcut for screen lock as alternative entry points. | Each route reports when Android/launcher does not allow pinning or operation. |
| LOCK-003 | MUST (platform-dependent) | Turn the screen off with a double tap on empty home-screen space, using the Accessibility service; ignore taps on icons, widgets, folders, dock, and search bar; offer a Light mode (timing-only watcher, no touch interception) and a Precise mode (launcher-only full-screen accessibility overlay that classifies taps/swipes/long presses and replays non-matching touches to the launcher, excluding system gesture areas); where neither works, offer a transparent resizable Able home widget that turns the screen off on double tap (added 2026-11-22; Precise mode added 2026-11-22 v0.18, ADR-039). | With the route active, every launcher touch still reaches the launcher (icons open, scrolling and long press work); a double tap on empty space turns the screen off within the configured double-tap interval; a double tap on an icon/widget does nothing extra; detection runs only while the launcher is the foreground window; where no public non-consuming API or verified device behavior exists, Able states that the direct route is unsupported and offers the widget, and never enables a route that consumes launcher touches; the widget fallback turns the screen off on double tap and ignores single taps; Accessibility disabled → feature shown inactive. **Precise mode:** the overlay exists only while the launcher home is the foreground window and is removed within one frame of the launcher losing focus (app launch, Recents, shade, lock screen, IME); a double tap on empty space turns the screen off; a single tap on an icon opens it, a long press opens the launcher's long-press menu, and a horizontal swipe changes the home page, all through replay; system gesture areas (status bar, gesture/navigation bar, back edges) are never covered, so back, home, and Recents gestures work unchanged; the self-test measures and shows the replay delay; when a replay fails (gesture cancelled or no node), Able logs it and the self-test reports it; multi-touch gestures that cannot be replayed are disclosed; Light mode is labelled "may miss taps". |
| TORCH-001 | MUST | Toggle the device torch and expose brightness levels only when supported. | Missing flash or unsupported intensity removes or disables the related control with an explanation. |
| TORCH-002 | SHOULD | Provide saved levels, SOS/Morse/interval modes, and a Quick Settings tile, with explicit stop behavior. | Every flashing mode has an on-screen/notification stop action and stops on camera conflict, feature loss, or service failure. |
| HOT-001 | MUST | Show hotspot status and, through Shizuku following the Delta approach, turn the hotspot on/off and edit the SoftAP configuration (SSID, password, security, band, hidden, maximum clients, auto-off); list connected clients; offer a join QR code (revised 2026-11-22, ADR-038). | State is read back from the system and shown as off/starting/on/stopping/failed with reason; each setting is read back after an attempted change and unsupported or rejected settings show the actual value and failure reason; only options the device reports as supported are offered; an invalid SSID/password (length, charset, security rules) is rejected before applying; the clients list updates on connect/disconnect or shows "unavailable" when not exposed; the QR code appears only after a user action and the password never appears in logs, crash reports, or the standard backup; without Shizuku Able shows readable state, opens Android tethering settings for changes, and claims no direct control; a missing hidden API on the current build is reported "not supported on this Android build" without a crash. |
| HOT-002 | SHOULD | Support advanced options (MAC randomization, client blocklist, auto-off timeout) and optional selected-Bluetooth-device automation only where Shizuku/API capability is verified (revised 2026-11-22). | Rule has independent enable, off-on-disconnect behavior, and last-run status; user can disable it without revoking all Bluetooth access. |
| STO-001 | MUST | Analyze user-visible storage categories and identify large files (>100 MB default) and old downloads (>30 days default) with adjustable filter chips for user review. | Displays large files and old downloads matching selected filter chips (50MB/100MB/500MB/1GB and 7/30/90 days); files are not deleted until the user selects items and confirms deletion in an Android-mediated SAF flow. |
| STO-002 | MUST | Clean Able's own cache directly and offer system-wide cache cleanup: with Shizuku, trim caches of all apps (for example `pm trim-caches`); without Shizuku, open Android's storage settings and explain why (revised 2026-11-22). | Cleanup runs only after an explicit user action and confirmation; Able measures free space before and after and reports the actual freed amount (including zero); a failed or rejected command is reported as failed; without Shizuku no success is claimed and the storage-settings route is offered; Able never claims to clear another app's private files/data. |
| STO-003 | MUST | Provide a storage visualization: used/free overview chart, category breakdown (apps, images, video, audio, documents, downloads, other where Android exposes it via `StorageStatsManager`/MediaStore), and largest folders/files. | Values come from Android-reported data; unavailable categories are labelled unavailable, not silently estimated; the chart has a text/TalkBack equivalent; any deletion started from the visualization follows STO-001 review-before-delete and Android-mediated confirmation. |
| SHORT-001 | MUST | Keep Activity Launcher and Shortcut Maker as separate entry categories with a shared shortcut editor. The Shortcut Maker editor (modelled on the referenced Shortcut Maker app) is in scope for v1 implementation. | From an activity's details, Create shortcut opens the same editor used by Shortcut Maker; Open and Create shortcut remain distinct actions. |
| SHORT-002 | MUST | Create shortcuts for supported apps, launchable activities, system actions, user-selected files/folders, websites, and Able actions. | Unsupported targets are rejected before pinning with a reason; pin requests go through the Android-supported launcher flow. |
| SHORT-003 | MUST | Support editable label, icon source, preview, favorites, history, and user-created shortcut collections with safe deletion options. | User can create, rename, reorder, and delete collections; deleting a non-empty collection prompts to either move shortcuts to 'Unsorted' or delete them; move shortcuts between collections; see a preview and launcher limitations before pinning. Icon/label variations imposed by the launcher are documented. |

#### App management and Activity Launcher

| ID | Priority | Requirement | Acceptance condition |
| --- | --- | --- | --- |
| APP-001 | MUST | List all installed packages with search, user/system filters, and an app detail page, using `QUERY_ALL_PACKAGES` (revised 2026-11-22, ADR-028). | Names/icons are resolved from Android; the list covers the full installed-package set on builds that hold `QUERY_ALL_PACKAGES`; a build without it (possible Play flavor) states that the list is limited; unavailable app metadata does not crash the list. |
| APP-002 | MUST | Show each app's protected status, configured profile actions, and available battery/usage evidence. | Missing usage access or system data is clearly marked; app drain is not inferred from memory use or shown as exact when Android provides only estimates. |
| APP-003 | MUST | Allow a user-initiated force-stop where supported. | The confirmation explains that notifications, alarms, sync, and background work may stop; Able reports actual success/failure and does not silently relaunch the app. |
| APP-004 | MUST | Allow app disable/enable and uninstall-for-user through Android-supported flows or optional Shizuku, keep an Able archive of removed/disabled packages with restore (`cmd package install-existing` for system apps, `pm enable`), and offer Android 15+ native app archiving where available. Before uninstalling a user (non-system) app, back up its APK(s) to Able's private storage and restore them through a Shizuku install session; without a backup, offer reinstall from the original store (revised 2026-11-22 twice; second revision after the verified `install-existing` failure for user apps, ADR-033). | Disable, uninstall-for-user, and archive each require a separate confirmation whose warning reflects the UAD removal level (Expert/Unsafe shown prominently; packages without information warned as unknown); for a user app the confirmation shows the APK backup size and the backup completes and is verified (file count and size) before uninstall; a failed backup blocks uninstall until the user explicitly confirms that Able cannot restore it; Able records prior/current status when readable, reads back the result, adds the package to the archive only after verified success, and offers restore and recovery guidance; restore of a user app installs the backed-up APK(s) via Shizuku and is verified by readback (package present, version matches the backup); a missing/corrupt backup offers the original-store route instead and does not claim success; backup storage per package and in total is shown and each backup can be deleted; native archiving is hidden where unsupported. No operation targets a protected/core app automatically. |
| APP-005 | MUST | Keep disable, uninstall, and freeze actions separate from automatic battery profiles. | Such actions require a direct user action and cannot run from a schedule, battery threshold, or Bluetooth rule. |
| APP-006 | MUST | Provide a package knowledge base from the bundled UAD-ng snapshot (ADR-026) with search and filters by state (enabled, disabled, uninstalled for user, archived), user/system, UAD list, and removal level; package detail shows description, dependencies/needed-by, and links. | Filters combine and persist during the session; packages missing from the snapshot are labelled "no information" (never "safe"); the snapshot commit/date is visible; the knowledge base works offline and is never updated silently from the network. |
| APP-007 | MUST | Provide background permissions management: per app and in bulk, restrict or allow background execution through Shizuku AppOps (`RUN_ANY_IN_BACKGROUND`, `RUN_IN_BACKGROUND`) and optionally set the standby bucket; read back, log, warn, and safeguard protected and system apps (added 2026-11-22, ADR-036). | Without Shizuku the tool shows the reason, makes no change, and offers Android's per-app battery settings; each change is read back and the list shows the actual AppOps mode and bucket; a rejected or mismatched change is shown failed with the actual value; bulk selection excludes protected apps and system/core packages; a system package change needs a separate confirmation showing its UAD removal level; the confirmation lists the affected apps and the risks (late notifications, missed alarms, no sync); every change is logged with prior and new values and a bulk change can be restored from the log; bucket options are hidden where the device does not support setting them; nothing runs automatically. |
| MEM-001 | MUST | Provide a RAM and processes tool in Strumenti next to Storage: RAM summary, running apps/processes with memory via Shizuku, force-stop or kill with confirmation, and a user block list stopped by one confirmed action, with honest copy (added 2026-11-22, ADR-037). | The RAM summary works without Shizuku; without Shizuku the process list is unavailable with the reason; with Shizuku the list shows apps/processes with memory and its source; force-stop and kill each require a confirmation naming the consequences and are verified by readback (process present or gone) and logged; protected apps, the launcher, the keyboard, Able, and core system processes cannot be added to the block list or stopped in bulk; copy states that Android manages memory, apps may restart, and no battery or speed gain is promised, and no screen uses "boost", "clean RAM", or savings claims; no schedule, trigger, profile, tile, or widget runs the tool automatically; memory before/after is shown only as measured values. |
| ACT-001 | MUST | Provide Activity Launcher as a separate category, with searchable activities grouped by installed app. | Only activities Android reports as normally launchable are shown; private/disabled components are omitted or marked unavailable, not exposed by bypassing platform rules. |
| ACT-002 | MUST | Offer Open and Create shortcut as distinct actions on an activity detail. | Open launches the target if Android permits; Create shortcut opens the shared shortcut editor; launch/pin rejection is reported without changing the selected activity. |

#### Experimental Pixel and system tools

These requirements define experimental capabilities derived from Pixel ToolBox. They are prioritized for technical feasibility validation on Google Pixel 8 Pro.

| ID | Priority | Requirement | Acceptance condition |
| --- | --- | --- | --- |
| PIX-001 | SHOULD | Provide an optional setting to trigger AOD on incoming notifications where the device/OS exposes a supported path. | Feature is off by default; if Shizuku, Accessibility, or system setting path is unsupported or rejected by Android, report unsupported without crashing or simulating success. |
| PIX-002 | SHOULD | Provide an optional setting to activate AOD while the device is charging. | Turns on AOD on charger connect and restores prior state on disconnect; unsupported OS state is clearly reported. |
| PIX-003 | SHOULD | Offer an experimental action to close all background apps in Recents via verified system access. | Requires explicit user invocation or shortcut; reports actual outcome; does not claim success if Android blocks the recents action. |
| PIX-004 | SHOULD | Assess and offer screen-off fingerprint unlock assistance if platform APIs permit it on Pixel 8 Pro. | Gated strictly by hardware/OEM support; marks feature unavailable if the ROM/kernel enforces screen-on fingerprint only. |
| PIX-005 | SHOULD | Provide a Quick Settings tile to toggle device ringtone modes (Normal, Vibrate, Silent). | Tile reflects actual audio state; cycles through modes correctly; requests Do Not Disturb / Notification Policy access in context if needed. |
| PIX-006 | SHOULD | Provide an optional feature to quick-launch the camera based on orientation upon screen wake. | Reads sensors only at screen wake; does not run continuous background sensor polling; respects user cancellation. |

#### GPS tools and coordinate catalog

| ID | Priority | Requirement | Acceptance condition |
| --- | --- | --- | --- |
| GPS-001 | MUST | Capture current GPS coordinates with accuracy, altitude, timestamp, custom label, tags, and notes on user request. | Location fix is user-initiated only; requires contextual `ACCESS_FINE_LOCATION`; never runs background tracking; records coordinates and estimated accuracy in meters. |
| GPS-002 | MUST | Provide an offline-first searchable coordinate catalog with map intent launch and quick sharing. | Displays saved points with labels/tags; one-tap share (geo URI, coordinates text, web map link); opens point in user-selected map app; included in configuration backup. |

#### Physical measurement and sensor tools

| ID | Priority | Requirement | Acceptance condition |
| --- | --- | --- | --- |
| SENS-001 | MUST | Provide a spirit level / inclinometer with 2D bubble level, dual-axis angles, zero calibration, and haptic feedback. | Displays pitch and roll with 0.1° precision; haptic pulse at 0° ± 0.5°; zero button enables relative leveling; unregisters sensors when screen pauses. |
| SENS-002 | MUST | Provide an on-screen ruler in centimeters/millimeters and inches calibrated to display DPI with a fine-tuning slider. | Scales accurately to screen DPI; calibration slider allows fine adjustment against physical reference; touch markers measure distances. |
| SENS-003 | MUST | Provide a digital compass showing heading, magnetic/true north, and calibration guidance where magnetometer hardware exists. | Shows heading in degrees and cardinal direction; magnetic/true north toggle; marks feature unavailable when magnetometer is missing (e.g. on TV). |

#### Advanced system toggles

| ID | Priority | Requirement | Acceptance condition |
| --- | --- | --- | --- |
| SYS-001 | MUST | Provide an NFC Quick Settings tile and in-app toggle following `pcolby/nfc-quick-settings`: basic mode opens NFC settings; advanced mode toggles NFC directly when `WRITE_SECURE_SETTINGS` was granted via ADB; Shizuku is a third route (revised 2026-11-22). | Reflects actual NFC state (off, turning on, on, turning off) and updates on the system broadcast; with `WRITE_SECURE_SETTINGS` granted the tile toggles directly; without it but with Shizuku connected the toggle runs through Shizuku; with neither it opens native NFC settings; setup shows the ADB grant command with copy and a revoke note; a failed toggle shows the actual state; devices without NFC hide the tile or mark it unavailable. |
| SYS-002 | MUST | Provide an Auto-brightness Quick Settings tile and in-app toggle via Android's `WRITE_SETTINGS` permission. | Toggles between Automatic and Manual brightness; requests `WRITE_SETTINGS` in context; updates tile state immediately. |
| SYS-003 | MUST | Provide Quick Settings tiles and in-app switches for USB Debugging and Wireless Debugging; the wireless debugging "details" action opens the Wireless debugging page directly with fallbacks (revised 2026-11-22). | Direct toggle via `WRITE_SECURE_SETTINGS` / Shizuku; fallback opens Developer Options; in-app view displays wireless ADB IP, port, and pairing status; "details" opens the Wireless debugging page where the route is available, otherwise Developer options, otherwise Android Settings, with an explanation of the route used; developer options disabled → Able explains how to enable them instead of opening an empty page. |
| SYS-004 | MUST | Provide a Private DNS quick-switch tile and in-app profile manager (Off, Automatic, custom hostname). | Cycles DNS modes or toggles custom hostnames via `WRITE_SECURE_SETTINGS` / Shizuku; fallback opens Private DNS settings dialog; lets user save DNS profiles. |

#### Universal Action Catalog, customizable tiles, and multi-action widgets

| ID | Priority | Requirement | Acceptance condition |
| --- | --- | --- | --- |
| CUST-001 | MUST | Expose customizable Quick Settings tile slots where users can bind any action from the unified `AbleAction` catalog; tile icons come from a curated Able icon set. | User can configure tile slots (1..N) to any supported Able feature and create custom tiles from the catalog; tile updates label, icon (from the Able icon set), and active/inactive state dynamically according to the assigned action. |
| CUST-002 | MUST | Provide multi-action customizable home-screen AppWidgets allowing users to configure multi-slot action bars and dashboards from the Action Catalog. | Widget builder lets user select widget grid size and assign action slots from the catalog to create custom widgets; widgets update dynamically via Glance/Compose; persists across reboots and backup export. |

#### Command shell, app lock, and button shortcuts

Added 2026-11-22 from owner requests (ADR-029–ADR-031). Each depends on Shizuku or the Accessibility service and must report its capability state.

| ID | Priority | Requirement | Acceptance condition |
| --- | --- | --- | --- |
| CMD-001 | MUST | Provide a command shell tool that runs user-typed commands as the Shizuku shell user, streams output, can stop a running command, keeps a local history excluded from backups, offers saved snippets and copy/share of output, warns and asks confirmation for dangerous commands, never auto-runs, and logs command and exit status (not output) to the action log. Requested by the owner as "SHELL-001"; recorded as CMD-001 because SHELL-001 already identifies the app shell. | Without Shizuku (missing, stopped, permission denied) the tool is shown unavailable with the reason and a setup route, and nothing runs; stdout/stderr appear while the command runs; Stop terminates the process and the log records "stopped"; exit status is shown and logged; a command matching the dangerous list shows a warning naming the risk and runs only after confirmation, and cancel runs nothing; history persists locally, can be cleared, is excluded from Auto Backup and `.ablebackup`; snippets can be created, edited, deleted and only fill the input; copy/share happen only on user action; the action log contains command, time, exit status, and duration but never output; no command runs from a trigger, tile, widget, key gesture, import, or snippet selection; Shizuku loss during a run reports the command as failed/unknown, not succeeded. |
| APPLOCK-001 | MUST | Protect user-selected apps: when one comes to the foreground, Able's own activity shows the system authentication prompt (`BiometricPrompt` with `BIOMETRIC_STRONG` or `DEVICE_CREDENTIAL`) without overlay windows; cancel goes to Home; grace period Immediately/1/5/15 minutes; re-lock on screen off; requires a device screen lock; honest limits stated (added 2026-11-22, ADR-030). | Opening a protected app shows the system prompt from Able's lock activity; success returns to the app; cancel/back/error sends the user to Android Home; within the grace period the app opens without a prompt and after it a prompt appears; screen off re-locks all protected apps; Able declares no `SYSTEM_ALERT_WINDOW` and draws no overlay; without a device screen lock (`canAuthenticate` not successful for the authenticators) the feature cannot be enabled and the security-settings route is offered; Accessibility disabled or revoked → app lock is shown inactive and Home shows a safety warning while apps are configured as protected; under Advanced Protection or any state where the service cannot run, Able reports app lock unavailable; setup and "?" help state that it is not OS-level enforcement, that Recents previews may show content, and that it depends on Accessibility; Able Settings and Android Settings are never locked in a way that prevents disabling the feature. |
| KEY-001 | MUST | Map hardware-key gestures (volume up + down together, double press volume up/down, long press volume up/down) to Able Action Catalog actions, camera launch, any launchable app, or a launchable exported activity, consuming only matching gestures, with the screen on; power-key remapping is recorded as unsupported (added 2026-11-22, ADR-031). | Each gesture can be bound, changed, or cleared independently; a bound gesture runs its target once and is consumed; unbound keys reach the system unchanged; on a key with a double/long-press mapping, a single press still changes volume once the gesture is decided (any added delay is disclosed in setup; if Android cannot re-deliver it, Able states that the single-press volume step is lost for that key); key down/up streams stay well-formed; the "together" gesture does not block Android's hold-both-volume-keys accessibility shortcut; with the screen off no action is promised and Able states it; power key is listed as unsupported with the reason; a target that is uninstalled or no longer launchable reports failure and is marked unresolved; destructive targets (package changes, shell commands) cannot be selected; Accessibility disabled → shortcuts shown inactive. |

#### Configuration export and import

| ID | Priority | Requirement | Acceptance condition |
| --- | --- | --- | --- |
| CFG-001 | MUST | Before each export, let the user choose which configuration sections to include, then choose a destination using Android's document picker. | Sections are independently selectable: battery profiles and app rules; Home cards/quick actions and appearance preferences; shortcut definitions and collections; custom shortcut icon assets. No prior section selection is silently reused. Export succeeds without broad storage permission and reports the saved filename/location. |
| CFG-002 | MUST | Export selected sections into a versioned `.ablebackup` archive containing a machine-readable JSON manifest/configuration and any selected custom shortcut icon assets. | Manifest identifies schema version, Able version, creation time, and included sections; archive has no executable content, and import does not rely on internal absolute paths. |
| CFG-003 | MUST | Exclude usage history, battery-monitor measurements, OS permission grants, Shizuku state, installed app data, and hotspot passwords by default. | Export preview lists included/excluded sections; no excluded data is silently included. |
| CFG-004 | MUST | Validate archive structure, schema version, sizes, checksums, and configuration values before applying an import; show a preview. | Invalid/corrupt/unsupported files make no changes and give an actionable error; preview shows counts, unsupported items, conflicts, and target-device differences. Parser must not execute archive content or trust paths supplied by the archive. |
| CFG-005 | MUST | Offer merge and replace modes with an explicit confirmation and conflict handling; do not preselect either mode. | Merge preserves unrelated current settings and lets the user choose existing vs imported values for conflicting items; replace summarizes overwritten Able configuration. The user can cancel before any mutation. |
| CFG-006 | MUST | Handle apps/activities/shortcuts missing on the target device. | Missing package or activity rules are skipped or marked unresolved; import never installs, disables, or uninstalls apps as a side effect. |
| CFG-007 | MUST | Make export/import recoverable, including shortcut definitions and custom icon files. | Before replace-mode import, save a local rollback snapshot; after import, offer an undo/restore route and log the result. Android launcher-pinned shortcuts are not silently recreated; Able explains when the user must pin them again. |

### Primary user flows

1. **First launch:** detect form factor and available features → display language/theme from system → explain local-first data handling and offer the optional crash-report opt-in → show capability checklist → allow Skip → offer the skippable guided tour (UX-001) → land on Home with battery/profile and quick actions. Never request Accessibility, Usage access, notifications, or Shizuku as a batch on first launch. Declining crash reports leaves them off without repeated prompts; the choice remains available in Settings → Privacy.
2. **Configure battery profile:** open Battery → Profiles → choose a profile or Create profile → name it → add and enable triggers (battery below, schedule with days, screen off for N minutes, charging, manual) → select per-app actions/exclusions → review consequences, protected apps, and conflicts with other profiles (most restrictive wins) → confirm → show active profiles and next trigger. Cancel leaves prior state unchanged. Built-ins offer Reset to default instead of Delete.
3. **Enable live monitor:** open Battery monitor → select notification/widget destinations and reset/alert options → explain persistent monitoring → request notification access if required → start → show initial-data waiting state → update notification/widget as readings arrive. Stop preserves or clears stats according to the chosen reset policy, not implicitly.
4. **Set up double tap:** open Screen lock tool → explain Accessibility and lock permission/setup → open Android settings → return to Able → choose home-screen and/or lock-screen context → run a test gesture per context → confirm result → show fallback tile/shortcut for any unsupported context. For the home screen (LOCK-003), Able checks whether a non-consuming observation route is supported; if not, it offers to place the transparent double-tap widget.
5. **Create shortcut from an activity:** Activity Launcher → search/group by app → inspect activity details → choose Open or Create shortcut → shared editor preview → edit name/icon → ask Android to pin → report launcher success/cancellation.
6. **Clean storage:** Storage → choose category → inspect file sizes/dates → select items → review total and paths → confirm → open Android document/delete confirmation where required → refresh scan and show actual outcome.
7. **Export configuration:** Settings → Backup/transfer → choose sections → show privacy exclusions → Android Save dialog → write versioned export → confirm success. **Import:** choose file → validate → preview merge/replace and conflicts → create rollback snapshot → user confirms → apply supported settings → show skipped/unresolved entries and restore option.
8. **Run a shell command:** Strumenti → Command shell → (Shizuku check) → type or pick a snippet → Run → dangerous-command warning and confirmation if matched → live output → Stop or completion → exit status → optional copy/share. History and the action-log entry (command and exit status only) are recorded locally.
9. **Protect an app:** Strumenti → App lock → check device screen lock → explain Accessibility and limits → enable the service in Android Settings → select apps and grace period → open a protected app → system prompt in Able's lock activity → success returns to the app, cancel goes Home.
10. **Map a button shortcut:** Strumenti → Button shortcuts → explain Accessibility and screen-on limit → choose gesture → choose target (catalog action, camera, app, activity) → test → save.
11. **Search options:** any primary destination → search in the top bar → type (Italian or English) → select result → destination opens with the option highlighted.
12. **Restrict background apps:** App → Background permissions → (Shizuku check) → filter/select apps (protected and system apps excluded from bulk) → Restrict or Allow (optional standby bucket) → review warnings → confirm → readback per app → result list and action-log entry with restore.
13. **Review RAM and processes:** Strumenti → RAM e processi → summary → (Shizuku) process list → select app → Force-stop or Kill → confirmation → readback → result; or edit the block list → Stop block list → confirmation → per-app result.
14. **Control the hotspot:** Strumenti → Hotspot → (Shizuku check) → toggle on/off or edit SoftAP configuration → validate → apply → readback → show clients and optional QR on request.
15. **Return to a section's start:** while inside a nested screen of a section, select that section's navigation item again → Able pops to the section root, closes detail panes, scrolls to the top, and selects the first tab.

### Screen and state inventory

| Area | Screens/states required |
| --- | --- |
| Shell | Home, Battery, Apps, Tools, Settings, Help (guided tour replay), options search (query, results, recent searches, no results), permission/capability checklist with expandable rows, contextual "?" help dialogs, crash-report review dialog |
| Battery | Status (active profiles with activating trigger, effective app actions with source profile, next trigger), Diagnosis, profile list/detail/editor (create, rename, duplicate, delete custom; reset built-in), trigger editor (battery below, schedule with days, screen off N minutes, charging, manual; validation errors), conflict view, app rules, protected list, action history, live monitor, monitor settings, no-data/unsupported states |
| Apps | Search/list, filters (state, user/system, UAD list, removal level), app detail with knowledge-base info, intervention editor, disable/uninstall/archive confirmation, action result, Able archive with restore, APK backup size/failure confirmation, backup storage review/delete, reinstall-from-store route, recovery, Background permissions (Shizuku unavailable, list with actual AppOps mode/bucket, bulk selection, warning/confirmation, partial failure, restore from log) |
| Tools | Torch main/advanced modes, Hotspot overview/on-off state/SoftAP configuration/validation errors/clients/QR/Shizuku unavailable/unsupported build/automation, RAM and processes (summary, Shizuku unavailable, process list, force-stop/kill confirmation, block list, result), NFC tile setup (basic, ADB grant, Shizuku), Wireless debugging details route, Storage overview/visualization chart/category breakdown/largest items/results/review/delete result/system cache cleanup result, Activity Launcher, Shortcut Maker/editor/preview/collections, Screen lock (per-context status, LOCK-003 mode choice Light/Precise, Precise self-test with measured replay delay, route status, transparent widget fallback), Command shell (Shizuku unavailable, input, running/streaming, stopped, exit status, dangerous-command confirmation, history, snippets), App lock (no screen lock, Accessibility off, Advanced Protection/unavailable, app selection, grace period, lock activity with system prompt), Button shortcuts (gesture list, target picker, test, unsupported power key, inactive service) |
| Settings | Appearance, language, theme, Home customization, monitor preferences, notification/tile setup, backup/export/import, privacy/crash reports, haptics, Help, About with GitHub/website/email links, licenses and UAD-ng attribution |
| Shared UI states | Loading, empty, permission denied, capability missing, Shizuku disconnected, OS rejection, partial success, stale data, confirmation, undo available |

### Data, privacy, and recovery rules

- Keep user-created profiles, app rules, interface preferences, shortcuts, and Able's action log on-device in v1 unless a future sync feature is separately approved.
- Command-shell history and recent searches stay on-device, are excluded from Android Auto Backup and from `.ablebackup` export, and can be cleared by the user. Command output is never stored in the action log. Whether saved snippets, app-lock selections, and button-shortcut mappings become selectable `.ablebackup` sections is open (ADR-034).
- APK backups made before uninstalling a user app (ADR-033) are an explicit exception to "do not copy app binaries": they are stored only in Able's private, no-backup storage, are never exported, and are deleted by the user or when the archive entry is removed.
- Store package/activity identifiers rather than copying app binaries or app-private data (except the APK backup of ADR-033). Resolve names/icons at display time because target devices may differ.
- Crash diagnostics use ACRA in a user-reviewed mode (ADR-015). The Settings → Privacy opt-in governs whether the crash dialog is offered at all; even when it is on, nothing is sent until the user reviews a report and chooses to send it by email to `info.dragolabs@gmail.com`. No third-party server, no automatic upload, and no feature-use analytics in v1. Offer the choice during first-run privacy setup and again in Settings → Privacy; declining keeps it off without repeated prompts; turning it off deletes stored unsent reports. Never include account identifiers, message/file contents, hotspot credentials, or the installed-app list. Cloud sync and account requirements remain out of scope for v1.
- Separate configuration from observations: users must be able to export configuration without including battery history or usage/accessibility observations.
- Retain battery session summaries locally for 90 days by default; offer 30 days or until manually deleted, and an immediate delete action. Deleting observations also clears derived baselines, not user-authored settings.
- Record reversible changes with before/after state when Android exposes it. If prior state cannot be read, label restoration as best-effort and ask before applying a guessed state.
- Never store hotspot passphrases in the standard backup. If credential backup is added later, require a separate explicit choice and protected/encrypted handling.

### Definition of done for a v1 feature

A feature is ready only when: (1) its normal flow and cancel path are specified; (2) required permission/capability and unsupported state are documented; (3) success is verified by reading back state or by an Android-confirmed result; (4) errors preserve the user's prior configuration; (5) Italian and English strings/accessibility labels exist; (6) compact, wide, and TV input behavior is defined where the feature appears; (7) destructive/restrictive actions have preview, confirmation, and recovery guidance; (8) its options have Italian and English options-search entries (SEARCH-001, ADR-032); and (9) acceptance conditions above are met on the relevant device class.

## Permissions and system access

| Capability | Purpose | Product handling |
| --- | --- | --- |
| Accessibility Service | Optional double-tap gesture on home and lock screen (LOCK-001, LOCK-003; Precise mode uses a launcher-only accessibility overlay and gesture dispatch, `canPerformGestures`), foreground-app detection for app lock (APPLOCK-001), and key-event filtering for button shortcuts (KEY-001) | Explain access plainly with the Play-required prominent disclosure; keep disabled until the user enables a related feature; each feature using the service can be turned off independently; never declare `isAccessibilityTool` (Able is not an accessibility tool). |
| Biometric / device credential (`USE_BIOMETRIC`) | App lock authentication via the system `BiometricPrompt` | Normal permission; requires a device screen lock; no Able-owned PIN or overlay. |
| Device Admin | Optional immediate device lock where supported | Explain how to deactivate it and what locking does. |
| Shizuku | Advanced app controls and diagnostics, background AppOps (APP-007), process list and force-stop/kill (MEM-001), hotspot control and SoftAP configuration (HOT-001), NFC third route (SYS-001) | Optional; show connection state and degrade gracefully when stopped. |
| `WRITE_SECURE_SETTINGS` (granted via ADB) | NFC advanced mode and other secure-settings toggles (SYS-001, SYS-003, SYS-004) | Never requested at runtime (not grantable by a dialog); setup shows the ADB command with copy; Able checks the grant on each use. |
| Usage access | App-use and battery analysis | Request only when the user enables diagnostics; provide a settings link. |
| Camera permission | Torch control | Request in context when enabling flashlight features. |
| Notifications | Profile/action alerts and status | Request only if notifications are used; keep a non-notification route where possible. |
| Foreground service | User-enabled ongoing battery monitoring | Start only from a user action, show a persistent notification, and offer a clear stop control. |
| Storage Access Framework | User-selected files and folders | Prefer picker-based access instead of broad file access. |
| Package visibility (`QUERY_ALL_PACKAGES`) | Full installed-package list for app management and Activity Launcher | Declared for full listing (ADR-028). Google Play restricts this permission to apps whose core purpose needs it and requires a Permissions Declaration (checked 22 Nov 2026, [Play policy](https://support.google.com/googleplay/android-developer/answer/10158779)); decide the declaration at release time and prepare a Play flavor without it if needed. |
| Shizuku shell commands | Disable/enable, uninstall-for-user, restore (including APK backup install session), cache trim, and the user's own commands in the command shell (CMD-001) | Optional; each Able-initiated command result is verified by readback or measured before/after; user shell commands report exit status only and are never run automatically. |

## Technical constraints and research references

- Android Doze and App Standby already defer background CPU/network work and can affect alarms, sync, and notifications. Able should manage selected apps rather than force global Doze as the normal battery mode.
- Shizuku-dependent changes may stop working when Shizuku is unavailable or after its service stops. Show current access state and whether an action has actually been applied.
- Android 11+ filters visibility of installed packages. Able declares `QUERY_ALL_PACKAGES` for the full list (ADR-028); Play eligibility is not assumed and is decided at release time, possibly with a Play flavor that uses narrower visibility.
- Android 15 (API 35) adds OS-level app archiving through `PackageInstaller.requestArchive`; unarchiving is routed to the app's responsible installer (checked 22 Nov 2026, [Android 15 features](https://developer.android.com/about/versions/15/features#app-archiving)). Able offers native archiving only where it is available and reports that restore depends on the installer.
- Accessibility touch observation (checked 22 Nov 2026): `setMotionEventSources()`/`onMotionEvent()` (API 34) consume events from the requested sources, so they cannot be used for LOCK-003 on the launcher; the non-consuming observed-sources API seen in AOSP for Android 15+ is not in the public SDK reference. LOCK-003 is feasibility-gated with the widget fallback.
- Accessibility key filtering (checked 22 Nov 2026): with `FLAG_REQUEST_FILTER_KEY_EVENTS`, `onKeyEvent()` receives key events before apps and returning true consumes them ([reference](https://developer.android.com/reference/android/accessibilityservice/AccessibilityService#onKeyEvent(android.view.KeyEvent))). The power key is handled by the system before accessibility dispatch and is not offered; screen-off delivery is not promised (to be confirmed per device in T-KEY-01).
- App lock authentication (checked 22 Nov 2026): `BIOMETRIC_STRONG | DEVICE_CREDENTIAL` is supported on Android 11+ and `canAuthenticate()` reports whether a credential exists ([biometric guide](https://developer.android.com/identity/sign-in/biometric-auth)); Able's minSdk 33 is within range.
- Google Play Accessibility API policy (checked 22 Nov 2026, [Play policy](https://support.google.com/googleplay/android-developer/answer/10964491)): non-accessibility tools need an in-app prominent disclosure with affirmative consent and a Play Console declaration with a video; only disability-focused apps may set `isAccessibilityTool`. Able uses the service for several features, so the declaration is a release risk (R-022).
- Android Advanced Protection (checked 22 Nov 2026): when active, it blocks or revokes accessibility services of apps that are not accessibility tools. The owner's request cites Android 16; current reports ([Android Developers Blog, Dec 2025](https://developer.android.com/blog/posts/enhancing-android-security-stop-malware-from-snooping-on-your-app-data); [Android Authority](https://www.androidauthority.com/android-17-beta-2-advanced-protection-mode-accessibility-apps-3648860)) describe the enforcement arriving with Android 17 builds. Able treats every Accessibility-based feature as unavailable whenever the service is blocked, on any version (R-023).
- Pinned shortcut creation asks the user to confirm through the launcher. Launcher behavior can alter icon shape, labels, and badges.
- Android limits periodic widget refresh frequency; live battery values belong in the opt-in notification, while widgets provide a lower-frequency summary.
- Battery Historian is useful as a development reference but is no longer actively maintained; use it for investigation, not as an Able dependency.
- Background AppOps (checked 22 Nov 2026 against the NoMoreBackground README, which describes the user-facing "Allow background usage" switch and requires Shizuku): `RUN_ANY_IN_BACKGROUND` and `RUN_IN_BACKGROUND` are hidden AppOps changed with `cmd appops set` as the shell user; standby buckets are changed with `am set-standby-bucket`. These are not public SDK APIs and OEMs may override them (R-029); every change is read back.
- NFC direct toggle (checked 22 Nov 2026, [nfc-quick-settings README](https://github.com/pcolby/nfc-quick-settings)): advanced mode uses `NfcAdapter` enable/disable, which are not intended for third-party apps and require `WRITE_SECURE_SETTINGS`, granted once via `adb shell pm grant`; revoking returns to basic mode.
- Hotspot control (checked 22 Nov 2026, [Delta README](https://github.com/supershadoe/delta), BSD-3-Clause): Delta reads and changes hotspot settings through Shizuku and compiles against system API stubs. `SoftApConfiguration` setters and tethering start/stop are system APIs; Able treats them as unstable (R-030).
- Precise home double tap: `TYPE_ACCESSIBILITY_OVERLAY` windows and `dispatchGesture()` (requires `canPerformGestures`) are public accessibility APIs; the replay latency, launcher compatibility, and gesture-inset exclusion are unverified and are the subject of T-LOCK-04 (R-032). Recheck the [AccessibilityService reference](https://developer.android.com/reference/android/accessibilityservice/AccessibilityService) during the spike.
- Wireless debugging page: Android has no public settings action for the Wireless debugging page; the direct route is detected at runtime with fallbacks (SYS-003).

### Reference projects and sources

- [Double Tap Screen Off / Lock on Google Play](https://play.google.com/store/apps/details?id=com.jamworks.doubletaptosleepscreenoff)
- [aShell on GitLab](https://gitlab.com/sunilpaulmathew/ashell): Shizuku-based local ADB shell, inspiration only for CMD-001
- [Android AccessibilityService reference](https://developer.android.com/reference/android/accessibilityservice/AccessibilityService) and [AccessibilityServiceInfo reference](https://developer.android.com/reference/android/accessibilityservice/AccessibilityServiceInfo)
- [Android biometric authentication guide](https://developer.android.com/identity/sign-in/biometric-auth)
- [Google Play Accessibility API policy](https://support.google.com/googleplay/android-developer/answer/10964491)
- User-provided source notes are retained locally under `private-references/source-descriptions/` and excluded from Git; their requested feature ideas are summarized above.
- [FlashDim on GitHub](https://github.com/cyb3rko/flashdim)
- [Shappky on GitHub](https://github.com/YasserNull/shappky): RAM/process tool inspiration (MEM-001), GPL-3.0; no code reuse planned
- [Canta on GitHub](https://github.com/samolego/Canta)
- [Shortcut Maker on Google Play](https://play.google.com/store/apps/details?id=rk.android.app.shortcutmaker)
- [Universal Android Debloater Next Generation (UAD-ng)](https://github.com/Universal-Debloater-Alliance/universal-android-debloater-next-generation): bundled package list snapshot (GPL-3.0, ADR-026)
- [ACRA crash reporting library](https://github.com/ACRA/acra): user-reviewed crash reports (ADR-015)
- [Essentials by sameerasw](https://github.com/sameerasw/essentials): visual-style inspiration only (ADR-027)
- [Delta advanced hotspot settings](https://github.com/supershadoe/delta): Shizuku hotspot control approach (HOT-001, ADR-038), BSD-3-Clause
- [Android BatteryManager reference](https://developer.android.com/reference/android/os/BatteryManager)
- [Android app widget update guidance](https://developer.android.com/develop/ui/views/appwidgets/advanced)
- [Android Doze and App Standby documentation](https://developer.android.com/training/monitoring-device-state/doze-standby)
- [Android pinned shortcut documentation](https://developer.android.com/develop/ui/compose/system/shortcuts/creating-shortcuts)
- [Android package visibility documentation](https://developer.android.com/training/package-visibility/declaring)
- [Android adaptive layouts and window size classes](https://developer.android.com/develop/adaptive-apps/guides/use-window-size-classes)
- [Android TV hardware and input guidance](https://developer.android.com/training/tv/get-started/hardware)
- [Android per-app language preferences](https://developer.android.com/guide/topics/resources/app-languages)
- [Android Storage Access Framework documents and files](https://developer.android.com/training/data-storage/shared/documents-files)
- [Android TV app quality requirements](https://developer.android.com/docs/quality-guidelines/tv-app-quality)
- [Android Battery Historian documentation](https://developer.android.com/topic/performance/power/setup-battery-historian)
- [Stop background app blocker](https://github.com/burakgon/stop)
- [Hail app freezer](https://github.com/aistra0528/Hail)
- [NoMoreBackground](https://github.com/adil192/no_more_background): background permissions management inspiration (APP-007), GPL-3.0-or-later; no code reuse planned
- [nfc-quick-settings](https://github.com/pcolby/nfc-quick-settings): NFC tile modes (SYS-001), GPL-3.0
- [Able public repository](https://github.com/dragolab/able)

### Related engineering documents

- [Use cases](../requirements/use-cases.md)
- [User stories and acceptance criteria](../requirements/user-stories.md)
- [Non-functional requirements](../requirements/non-functional-requirements.md)
- [Requirements traceability](../requirements/traceability-matrix.md)
- [Adaptive UI specification](../ux/adaptive-ui.md)
- [Architecture decisions](../architecture/decisions.md)
- [Conceptual domain model](../architecture/domain-model.md)
- [Risk register](../architecture/risk-register.md)
- [Verification strategy](../verification/verification-strategy.md)
- [Research and reference register](../research/source-register.md)

## Open decisions for the ongoing interview

All core v1 product scope and architectural UI choices are now settled; the 22 November 2026 decisions are recorded in ADR-005, ADR-015, ADR-026, ADR-027, and ADR-028, the v0.17 owner requests in ADR-029–ADR-033, and the v0.18 decisions in ADR-005 (revised), ADR-011 (revised), ADR-023 (revised), ADR-027 (revised), and ADR-035–ADR-040. Implementation proposals for the v0.18 requests (profile resolution algorithm, AppOps commands and bucket values, RAM data sources and kill route, SoftAP API binding, Precise-mode parameters, wireless-debugging route) remain open in ADR-041. The rebrand option is pending the owner's choice (BRAND-001). Implementation proposals for the v0.17 requests (shell execution route, dangerous-command list, LOCK-003 detection route, app-lock foreground detection, key-gesture timings, search index, motion parameters, APK backup location and install path, backup sections for snippets/app lock/key mappings) remain open in ADR-034. The Google Play `QUERY_ALL_PACKAGES` declaration remains a release-time decision. Implementation details (exact local database library, background job vs foreground service scheduling) remain open pending technical prototyping on Pixel 8 Pro, as tracked in ADR-009 and ADR-010.

## Implementation readiness

The product scope and key user choices are defined well enough to begin a UI flow and technical feasibility prototype. Before a full implementation plan is finalized, settle the open decisions above, confirm system-level features on the Pixel 8 Pro, and separate standard Android capabilities from Shizuku- or Accessibility-only behavior.
