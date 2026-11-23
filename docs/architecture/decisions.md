# Able architecture and product decisions

This log separates decisions confirmed by the owner from implementation proposals. Accepted product decisions must not be changed silently. Implementation choices remain open until a feasibility prototype and target-device review.

## Accepted decisions

### ADR-001 — Product identity

- **Status:** Accepted
- **Decision:** Product name Able; owner Drago Labs.
- **Consequence:** Use Able in user-facing names and Drago Labs in ownership/about materials. The Drago Labs website is `https://dragolabs.it` (confirmed 2026-11-22; replaces `https://dragolabs.vercel.app`).

### ADR-002 — First platform target

- **Status:** Accepted
- **Decision:** Android 13+; Google Pixel 8 Pro is the primary phone reference. Phones, tablets, and Android TV/Google TV are v1 form factors. TV remote-first UI and Google Play TV distribution are targets.
- **Consequence:** Hardware-dependent actions are capability-gated. TV Play inclusion must pass the current TV quality process.

### ADR-003 — Local-first v1; no cloud sync

- **Status:** Accepted
- **Decision:** Cloud sync is out of scope for v1. User-created settings stay local except when the user explicitly exports a configuration file.
- **Consequence:** No account/login dependency. No private reference notes in public repository. Crash diagnostics are user-reviewed and sent only by the user's own email action (ADR-015); there is no automatic upload or feature-use analytics in v1.

### ADR-004 — UI identity and language

- **Status:** Accepted
- **Decision:** Material 3 with Android dynamic colors; System/Light/Dark theme choice; Italian and English in v1, following system by default with an in-app override.
- **Consequence:** Localization is a v1 requirement across accessibility, notifications, errors, and onboarding, not a later translation pass.

### ADR-005 — Battery automation policy

- **Status:** Accepted (revised 2026-11-22, v0.18)
- **Decision:** Profiles are configurable and may run pre-authorized app actions automatically with notification/logging. Observe is the safe default. App freeze/disable/uninstall remains manual. **All automatic triggers are disabled by default**; nothing activates until the user enables each trigger (confirmed 2026-11-22; supersedes the earlier decision that Night's triggers are enabled by default).
- **Revision 2026-11-22 (owner decision, v0.18):** profiles are **user-creatable and editable**. Normale, Notte, Risparmio, Emergenza, and Pausa app become built-in editable defaults that cannot be deleted and can be reset. Each profile has its own triggers: battery below (activate/reset thresholds, only-unplugged option), schedule (times and days), screen off for N minutes, charging, and manual, all disabled by default; a profile is active while any enabled trigger is true. **Several profiles can be active at once.** When active profiles set different actions for the same app, **the most restrictive wins** (Osserva < Limita in background < Arresta quando esco); protected apps are always excluded; the UI shows the source profile of each applied action.
- **Consequence:** Profile evaluation must be deterministic, state changes idempotent, and OS-rejected actions reported. The per-app effective action is a pure function of the set of active profiles and the protected list; ending a profile reverts only Able-attributed changes no other active profile still requires. On a fresh install Normale is effective. Status and Diagnosis screens are redesigned to show multiple active profiles (adaptive UI spec). Freeze/disable still cannot be a profile action.
- **Superseded (history):** until v0.17, one effective profile was chosen by the fixed priority Emergency → Savings → Night → Normal, and Night had two hard-coded, independently enabled triggers combined with OR. The built-in defaults keep those thresholds and times.

### ADR-006 — Configuration portability

- **Status:** Accepted
- **Decision:** V1 supports local `.ablebackup` export/import. The user chooses included sections on each export. Import validates and previews data, offers merge/replace, creates rollback before replace, and reports unresolved device-specific items.
- **Consequence:** Standard export excludes battery/usage history, OS permissions, Shizuku state, installed app data, and hotspot credentials. Import never preselects merge or replace; the user chooses each time.
- **Revision 2026-11-23 (owner decision, v0.19, ADR-046):** battery history and measurements may be exported through an optional section that is off by default, never preselected, and preceded by a privacy notice; usage history, permissions, Shizuku state, app data, and hotspot credentials stay non-exportable. Custom battery profiles become their own backup section. The archive moves to schema version 2; import keeps accepting schema 1.

### ADR-007 — Shortcut and activity tools

- **Status:** Accepted
- **Decision:** Activity Launcher and Shortcut Maker remain separate categories. Selecting Create shortcut on an activity opens the shared editor. Collections/folders are required in v1.
- **Consequence:** Pinned shortcuts use the Android/launcher confirmation flow; import may restore definitions but cannot silently pin icons.

### ADR-012 — Battery-history retention

- **Status:** Accepted
- **Decision:** Keep battery-session summaries locally for 90 days by default. Offer 30 days or until manually deleted, plus immediate deletion. Temporary high-frequency readings are not kept as long-term history.
- **Consequence:** Expiring/deleting session history also deletes the derived idle-drain baseline, but preserves profiles and app rules. Battery observations remain excluded from standard configuration export unless the user selects the optional battery-history section (revised 2026-11-23, ADR-046); imported sessions follow the same retention and the import preview warns when they would expire.

### ADR-013 — Opt-in for personal idle-drain alerts

- **Status:** Accepted for the proposed baseline design; the baseline itself is not yet v1 scope.
- **Decision:** After enough valid sessions establish the personal range, Able offers a one-time, dismissible prompt to enable notifications. Alerts remain off until the user opts in; “Not now” can be changed later in Battery settings.
- **Consequence:** Notification permission is requested only after opt-in. No alert is sent for incomplete sessions, and an enabled alert is sent at most once after a qualifying session completes.

### ADR-014 — Optional context for idle-drain diagnosis

- **Status:** Accepted for the proposed baseline design; the feature is not yet v1 scope.
- **Decision:** Keep the core baseline independent of context. Let the user separately opt in to available temperature, hotspot, or network-reception signals when investigating an anomaly.
- **Consequence:** Context sources can be unavailable without affecting the baseline. Request additional access only for a selected source, and delete its observations with the battery history.

### ADR-015 - User-reviewed crash diagnostics (ACRA, email)

- **Status:** Accepted (revised 2026-11-22; replaces the earlier opt-in telemetry with an unselected provider)
- **Decision:** Able uses the open-source ACRA library for crash reports in a user-reviewed mode. After a crash Able shows the report and the user chooses whether to send it by email to Drago Labs (`info.dragolabs@gmail.com`) through the Android email composer. There is no third-party server, no automatic upload, and no feature-use analytics in v1. An opt-in setting in Settings → Privacy (also offered during onboarding) governs whether the crash dialog is offered at all; nothing is sent without the per-report user action.
- **Data boundary:** Reports do not include account identifiers, message or file contents, hotspot credentials, or the installed-app list. The user sees the report content before sending. Exclude the preference and stored reports from configuration backups. Turning the setting off deletes stored unsent reports.
- **Consequence:** PRIV-001 and NFR-PRIV-001/003 are rewritten for crash reports. ACRA's license and notices are added to the third-party license inventory. Any future analytics or server endpoint needs a new product decision.

### ADR-016 — Home dashboard modular card architecture

- **Status:** Accepted
- **Decision:** The Home screen is a fully modular, card-based dashboard. Every dashboard section (Battery & Active Profile, Quick Actions, Capability/Permission Status, Storage Summary, Favorite Shortcuts, Recent Actions/Warnings) is implemented as a distinct, configurable card. The user can freely reorder, show, or hide each card, and reset the dashboard to the default layout at any time.
- **Consequence:** The default initial order places Battery & Active Profile first, followed by Quick Actions. Critical warnings (permission revocation, broken automation, Shizuku disconnection) must retain a prominent safety display or pinned banner even if non-critical cards are reordered. Wide windows (tablets) adapt the cards into a responsive multi-column grid; Android TV displays cards in a D-pad navigable grid with visible focus states. Card visibility and ordering are persisted locally and included in configuration export/import.

### ADR-017 — Pixel ToolBox candidate features as experimental v1 scope with Pixel 8 Pro validation gate

- **Status:** Accepted
- **Decision:** The 6 candidate features derived from Pixel ToolBox (AOD for new notifications, AOD while charging, Recents "close all apps", screen-off fingerprint unlock, ringtone-mode Quick Settings tile, and orientation-based camera quick launch) are included in the v1 scope as experimental/advanced capabilities, subject to an explicit feasibility verification gate on Google Pixel 8 Pro.
- **Consequence:** These features are disabled by default and clearly badged as "Experimental (Pixel/Device-dependent)". Each feature must be verified against current Android APIs, Accessibility, and Shizuku capabilities on the reference Pixel 8 Pro before code completion. If Android security, private APIs, or OEM firmware blocks any of these actions, Able must gracefully report the feature as unsupported or failed rather than crashing or simulating success. Sensor-dependent actions (camera orientation) must respect battery budgets and avoid background battery drain.

### ADR-018 — Advanced battery analytics suite and aBattery hardware health capabilities in v1 scope

- **Status:** Accepted
- **Decision:** Include the full suite of advanced battery diagnostics and brainstorming capabilities in v1: personal idle-drain baseline (BASE-001, BASE-002), app impact review, before/after profile review, charging session diary, battery anomaly checklist, battery health history, and session event notes. In addition, integrate hardware battery health capabilities inspired by "aBattery": cycle count, State of Health (SoH) percentage, design capacity vs full charge capacity, manufacture date, first usage date, and charging policy.
- **Consequence:** 
  1. Base capabilities rely on standard Android APIs (such as the Android 14+ `BatteryManager.EXTRA_CYCLE_COUNT` extra in `ACTION_BATTERY_CHANGED` and broadcast charging events) without requiring privileged permissions.
  2. Deep hardware health metrics (SoH, manufacture date, first usage date, `BATTERY_STATS`) require Shizuku or user-granted system permissions; where ungranted or unsupported by OEM HAL/kernel, Able reports "unavailable" or "unsupported by device" honestly.
  3. No causal claims: before/after profile reviews and app impact reviews must label results as observed correlations and never promise guaranteed savings.
  4. All session notes, charging records, and health observations remain local-first and follow local retention rules (90 days default), excluded from standard configuration backup.

### ADR-019 — Storage review thresholds and user-mediated deletion

- **Status:** Accepted
- **Decision:** The Storage cleanup tool uses default presets of files >100 MB for "Large files" (with selectable filter chips: 50 MB, 100 MB, 500 MB, 1 GB) and files >30 days old for "Old downloads" (with selectable filter chips: 7, 30, 90 days). File deletion is strictly user-confirmed and executed through Android-mediated Storage Access Framework (SAF) / MediaStore flows. Able can directly clear only its own internal cache.
- **Consequence:** Eliminates arbitrary or hardcoded scanning criteria. Users have transparent controls over file scan scopes. No automated or background file deletions are permitted. Private app data directories remain inaccessible in accordance with modern Android security boundaries.
- **Revision 2026-11-23 (owner request, v0.19):** the defaults stay (>100 MB, >30 days). Size adds a custom value; age adds 365 days; the large-file finder adds filters by type, location (all accessible storage, Downloads, DCIM/Pictures, user-picked SAF folder), sort, name search, and thumbnails (STO-001). The finder's unreliable results are a defect to fix. With Shizuku, a per-app cache-clear bulk action consistent with the RAM tool is added (STO-002); it clears caches only, never app data.

### ADR-020 — Shortcut collection deletion and item preservation choice

- **Status:** Accepted
- **Decision:** When deleting a non-empty shortcut collection, Able requires an explicit user choice via confirmation dialog: (1) "Move shortcuts to Unsorted" (preserving shortcut definitions, custom icons, and favorites) or (2) "Delete shortcuts too" (permanently deleting the collection and its contained shortcut definitions and custom icon assets).
- **Consequence:** Shortcut definitions and user-crafted custom icon assets are never deleted silently or unexpectedly. Empty collections can be deleted with a single confirmation. Pinned launcher shortcuts cannot be revoked by the OS upon definition deletion, but Able accurately clears its internal mappings and reports launcher limits.

### ADR-008 — 100% Native Kotlin and Jetpack Compose UI architecture

- **Status:** Accepted
- **Decision:** Able is built with 100% native Kotlin and Jetpack Compose across all target form factors. Phone and tablet interfaces use Material 3 and `androidx.compose.material3.adaptive` (WindowSizeClass). The Android TV / Google TV interface uses Compose for TV (`androidx.tv:tv-foundation` and `androidx.tv:tv-material`) for native 10-foot D-pad remote-first navigation.
- **Consequence:** Eliminates legacy Android View and XML layout overhead. UI components, state holders, and viewmodels are shared cleanly across mobile, tablet, and TV surfaces while tailoring presentation to window classes and remote-control focus. Fully integrates with Android 13+ dynamic color theming, `LocaleManager` per-app language settings, and TalkBack accessibility semantics.

### ADR-011 — GNU GPLv3 open source licensing with reserved trademark and private-to-public workflow

- **Status:** Accepted (revised 2026-11-22: publication executed by owner decision)
- **Decision:** Able will be released as Open Source under the **GNU General Public License v3.0 (GPLv3)** for source code. The project name **Able**, ownership identity **Drago Labs**, visual branding, icons, and logo assets are explicitly reserved intellectual property and are not licensed under GPLv3. Anyone redistributing or modifying the code must provide full attribution to Drago Labs, maintain the GPLv3 copyleft license, and must not reuse the name Able or official logos for derivative distributions.
- **Workflow & Consequence (revised 2026-11-22):** the owner decided on 2026-11-22 to publish now, during beta (ADR-035). The public repository is **https://github.com/dragolab/able**. At publication the GPLv3 `LICENSE` file and a `NOTICE.md` file are added; the `NOTICE.md` states that the "Able" and "Drago Labs" names, logos, and brand assets are reserved and not licensed under GPLv3. ABOUT-001 links to this repository. `Able_Documentation_Pack.zip`, `private-references/`, signing keys, keystores, `local.properties`, secrets, and release binaries (APK/AAB) stay local and git-ignored; a pre-push hygiene check (T-REPO-01, R-033) confirms none is tracked. Distribution targets include Google Play (Phone, Tablet, Android TV) and standalone sideloaded APKs / GitHub Releases; publishing a release binary on GitHub needs its own owner decision.
- **Superseded (history):** until 2026-11-22 the repository stayed local/private without a remote or license file until the first public release milestone.

### ADR-021 — GPS coordinate saver, local catalog, and sharing architecture

- **Status:** Accepted
- **Decision:** Able provides an on-device GPS coordinate saver and catalog tool. Location capture is strictly user-initiated (with contextual `ACCESS_FINE_LOCATION` permission). Points record latitude, longitude, altitude, accuracy, and timestamp, enriched with user labels, category tags, and notes. Points are stored in the local Room database and can be exported as part of `.ablebackup` or shared as geo URIs, plain text, and web map links.
- **Consequence:** Zero background location tracking; location services run only while the user explicitly requests a fix or views the GPS tool. Offline-first: points can be cataloged, searched, and filtered without internet connectivity.

### ADR-022 — Physical measurement and sensor tools architecture

- **Status:** Accepted
- **Decision:** Able includes physical measurement tools: (1) Spirit level / Inclinometer (using accelerometer and rotation vector sensors with 2D bubble, degree readouts, custom zero calibration, and haptic feedback at 0°); (2) Screen ruler (calibrated with `DisplayMetrics.xdpi` / `ydpi` in centimeters and inches with a manual fine-tuning slider); and (3) Digital compass (using magnetometer and accelerometer).
- **Consequence:** Sensor listeners are registered strictly during the active screen lifecycle and unregistered on pause/stop to prevent battery drain. If hardware sensors (e.g. magnetometer or accelerometer) are absent (such as on Android TV), tools are hidden or marked unavailable.

### ADR-023 — Advanced system toggles with two-tier fallback architecture

- **Status:** Accepted
- **Decision:** Advanced system tiles and tools (NFC, Auto-Brightness, USB Debugging, Wireless Debugging, Private DNS) follow a strict two-tier architecture:
  1. **Tier 1 (Advanced Direct Toggle):** When privileged access (`WRITE_SECURE_SETTINGS` via ADB or Shizuku, or `WRITE_SETTINGS` for Auto-Brightness) is available, tapping the Quick Settings tile or in-app toggle immediately changes the system state and updates the tile icon/state.
  2. **Tier 2 (Safe Intent Fallback):** When privileged access is ungranted, the tile or tool gracefully opens the corresponding native Android settings screen (e.g. NFC settings, Display settings, Developer Options, Private DNS dialog) with a clear explanation of how to unlock direct toggling.
- **Consequence:** Provides immediate utility for standard non-privileged users while offering instant one-tap power controls for advanced users with Shizuku/ADB. No silent failures or false success reports.
- **Revision 2026-11-22 (v0.18):** the NFC tile follows `pcolby/nfc-quick-settings` (GPL-3.0) with three routes in order: `WRITE_SECURE_SETTINGS` granted via ADB (direct `NfcAdapter` enable/disable), Shizuku, then the NFC settings page; the tile reflects the real adapter state. The Wireless debugging "details" action opens the Wireless debugging page directly, falling back to Developer options and then Android Settings.
- **Revision 2026-11-23 (v0.19, ADR-044):** the Shizuku route no longer runs commands such as `svc nfc`; with Shizuku Able self-grants `WRITE_SECURE_SETTINGS` after confirmation and then uses Tier 1 in-process. USB and Wireless debugging state is observed with Settings content observers so the UI and tiles update immediately.

### ADR-024 — Universal Action Catalog for customizable tiles and multi-action widgets

- **Status:** Accepted
- **Decision:** Able establishes a unified `ActionCatalog` registry where every tool, profile, toggle, and shortcut is registered as an executable `AbleAction`. Able provides multiple configurable `TileService` slots and multi-action customizable home-screen AppWidgets (built with Glance/Compose) where users can map any combination of actions from the catalog.
- **Consequence:** Replaces fragmented one-off widget implementations with a modular, extensible action execution framework. User widget layouts and custom tile mappings are saved in local persistence and included in configuration export/import.

### ADR-026 — Bundled UAD-ng package knowledge base

- **Status:** Accepted (2026-11-22)
- **Decision:** Able bundles a read-only snapshot of the Universal Android Debloater Next Generation list (`uad_lists.json`, GPL-3.0, compatible with Able's GPLv3 source license, ADR-011). Snapshot: commit `31e3dea4546c9b05eb975474d30ea7c88fd0b930` (2026-11-05), stored at `feature/apps/src/main/assets/uad/` with its license and notice. Able shows, per package, the description, list (Oem, Aosp, Google, Carrier, Misc), removal level (Recommended, Advanced, Expert, Unsafe), dependencies, and needed-by relations, and filters by these fields (APP-006). The removal level drives the warnings of APP-004.
- **Consequence:** Attribution, license, and snapshot commit appear in Settings → About (ABOUT-001). The snapshot is refreshed manually per release; Able never downloads list updates at runtime. Packages missing from the snapshot are labelled "no information", never "safe". The removal level is advisory and never triggers an action automatically. Replaces the former note to integrate a debloat list only after defining its policy.

### ADR-027 — Able brand identity and expressive visual style

- **Status:** Accepted (2026-11-22)
- **Decision:** Able gets its own logo, adaptive and monochrome (themed) launcher icon, in-app logo, and a curated Quick Settings tile icon set, with a more distinctive expressive visual style built on Material 3 Expressive and dynamic color. The open-source "Essentials" app by sameerasw is inspiration only; its branding, icons, and assets are not copied.
- **Consequence:** Brand assets remain Drago Labs IP and are not licensed under GPLv3 (ADR-011). Assets must be legible in light, dark, and dynamic color themes (BRAND-001). Custom tiles use icons from the Able set (CUST-001).
- **Revision 2026-11-22 (v0.18):** the owner asked for a rebrand; redesign started.
- **Revision 2026-11-23 (owner decision, v0.19): concept "Pulse" accepted.** The mark is a lowercase "a" built from a ring and a stem; the stem doubles as a level indicator with a mint segment. Colors: ink `#101418`, mint `#1FC98E`. Wordmark: Google Sans Flex, weight 650, ROND 0. Style: calm and precise. The monochrome (themed) icon uses ring and stem only; the tile icon set follows the same stroke and geometry. The options comparison is kept locally in `private-references/brand/` and is not part of the public repository or documentation. TSK-1606 is unblocked. The font license for shipping the wordmark (embedded or as outlines) is confirmed before release (R-040).

### ADR-028 — Full installed-package visibility

- **Status:** Accepted (2026-11-22)
- **Decision:** App management lists the full installed-package set using `QUERY_ALL_PACKAGES` (APP-001). This works for sideloaded builds.
- **Consequence:** Google Play restricts `QUERY_ALL_PACKAGES` to apps whose core purpose requires broad visibility and requires a Permissions Declaration (Play Console Help, checked 22 Nov 2026). The Play declaration is decided at release time; a Play build flavor without the permission and with narrower visibility may be needed (risk R-007). The installed-app list stays on-device and is never included in crash reports or backups.

### ADR-029 — Command shell tool through Shizuku

- **Status:** Accepted (owner request, 2026-11-22)
- **Decision:** Able provides an aShell-like command shell (CMD-001) that runs commands typed by the user as the Shizuku shell user, with streaming output, Stop, local history, saved snippets, and copy/share of output. Dangerous commands show a warning and require confirmation. Commands never run automatically. The owner's label "SHELL-001" is recorded as CMD-001 because SHELL-001 already identifies the app shell requirement (stable-ID rule).
- **Consequence:** The tool is unavailable without Shizuku and says so. History is local only, excluded from Android Auto Backup and `.ablebackup`. The action log records command text, time, exit status, and duration, never output. No profile, trigger, tile, widget, key gesture, or import can start a command. The dangerous-command list is advisory; unmatched commands are not described as safe. Execution route and pattern list are proposals (ADR-034).

### ADR-030 — App lock with the system authentication prompt, no overlays

- **Status:** Accepted (owner request, 2026-11-22)
- **Decision:** App lock (APPLOCK-001) uses the Able Accessibility service to notice that a protected app came to the foreground and starts an Able activity that shows the system `BiometricPrompt` (`BIOMETRIC_STRONG` or `DEVICE_CREDENTIAL`). Able does not use overlay windows (`SYSTEM_ALERT_WINDOW` or accessibility overlays) and does not implement its own PIN. Cancel sends the user to Home. Grace period: Immediately, 1, 5, or 15 minutes; all protected apps re-lock on screen off. A device screen lock is required.
- **Consequence:** App lock is a convenience barrier, not OS-level enforcement; Able states this together with the Recents-preview and Accessibility-dependency limits. It is unavailable where the Accessibility service is blocked (Android Advanced Protection, R-023) and is part of the Play Accessibility declaration (R-022). Foreground detection details are proposals (ADR-034).

### ADR-031 — Hardware-key shortcut scope

- **Status:** Accepted (owner request, 2026-11-22)
- **Decision:** Button shortcuts (KEY-001) cover volume up + down together, double press, and long press of each volume key while the screen is on. Targets are Action Catalog entries, camera launch, launchable apps, and launchable exported activities. Only gestures matching an enabled mapping are consumed. Power-key remapping is out of scope and recorded as unsupported because Android does not deliver the power key to third-party accessibility services; screen-off shortcuts are not promised.
- **Consequence:** Destructive targets (package changes, shell commands) cannot be bound. The combined-press gesture must not interfere with Android's hold-both-volume-keys accessibility shortcut. Supersedes the earlier "evaluate a volume-button torch shortcut" note. Timing thresholds are proposals (ADR-034).

### ADR-032 — App-wide options search

- **Status:** Accepted (owner request, 2026-11-22)
- **Decision:** Every primary destination's top app bar has a search action (SEARCH-001) over a bundled index of tools, battery options, apps features, settings, tiles & widgets, and Home customization, with Italian and English keywords. Recent searches are stored locally.
- **Consequence:** The index ships with the app and is maintained with each feature change (new feature → new index entries in both languages; part of the definition of done). Search is offline, never changes state, and recent searches are excluded from `.ablebackup`. Index construction is a proposal (ADR-034).
- **Revision 2026-11-23 (ADR-042):** on compact windows the entry point is the circular Search button next to the floating navigation bar instead of the top app bar; wider windows and TV keep the top bar entry.

### ADR-033 — APK backup before uninstalling a user app

- **Status:** Accepted (owner request, 2026-11-22; revises the restore route of ADR-026/APP-004)
- **Context:** Observed 2026-11-22 on the Android 16 / API 36.1 emulator: after `pm uninstall -k --user 0` on a user (non-system) app the data is kept, but Android deletes the APK, so `cmd package install-existing` fails; system apps restore correctly.
- **Decision:** Before uninstalling a user app for the user, Able backs up its APK(s) to Able's private storage, shows the size, and restores through a Shizuku package-install session. If the backup fails, uninstall needs an explicit confirmation that Able cannot restore it. Without a backup, Able offers reinstall from the original store.
- **Consequence:** Backups use device storage (shown per package and in total, deletable) and are never exported. A restore through a shell install session may change or lose the installer-of-record attribution, which can affect store updates (R-025). System apps keep using `install-existing`. Storage location and install path are proposals (ADR-034).

### ADR-035 — Beta release versioning

- **Status:** Accepted (owner decision, 2026-11-22)
- **Decision:** The project is at **beta 0.5**. The next APK is **0.6** with `versionName 0.6.0-beta` and `versionCode 6`. Beta versions use `0.<minor>.<patch>-beta`; `versionCode` increases by one for every distributed APK.
- **Consequence:** README and project docs state the current beta. The product specification version (0.18) is a document version and is independent of the app version. The v1 release version scheme is decided before the first non-beta release.

### ADR-036 — Background permissions management through Shizuku AppOps

- **Status:** Accepted (owner request, 2026-11-22)
- **Decision:** Able adds a NoMoreBackground-like background permissions tool (APP-007): per app and in bulk, restrict or allow background execution through Shizuku AppOps (`RUN_ANY_IN_BACKGROUND`, `RUN_IN_BACKGROUND`) and optionally set the standby bucket. Changes are manual, read back, and logged with prior values; protected and system/core apps are excluded from bulk changes; system packages need a separate confirmation with the UAD removal level.
- **Consequence:** Unavailable without Shizuku (Android's per-app battery settings are offered instead). AppOps and buckets are hidden platform behavior that OEMs may override or reset (R-029). NoMoreBackground is inspiration only (GPL-3.0-or-later); no code reuse planned. Commands are proposals (ADR-041).

### ADR-037 — Manual RAM and processes tool

- **Status:** Accepted (owner decision, 2026-11-22; narrows the earlier "avoid clean RAM" principle)
- **Decision:** Able adds a Shappky-inspired RAM and processes tool (MEM-001) in Strumenti next to Storage: RAM summary, running apps/processes with memory via Shizuku, force-stop/kill with confirmation, and a user block list stopped by one confirmed action. The owner decision overrides the earlier principle only as far as offering this manual tool.
- **Consequence:** Automatic "RAM cleaning" stays excluded (BAT-007 revised): no profile, trigger, schedule, tile, or widget runs the tool. Copy must stay honest: Android manages memory, apps may restart, no battery-saving or "boost" claims. Protected apps, the launcher, the keyboard, Able, and core system processes cannot be stopped in bulk. Misuse risk is R-031. Shappky (GPL-3.0) is inspiration only. Data sources and kill route are proposals (ADR-041).

### ADR-038 — Real hotspot control through Shizuku

- **Status:** Accepted (owner request, 2026-11-22; revises HOT-001/HOT-002)
- **Decision:** Following the Delta app approach (BSD-3-Clause; Shizuku plus system API stubs), Able controls the hotspot through Shizuku: on/off, SoftAP configuration (SSID, password, security, band, hidden, maximum clients, auto-off), connected clients, and a join QR code.
- **Consequence:** These are system/hidden APIs that may change between Android builds (R-030); Able resolves them at runtime, reads back every change, and reports "not supported on this Android build" rather than crashing or claiming success. Without Shizuku, Able only reads what it can and opens Android tethering settings. The hotspot password is excluded from logs, crash reports, and the standard backup (NFR-SEC-003). No Delta code is copied unless a later decision accepts reuse with its license.

### ADR-039 — Precise home double-tap mode with a launcher-only accessibility overlay

- **Status:** Superseded by ADR-050 (2026-11-23). Previously accepted (owner request, 2026-11-22; revises the LOCK-003 route)
- **Context:** The light watcher (accessibility event timing only, no touch interception) proved unreliable on the owner's Pixel 8 Pro on 2026-11-22. The non-consuming observed-motion API is still not public (R-027). The reference double-tap app was studied only through its manifest; no decompilation.
- **Decision:** Add a user-selectable **Precise** mode next to **Light**: while the launcher home is the foreground window, the Accessibility service shows a full-screen accessibility overlay that classifies taps, double taps, swipes, and long presses; a double tap on empty space turns the screen off; every other touch is replayed to the launcher (`dispatchGesture`, or `ACTION_CLICK`/`ACTION_LONG_CLICK` on the node); system gesture areas are excluded. The overlay is removed as soon as the launcher loses the foreground.
- **Consequence:** This knowingly intercepts launcher touches (unlike the earlier "never consume" rule, which still applies to the Light mode and to any non-launcher context). Trade-offs: single taps are delayed by the double-tap window, replay may be imperfect for drags, scrolling widgets, and multi-touch, and launcher updates can break it (R-024, R-032). Setup discloses this and shows a measured delay; the mode is part of the Play Accessibility declaration (R-022). The transparent widget stays as fallback. Parameters are proposals (ADR-041).
- **Revision 2026-11-23 (owner report, v0.19):** launcher page swipes are slow with Precise active on the owner's Pixel 8 Pro. Accepted mitigation: a pass-through browsing window after each recognized swipe (overlay touch-transparent, so further swipes go straight to the launcher), velocity-preserving replay of the first swipe (recorded path and timing), and a reduced intercept area (no dock/hotseat row, no search bar band, no system gesture insets). Accepted trade-offs, disclosed in setup and help: double taps during the pass-through window and in excluded areas are not detected, and the first swipe still waits for classification. Parameters are proposals (ADR-049); T-LOCK-05 measures the result.

### ADR-040 — Navigation re-select returns to the section start

- **Status:** Accepted (owner request, 2026-11-22; revises SHELL-001)
- **Decision:** Selecting the navigation item of the section already shown returns to that section's start: pop nested screens, close detail panes, scroll to top, select the first tab. If already at the start, it scrolls to the top.
- **Consequence:** Applies to the bottom bar, the navigation rail, and the TV drawer. Dirty-form back rules still apply. Each section keeps its own back stack when switching between sections.

### ADR-042 — Floating navigation bar with background blur on compact windows

- **Status:** Accepted (owner request, 2026-11-23; revises SHELL-001 and the SEARCH-001 entry point)
- **Decision:** On compact windows the four primary destinations sit in a floating pill-shaped navigation bar with a light blur of the content behind it, using the Haze library (Apache-2.0). A separate small circular Search button sits to the right of the pill and opens the options search (SEARCH-001). Content is padded by the bar height, margins, and the system navigation inset so nothing hides under the bar. The navigation rail (medium/expanded windows) and the TV drawer are unchanged.
- **Consequence:** Contrast must meet WCAG AA over any content; an opaque tonal surface replaces the blur where blur is unsupported, too costly, or disabled. Haze is added to the third-party license inventory and About. Blur cost is measured on the Pixel 8 Pro and the tablet (T-UI-03, R-038). Re-select behavior (ADR-040) and back rules are unchanged. Parameters are proposals (ADR-049).

### ADR-043 — Haptic language with per-action patterns and intensity

- **Status:** Accepted (owner request, 2026-11-23; revises UX-003)
- **Decision:** Every tap on an interactive element gives haptic feedback. Six kinds are distinguished: tick, click, toggle (on/off), confirm, reject/destructive, and long press. Patterns are composed from `VibrationEffect.Composition` primitives where `Vibrator.arePrimitivesSupported` reports them, otherwise `HapticFeedbackConstants` are used. Settings → Appearance → Haptic feedback offers Off / Light / Normal / Strong (default Normal), replacing the on/off switch; the system touch-feedback setting is respected.
- **Consequence:** Destructive confirmations and failures never feel like a success. On fallback devices intensity levels may feel the same, and Settings says so. Haptics stay short to avoid fatigue; continuous gestures throttle ticks to the step rate. The mapping table is a proposal (ADR-049); T-HAPTIC-01 and T-HAPTIC-02 verify it.

### ADR-044 — WRITE_SECURE_SETTINGS self-grant through Shizuku for NFC and secure-settings toggles

- **Status:** Accepted (owner decision after a device observation, 2026-11-23; revises SYS-001, SYS-003, SYS-004 routes and ADR-023)
- **Context:** On the owner's Pixel 8 Pro (Android 16), running `svc nfc` through Shizuku crashed with `SecurityException: Package android does not belong to 2000` (observed 2026-11-23).
- **Decision:** With Shizuku connected and after an explicit confirmation, Able runs `pm grant <able package> android.permission.WRITE_SECURE_SETTINGS` as the shell user, verifies the grant with `checkSelfPermission`, and then toggles NFC in-process through `NfcAdapter`, the route used by `pcolby/nfc-quick-settings`. The same grant serves USB/Wireless debugging, Private DNS, and future secure-settings features. Able no longer runs `svc nfc` through Shizuku.
- **Consequence:** The grant persists across updates until revoked or Able is uninstalled, so Able holds a powerful permission beyond the Shizuku session (R-037). The confirmation explains what it allows; the access checklist shows the state and offers Revoke (through Shizuku `pm revoke`), plus the ADB revoke command and uninstall as alternatives. Able writes only the settings keys of the features the user operates, and every write is read back. The grant is never exported.

### ADR-045 — Fake GPS tool with OpenStreetMap and Android mock locations

- **Status:** Accepted (owner request, 2026-11-23; new GPS-003)
- **Decision:** Able adds a Fake GPS tool: osmdroid map (Apache-2.0) on OpenStreetMap tiles, fake position by map tap or search, saved points, joystick, route simulation with speed, optional jitter/altitude/accuracy, and a foreground service with a stop notification. Android delivers mock locations only from the selected mock-location app; Able explains the Developer options route and, with Shizuku, selects itself in one confirmed tap through AppOps with readback.
- **Consequence:** This is Able's first network use; tiles are fetched only while the map is open, under the OSM tile usage policy (distinct User-Agent, visible attribution, cache headers honoured, no bulk or offline prefetch), and the privacy disclosure says the tile server sees the viewed area and IP address (R-036). osmdroid is archived (2024-11-20) and must be monitored or replaced later. Honest limits: apps can detect mocks (`Location.isMock()`), some refuse to work, and Able does not support deception; Play policy and misuse risks are R-035. No automation starts a mock position.

### ADR-046 — Optional battery-history export and archive schema version 2

- **Status:** Accepted (owner request, 2026-11-23; revises ADR-006, CFG-001–CFG-004)
- **Decision:** `.ablebackup` gains an optional battery history and measurements section (session summaries, charging diary, notes, health history), off by default, never preselected, and shown with a privacy notice. Custom battery profiles become their own section. New archives use schema version 2; import accepts schema 1 (profiles mapped to the v0.18 model) and schema 2. Imported history is validated, deduplicated against local sessions, and the preview warns when it would be removed by the current retention period.
- **Consequence:** Battery history can leave the device only through an explicit user export (R-039). Usage history, permission and Shizuku state, the `WRITE_SECURE_SETTINGS` grant, mock-location selection, shell history, recent searches, APK backups, and hotspot credentials remain non-exportable. The schema-1 proposal in ADR-025 is superseded for new exports. Layout details are proposals (ADR-049).

### ADR-047 — Commit authorship in the public repository

- **Status:** Accepted (owner decision, 2026-11-23)
- **Decision:** The public history of `https://github.com/dragolab/able` shows only the owner (DragoLab) as commit author. Commit messages carry no tool attribution trailers (for example generated-by or co-author lines for tools).
- **Consequence:** The contributing note in README states the rule. T-REPO-01 includes a check of authors and trailers before each push. External contributions, if accepted later, need a separate decision.

### ADR-048 — Home, Battery, and Tools layout revisions

- **Status:** Accepted (owner requests, 2026-11-23; revises HOME-003, adds BAT-UI-001 and TOOLS-001)
- **Decision:** (1) Home: critical safety banners (for example missing Shizuku permission) stay at the top; the failed-actions notice moves to the bottom of Home and can be hidden until a new failure; the Recent actions card loses its "Open Battery" button and may offer a "Show all" history sheet. (2) Battery: the sub-sections Stato, Monitor, Salute, Diagnosi are swipeable horizontally; Monitor is ordered live status → statistics → sessions → settings and Health summary → capacity → lifecycle → charging → advanced. (3) Tools hub: all tiles have the same size.
- **Consequence:** Hiding the notice never hides a critical condition or deletes log entries. Swipe navigation keeps tabs as the accessible and TV alternative and leaves system back edges alone. Uniform tiles also make the container transform (UX-004) start and end at predictable bounds. Layout details remain to be mocked up (adaptive UI spec).

## Implementation decisions still open

### ADR-009 — Local persistence and schema migrations

- **Status:** Open
- **Questions:** Which local database/preferences stack? How are profiles, actions, icon assets, and backups versioned/migrated? How long is action history kept?
- **Constraint:** No cloud dependency in v1; archive schema must be versioned independently from internal database schema.

### ADR-010 — Background scheduling and monitor implementation

- **Status:** Open pending Android feasibility prototype
- **Questions:** Which system callbacks/jobs/foreground service are actually required for each trigger? How can screen-off monitoring stay reliable without excessive wakeups or battery drain?
- **Constraint:** Do not select a polling architecture before validating event availability and power cost on Pixel 8 Pro.
- **Current implementation proposal (open):** The user-started live battery monitor uses a foreground service with Android's `specialUse` type and a five-minute sample interval plus battery/screen broadcasts. This is only an implementation experiment; measure its power impact, review Play declaration eligibility, and validate lifecycle/reboot behavior before accepting it for release.

### ADR-025 — Proposals raised by the 2026-11-22 implementation rewrite

- **Status:** Proposed (not accepted); each item needs an owner decision. The 2026-11-22 owner decisions (ADR-005, ADR-015, ADR-026–ADR-028) did not settle any item below, so all remain open.
- **Monitor resume after reboot:** if the user left the live monitor running, a `BOOT_COMPLETED` receiver restarts it (disclosed in the Monitor tab). Confirm or require a manual restart.
- **Web map link provider:** GPS sharing uses an OpenStreetMap web link (`openstreetmap.org/?mlat=…&mlon=…`) as a vendor-neutral default.
- **Storage media permissions:** the storage review declares `READ_MEDIA_IMAGES`/`VIDEO`/`AUDIO`; Google Play's photo/video permission policy may not accept broad media access for a cleaner. Alternative: keep only the Storage Access Framework folder picker.
- **Backup storage locations:** `.ablebackup` schema 1 (ZIP with `manifest.json` and per-section JSON, SHA-256 per entry); the replace/merge rollback snapshot is stored at `filesDir/backup/rollback.ablebackup`. (2026-11-23: new exports move to schema 2, ADR-046; schema 1 stays importable.)
- **Initial focus:** Compose's initial-focus behavior is enabled only for the remote-first (TV/no-touchscreen) layout, so touch devices do not focus text fields and open the keyboard unexpectedly.

### ADR-034 — Implementation proposals for the v0.17 owner requests

- **Status:** Proposed (not accepted); each item needs feasibility evidence or an owner decision. ADR-029–ADR-033 accept the product behavior only.
- **Shell execution (CMD-001):** run each command in a Shizuku-spawned `sh -c` process (Shizuku remote process or a Shizuku UserService), read stdout/stderr on background coroutines, and terminate the process (then its process group) on Stop. Store history and snippets in Room with history in a table excluded from Auto Backup via `dataExtractionRules`/`fullBackupContent`.
- **Dangerous-command list (CMD-001):** initial patterns `pm uninstall|disable|clear|reset-permissions`, `cmd package uninstall`, `rm`, `dd`, `settings put|delete`, `reboot`, `svc`, `wm size|density`, `am force-stop` on system packages, `setprop`; reviewed per release. Advisory only.
- **LOCK-003 detection route:** (a) use a non-consuming observed motion-event source only if it becomes public SDK or is verified on the device; (b) never use consuming `setMotionEventSources(SOURCE_TOUCHSCREEN)` on the launcher; (c) decide "empty space" by hit-testing the tap against the launcher window's accessibility node bounds (clickable/long-clickable nodes, `AppWidgetHostView`); (d) otherwise the transparent 1×1..4×4 widget fallback. Double-tap interval proposal: Android `ViewConfiguration.getDoubleTapTimeout()`.
- **App lock foreground detection (APPLOCK-001):** `TYPE_WINDOW_STATE_CHANGED` events with package filter limited to the protected list; ignore Able's own lock activity, SystemUI, and the IME; lock activity with `excludeFromRecents`, `noHistory`, and `setRecentsScreenshotEnabled(false)` for Able's own window; unlock state kept in memory only and cleared on `ACTION_SCREEN_OFF`.
- **Key gestures (KEY-001):** double press within 300 ms, long press ≥ 500 ms, "together" when both volume keys go down within 150 ms; consume the matched down/up pair. A key event cannot be consumed retroactively, so when a double or long press is mapped for a key, its first press is held back until the gesture is decided and then either consumed or re-injected as a normal volume step (latency and re-injection route to be validated; if re-injection is not possible, Able explains that the mapped key loses its single-press volume step).
- **Search index (SEARCH-001):** a compile-time registry of `SearchEntry(id, titleRes, keywordRes(it/en), destination route, highlight key, capability gate)` generated from the navigation graph and Action Catalog; recent searches in DataStore (max 10).
- **Motion parameters (UX-004):** horizontal slide with a spring of damping ratio ≈ 0.85 and medium-low stiffness (light overshoot); container transform via Compose shared-element/`SharedTransitionLayout` bounds transform; predictive back drives the shrink progress.
- **APK backup (APP-004, ADR-033):** copy `sourceDir` and `splitSourceDirs` to `noBackupFilesDir/apk-backup/<package>/<versionCode>/` with SHA-256 per file; restore with a Shizuku `pm install-create`/`install-write`/`install-commit` session (or `PackageInstaller` session created as shell) using `--user 0`; reinstall-from-store via `market://details?id=` or the recorded installer package.
- **Backup sections:** whether saved snippets, app-lock selections, and button-shortcut mappings are new optional `.ablebackup` sections (proposal: snippets and key mappings yes, app-lock list yes; shell history, recent searches, and APK backups never).

### ADR-041 — Implementation proposals for the v0.18 owner requests

- **Status:** Proposed (not accepted); each item needs feasibility evidence or an owner decision. ADR-005 (revised) and ADR-035–ADR-040 accept the product behavior only.
- **Profile resolution (BAT-001–BAT-003):** store profiles with a stable ID, `builtIn` key, and trigger list; evaluate each trigger from system events (battery, power, screen, time alarms) into an active-profile set; compute `effective(app) = max(action(app, p) for p in active)` over the order Osserva < Limita < Arresta, skipping protected apps; diff against the previously applied map and apply only the delta; keep the attribution map (app → profiles) for the UI and for revert. Unit tests cover the ordering, protected exclusion, idempotency, and ending one of several profiles.
- **Background AppOps (APP-007):** `cmd appops set <pkg> RUN_ANY_IN_BACKGROUND ignore|allow`, `cmd appops set <pkg> RUN_IN_BACKGROUND ignore|allow`, readback with `cmd appops get <pkg>`; optional `am set-standby-bucket <pkg> active|working_set|frequent|rare|restricted` with readback via `am get-standby-bucket <pkg>`; bulk changes executed sequentially with a per-app result and a stored prior-value snapshot for restore.
- **RAM and processes (MEM-001):** summary from `ActivityManager.getMemoryInfo()`; process list from `dumpsys meminfo` / `dumpsys activity processes` parsed defensively (format varies by build); stop routes `am force-stop <pkg>` and `am kill <pkg>` (background processes only); readback by re-listing processes; block list stored in Room and included as an optional `.ablebackup` section (proposal).
- **Hotspot (HOT-001):** call `TetheringManager.startTethering/stopTethering` and `WifiManager.getSoftApConfiguration/setSoftApConfiguration` and the SoftAP callback (clients, state) through a Shizuku binder wrapper (`ShizukuBinderWrapper`) on the system services, compiled against system API stubs; feature-detect each setter by reflection; validate SSID (1–32 bytes) and WPA2/WPA3 passwords (8–63 characters) before applying.
- **Precise double tap (LOCK-003):** double-tap window from `ViewConfiguration.getDoubleTapTimeout()`; touch slop from `ViewConfiguration`; long press at `getLongPressTimeout()`; exclude `WindowInsets` system-gesture and tappable-element insets; replay taps with a 1-stroke `GestureDescription` at the original point and swipes with the recorded path and duration; remove the overlay on `TYPE_WINDOWS_CHANGED`/`TYPE_WINDOW_STATE_CHANGED` when the launcher loses focus; target replay delay to be measured, not assumed.
- **Wireless debugging route (SYS-003):** try `Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS` with the Wireless debugging fragment extra used by AOSP Settings (`com.android.settings.development.WirelessDebuggingFragment`), verify the activity resolves, else Developer options, else `Settings.ACTION_SETTINGS`; record which route works on Pixel 8 Pro and the tablet.
- **Container transform fix (UX-004):** key each small card's shared bounds by a stable tool ID, keep the origin card composed during the transition (no lazy-grid recycling), clip with the card's shape, and fall back to fade when the origin key is not laid out.

### ADR-049 — Implementation proposals for the v0.19 owner requests

- **Status:** Proposed (not accepted); each item needs feasibility evidence or an owner decision. ADR-027 (revised), ADR-039 (revised), and ADR-042–ADR-048 accept the product behavior only.
- **Floating bar (SHELL-001, ADR-042):** Haze `hazeSource` on the content and `hazeEffect` on the bar with a light blur radius (≈ 20–24 dp) and a tonal tint at about 70–80% opacity; bar height ≈ 64 dp, 16 dp side and bottom margins above the navigation inset; Search button 56 dp; content padding through `Scaffold`/`contentPadding` and `WindowInsets.navigationBars`; opaque fallback when blur is unavailable or the device is in power-save mode.
- **Haptic composition table (UX-003, ADR-043):** tick = `PRIMITIVE_TICK` (or `LOW_TICK`) at scale 0.4; click = `PRIMITIVE_CLICK` 0.7; toggle on = `QUICK_RISE` 0.5 + `TICK`, toggle off = `QUICK_FALL` 0.5; confirm = `CLICK` 0.8 + `TICK` after 60 ms; reject/destructive = `THUD` 0.8 (API 31+) or two `CLICK` at 0.9 with 80 ms gap; long press = `SLOW_RISE` 0.6 + `CLICK`. Light/Normal/Strong multiply scales by 0.6/1.0/1.3 (clamped to 1.0). Fallback constants as in the current haptics map (`CLOCK_TICK`, `VIRTUAL_KEY`, `TOGGLE_ON/OFF`, `CONFIRM`, `REJECT`, `LONG_PRESS`).
- **Self-grant (SYS-001, ADR-044):** Shizuku command `pm grant <pkg> android.permission.WRITE_SECURE_SETTINGS`; readback with `Context.checkSelfPermission`; revoke with `pm revoke`; NFC through `NfcAdapter.enable()/disable()` via reflection as in nfc-quick-settings, state from `ACTION_ADAPTER_STATE_CHANGED`.
- **Debug observers (SYS-003):** `ContentObserver` on `Settings.Global.getUriFor("adb_enabled")` and `"adb_wifi_enabled"`, registered while the screen or tile is listening; tiles call `requestListeningState` on change.
- **Precise pass-through (LOCK-003, ADR-039 revised):** after a replayed swipe set `FLAG_NOT_TOUCHABLE` on the overlay for a browsing window of ≈ 1.5 s that restarts with each launcher scroll event (`TYPE_VIEW_SCROLLED` from the launcher) and ends after ≈ 1.5 s idle; replay the first swipe as a `GestureDescription` stroke with the recorded points and duration (velocity preserved), dispatched as soon as the touch slop is exceeded rather than on finger up where the launcher accepts a continued stroke (`willContinue`); exclude the hotseat and search-bar bounds found in the launcher's accessibility node tree plus a fixed band fallback. Values to be measured, not assumed.
- **Fake GPS (GPS-003, ADR-045):** osmdroid `MapView` with `Configuration.userAgentValue` set to an Able-specific value with the repository URL, tile source `MAPNIK` over HTTPS, default osmdroid tile cache with expiry honouring headers and no `CacheManager` bulk download; mock provider through `LocationManager.addTestProvider` for GPS and network providers with `setTestProviderLocation` at 1 Hz (joystick/route) and removal on stop; foreground service type `location` or `specialUse` to be decided with the Play declaration; AppOps command `appops set <pkg> android:mock_location allow` with readback `appops get`; place search provider (for example Nominatim under its usage policy, user-triggered only) needs an owner decision because it adds a second network endpoint.
- **Large-file finder (STO-001):** MediaStore `Files` query with `SIZE`, `DATE_MODIFIED`, `MIME_TYPE`, `RELATIVE_PATH` selection per filter, SAF `DocumentFile` walk for user-picked folders, thumbnails via `ContentResolver.loadThumbnail`; a seeded test folder for the defect regression.
- **Per-app cache (STO-002):** `pm clear --cache-only --user <id> <pkg>` per selected app with before/after cache size from `StorageStatsManager.queryStatsForPackage`.
- **Archive schema 2 (CFG-002, ADR-046):** ZIP with `manifest.json` (`schemaVersion: 2`, sections list), `profiles.json`, `appRules.json`, `home.json`, `shortcuts.json`, `icons/`, `gps.json`, optional `batteryHistory.json`; SHA-256 per entry as in schema 1; schema-1 `profiles` mapped to built-in profile edits with triggers disabled unless enabled in the source; dedup key (session start, end, source).

### ADR-050 — Home double tap through a single non-intercepting watcher

- **Status:** Superseded by ADR-053 (2026-11-23). Previously accepted (owner request, 2026-11-23; supersedes ADR-039 and the Precise items of ADR-049)
- **Context:** With the Precise overlay the owner still saw double tap working only sometimes and launcher page swipes slowed on the Pixel 8 Pro. The owner provided a decompiled build of "Double Tap Screen Off" (jamworks, v1.22), which works reliably on the same phone. Its accessibility service never intercepts launcher touches: a 1×1 `TYPE_ACCESSIBILITY_OVERLAY` with `FLAG_WATCH_OUTSIDE_TOUCH` receives `ACTION_OUTSIDE` per touch-down, two downs 80–200 ms apart count as a double tap, and at that moment it checks live that the launcher is the focused window and (on Pixel Launcher) `id/workspace` is visible. Able's earlier Light mode used the same watcher but attached it only while window events said the launcher was in front (events missed when returning home with a gesture) and waited 450 ms for launcher events.
- **Decision:** Remove the Precise mode, the mode and area settings, and their code. Keep one route: the watcher stays attached whenever the feature is on and the phone is on and unlocked; a pair is decided after ≈ 150 ms (launcher click/long-click/scroll events requested with `notificationTimeout = 0` while on); then a live window check (launcher active application window, `id/workspace` visible, `id/apps_view` not visible, no SystemUI shade) gates the lock. A 300 ms re-arm prevents a triple tap from locking twice. No code was copied from the reference app.
- **Consequence:** Launcher touches are never delayed. Position stays unknown, so a quick double tap next to an icon can still be misread (disclosed). The stored `home_mode` / `home_area` keys are ignored. Verification: T-LOCK-06 on the Pixel 8 Pro.

### ADR-051 — In-app updater from GitHub Releases

- **Status:** Accepted (owner request, 2026-11-23)
- **Context:** Able betas are distributed as APK assets of GitHub pre-releases (`able-0.7-beta`, `able-0.8-beta`, `able-0.9-beta`, …). Users had to visit GitHub to find a newer build. Able had no update path and, until Fake GPS, no network use.
- **Decision:** Settings → Updates checks `https://api.github.com/repos/dragolab/able/releases` without authentication (`Accept: application/vnd.github+json`, generic User-Agent `Able-Updater`, no user data), includes pre-releases, parses the version from the tag (`able-<major>.<minor>[.<patch>][-pre]` or `v<x.y.z>[-pre]`) and compares it with the installed `versionName` using semantic-versioning precedence. Network contact is limited to `api.github.com` and, only after the user taps "Download and install", the release asset on `github.com`, which redirects to GitHub's download CDN (`objects.githubusercontent.com` / `*.githubusercontent.com`). The JDK `HttpURLConnection` and kotlinx.serialization are used; no new library. The APK goes to `cacheDir/updates/`, its length must equal the asset `size` and its package name must be Able's, then it is installed through a `PackageInstaller` session whose status reaches a non-exported receiver; `STATUS_PENDING_USER_ACTION` launches the system confirmation (user action always required). The automatic check is **on by default**, runs at app start after onboarding at most once every 24 h, only checks, announces a newer version once (snackbar plus a card at the top of Settings), and **can be disabled** in Settings → Updates. Preferences (auto-check flag, last check time, latest seen version, last announced version) live in the existing DataStore and are not part of `.ablebackup`.
- **Consequence:** Adds `INTERNET` use for GitHub and `REQUEST_INSTALL_PACKAGES` ("Install unknown apps", granted by the user in Android settings). Updates install only when signed with the same key as the installed app (see ADR-052); otherwise Android returns `STATUS_FAILURE_CONFLICT`/`STATUS_FAILURE_INCOMPATIBLE` and Able asks the user to uninstall first. Google Play forbids self-updating apps (checked 23 Nov 2026), so a Play build must remove the updater and the permission. Unauthenticated GitHub API calls are limited to 60 per hour per IP; Able reports the limit instead of retrying. Verification: T-UPD-01.

### ADR-052 — Distribute R8-optimised release builds signed with the beta key

- **Status:** Accepted (owner request, 2026-11-23: smoother animations, APK around 60 MB)
- **Context:** Releases 0.6–0.8 were debug builds: about 90 MB (mostly unshrunk dex, including the full Material icon set) and Compose running without R8 optimisation, which made screen transitions stutter on the owner's Pixel 8 Pro.
- **Decision:** Release APKs are built with `isMinifyEnabled` and `isShrinkResources` (R8, `proguard-android-optimize.txt` plus `app/proguard-rules.pro`, which keeps all `com.dragolabs.able.**` classes for reflection and the Shizuku `app_process` entry point). To keep in-place updates (and the in-app updater, ADR-051) working for existing beta users, release builds are signed with the same developer key used for 0.6–0.8. Moving to a dedicated release key (required for Play) is a separate owner decision and will need one uninstall/reinstall.
- **Consequence:** 0.9.0-beta APK ≈ 12.5 MB. R8 can break reflective code paths not covered by keep rules; every release gets an emulator smoke test of the main screens.
- **Revision to ADR-050 (same date):** pairs are accepted up to the user's double-tap time plus 150 ms measured down-to-down (the setting is lift-to-touch like Android's double-tap timeout); scroll events cancel a pair only when they happen between the two touches; the tool shows the last attempt's outcome and gap.

### ADR-053 — Home double tap with the reference app's exact logic

- **Status:** Accepted (owner request, 2026-11-23: "use the same logic, that app works well"; supersedes ADR-050)
- **Context:** The owner reports that Able's home double tap still does not work on the Pixel 8 Pro while "Double Tap Screen Off" (jamworks, v1.22) does. Able's version differed in three ways: it waited 150 ms and rejected pairs after launcher click/scroll events, it removed and re-added the watcher on screen and lock changes, and it checked the active window plus `id/apps_view`.
- **Decision:** Mirror the reference app (`PixelService`, `d30.onTouch`, `c30` case 8), re-implemented without copying code: a 1×1 `TYPE_ACCESSIBILITY_OVERLAY` with `FLAG_NOT_FOCUSABLE | FLAG_NOT_TOUCH_MODAL | FLAG_WATCH_OUTSIDE_TOUCH | FLAG_HARDWARE_ACCELERATED` attached for as long as the feature is on; on each `ACTION_OUTSIDE`, a double tap when the gap since the previous touch-down is over 80 ms and under the user's time + 150 ms and the last double tap is over 200 ms old; decided immediately; then, as the reference app does, skip when the keyguard is locked or a keyboard window is present, and require the focused application/system window to be the default launcher with `id/workspace` visible on a refreshed tree (launchers without that ID: package check only); then `GLOBAL_ACTION_LOCK_SCREEN`. No launcher click/scroll events are requested for this feature.
- **Consequence:** Two quick taps on an icon also lock (same as the reference app; disclosed in help). Emulator (API 36, Pixel Launcher, 2026-11-23): 3/3 empty-space double taps locked, none in another app. Owner device verification pending (T-LOCK-06).
