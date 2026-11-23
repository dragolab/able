# ABLE documentation completion goal

**Owner:** Drago Labs  
**Project:** Able Android toolbox  
**Status:** Completed; documentation exit criteria fulfilled  
**Current phase:** Documentation closure complete; implementation in progress  
**Next phase:** Complete v1 implementation and verify against the documented acceptance criteria

## Goal

Complete a precise, internally consistent, implementation-ready v1 documentation set for Able, using a guided interview with the product owner. Do not start feature implementation until the documentation exit criteria below are met or every remaining exception is explicitly accepted and recorded.

## Continuity rule

Treat this goal and the repository documents as the persistent source of truth. A new user message is steering for this goal unless the user explicitly cancels or replaces it. Incorporate new decisions, preserve completed work, and resume from the next unchecked item. Do not restart the interview or ask again about already recorded decisions.

## Interview method

- Ask one concise, plain-language question at a time, with a recommendation when useful.
- Explain technical terms and what each choice changes in the product.
- After each answer, update the product specification, use cases, user stories/acceptance criteria, traceability, UX, architecture decisions, risks, and verification documents as applicable.
- Keep confirmed decisions, proposals, open questions, platform constraints, and items needing device validation clearly distinguished.
- Avoid asking the owner to decide implementation details that can be chosen through engineering judgment; document those as proposals or validation tasks.

## Documentation exit criteria

- [x] Product scope is clear: v1, explicitly out of scope, and later ideas.
- [x] Key user journeys and supported/unsupported/error/cancel states are specified.
- [x] Every v1 user-facing feature has a stable requirement ID, use case, user story, acceptance criteria, traceability entry, and planned verification.
- [x] Phone, tablet, and TV navigation, responsive behavior, input methods, accessibility, and visual direction are sufficiently specified.
- [x] Permission, privacy, telemetry, data retention, backup/import, and destructive-action behavior are explicit.
- [x] Android/API/Play-policy-sensitive statements are checked against current primary sources and dated; device-dependent behavior is assigned Pixel 8 Pro feasibility checks.
- [x] Architecture decisions distinguish accepted product decisions from proposed implementation choices; risks and validation gates are recorded.
- [x] Cross-document ID/link consistency and repository cleanliness are reviewed; documentation package is refreshed.
- [x] Create an implementation backlog and an ordered first development milestone.

## Confirmed decisions already recorded

- Product name Able; owner Drago Labs.
- Pixel 8 Pro is the first reference phone; Android 13+; phone, tablet, and Android TV/Google TV are targets.
- Italian and English, system locale by default with override; System/Light/Dark theme.
- Cloud sync is out of scope for v1.
- Crash diagnostics (revised 2026-11-22, ADR-015): ACRA crash reports, offered only after opt-in, reviewed by the user and sent only by the user via email to `info.dragolabs@gmail.com`; no automatic upload, no third-party server, no feature-use analytics. No account identifiers, message/file contents, hotspot credentials, or installed-app list.
- Battery automatic triggers are all disabled by default (ADR-005, 2026-11-22). Profiles are user-creatable; several can be active at once and the most restrictive per-app action wins (ADR-005 revised, v0.18).
- Settings/About includes the Able GitHub repository, `https://dragolabs.it`, and `info.dragolabs@gmail.com`; the repository is `https://github.com/dragolab/able` (configured 2026-11-22).
- Home screen is a fully modular card-based dashboard: all sections are independent cards that can be reordered, hidden, shown, and reset to defaults; battery level/profile and quick actions are prioritized at top by default; critical automation and capability failures always display a prominent safety alert/banner.
- Pixel ToolBox candidate features (AOD for notifications/charging, Recents close all, screen-off fingerprint, ringtone QS tile, orientation camera launch) are in v1 scope as experimental tools, conditional on Pixel 8 Pro feasibility verification; unsupported states will be reported honestly.
- Full battery diagnostics, analytics suite, and aBattery-inspired hardware health capabilities (baseline BASE-001/002, app impact review, before/after profile review, charging session diary, anomaly checklist, session notes, cycle count via Android 14+ public API, SoH % / capacity / manufacture & first-use dates via Shizuku) are confirmed for v1.
- Storage cleanup presets: default thresholds identify large files >100 MB (filters: 50MB, 100MB, 500MB, 1GB) and old downloads >30 days (filters: 7, 30, 90 days); deletions require explicit user selection and Android Storage Access Framework confirmation.
- Shortcut Maker collections: deleting a non-empty collection prompts the user to choose between 'Move to Unsorted' and 'Delete shortcuts too', protecting user configurations and custom icon assets from accidental deletion.
- Architecture and UI stack: 100% native Kotlin with Jetpack Compose across all form factors; `androidx.compose.material3.adaptive` for phones and tablets, and Compose for TV (`androidx.tv:tv-material`, `androidx.tv:tv-foundation`) for Android TV remote-first navigation (ADR-008).
- Software licensing and repository governance: GNU General Public License v3.0 (GPLv3) for source code; project name 'Able', 'Drago Labs', and brand/logo assets remain proprietary/reserved. Revised 2026-11-22: the repository is public at `https://github.com/dragolab/able` with the GPLv3 `LICENSE` and a `NOTICE.md` reserving names and logos; `Able_Documentation_Pack.zip` stays local (ADR-011). Current version: beta 0.5; next APK 0.6.0-beta (ADR-035).
- Geolocation tools: offline GPS coordinate grabbing with accuracy thresholds, custom labels, notes, tag filtering, quick text/geo: sharing, and navigation launching (ADR-021).
- Physical measurement suite: spirit level (inclinometer) with 2D bubble and haptic snap at 0°, screen ruler with dual mm/inch scales and DPI calibration, and digital compass with magnetic declination correction; sensors strictly detach on pause with zero background battery impact (ADR-022).
- Advanced system toggles: Quick Settings tiles and in-app controls for NFC, Auto-brightness, USB/Wireless Debugging, and Private DNS cycling; implements two-tier fallback (privileged direct toggle via Shizuku/WRITE_SECURE_SETTINGS vs opening Android settings intent) (ADR-023).
- Universal Action Catalog and multi-action widgets: centralized `AbleAction` registry powering user-assignable Quick Settings tiles and interactive multi-action AppWidgets via Jetpack Glance (ADR-024).

## Next interview area

Interview complete: all v1 product requirements, UX patterns, and architecture decisions confirmed. Transition to technical phase (Milestone 1) is ready upon explicit user confirmation.
