# Able

**Owner:** Drago Labs  
**Project type:** Android toolbox; implementation in progress  
**Version:** beta 0.5; next APK 0.6 (`versionName 0.6.0-beta`, `versionCode 6`, ADR-035)  
**Repository:** https://github.com/dragolab/able  
**Current product specification:** [`docs/product/ABLE_PRODUCT_SPECIFICATION.md`](docs/product/ABLE_PRODUCT_SPECIFICATION.md)

Able is planned as a device-aware Android toolbox for battery diagnostics and profiles, app management, flashlight and hotspot controls, storage review, and shortcut/activity tools. The first reference phone is a Google Pixel 8 Pro. The product target includes phones, tablets, and a complete Android TV / Google TV remote-first interface.

## Project status

- Product and engineering documentation completed and consolidated.
- Implementation rewritten on 2026-11-22 following the documentation: Material 3 Expressive UI, ViewModel-based screens, manual DI container, adaptive phone/tablet/TV shell. See [`docs/architecture/code-structure.md`](docs/architecture/code-structure.md) for toolchain, layering and conventions.
- V1 target: Android 13+ (minSdk 33, targetSdk 35, compileSdk 37.1), Italian and English, Material You dynamic colors, System/Light/Dark themes.
- Battery automation applies only rules the user has reviewed and enabled; every automatic trigger is off by default; changes are reported and logged. App freeze/disable stays manual. Profiles are user-creatable; several can be active at once and the most restrictive per-app action wins (ADR-005 revised, spec v0.18).
- Product decisions of 2026-11-22 (spec v0.16): full package list via `QUERY_ALL_PACKAGES`, bundled UAD-ng package knowledge base (ADR-026), user-reviewed ACRA crash reports sent by email (ADR-015), Able brand identity (ADR-027), website `https://dragolabs.it`.
- Owner requests of 2026-11-22 (spec v0.17): command shell via Shizuku (CMD-001, ADR-029; requested as "SHELL-001", renumbered because that ID is the app shell), double tap on empty home-screen space with transparent widget fallback (LOCK-003, platform-dependent), app lock with the system biometric/credential prompt and no overlays (APPLOCK-001, ADR-030), volume-key button shortcuts (KEY-001, ADR-031), app-wide options search (SEARCH-001, ADR-032), revised motion/haptics (UX-003, UX-004), readable card headers and human-formatted values (NFR-ACC-002), and APK backup before uninstalling user apps (APP-004, ADR-033).
- Owner decisions and requests of 2026-11-22 (spec v0.18): beta versioning (ADR-035); public repository with GPLv3 `LICENSE` and `NOTICE.md` (ADR-011 revised); custom and concurrent battery profiles with most-restrictive-wins (BAT-001–BAT-003, ADR-005); background permissions management via Shizuku AppOps (APP-007, ADR-036); manual, honest RAM and processes tool (MEM-001, ADR-037; automatic RAM cleaning stays excluded); NFC tile with settings/ADB/Shizuku routes (SYS-001); direct Wireless debugging page (SYS-003); real hotspot control via Shizuku (HOT-001/002, ADR-038); Precise home double-tap mode (LOCK-003, ADR-039); navigation re-select returns to the section start (SHELL-001, ADR-040); small-card container-transform defect (UX-004); rebrand started (BRAND-001, decided in v0.19).
- Owner decisions and requests of 2026-11-23 (spec v0.19): brand concept "Pulse" accepted — a lowercase "a" of a ring and a stem that doubles as a level indicator (mint segment), ink `#101418`, mint `#1FC98E`, Google Sans Flex wght 650 / ROND 0, calm and precise (BRAND-001, ADR-027 revised); floating pill navigation bar with Haze blur and a separate Search button on compact windows (SHELL-001, SEARCH-001, ADR-042); haptics on every tap with per-action patterns and Off/Light/Normal/Strong intensity (UX-003, ADR-043); Home failed-actions notice at the bottom, safety banners on top (HOME-003, ADR-048); swipeable Battery sub-sections with reorganized Monitor and Health (BAT-UI-001); equal-size Tools tiles (TOOLS-001); Storage/RAM app rows, per-app cache bulk clear, and a fixed, filterable large-file finder (STO-001–STO-003, MEM-001); NFC via a Shizuku `WRITE_SECURE_SETTINGS` self-grant after `svc nfc` failed on Android 16 (SYS-001, ADR-044); live debugging toggles (SYS-003); Precise double-tap swipe mitigation (LOCK-003, ADR-039 revised); Fake GPS tool on OpenStreetMap (GPS-003, ADR-045); optional battery-history export and archive schema 2 (CFG-001–CFG-004, ADR-046); commit authorship rule (ADR-047).
- Cloud sync is out of scope for v1. Configuration transfer is a user-controlled local file export/import.
- Android capability and distribution limits are part of the requirements. Features report unsupported/failed states honestly.

## Architecture and module layout

Clean multi-module structure ([ADR-008](docs/architecture/decisions.md#ADR-008), details in [`code-structure.md`](docs/architecture/code-structure.md)):

- `:app` — `AbleApplication` (owns the `AbleContainer`), `MainActivity`, adaptive shell and type-safe navigation (floating pill navigation bar with blur and a separate Search button on compact windows, ADR-042;wide navigation rail on wider windows, Compose for TV drawer on TV), Accessibility screen-lock service, Quick Settings tiles, Glance widgets.
- `:core:model` — Pure Kotlin domain models (`BatteryStatus`, `ToolId`, profiles, GPS points, Home cards...).
- `:core:common` — `AbleResult`/`AbleError`, dispatchers, formatters.
- `:core:database` — Room database (v4), DAOs, migrations, DataStore `UserPreferencesRepository`.
- `:core:capability` — Hardware/permission/Shizuku/secure-settings/location adapters.
- `:core:data` — `AbleContainer` manual DI, shared repositories (battery status, storage summary, Home layout, action log, access snapshot), `ableViewModelFactory`.
- `:core:designsystem` — `AbleTheme` (`MaterialExpressiveTheme`, expressive motion, dynamic color + fallback palette, semantic success/warning colors) and shared components (expressive cards and tiles, status banners/pills, grouped lists, state views with `LoadingIndicator`, focus ring for D-pad).
- `:feature:home` — Modular dashboard (reorder/hide/reset cards), safety banners above cards, adaptive staggered grid.
- `:feature:battery` — Overview & profiles, live monitor service, health, diagnostics.
- `:feature:apps` — Installed-app catalog with list/detail panes, protected apps, per-profile intervention levels, verified Shizuku force-stop.
- `:feature:tools` — Tools hub plus torch, hotspot, storage review, screen lock, Activity Launcher, system toggles, GPS catalog, Fake GPS (in progress), spirit level, ruler, compass.
- `:feature:settings` — Appearance/language (per-app locale), access checklist, privacy, `.ablebackup` export/import with rollback, About, first-run onboarding.

## Implementation snapshot (2026-11-22)

**Verified:** `./gradlew :app:assembleDebug` succeeds; 26 JVM unit tests pass (`testDebugUnitTest`, `:core:model:test`); a launch smoke test on the Android 16 (API 36.1) phone emulator shows onboarding, Home, Battery, Apps, Tools, a tool detail and Settings rendering without Able crashes. No acceptance criterion is marked passed: nothing has been verified on the Pixel 8 Pro, a tablet or a TV.

**Not implemented yet (MUST items still open):** battery profile automation engine and app-action execution/reconciliation (BAT-003..006, BAT-008 — the UI states that automatic app actions are not active), before/after profile comparison (BAT-REV-002), baseline context signals (BASE-002), app disable/enable/uninstall, Able archive and restore (APP-004), UAD-ng knowledge-base filters (APP-006), system-wide cache cleanup and storage visualization (STO-002 revised, STO-003), lock-screen double tap (LOCK-001 revised), guided tour, contextual help, haptics, predictive back (UX-001..UX-004), brand assets (BRAND-001), Shortcut Maker editor, collections and history (SHORT-001..003), hotspot QR/enable/advanced/Bluetooth automation (HOT-001/002 partial), configurable tile/widget action binding (CUST-001/002), ACRA user-reviewed crash reports (PRIV-001 has consent UI only), custom shortcut icon export (CFG-001/002 partial), experimental Pixel tools (PIX-001..006), and the v0.17 additions: command shell (CMD-001), empty home-space double tap and widget fallback (LOCK-003), app lock (APPLOCK-001), button shortcuts (KEY-001), options search (SEARCH-001), revised motion/haptics (UX-003/UX-004 revised), card-header and value-formatting fixes (NFR-ACC-002 revised), user-app APK backup/restore (APP-004 revised), and the v0.18 additions: custom/concurrent profiles (BAT-001–BAT-003 revised), background permissions (APP-007), RAM and processes (MEM-001), NFC routes and Wireless debugging page (SYS-001/SYS-003 revised), Shizuku hotspot control (HOT-001/002 revised), Precise double tap (LOCK-003 revised), navigation re-select (SHELL-001 revised), the small-card container-transform fix (UX-004), and the rebrand assets (BRAND-001, concept "Pulse" decided 2026-11-23); and the v0.19 additions: floating navigation bar (SHELL-001), per-action haptics with intensity (UX-003), Home notice placement (HOME-003), swipeable Battery sub-sections (BAT-UI-001), uniform Tools tiles (TOOLS-001), Storage/RAM row and finder fixes (STO-001–STO-003, MEM-001), NFC self-grant and live debugging state (SYS-001, SYS-003), Precise swipe mitigation (LOCK-003), Fake GPS (GPS-003, in progress), and battery-history export with schema 2 (CFG-001–CFG-004).

## Documentation map

| Document | Purpose |
| --- | --- |
| [`docs/product/ABLE_PRODUCT_SPECIFICATION.md`](docs/product/ABLE_PRODUCT_SPECIFICATION.md) | Product scope, feature behavior, functional requirements, platform constraints, current decisions |
| [`docs/requirements/use-cases.md`](docs/requirements/use-cases.md) | Actor-based use cases with main and alternative flows |
| [`docs/requirements/user-stories.md`](docs/requirements/user-stories.md) | User-centered stories and acceptance criteria |
| [`docs/requirements/non-functional-requirements.md`](docs/requirements/non-functional-requirements.md) | Quality, privacy, accessibility, reliability, compatibility requirements |
| [`docs/requirements/traceability-matrix.md`](docs/requirements/traceability-matrix.md) | Links from requirements to use cases, stories, and verification cases |
| [`docs/ux/adaptive-ui.md`](docs/ux/adaptive-ui.md) | Navigation, layouts, TV remote behavior, localization, themes, capability states |
| [`docs/architecture/decisions.md`](docs/architecture/decisions.md) | Accepted product decisions and implementation decisions still open |
| [`docs/architecture/code-structure.md`](docs/architecture/code-structure.md) | Toolchain, module layering, screen pattern and Material 3 Expressive UI conventions |
| [`docs/architecture/domain-model.md`](docs/architecture/domain-model.md) | Conceptual entities, state boundaries, and portable-data rules |
| [`docs/architecture/risk-register.md`](docs/architecture/risk-register.md) | Feasibility, safety, distribution, and scope risks |
| [`docs/verification/verification-strategy.md`](docs/verification/verification-strategy.md) | Planned verification levels and device/form-factor matrix; tests have not been run |
| [`docs/research/source-register.md`](docs/research/source-register.md) | Public project and Android documentation references |
| [`docs/project/backlog.md`](docs/project/backlog.md) | Implementation backlog organized by epics, tasks, and requirement IDs |
| [`docs/project/first-milestone.md`](docs/project/first-milestone.md) | Technical setup, architecture foundation, and Pixel 8 Pro feasibility milestone |

## Repository preparation

The owner decided on 2026-11-22 to publish the repository now: **https://github.com/dragolab/able** (ADR-011 revised). Source code is licensed under the GNU General Public License v3.0 (GPLv3) through the `LICENSE` file added at publication; a `NOTICE.md` file reserves the "Able" and "Drago Labs" names, logos, and brand assets, which are not licensed under GPLv3. Settings → About links to this repository (ABOUT-001).

These stay local and git-ignored: `private-references/` (private source-description notes), `Able_Documentation_Pack.zip`, signing keys and keystores, `local.properties`, secrets, and release binaries. The public docs contain concise summaries and source links instead of full copied app-store descriptions. Before every push, run the hygiene check T-REPO-01 (risk R-033). Publishing APKs on GitHub Releases needs a separate owner decision.

## Working on the project

Read [`AGENTS.md`](AGENTS.md) and the product specification before changing requirements or beginning implementation. Use stable requirement IDs and update the traceability matrix whenever requirements, stories, or acceptance criteria change. Treat every Android/Shizuku/Accessibility feature as capability-dependent until validated on the target device.

**Commits (ADR-047):** the public history shows only the owner (DragoLab) as author; commit messages carry no tool attribution (no generated-by or co-author trailers for tools).
