# Able implementation backlog

**Owner:** Drago Labs  
**Project:** Able Android Toolbox  
**Status:** Implementation in progress (beta 0.5; next APK 0.6.0-beta, versionCode 6, ADR-035); remaining work tracked below and in the implementation snapshot in README  
**Target:** v1 release (Android 13+, Google Pixel 8 Pro reference, Phone / Tablet / Android TV)

This backlog organizes the accepted v1 product requirements into cohesive epics and development tasks. Each task links to stable requirement IDs, use cases, and user stories.

---

## Epic 1 — Foundation, tooling and adaptive design system

*Goal: Establish the native Kotlin/Compose project structure, build configuration, theme/localization resources, and adaptive shell.*

| Task ID | Summary | Requirements | Links | Verification |
| --- | --- | --- | --- | --- |
| TSK-0101 | Initialize Android multi-module project (App, Core:Model, Core:DesignSystem, Core:Capability, Feature modules) with minSdk 33, targetSdk 34+. | NFR-MAINT-001, ADR-008 | ADR-008 | T-PERF-01 |
| TSK-0102 | Implement Material 3 theme engine with Dynamic Color support, fallback semantic palette, and System/Light/Dark mode overrides. | SET-001, NFR-UI-001 | UC-02, US-SET-01 | T-LOCALE-01 |
| TSK-0103 | Configure string resources for 100% complete Italian and English localization, including plurals, units, and TalkBack semantics. | SET-001, NFR-I18N-001, NFR-ACC-001 | UC-02, US-SET-01 | T-LOCALE-01, T-ACCESS-01 |
| TSK-0104 | Build the adaptive shell navigation (Bottom navigation on compact phone; Navigation rail on wide tablet; 10-foot D-pad layout for TV). | SHELL-001, NFR-TV-001, NFR-UI-001 | UC-03, US-UI-01 | T-UI-01, T-TV-01 |

---

## Epic 2 — Capability architecture and system adapters

*Goal: Build robust hardware and system access adapters that degrade gracefully, report honest status, and prevent unverified actions.*

| Task ID | Summary | Requirements | Links | Verification |
| --- | --- | --- | --- | --- |
| TSK-0201 | Build `CapabilityManager` for runtime detection of battery, flash hardware, hotspot/tethering, and sensors. | NFR-COMPAT-001 | UC-01, US-HOME-01 | T-CAP-01 |
| TSK-0202 | Implement `ShizukuAdapter` for checking connection state, binder lifecycle, and safe command execution with timeout/recovery. | BAT-006, APP-004, HOT-002 | UC-05, US-APP-01 | T-SHIZUKU-01 |
| TSK-0203 | Implement `PermissionCoordinator` for contextual, on-demand permission requests (Notification, UsageStats, Accessibility, DND). | SET-002, NFR-SEC-002 | UC-01, US-SET-01 | T-PERM-01 |
| TSK-0204 | Build `ActionAuditLogger` local database for recording all attempted, applied, skipped, and failed Able actions. | BAT-005, NFR-REL-001 | UC-05, US-BAT-02 | T-STATE-01 |

---

## Epic 3 — Modular card Home dashboard

*Goal: Deliver the fully customizable card-based Home dashboard with priority safety alerts.*

| Task ID | Summary | Requirements | Links | Verification |
| --- | --- | --- | --- | --- |
| TSK-0301 | Implement Home dashboard card container with responsive single-column (phone), multi-column grid (tablet), and D-pad grid (TV). | HOME-001, NFR-UI-001 | UC-01, UC-03, US-HOME-01 | T-HOME-01, T-TV-01 |
| TSK-0302 | Build core cards: Battery & Profile, Quick Actions, Capability Status, Storage Summary, Favorite Shortcuts, Recent Actions/Warnings. | HOME-001, HOME-002 | UC-20, US-HOME-01 | T-HOME-01 |
| TSK-0303 | Implement Home card customization mode (reorder, toggle visibility, and Reset to Default). | HOME-002 | UC-20, US-HOME-01 | T-HOME-01 |
| TSK-0304 | Build persistent high-priority safety alert banner for disconnected Shizuku, revoked permissions, or failed profile automations. | HOME-003, NFR-REL-001 | UC-20, US-HOME-01 | T-HOME-01, T-STATE-01 |

---

## Epic 4 — Battery profiles and automation engine

*Goal: Implement configurable battery profiles with deterministic trigger evaluation, protected apps, and safe app rules.*

| Task ID | Summary | Requirements | Links | Verification |
| --- | --- | --- | --- | --- |
| TSK-0401 | Build `ProfileManager` for Normale, Notte, Risparmio, Emergenza, and Pausa app. Revised 2026-11-22: the fixed priority resolution (Emergenza → Risparmio → Notte → Normale) is superseded by TSK-0406/TSK-0407; built-ins become editable defaults. | BAT-001, BAT-002 | UC-04, US-BAT-01 | T-PROFILE-01 |
| TSK-0402 | Implement dual-trigger Notte engine (daily schedule OR 20-minute screen-off continuous delay) with independent enable flags; every automatic trigger (Notte, Risparmio, Emergenza) defaults to off (ADR-005, 2026-11-22). | BAT-001, BAT-008 | UC-04, UC-05, US-BAT-01 | T-PROFILE-02 |
| TSK-0403 | Implement per-app intervention levels (Osserva, Limita in background, Arresta quando esco) with preview and protected-app whitelist. | BAT-003, BAT-004 | UC-04, US-BAT-01 | T-PROFILE-01 |
| TSK-0404 | Implement automation execution service with transition notifications, idempotency, and explicit manual-only restriction for app freeze/disable. | BAT-007, BAT-008 | UC-05, US-BAT-02 | T-PROFILE-03 |
| TSK-0405 | Implement state reconciliation on device resume, charger connect/disconnect, app update, and reboot. | BAT-006, NFR-REL-001 | UC-05, US-BAT-02 | T-STATE-01 |
| TSK-0406 | Custom profiles: create/rename/duplicate/delete custom profiles, edit and Reset to default for built-ins (no delete), trigger editor (battery below with activate/reset and only-unplugged, schedule with days, screen off N minutes, charging, manual) with validation, all triggers off by default; data migration from the v0.17 profile model. | BAT-001, BAT-003, ADR-005 | UC-43, US-BAT-07 | T-PROFILE-04 |
| TSK-0407 | Concurrent activation engine: active-profile set from triggers (OR per profile), per-app most-restrictive resolution with protected exclusion, attribution map, delta apply and attributed revert; unit tests. | BAT-002, BAT-008, ADR-005, ADR-041 | UC-05, US-BAT-08 | T-PROFILE-05, T-STATE-01 |
| TSK-0408 | Redesign Battery Status (active profiles with triggers, effective app actions with source profile, next trigger), Profiles list/editor, and Diagnosis screens per the adaptive UI spec. | BAT-001–BAT-003, NFR-UI-001 | UC-04, UC-05, US-BAT-08 | T-PROFILE-05, T-UI-01 |

---

## Epic 5 — Live battery monitor, analytics and aBattery hardware health

*Goal: Deliver opt-in continuous battery monitoring, session summaries, statistical analytics, and deep hardware health insights.*

Implementation note (19 Nov 2026): TSK-0501 and TSK-0502 have partial source implementations: user-started foreground sampling, session summaries, local retention selection, and deletion UI are present. TSK-0503 now has a partial local baseline calculation from eligible monitor sessions, with a five-session calibration gate and comparable duration/start-level grouping; alerts, dismiss-once opt-in, trend chart, and device validation remain outstanding. TSK-0505 has partial source implementation through charging/discharging sessions, thermal flags, and session notes/tags. These tasks are not verified or accepted; optional widget behavior, reset rules, session continuity after process death, and device measurements still need implementation/review. TSK-0504 and TSK-0506 through TSK-0508 remain incomplete or partial as tracked in code and verification evidence.

| Task ID | Summary | Requirements | Links | Verification |
| --- | --- | --- | --- | --- |
| TSK-0501 | Build low-overhead `BatteryMonitorService` for screen-on, screen-off, and charging intervals, with ongoing notification and home widget. | MON-001–MON-008, NFR-PWR-001 | UC-06, US-MON-01 | T-MON-01, T-MON-02, T-POWER-01 |
| TSK-0502 | Implement local battery history storage with 30-day, 90-day (default), and manual retention options, plus immediate delete. | MON-009, NFR-PRIV-002 | UC-06, US-MON-02 | T-MON-03 |
| TSK-0503 | Implement personal idle-drain baseline calculation and dismissible alert opt-in (BASE-001, BASE-002). | BASE-001, BASE-002 | UC-16, UC-17, US-BASE-01, US-BASE-02 | T-BASE-01, T-BASE-02 |
| TSK-0504 | Build App impact review and profile before/after comparison views with non-causal reporting. | BAT-REV-001, BAT-REV-002, NFR-PWR-002 | UC-22, US-BAT-03 | T-BAT-REV-01 |
| TSK-0505 | Implement Charging session diary with thermal alert flagging and session event note/tagging editor. | BAT-CHG-001, BAT-NOTE-001 | UC-23, US-BAT-04 | T-BAT-CHG-01 |
| TSK-0506 | Build guided Battery anomaly checklist with direct intents to Android system settings. | BAT-CHK-001 | UC-24, US-BAT-05 | T-BAT-CHK-01 |
| TSK-0507 | Implement public API battery cycle count reader (`BatteryManager.EXTRA_CYCLE_COUNT` from `ACTION_BATTERY_CHANGED`) for Android 14+. | BAT-HLT-001, NFR-COMPAT-001 | UC-25, US-BAT-06 | T-BAT-HLT-01 |
| TSK-0508 | Implement advanced hardware battery health reader via Shizuku/system APIs (SoH %, capacity mAh, manufacture/first use dates, charging policy). | BAT-HLT-002, NFR-COMPAT-001 | UC-25, US-BAT-06 | T-BAT-HLT-01, T-SHIZUKU-01 |

---

## Epic 6 — Device tools (Torch, Hotspot, Screen lock)

*Goal: Deliver dependable device utilities with hardware-aware controls and Quick Settings tiles.*

| Task ID | Summary | Requirements | Links | Verification |
| --- | --- | --- | --- | --- |
| TSK-0601 | Implement Torch manager with toggle, hardware brightness level control (Android 13+), SOS/Morse/BPM modes, and QS tile. | TORCH-001, TORCH-002 | UC-08, US-TORCH-01 | T-TORCH-01, T-CAP-01 |
| TSK-0602 | Implement Hotspot manager with state display, QR code sharing, and Shizuku-backed advanced settings. | HOT-001, HOT-002 | UC-09, US-HOT-01 | T-HOT-01, T-SHIZUKU-01 |
| TSK-0603 | Implement Hotspot Bluetooth automation rule (auto-on on connect, auto-off on disconnect for selected paired device). | HOT-002 | UC-09, US-HOT-01 | T-HOT-01 |
| TSK-0608 | Hotspot control via Shizuku (Delta approach): system API binding with runtime feature detection, on/off with read-back state, SoftAP configuration form with validation and per-field readback, clients list, QR on request, unsupported-build state, tethering-settings fallback; password excluded from logs/crash reports/backup. | HOT-001, HOT-002, ADR-038 | UC-46, US-HOT-02 | T-HOT-02, T-HOT-03 |
| TSK-0604 | Implement double tap to turn off/lock on the launcher home screen and on the lock screen via the Accessibility service, with per-context test and support reporting, plus Quick Settings tile and shortcut fallbacks. | LOCK-001, LOCK-002 | UC-07, US-LOCK-01 | T-LOCK-01 |
| TSK-0605 | Feasibility spike: non-consuming touchscreen observation for empty home-space double tap on Pixel 8 Pro (Android 15/16+); record API availability and date; never ship a consuming route (R-027). | LOCK-003 | UC-39, US-LOCK-02 | T-LOCK-02 |
| TSK-0606 | Implement empty home-space detection (launcher foreground check, node hit-test for icons/widgets/dock/search) where TSK-0605 confirms a route. | LOCK-003, ADR-034 | UC-39, US-LOCK-02 | T-LOCK-02 |
| TSK-0607 | Build the transparent resizable double-tap home widget fallback. | LOCK-003 | UC-39, US-LOCK-02 | T-LOCK-03 |
| TSK-0609 | Precise home double-tap mode: launcher-only accessibility overlay excluding system gesture insets, tap/double-tap/swipe/long-press classifier, replay via `dispatchGesture`/node actions, overlay removal on focus loss, Light/Precise selector, self-test with measured replay delay, disclosures. | LOCK-003, ADR-039, ADR-041 | UC-47, US-LOCK-03 | T-LOCK-04 |

---

## Epic 7 — App management and Activity Launcher

*Goal: Provide safe app browsing, background controls, and normally launchable activity inspection.*

Implementation note (19 Nov 2026): TSK-0701 and part of TSK-0702 have source implementations for the app catalog, search/filters, app details, protected status, profile rule storage, and confirmed Shizuku force-stop. TSK-0703 now has the Android-confirmed uninstall-for-user path for downloaded apps; reversible disable/enable, state history, and recovery remain outstanding. None of these tasks are verified or accepted without device evidence.

| Task ID | Summary | Requirements | Links | Verification |
| --- | --- | --- | --- | --- |
| TSK-0701 | Implement installed app scanner with search, user/system filters, and full package visibility via `QUERY_ALL_PACKAGES` (ADR-028); keep a build flavor option without it for Play. | APP-001, NFR-PERF-001 | UC-10, US-APP-01 | T-APP-01 |
| TSK-0702 | Build app detail screen showing protected state, battery usage data, and manual force-stop with impact warning. | APP-002, APP-003 | UC-10, US-APP-01 | T-APP-01, T-APP-02 |
| TSK-0703 | Implement explicit, reversible app disable/enable and uninstall-for-user via Shizuku, with UAD-level warnings, separate confirmation, and restore guidance. | APP-004, APP-005 | UC-10, US-APP-01 | T-APP-02, T-SHIZUKU-01 |
| TSK-0705 | Build the Able archive of removed/disabled packages with restore (`cmd package install-existing`, `pm enable`) and Android 15+ native archiving where available. | APP-004, APP-005 | UC-32, US-APP-02 | T-APP-03, T-SHIZUKU-01 |
| TSK-0706 | Integrate the bundled UAD-ng snapshot (ADR-026): parser, package detail (description, list, removal level, dependencies/needed-by, links), filters by state, user/system, list, and removal level; About attribution. | APP-006, ABOUT-001 | UC-33, US-APP-03 | T-APP-04, T-ABOUT-01 |
| TSK-0707 | Implement APK backup before user-app uninstall (base + splits, size shown, checksum verify, failure confirmation), restore via Shizuku install session with readback, reinstall-from-store route, and backup storage review/delete in the archive. | APP-004, ADR-033 | UC-32, US-APP-02 | T-APP-05, T-SHIZUKU-01 |
| TSK-0708 | Background permissions tool: Shizuku AppOps `RUN_ANY_IN_BACKGROUND`/`RUN_IN_BACKGROUND` per app and bulk, optional standby bucket, readback, protected/system safeguards, UAD-level confirmation for system packages, action log with batch restore, Shizuku-unavailable route. | APP-007, ADR-036 | UC-44, US-APP-04 | T-APP-06, T-APP-07 |
| TSK-0709 | RAM and processes tool in Strumenti next to Storage: RAM summary, Shizuku process list with memory, force-stop/kill with confirmation and readback, block list with exclusions, honest IT/EN copy and search entries; no automatic run. | MEM-001, BAT-007, ADR-037 | UC-45, US-MEM-01 | T-MEM-01, T-MEM-02 |
| TSK-0704 | Implement Activity Launcher screen listing normally launchable activities grouped by package, with Open and Create Shortcut actions. | ACT-001, ACT-002 | UC-12, US-ACT-01 | T-ACT-01 |

---

## Epic 8 — Shortcut Maker and collections

*Goal: Provide a unified shortcut editor, preview, icon customization, and organized collections with safe deletion.*

| Task ID | Summary | Requirements | Links | Verification |
| --- | --- | --- | --- | --- |
| TSK-0801 | Implement shared shortcut creation editor (in scope, modelled on the referenced Shortcut Maker app) supporting apps, activities, files/folders, URLs, and Able actions. | SHORT-001, SHORT-002 | UC-12, UC-13, US-SHORT-01 | T-SHORT-01 |
| TSK-0802 | Build icon customizer (app icon, text/emoji, custom gallery image) and preview generator with launcher pinning integration. | SHORT-003, NFR-UI-001 | UC-13, US-SHORT-01 | T-SHORT-01 |
| TSK-0803 | Implement Shortcut Collections manager (create, rename, reorder) with non-empty collection deletion choice (Move to Unsorted vs Delete shortcuts). | SHORT-003, ADR-020 | UC-13, US-SHORT-01 | T-SHORT-01 |

---

## Epic 9 — Storage review and cleanup

*Goal: Help users review space consumption and clean storage through verified SAF interactions.*

| Task ID | Summary | Requirements | Links | Verification |
| --- | --- | --- | --- | --- |
| TSK-0901 | Build storage overview screen displaying internal storage utilization and categories via `StorageStatsManager` / MediaStore. | STO-001, NFR-PERF-001 | UC-11, US-STO-01 | T-STORAGE-01 |
| TSK-0902 | Implement Large files scanner (>100MB default, chips: 50MB, 100MB, 500MB, 1GB) and Old downloads scanner (>30 days default, chips: 7, 30, 90 days). | STO-001, ADR-019 | UC-11, US-STO-01 | T-STORAGE-01 |
| TSK-0903 | Implement item review and Android SAF-mediated deletion confirmation, plus Able direct cache cleanup. | STO-002, NFR-SEC-001 | UC-11, US-STO-01 | T-STORAGE-01, T-STORAGE-02 |
| TSK-0904 | Build storage visualization: used/free chart with text equivalent, category breakdown (`StorageStatsManager`/MediaStore), largest folders/files. | STO-003, NFR-ACC-002 | UC-30, US-STO-02 | T-STORAGE-03 |
| TSK-0905 | Implement system-wide cache cleanup via Shizuku (`pm trim-caches`) with before/after free-space measurement, and storage-settings fallback without Shizuku. | STO-002, NFR-REL-001 | UC-31, US-STO-03 | T-STORAGE-04, T-SHIZUKU-01 |

---

## Epic 10 — Configuration portability and privacy

*Goal: Enable transparent, secure, offline `.ablebackup` export/import and user-reviewed crash reports.*

| Task ID | Summary | Requirements | Links | Verification |
| --- | --- | --- | --- | --- |
| TSK-1001 | Build configuration exporter with section chooser, privacy exclusions (no history/credentials/grants), and versioned `.ablebackup` zip writer. | CFG-001–CFG-003, NFR-SEC-003 | UC-14, US-CFG-01 | T-EXPORT-01 |
| TSK-1002 | Build secure archive importer with schema validation, path traversal defense, integrity checksums, and preview screen. | CFG-004, CFG-006, NFR-SEC-001 | UC-15, US-CFG-02 | T-IMPORT-01, T-IMPORT-02 |
| TSK-1003 | Implement merge and replace modes with conflict resolver and automatic rollback snapshot before replace apply. | CFG-005, CFG-007, NFR-REL-002 | UC-15, US-CFG-02 | T-IMPORT-03 |
| TSK-1004 | Integrate ACRA in user-reviewed mode (ADR-015): opt-in in onboarding and Settings → Privacy, crash dialog with report preview, Send via email composer to `info.dragolabs@gmail.com`, Discard, deletion on opt-out; no server, no analytics. | PRIV-001, NFR-PRIV-001, NFR-PRIV-003 | UC-18, US-PRIV-01 | T-TELE-01 |
| TSK-1005 | Build Settings → About screen with configured GitHub repository, Drago Labs website (`https://dragolabs.it`), support email intents, and third-party attributions (UAD-ng, ACRA). | ABOUT-001, NFR-I18N-001 | UC-19, US-ABOUT-01 | T-ABOUT-01 |

---

## Epic 11 — Experimental Pixel and system tools

*Goal: Build opt-in experimental capabilities derived from Pixel ToolBox, gated by feasibility validation on Pixel 8 Pro.*

| Task ID | Summary | Requirements | Links | Verification |
| --- | --- | --- | --- | --- |
| TSK-1101 | Implement AOD on notification and AOD while charging triggers with capability verification and fallback. | PIX-001, PIX-002 | UC-21, US-PIX-01 | T-PIXEL-01 |
| TSK-1102 | Implement Recents close-all action and Screen-off fingerprint assistance spike with graceful degradation. | PIX-003, PIX-004 | UC-21, US-PIX-01 | T-PIXEL-01 |
| TSK-1103 | Build Ringtone-mode Quick Settings tile cycling Normal, Vibrate, Silent with Notification Policy access. | PIX-005 | UC-21, US-PIX-01 | T-PIXEL-01 |
| TSK-1104 | Implement Orientation-based camera quick launch evaluated strictly on screen wake without background sensor polling. | PIX-006, NFR-PWR-001 | UC-21, US-PIX-01 | T-PIXEL-01 |

---

## Epic 12 — Android TV remote-first interface and quality compliance

*Goal: Ensure complete remote-first usability, focus management, and Google Play TV quality guideline adherence.*

| Task ID | Summary | Requirements | Links | Verification |
| --- | --- | --- | --- | --- |
| TSK-1201 | Build TV-specific Leanback/remote layout using Compose for TV with high-visibility D-pad focus indicators. | NFR-TV-001, ADR-008 | UC-03, US-UI-01 | T-TV-01 |
| TSK-1202 | Implement TV launcher banner, leanback intent filter, and hardware feature gating (`android.hardware.touchscreen = false`). | NFR-TV-001 | UC-03 | T-TV-01 |
| TSK-1203 | Execute Play TV app quality checklist audit (focus loops, safe margins, D-pad navigation completeness). | NFR-TV-001 | UC-03 | T-TV-01 |

---

## Epic 13 — Geolocation and physical measurement tools

*Goal: Deliver accurate offline GPS coordinate capture with labeled cataloging, and responsive physical measurement tools with zero background battery impact.*

| Task ID | Summary | Requirements | Links | Verification |
| --- | --- | --- | --- | --- |
| TSK-1301 | Implement GPS coordinate grabber (`FusedLocationProviderClient` / `LocationManager`) with accuracy threshold, timestamping, and reverse geocoding fallback. | GPS-001, NFR-SEC-002 | UC-26, US-GPS-01 | T-GPS-01 |
| TSK-1302 | Build GPS Point Catalog with custom label, notes, tag filtering, quick share (text/`geo:` intent), and navigation launcher. | GPS-002, NFR-PRIV-002 | UC-26, US-GPS-01 | T-GPS-01 |
| TSK-1303 | Implement Spirit Level (inclinometer) with pitch/roll angles, 2D circular bubble visualization, zero-degree calibration offset, and haptic snap at 0°. | SENS-001, SENS-003, ADR-022 | UC-27, US-SENS-01 | T-SENS-01 |
| TSK-1304 | Implement Screen Ruler with dual millimeter/inch scales, sub-millimeter caliper markers, and real-DPI calibration slider. | SENS-002, ADR-022 | UC-27, US-SENS-01 | T-SENS-01 |
| TSK-1305 | Implement Digital Compass with azimuth heading, cardinal points, magnetic declination correction, and calibration prompt. | SENS-003, ADR-022 | UC-27, US-SENS-01 | T-SENS-01 |

---

## Epic 14 — Advanced system toggles and connectivity control

*Goal: Implement two-tier quick setting tiles and in-app switches for system connectivity and developer options, degrading gracefully to Android Settings intents.*

| Task ID | Summary | Requirements | Links | Verification |
| --- | --- | --- | --- | --- |
| TSK-1401 | Implement NFC Quick Settings tile and in-app status switch following `pcolby/nfc-quick-settings` (revised 2026-11-22): `WRITE_SECURE_SETTINGS` direct toggle (ADB grant shown with copy), Shizuku route, `ACTION_NFC_SETTINGS` fallback; tile tracks real adapter state. | SYS-001, ADR-023 | UC-28, US-SYS-01 | T-SYS-01, T-SYS-02 |
| TSK-1402 | Implement Auto-brightness Quick Settings tile and in-app switch via `Settings.System.SCREEN_BRIGHTNESS_MODE` with `WRITE_SETTINGS` grant flow. | SYS-002, ADR-023 | UC-28, US-SYS-01 | T-SYS-01 |
| TSK-1403 | Implement USB Debugging and Wireless Debugging Quick Settings tiles and in-app switches via `Settings.Global.ADB_ENABLED` / Shizuku with `DEVELOPMENT_SETTINGS` fallback. | SYS-003, ADR-023 | UC-28, US-SYS-01 | T-SYS-01 |
| TSK-1405 | Wireless debugging "details": open the Wireless debugging page directly with runtime route detection and fallbacks (Developer options, Settings). | SYS-003, ADR-023 | UC-28, US-SYS-01 | T-SYS-03 |
| TSK-1404 | Implement Private DNS manager and Quick Settings cycle tile (`off`, `opportunistic`, `hostname`) with preset hostnames (Quad9, Cloudflare, AdGuard, custom). | SYS-004, ADR-023 | UC-28, US-SYS-01 | T-SYS-01 |

---

## Epic 15 — Universal Action Catalog, customizable tiles and Glance widgets

*Goal: Provide a unified action execution catalog that powers user-assignable Quick Settings tiles and interactive multi-action home-screen AppWidgets.*

| Task ID | Summary | Requirements | Links | Verification |
| --- | --- | --- | --- | --- |
| TSK-1501 | Build `AbleAction` registry and metadata catalog covering all Able utilities, profiles, toggles, shortcuts, and tools. | CUST-001, CUST-002, ADR-024 | UC-29, US-CUST-01 | T-CUST-01 |
| TSK-1502 | Implement Custom Quick Settings tile dispatcher (`CustomActionTileService_1..4`) with in-app configuration sheet, curated Able tile icon chooser, and real-time state reflection. | CUST-001, ADR-024 | UC-29, US-CUST-01 | T-CUST-01 |
| TSK-1503 | Build interactive multi-action AppWidgets using Jetpack Glance (`GlanceAppWidget`) supporting grid layouts (2x1, 4x1, 4x2) with customizable action slots. | CUST-002, ADR-024 | UC-29, US-CUST-01 | T-CUST-01 |
| TSK-1504 | Implement configuration persistence and import/export integration for custom tile mappings and widget layouts. | CUST-001, CUST-002, CFG-001 | UC-14, UC-29, US-CFG-01 | T-EXPORT-01, T-CUST-01 |

---

## Epic 16 — Guidance, feedback, accessibility and brand identity

*Goal: Make Able immediately understandable and recognizable: guided tour, contextual help, haptics, predictive back and expressive motion, readable layouts, and original brand assets (confirmed 2026-11-22).*

| Task ID | Summary | Requirements | Links | Verification |
| --- | --- | --- | --- | --- |
| TSK-1601 | Build skippable guided tour after onboarding, replayable from Settings → Help. | UX-001, NFR-TV-001 | UC-34, US-UX-01 | T-TOUR-01, T-TV-01 |
| TSK-1602 | Add "?" contextual help button and What/Risks/Undo dialog to every advanced or risky option. | UX-002, NFR-ACC-001 | UC-35, US-UX-02 | T-HELP-01 |
| TSK-1603 | Add haptic feedback per the haptics map (confirmations, toggles, actions, slider step ticks and release, buttons, chips, switches, list rows) with a Settings switch (default on) respecting system haptics (revised 2026-11-22). | UX-003 | UC-36, US-UX-03 | T-HAPTIC-01 |
| TSK-1604 | Enable predictive back, tab-order horizontal slide with light spring overshoot, and card/menu container transform with predictive-back shrink; honor "Remove animations" (revised 2026-11-22). | UX-004 | UC-36, US-UX-04 | T-MOTION-01, T-MOTION-02 |
| TSK-1605 | Rework the access checklist into compact rows with expandable explanations. | SET-002, NFR-ACC-002 | UC-01, US-SET-02 | T-PERM-02 |
| TSK-1606 | Produce Able logo, adaptive/monochrome launcher icon, and tile icon set; integrate in app, launcher, and tiles. Blocked 2026-11-22: rebrand in progress, options in `docs/brand/branding-options.html`, owner decision pending. | BRAND-001, CUST-001, ADR-027 | UC-37, US-BRAND-01 | T-BRAND-01 |
| TSK-1607 | Readability audit: 48dp targets, WCAG AA contrast per theme, TalkBack labels, collapsing headers without overlap. | NFR-ACC-002 | US-ACC-01 | T-ACCESS-02 |
| TSK-1608 | Build the app-wide options search: bundled IT/EN index of tools, battery options, apps features, settings, tiles & widgets, Home customization; top-bar entry on every primary destination; result highlight; local recent searches excluded from export. | SEARCH-001, ADR-032 | UC-42, US-SEARCH-01 | T-SEARCH-01, T-TV-01 |
| TSK-1610 | Fix the container-transform defect on small tool cards (stable shared keys, origin kept composed, shape clip, fade fallback) and record before/after captures. | UX-004 | UC-36, US-UX-04 | T-MOTION-03 |
| TSK-1611 | Navigation re-select: re-selecting the current section's item pops to root, closes panes, scrolls to top, selects first tab (bar, rail, TV drawer), with dirty-form guard. | SHELL-001, ADR-040 | UC-48, US-UI-02 | T-UI-02, T-TV-01 |
| TSK-1609 | Fix card headers that squeeze titles (pill/help under the title when needed) and replace raw system values (epoch dates, enum codes, byte counts) with human-formatted values plus secondary raw value. | NFR-ACC-002 | US-ACC-01 | T-ACCESS-03 |

---

## Epic 17 — Power tools: command shell, app lock, and button shortcuts

*Goal: Deliver the 2026-11-22 owner requests that depend on Shizuku or the Accessibility service, with honest capability states (ADR-029–ADR-031).*

| Task ID | Summary | Requirements | Links | Verification |
| --- | --- | --- | --- | --- |
| TSK-1701 | Implement the command shell: Shizuku shell-user execution, streaming stdout/stderr, Stop, exit status, unavailable state without Shizuku. | CMD-001, ADR-029 | UC-38, US-CMD-01 | T-CMD-01, T-SHIZUKU-01 |
| TSK-1702 | Add shell history (local, excluded from Auto Backup and `.ablebackup`), saved snippets, copy/share output, and action-log entries (command + exit status, no output). | CMD-001, NFR-PRIV-002 | UC-38, US-CMD-01 | T-CMD-02 |
| TSK-1703 | Implement the advisory dangerous-command matcher with warning/confirmation dialog and "?" help. | CMD-001, UX-002, ADR-034 | UC-38, US-CMD-01 | T-CMD-01 |
| TSK-1704 | Implement app lock: protected-app picker, grace period, screen-lock check, foreground detection via the Accessibility service, Able lock activity with system `BiometricPrompt`, cancel → Home, re-lock on screen off; no overlays. | APPLOCK-001, ADR-030 | UC-40, US-APPLOCK-01 | T-APPLOCK-01 |
| TSK-1705 | Add app-lock limit disclosures, inactive/unavailable states (Accessibility off, Advanced Protection), and Home safety warning. | APPLOCK-001, HOME-003 | UC-40, US-APPLOCK-01 | T-APPLOCK-02 |
| TSK-1706 | Implement button shortcuts: key-event filtering, gesture recognizer (together, double, long press), target binding to Action Catalog/camera/app/activity, test action, unsupported power-key row. | KEY-001, ADR-031, CUST-001 | UC-41, US-KEY-01 | T-KEY-01 |
| TSK-1707 | Prepare the Play Accessibility prominent disclosure and Permissions Declaration content covering LOCK-001/LOCK-003 (including the Precise overlay), APPLOCK-001, and KEY-001 (release-time decision). | APPLOCK-001, KEY-001, LOCK-003, NFR-SEC-002 | UC-39, UC-40, UC-41 | Release review (R-022) |

---

## Epic 18 — Public repository and release versioning

*Goal: Keep the public GitHub repository clean and the beta versioning consistent (ADR-011 revised, ADR-035, 2026-11-22).*

| Task ID | Summary | Requirements | Links | Verification |
| --- | --- | --- | --- | --- |
| TSK-1801 | Publish to `https://github.com/dragolab/able` with GPLv3 `LICENSE` and a `NOTICE.md` reserving the Able and Drago Labs names and logos; confirm `.gitignore` covers `Able_Documentation_Pack.zip`, `.kotlin/`, `private-references/`, signing material, and binaries; run the pre-push hygiene check and a full-history secret scan. | ADR-011, NFR-MAINT-001 | R-033 | T-REPO-01 |
| TSK-1802 | Set the About GitHub link to `https://github.com/dragolab/able`. | ABOUT-001 | UC-19, US-ABOUT-01 | T-ABOUT-01 |
| TSK-1803 | Release APK 0.6 (`versionName 0.6.0-beta`, `versionCode 6`); keep versionCode monotonic for later betas; attach binaries to GitHub only by separate owner decision. | ADR-035 | — | Release review |
