# Able use cases

**Status:** Draft based on confirmed product decisions.  
**Actors:** End user; Android OS; launcher; Shizuku service (optional); installed app (target).

Use case IDs are stable. Android, the launcher, and Shizuku are external actors: Able must handle cancellation, denial, service loss, and rejected operations.

## UC-01 — First launch and capability setup

**Primary actor:** End user  
**Preconditions:** Able is installed and opened for the first time.

**Main flow:**

1. Able reads the current display size/input context and available device features.
2. Able follows the Android language and theme unless the user already set Able-specific overrides.
3. Able explains what Able does and how local configuration is stored.
4. Able shows optional capabilities (Accessibility, Usage access, notifications, Shizuku) with purpose and state.
5. User may open the relevant Android settings, grant access, or skip setup.
6. Able offers the skippable guided tour (UC-34).
7. Able opens Home with the default modular card layout (battery/profile and quick action cards at top); unavailable actions have a clear state.

**Alternatives:** Permission denied or setup skipped → feature remains off and displays a settings path. No battery/torch → hide or mark the affected tool unavailable.  
**Postconditions:** No powerful permission is enabled by Able without user action; no app restriction is applied; every trigger of every profile is disabled, so Normale is the effective profile.

## UC-02 — Change Able language or theme

**Primary actor:** End user  
**Preconditions:** Able is open.

1. User opens Settings → Appearance.
2. User chooses System / Italiano / English and System / Light / Dark.
3. Able applies the selected values and persists them.
4. If System is selected, Able follows the current Android setting again.

**Alternatives:** Locale resource missing → fall back to the default language and log a development defect; user cancels → keep prior selection.  
**Postconditions:** Able content, accessibility labels, notifications, and supported widgets use the chosen locale/theme.

## UC-03 — Use Able on phone, tablet, or TV

**Primary actor:** End user  
**Preconditions:** Able is running on a supported Android form factor.

1. Able chooses a layout based on current window size and input capabilities.
2. On compact phone windows it shows a one-column modular card layout and the floating navigation bar with a light background blur and a separate circular Search button on its right; content is padded so nothing hides under the bar (revised 2026-11-23, ADR-042).
3. On wide/tablet windows it uses available space for responsive multi-column card grids, or list/detail multi-pane layouts.
4. On Android TV it exposes a remote-first card layout; user reaches all visible controls and cards using D-pad, Select, and Back.
5. Able includes only actions supported by the device, or explains why an action is unavailable.
6. Selecting the current section's navigation item again returns to that section's start (UC-48).

**Alternatives:** Window resized/rotated → layout recomputes without losing unsaved data; a window that becomes medium/expanded switches from the floating bar to the rail. Blur unavailable or disabled → the bar uses an opaque tonal surface. Keyboard shown → the bar hides or lifts and never covers the focused field. D-pad focus cannot reach a visible action → TV usability defect.  
**Postconditions:** The current screen is operable using the device's primary input method.

## UC-04 — Configure a battery profile

**Primary actor:** End user  
**Preconditions:** Battery feature exists; user can open Battery.

1. User opens Battery → Profiles and selects a built-in profile (Normale, Notte, Risparmio, Emergenza, Pausa app) or a user-created profile (creating one is UC-43).
2. Able displays the profile's triggers, whether it is active, proposed protected apps, per-app actions, and any app where another profile sets a more restrictive action (revised 2026-11-22).
3. User enables the individual triggers to use (all are disabled by default) and edits schedule/days/thresholds, protected apps, and app intervention levels.
4. Able previews the affected apps and likely impact on notifications, alarms, and synchronization.
5. User confirms and enables the profile.
6. Able stores the configuration and shows its next trigger/effective state.

**Alternatives:** User cancels → retain the old configuration. User saves without enabling any trigger → the profile is stored but never activates automatically, and Able says so. Shizuku unavailable → only standard actions remain selectable. All apps protected/Observe-only → profile can still provide monitoring but makes no restriction.  
**Postconditions:** Only reviewed and pre-authorized actions may run automatically.

## UC-05 — Automatic profile transition

**Primary actors:** End user; Android OS  
**Preconditions:** User enabled a profile and pre-authorized its actions.

1. Android reports a trigger, such as battery threshold, charging state, schedule, or screen-off duration, for a trigger the user has enabled (all are disabled by default), or the user activates a profile manually.
2. Able updates the set of active profiles: each profile is active while any of its enabled triggers is true; several can be active at once (revised 2026-11-22; the fixed priority Emergenza → Risparmio → Notte → Normale is superseded).
3. For each app Able computes the effective action across active profiles: the most restrictive wins (Osserva < Limita in background < Arresta quando esco); protected apps are excluded.
4. Able compares actual app state with the effective desired state and applies only necessary, supported changes, recording the source profile(s) of each.
5. Able records each result and sends a summary notification naming the profile(s) that changed.
6. When a profile ends, Able recomputes the effective actions from the remaining active profiles and restores only changes it can safely attribute to Able and that no other active profile still requires.

**Alternatives:** Permission revoked, Shizuku disconnected, or OS rejects an operation → mark skipped/failed, notify when possible, and do not claim success. Notification delivery fails → the operation and action log remain independent. Freeze/disable requested as an automated action → reject configuration; those operations remain manual. Two profiles activate from the same event → one combined evaluation, no duplicate changes. A profile is deleted while active → it is deactivated first and its Able-attributed changes are re-evaluated.  
**Postconditions:** Active profiles, effective app actions with their source profiles, changed app states, and failures are inspectable, and automation can be paused.

## UC-06 — Inspect battery use and run the live monitor

**Primary actor:** End user  
**Preconditions:** Battery data is available; continuous monitoring is optional.

1. User opens Battery to view state, profile, and supported diagnostics.
2. User enables the monitor and selects notification, widget, or both.
3. User chooses reset rules, idle-drain threshold, units, compact/detailed view, and history retention (30 days, 90 days by default, or until manually deleted).
4. Able explains persistent monitoring and starts only after confirmation.
5. Able records screen-on/screen-off/charging intervals and shows unavailable metrics honestly.
6. User stops the monitor from Able or its notification.

**Alternatives:** Insufficient sample → show waiting/not enough data. Reboot or service interruption → mark the interval partial. Data source missing deep-sleep/current values → omit or mark unavailable. User selects Delete history now → delete session summaries and derived baseline, but preserve profiles/app rules; report completion.  
**Postconditions:** Measurement history stays local; rate calculations are estimates for valid recorded intervals. Session summaries follow the selected retention period; high-frequency samples are temporary.

## UC-16 — Opt in to personal idle-drain alerts

**Primary actor:** End user; Android notification system  
**Preconditions:** The personal idle-drain baseline is enabled and has enough valid sessions to be established.

1. Able presents a one-time, dismissible in-app offer to enable alerts when a later completed night is outside the user's personal range.
2. User chooses Enable or Not now.
3. If Enable is selected, Able explains and requests notification permission in context, then activates the alert preference only if permission is granted.
4. When a later valid session completes, Able evaluates the calibrated personal threshold and sends at most one alert if exceeded.
5. User can disable alerts or re-enable them later in Battery settings.

**Alternatives:** Baseline not established → no prompt. User selects Not now → alerts remain off and the offer is not repeated automatically, but the setting remains available. Notification permission denied or later revoked → alerts remain off/paused and Able links to Android Settings without nagging. Invalid/partial session → no alert.  
**Postconditions:** Alerts are enabled only by explicit user action and never identify an app as the cause without separate evidence.

## UC-17 — Review optional context for an idle-drain anomaly

**Primary actor:** End user  
**Preconditions:** A personal baseline exists; the user opens a session or anomaly checklist.

1. Able shows the battery/screen/charging measurements that formed the baseline comparison.
2. Able offers contextual diagnosis as a separate optional action and explains the candidate signals.
3. User chooses which available context sources to enable (for example temperature, hotspot activity, or network reception).
4. Able requests any required access only for the selected source and only at this point.
5. Able displays collected context with its source/availability and lets the user turn off collection.

**Alternatives:** User skips or denies access → baseline remains usable; context is marked not collected/unavailable. Device/API does not expose a signal → do not infer it.  
**Postconditions:** Context is stored locally with the session retention period and is deleted by the same Delete history action.

## UC-18 — Review and send a crash report

**Primary actor:** End user; Android email composer  
**Preconditions:** Able is installed; the user decides on crash reports during onboarding or in Settings → Privacy, or Able has crashed.

1. Able explains that crash reports are collected locally by the open-source ACRA library, contain technical crash data only, and are sent only if the user chooses to email them to Drago Labs.
2. User turns the crash-report setting on or keeps it off (default off until chosen).
3. After a crash, if the setting is on, Able shows the report on next launch and lets the user read its content.
4. User chooses Send or Discard. Send opens the Android email composer addressed to `info.dragolabs@gmail.com` with the report attached or in the body; the user sends it from the email app.
5. User can turn the setting off at any time in Settings → Privacy; Able stops offering reports and deletes stored unsent reports.

**Alternatives:** Setting off → no crash dialog is shown and nothing is sent. User discards → delete that report. No email app available → offer to copy the report text and the address; report the outcome. User cancels in the email app → nothing is sent; Able does not retry automatically.  
**Postconditions:** No automatic upload, no third-party server, and no feature-use analytics; reports exclude account identifiers, message/file contents, hotspot credentials, and the installed-app list; the setting and reports are excluded from configuration export.

## UC-19 — Open Drago Labs project and support links

**Primary actor:** End user  
**Preconditions:** Able is installed; user opens Settings → About.

1. Able displays the Able GitHub repository link (`https://github.com/dragolab/able`), Drago Labs website link (`https://dragolabs.it`), support email (`info.dragolabs@gmail.com`), and third-party attributions including the UAD-ng list (license and snapshot commit).
2. User selects a link; Able opens the matching external browser or email-composer intent.
3. Able returns to the About screen without losing app state.

**Alternatives:** The repository row opens `https://github.com/dragolab/able` (configured 2026-11-22); no other repository is linked. No compatible browser/email handler or launch rejected → offer to copy the exact URL/email address and show success or failure. User cancels → return to About with no changes.  
**Postconditions:** Able does not send an email or submit data automatically; external navigation occurs only after the user selects a link.

## UC-07 — Turn off and lock the screen using double tap

**Primary actor:** End user  
**Preconditions:** Compatible touch/launcher/OS behavior and supported lock route exist.

1. User opens Screen lock → Double tap.
2. Able describes the Accessibility service and why it is needed to detect the gesture and lock the screen.
3. User opens Android Settings, enables the Able Accessibility service, and returns.
4. User chooses the contexts to enable: launcher home screen, lock screen, or both.
5. Able offers a test gesture for each chosen context and reports whether it was detected and the screen turned off.
6. User enables the feature for the contexts that passed and may add the Quick Settings tile or home-screen shortcut.
7. For the home-screen context, the double tap applies to empty home-screen space only (UC-39).

**Alternatives:** Launcher does not expose reliable home detection, the lock screen blocks overlay/gesture detection, permission denied, or Pixel behavior is unsupported → leave that context disabled, label it unsupported, and offer the tile/shortcut fallback. Accessibility service later disabled by the user or OS → report the feature as inactive; do not claim it works.  
**Postconditions:** Lock is initiated only by a user gesture/action and only through a verified supported route.

## UC-08 — Control the flashlight

**Primary actor:** End user  
**Preconditions:** Device reports a camera flash/torch.

1. User opens Torch or a configured shortcut/tile.
2. Able displays toggle and only the brightness levels/modes reported as supported.
3. User selects a level or mode and starts it.
4. Able shows an accessible stop action and stops on user request or torch/camera failure.

**Alternatives:** No flash, unsupported intensity, camera conflict, or service error → show actual limitation and leave the torch off when state is uncertain.  
**Postconditions:** Torch state is read back where the API permits; SOS/Morse modes can be stopped without navigating away.

## UC-09 — Configure hotspot or Bluetooth automation

**Primary actor:** End user  
**Preconditions:** Hotspot feature is supported; advanced controls may require Shizuku.

1. User opens Hotspot and views current status (on/off control and SoftAP configuration are UC-46).
2. User creates a rule for a selected paired Bluetooth device.
3. Able explains Shizuku, Bluetooth, and background requirements in context.
4. User reviews on-connect and on-disconnect behavior and enables the rule.
5. Able reads back the resulting hotspot state and records the last execution.

**Alternatives:** Feature missing, Shizuku stops, permission denied, or OS rejects a setting → preserve the prior rule/state and show failure.  
**Postconditions:** Rule can be paused/deleted independently and never silently claims a rejected change succeeded.

## UC-10 — Review or change an installed app

**Primary actor:** End user  
**Preconditions:** App listing is available; privileged operations are optional.

1. User searches/filters the Apps list.
2. Able shows app identity, protected state, configured profile actions, and available battery evidence.
3. User may configure observation/restriction, force-stop manually, disable/enable, uninstall for the user, or archive (UC-32).
4. For disruptive actions, Able previews consequences, including the UAD removal level (UC-33), and requires confirmation.
5. Able verifies and logs the outcome; if possible it offers a restore action.

**Alternatives:** App not visible because the build lacks `QUERY_ALL_PACKAGES` (possible Play flavor), system protection, missing Shizuku, or OS rejection → explain the limitation. App data is not included or deleted by these package actions.  
**Postconditions:** No app is frozen, disabled, or uninstalled by an automatic profile.

## UC-11 — Review and clean storage

**Primary actor:** End user  
**Preconditions:** Storage summary or user-selected documents are available.

1. User opens Storage (overview in UC-30) and selects a category (Large files, Old downloads, or Cache; system-wide cache cleanup is UC-31).
2. For Large files, Able defaults to >100 MB and offers filter chips (50MB, 100MB, 500MB, 1GB, custom); for Old downloads, Able defaults to >30 days with filter chips (7, 30, 90, 365 days). Revised 2026-11-23: the large-file finder also filters by type, age, location (all, Downloads, DCIM/Pictures, user-picked SAF folder), and name, and sorts by size, date, or name.
3. Able lists matching items with name, size, date, path, and preview thumbnail where available.
4. User selects items to delete and reviews the selection count and total space to be freed.
5. User confirms; Able opens Android's Storage Access Framework / MediaStore delete confirmation.
6. Able deletes only confirmed items, refreshes the storage scan, and displays the actual space freed.

**Alternatives:** User adjusts filter chip → Able updates scan results without deleting anything. Invalid custom size → rejected with a message. No result → Able lists the active filters and offers to clear them. Scan cancelled → partial results labelled partial. User-picked folder access revoked → Able asks to pick it again. User cancels confirmation or file disappears → no unrelated file is deleted; Able reports partial completion. Other apps' private data is not exposed.  
**Postconditions:** Actual freed space is refreshed; deletions occur only upon explicit user confirmation.

## UC-12 — Find and open an app activity

**Primary actor:** End user  
**Preconditions:** Activity Launcher is available.

1. User opens Activity Launcher, searches or expands an installed app.
2. Able displays activities Android reports as normally launchable.
3. User selects an activity and chooses Open or Create shortcut.
4. Open starts the activity if Android permits; Create shortcut opens UC-13.

**Alternatives:** Component is private, disabled, removed, or rejected → show unavailable/failed result and do not bypass Android controls.  
**Postconditions:** Activity Launcher remains a distinct tool; no system app state changes.

## UC-13 — Create and organize a shortcut

**Primary actor:** End user; launcher  
**Preconditions:** Target is supported and the launcher accepts shortcut pinning.

1. User chooses a target from Shortcut Maker or Activity Launcher.
2. Able opens the shared editor with target details.
3. User edits label/icon, previews the shortcut, and optionally selects a collection.
4. Able requests pinning through Android/launcher UI.
5. Able records success or cancellation and adds the definition to history/favorites as appropriate.
6. When managing collections, user can create, rename, reorder, or delete them; deleting a non-empty collection prompts the user to either move contained shortcuts to 'Unsorted' or delete them.

**Alternatives:** Target unsupported or launcher cancels → keep the draft or show a reason; do not claim the icon was pinned. User cancels collection deletion dialog → collection and shortcuts remain unchanged.  
**Postconditions:** User can create, rename, reorder, delete collections and move shortcut definitions among them; shortcut definitions and custom icon assets are never deleted without explicit confirmation.

## UC-14 — Export selected configuration

**Primary actor:** End user  
**Preconditions:** User opens Settings → Backup/transfer.

1. User selects the export sections for this export (revised 2026-11-23, ADR-046): battery profiles, app rules/protected list, Home/appearance, shortcuts/collections, custom icons, GPS and Fake GPS points, and optionally battery history and measurements (never preselected).
2. If battery history is selected, Able shows a privacy notice (the file reveals usage patterns over time) and the user confirms or deselects it.
3. Able previews included and excluded information; usage history, OS permission grants, Shizuku state, the `WRITE_SECURE_SETTINGS` grant, the mock-location selection, app data, and hotspot passwords are never included.
4. User chooses a destination with Android's document picker.
5. Able writes a `.ablebackup` archive with schema version 2 and confirms the result.

**Alternatives:** User cancels the section chooser, the privacy notice, or the file picker → no export is written. Battery history empty → the section is shown as empty and cannot be selected. Write fails → retain local configuration and show failure.  
**Postconditions:** Export is a user-controlled file and does not upload to Able/Drago Labs.

## UC-15 — Import configuration

**Primary actor:** End user  
**Preconditions:** User has a candidate `.ablebackup` file.

1. User selects the file through Android's document picker.
2. Able validates archive structure, size, checksum, schema, and values without executing archive content; schema 1 and schema 2 are accepted (schema-1 profiles are mapped to the current profile model).
3. Able previews sections, target-device differences, missing apps/activities, conflicts, and, for battery history, the number of new sessions, the duplicates that will be skipped, and the sessions older than the current retention period that the next retention run would delete.
4. User selects merge or replace; merge conflicts are resolved explicitly.
5. Able creates a rollback snapshot and asks for confirmation.
6. Able applies supported configuration, reports skipped items, and offers undo/restore.

**Alternatives:** Invalid/unsupported file (including a schema newer than 2), user cancellation, or storage permission loss → no configuration changes are applied. Invalid battery-history records (time order, ranges, overlaps) → those records are rejected and listed; the rest of the import can continue only after the user confirms. Same file imported twice → duplicate sessions are skipped. Missing target app → mark the rule unresolved; never install/disable an app as a side effect.  
**Postconditions:** Only the user's selected configuration changes; launcher-pinned icons may need a separate pin confirmation.

## UC-20 — Customize Home dashboard cards

**Primary actor:** End user  
**Preconditions:** Able is open at Home.

1. User enters Home customization mode (via edit action on Home or Settings → Home layout).
2. Able displays the list of available modular cards (Battery & Profile, Quick Actions, Capability Status, Storage Summary, Favorite Shortcuts, Recent Actions/Warnings) with their current visibility and order.
3. User reorders cards via drag-and-drop or move controls, and toggles visibility of individual cards.
4. User may select Reset to default to restore factory ordering and visibility.
5. User confirms changes.
6. Able applies the new card configuration to Home and persists it locally.

7. (Revised 2026-11-23, HOME-003, ADR-048) Critical safety banners appear at the top of Home; failed or skipped individual actions appear in a failed-actions notice at the bottom of Home. The user can select Details (action history filtered to failures) or Hide; the Recent actions card offers "Show all" (history sheet) and no "Open Battery" button.

**Alternatives:** User cancels without saving → keep previous card configuration. User hides all non-critical cards → Home still displays an active safety banner if any critical condition occurs. User hides the failed-actions notice → it stays hidden until a newer failure is recorded; log entries are kept. A critical condition cannot be hidden while it persists.  
**Postconditions:** Customized card order and visibility persist across restarts and are included in exported configuration.

## UC-21 — Use experimental Pixel toolbox actions

**Primary actor:** End user  
**Preconditions:** Able is running on a supported device (primary reference: Google Pixel 8 Pro).

1. User opens Tools → Experimental / Pixel Tools.
2. Able badges the section as experimental and lists available actions: AOD on notification, AOD while charging, Recents close all apps, screen-off fingerprint, ringtone Quick Settings tile, and orientation camera launch.
3. For each tool, Able shows the required system access (Notification listener, Shizuku, Accessibility, Notification Policy, sensor access).
4. User enables a chosen feature; Able prompts for the specific required permission/service in context.
5. If the feature requires hardware or system capabilities unsupported by the device/OS, Able displays an unsupported reason and keeps the toggle disabled.
6. When an enabled trigger occurs (e.g. charging connects, screen wakes with specific orientation, ringtone tile tapped), Able executes the verified action.

**Alternatives:** Device lacks capability, OS revokes permission, or Android security rejects operation → Able reports failure honestly in the action log / UI and does not simulate success. Camera orientation sensor check times out → do not launch camera; abort safely.  
**Postconditions:** Experimental actions operate only after explicit user configuration and never run destructive operations automatically without warning.

## UC-22 — Review app battery impact and profile before/after comparison

**Primary actor:** End user  
**Preconditions:** Battery monitor has collected valid session intervals; user opens Battery → Impact & Comparison.

1. User selects an app from the observed list or chooses a profile rule comparison.
2. For an app, Able summarizes system-reported background and foreground activity, indicates confidence level, and provides a direct shortcut to Android's App Info / Battery usage settings.
3. For profile review, Able displays two comparable observation windows (before and after rule activation) matching duration and screen-off status.
4. Able labels differences as observed correlations without claiming guaranteed or causal savings.

**Alternatives:** Usage access missing → Able marks data as incomplete and provides an optional link to grant access; user may skip. Windows are too dissimilar (e.g. charging vs unplugged) → Able marks comparison invalid/incomparable.  
**Postconditions:** No app action is triggered automatically; no causal battery claims are made.

## UC-23 — Track charging sessions and annotate battery events

**Primary actor:** End user  
**Preconditions:** Device is connected to charger, or user reviews past sessions in Battery → Charging diary.

1. When charging begins, Able records start percentage, start timestamp, and monitors temperature, voltage, and wattage where hardware exposes it.
2. When charging finishes, Able records duration, end percentage, peak temperature, and average charging rate.
3. If temperature exceeds the safety/warm threshold, Able highlights the session with a thermal note without altering hardware charging behavior.
4. User opens a charging or screen-off session and selects Add note/tag (e.g. travel, gaming, hotspot, navigation).
5. Able persists the note locally alongside the session record.

**Alternatives:** Device does not expose current/wattage → record only percentage, timestamp, and temperature. User deletes battery history → notes and charging records associated with those sessions are removed.  
**Postconditions:** Charging records and notes stay on-device and follow the user's retention choice (30/90 days or manual).

## UC-24 — Troubleshoot battery drain with the anomaly checklist

**Primary actor:** End user  
**Preconditions:** User opens Battery → Troubleshooting / Anomaly checklist.

1. Able guides the user through common battery drain factors: poor mobile signal, active hotspot, excessive screen-on time, and heavy background apps.
2. For each factor, Able evaluates currently available system signals without continuous background polling.
3. Able provides clear recommendations and direct intents to Android Settings (e.g., Cellular network, Hotspot, Display, Battery usage).
4. User reviews and adjusts system settings according to their preferences.

**Alternatives:** Signal or hotspot status unavailable → step displays guidance with a manual check link. User dismisses wizard → no changes are forced.  
**Postconditions:** Checklist serves as an advisory tool and does not apply hidden system tweaks.

## UC-25 — Inspect hardware battery health and lifecycle data (aBattery capabilities)

**Primary actor:** End user  
**Preconditions:** Device runs Android 13+ (primary reference: Pixel 8 Pro on Android 14+); user opens Battery → Battery health.

1. Able reads `BatteryManager.EXTRA_CYCLE_COUNT` from `ACTION_BATTERY_CHANGED` on Android 14+ and displays the charge cycle count when the device supplies it.
2. Able checks for Shizuku or system-level battery stats access.
3. If Shizuku or system access is active, Able reads State of Health (SoH) %, design capacity vs current full charge capacity in mAh, battery manufacturing date, first usage date, and charging policy.
4. Able displays these metrics alongside a clear explanation of their source (Public Android 14 API vs System/Shizuku).
5. If the hardware, OEM security layer (e.g. Knox), or HAL returns unpopulated or -2/null values, Able displays "Unsupported by device hardware/firmware" rather than fabricating estimates.

**Alternatives:** Shizuku is not running or permission denied → show public cycle count (on Android 14+) and mark advanced health metrics as requiring Shizuku; offer connection guide. Device on Android 13 without HAL cycle count → display cycle count as unsupported on current OS version.  
**Postconditions:** No fake health scores are generated; all health observations are stored locally.

## UC-26 — Save, manage, and share GPS coordinates

**Primary actor:** End user; Android Location subsystem  
**Preconditions:** Device has location provider; user opens Tools → GPS.

1. User taps Save current location.
2. Able requests `ACCESS_FINE_LOCATION` in context if not previously granted.
3. Able acquires current GPS fix (latitude, longitude, altitude, estimated accuracy in meters, timestamp).
4. User enters custom label, optional tags (e.g. Parking, Trekking), and notes, then saves.
5. Point appears in the local searchable GPS catalog.
6. User can view point details, tap Open in Maps to launch external map apps via `geo:` intent, or tap Share to share coordinates via text, geo URI, or web link.

**Alternatives:** Permission denied or GPS hardware disabled → show prompt to enable location services in Android settings; do not run continuous retries. Location fix times out or accuracy is poor (>50m) → inform user and offer to save with current accuracy or wait.  
**Postconditions:** Location capture is user-initiated only; points are saved locally in Room and included in configuration export.

## UC-27 — Use physical measurement and sensor tools (level, ruler, compass)

**Primary actor:** End user; Android Sensor subsystem  
**Preconditions:** User opens Tools → Measurement (Level, Ruler, or Compass).

1. User selects Spirit level: Able registers accelerometer/rotation sensors, displays 2D bubble and pitch/roll degrees, provides zero calibration, and triggers haptic feedback when aligned at 0°.
2. User selects Screen ruler: Able renders millimeter and inch graduations based on physical screen DPI; user can adjust the calibration slider against a physical card.
3. User selects Compass: Able registers magnetometer/accelerometer, shows heading in degrees and cardinal direction, with magnetic/true north toggle.
4. When leaving the screen or putting Able in background, sensor listeners are immediately unregistered.

**Alternatives:** Magnetometer or accelerometer missing (e.g. on Android TV or specific tablets) → tool displays "Hardware sensor unavailable on this device" and disables active readouts. Compass sensor reports low accuracy → display figure-8 motion calibration hint.  
**Postconditions:** Sensors are active strictly during foreground use; zero background power drain.

## UC-28 — Control advanced system tiles (NFC, Auto-brightness, USB/Wireless Debugging, Private DNS)

**Primary actor:** End user; Android System Settings / TileService  
**Preconditions:** User adds Able's system tiles to the Quick Settings shade or uses in-app switches.

1. **NFC tile (revised 2026-11-23, ADR-044):** Tapping toggles NFC directly if `WRITE_SECURE_SETTINGS` is granted (via ADB, setup shows the command with copy, or via the Shizuku self-grant of UC-51); with Shizuku but no grant, Able offers the self-grant first; otherwise opens native Android NFC settings. Able never runs `svc nfc` through Shizuku (it fails on Android 16). The tile shows the real adapter state.
2. **Auto-brightness tile:** Tapping toggles between Automatic and Manual brightness using `WRITE_SETTINGS` (prompts user to allow Modify System Settings if not yet granted).
3. **USB / Wireless Debugging tile:** Tapping toggles debugging directly if `WRITE_SECURE_SETTINGS` is granted (ADB or Shizuku self-grant); otherwise opens Android Developer Options; in-app view displays IP and port for wireless ADB; "details" opens the Wireless debugging page directly, falling back to Developer options and then Android Settings (revised 2026-11-22). Switches and tiles follow changes made elsewhere immediately through Settings observers (revised 2026-11-23).
4. **Private DNS tile:** Tapping cycles between Off, Automatic, and user's saved DNS provider (e.g. Cloudflare, AdGuard) via Shizuku; fallback opens Private DNS settings dialog.

**Alternatives:** Privileged access missing → tier-2 safe fallback opens the matching Android settings screen with explanation; no crash or silent failure. Self-grant refused or failed → reason shown, settings fallback. Grant revoked externally → next route used and the checklist updated. No NFC hardware → NFC tile unavailable.Wireless debugging page route not resolvable → next fallback with an explanation; Developer options disabled → explain how to enable them.  
**Postconditions:** Real system state is read back and reflected on tile icons/subtitles immediately.

## UC-29 — Create and customize dynamic tiles and multi-action widgets

**Primary actor:** End user; Android AppWidget subsystem / Quick Settings  
**Preconditions:** User accesses Tools → Tile & Widget customizer, or adds an Able widget/tile to the home screen.

1. User opens Custom Tile setup, selects a tile slot (1..N), and binds an action from the `AbleAction` catalog (e.g. Torch, Profile pause, Hotspot, GPS Quick Save, Private DNS). Able updates the tile state and label dynamically.
2. User adds an Able multi-action widget to their launcher, chooses a template (e.g. 2x1, 4x1, 4x2), and assigns distinct actions to each slot.
3. Tapping a widget button executes the bound action immediately (with haptic feedback and visual state update).
4. Custom tile and widget configurations are saved locally and included in `.ablebackup`.

**Alternatives:** Launcher does not support dynamic widget resizing → widget retains default layout. Action requires ungranted permission → widget button displays an alert badge and opens the permission rationale in Able.  
**Postconditions:** Multi-action widgets and custom tiles operate reliably without requiring full app launch; settings survive reboot and configuration restore.

## UC-30 — Visualize storage use

**Primary actor:** End user; Android storage services  
**Preconditions:** User opens Tools → Storage.

1. Able reads total, used, and free space and shows an overview chart with a text equivalent.
2. Able shows a category breakdown (apps, images, video, audio, documents, downloads, other) using `StorageStatsManager` and MediaStore where Android exposes each category.
3. Able lists the largest folders and files it can access, with size and path.
4. User selects a category or item to drill into; any deletion continues through the review-before-delete flow of UC-11.

**Alternatives:** A category is not exposed or needs an access the user has not granted → label it unavailable and offer the relevant access in context. Scan cancelled → keep partial results labelled partial. Values change during the scan → refresh on completion.  
**Postconditions:** Nothing is deleted from the visualization itself; values are Android-reported, not estimated silently.

## UC-31 — Clean all app caches

**Primary actor:** End user; Shizuku service (optional); Android OS  
**Preconditions:** User opens Storage → Cache.

1. Able offers Clean Able cache and Clean all app caches, and explains that apps rebuild caches and the operation can take time.
2. User chooses Clean all app caches and confirms.
3. If Shizuku is connected, Able measures free space, runs the system cache trim (for example `pm trim-caches`), and measures free space again.
4. Able reports the actual freed space and logs the result.
5. (Revised 2026-11-23) Alternatively, the user opens the per-app cache list (app icon, name, cache size, centered checkbox), selects one or more apps or all user apps, and chooses Clear cache; Able shows a confirmation listing apps and total cache size, clears each app's cache, and shows a per-app result with measured freed space, failed rows first, consistent with the RAM tool's bulk action.

**Alternatives:** Shizuku unavailable → Able explains why it cannot clear other apps' caches and opens Android's storage settings. Command rejected or fails → report failure, no success claim. Freed space is zero or negative due to concurrent writes → report the measured value honestly.  
**Postconditions:** No app private files/data are deleted; the reported amount is measured, not estimated.

## UC-32 — Archive and restore removed or disabled apps

**Primary actor:** End user; Shizuku service; Android PackageManager / PackageInstaller  
**Preconditions:** User opens an app detail (UC-10); privileged actions require Shizuku.

1. User chooses Disable, Uninstall for user, or, on Android 15+ where available, Archive.
2. Able shows a separate confirmation with consequences, the UAD removal level warning, and recovery guidance.
3. User confirms; Able runs the operation and reads back the package state.
4. On verified success, Able adds the package to the Able archive with prior state, action, and time.
5. Later, user opens Apps → Archive, selects a package, and chooses Restore.
6. Able runs `cmd package install-existing` (system app uninstalled for user), installs the backed-up APK(s) through a Shizuku install session (user app, see 3a), or runs `pm enable` (disabled), or requests unarchive for a natively archived app, then reads back the result.

**3a. User (non-system) app uninstall (revised 2026-11-22, ADR-033):** before uninstalling, Able shows the APK backup size, copies the APK(s) to private storage, and verifies the copy; only then runs the uninstall. Android deletes a user app's APK on `pm uninstall -k --user 0` (observed 2026-11-22 on the Android 16 / API 36.1 emulator), so `install-existing` is not used for user apps.

**Alternatives:** Shizuku missing → privileged actions are unavailable with a reason; user-mediated Android uninstall remains available for downloaded apps. Operation rejected → package stays unchanged and is not archived. Restore fails (APK no longer on the system partition, installer unavailable for unarchive) → report failure and give recovery guidance. APK backup fails (no space, unreadable file) → uninstall is blocked until the user explicitly confirms that Able cannot restore it. No or corrupt backup at restore → Able offers reinstall from the original store (data kept where Android retained it) and claims no success. Restored app shows a different installer → Able reports it. Package is protected or core → no automatic action; warning shown.  
**Postconditions:** Every Able-applied removal or disable is listed with a restore route where Android permits; no package action runs from a profile or schedule.

## UC-33 — Browse the package knowledge base

**Primary actor:** End user  
**Preconditions:** The bundled UAD-ng snapshot is available in the Apps section.

1. User opens Apps and searches by name or package.
2. User filters by state (enabled, disabled, uninstalled for user, archived), user/system, UAD list (Oem, Aosp, Google, Carrier, Misc), and removal level (Recommended, Advanced, Expert, Unsafe).
3. User opens a package detail with the UAD description, dependencies and needed-by packages, and links.
4. User optionally continues to an app action (UC-10, UC-32).

**Alternatives:** Package not in the snapshot → show "no information" (never "safe"). Snapshot asset missing or corrupt → hide knowledge-base fields with an explanation; the app list still works.  
**Postconditions:** The knowledge base is read-only and offline; it never triggers an action.

## UC-34 — Take or replay the guided tour

**Primary actor:** End user  
**Preconditions:** First-run onboarding has completed, or the user opens Settings → Help.

1. Able offers the guided tour with Start and Skip.
2. User starts the tour; Able highlights each section (Home, Batteria, App, Strumenti, Settings) and its main options in short steps.
3. User moves next/back or exits at any step.
4. Able records that the tour was completed or skipped.
5. User can replay the tour later from Settings → Help.

**Alternatives:** User skips → Able does not offer it again automatically. A highlighted feature is unavailable on the device → the step explains it is unavailable instead of pointing at a missing control. TV → steps are operable with D-pad/Select/Back.  
**Postconditions:** The tour changes no settings and requests no permission.

## UC-35 — Get contextual help for an advanced option

**Primary actor:** End user  
**Preconditions:** User views an option marked advanced or risky.

1. User selects the "?" info button next to the option.
2. Able opens a dialog explaining what the option does, its risks, and how to undo it.
3. User closes the dialog and returns to the same screen state.

**Alternatives:** Help text missing for a locale → fall back to the default language and log a development defect.  
**Postconditions:** Opening help never changes state or starts an action.

## UC-36 — Adjust haptics and experience motion

**Primary actor:** End user; Android OS  
**Preconditions:** Able is open.

1. User taps any interactive element, confirms, toggles, long-presses, or uses a slider; Able gives the pattern of that kind from the haptics map (tick, click, toggle on/off, confirm, reject/destructive, long press; slider: tick per step and confirm on release), composed from vibration primitives where supported (revised 2026-11-23, ADR-043).
2. User opens Settings → Appearance → Haptic feedback and chooses Off, Light, Normal (default), or Strong; Able plays a preview.
3. User switches primary destination; content slides horizontally in the direction of the tab order with a very light spring overshoot.
4. User opens a card or menu, including a small tool card; it expands into the full-screen destination (container transform) without jumps. Back or predictive back shrinks it into the originating card's current bounds (revised 2026-11-22: glitches on small cards are a defect).
5. User navigates back with the predictive back gesture; Able previews the destination on supported Android versions.

**Alternatives:** System touch feedback disabled or no vibrator → no haptics, no error (no vibrator → intensity control hidden or unavailable). Primitives unsupported → fallback constants; Settings notes that intensity may not change. Destructive confirmation → reject pattern, never confirm."Remove animations" enabled → transitions are instant or a plain fade, without slide, overshoot, or container transform. Android version without predictive back → standard back. Originating card scrolled out of view or removed → return with a fade. Predictive back cancelled → full screen restored without a jump. Dirty form → the discard/keep prompt still appears before leaving.  
**Postconditions:** Haptics and motion preferences persist and never block an action.

## UC-37 — Recognize Able across system surfaces

**Primary actor:** End user; launcher; Quick Settings  
**Preconditions:** Able is installed.

1. User sees the Able adaptive launcher icon, or the monochrome icon when themed icons are enabled.
2. User opens Able and sees the Able logo in onboarding and About.
3. User adds Able tiles; each tile uses an icon from the curated Able icon set.

**Alternatives:** Launcher does not support themed icons → the standard adaptive "Pulse" icon is shown. Dynamic color unavailable → fallback palette (ink `#101418`, mint `#1FC98E` accent) keeps assets legible. Brand concept decided 2026-11-23 ("Pulse", BRAND-001); until the new assets ship, the previous assets remain and are reported as such in the backlog (TSK-1606).  
**Postconditions:** Brand assets are original Drago Labs work, consistent in light, dark, and dynamic color themes.

## UC-38 — Run a command in the command shell

**Primary actor:** End user; Shizuku service  
**Preconditions:** User opens Strumenti → Command shell. Related: CMD-001, ADR-029.

1. Able checks Shizuku. If connected and granted, the shell input is enabled.
2. User types a command or selects a saved snippet (which only fills the input).
3. User presses Run. If the command matches the dangerous list, Able shows a warning naming the risk and asks for confirmation.
4. Able runs the command as the Shizuku shell user and streams stdout/stderr live.
5. User may press Stop at any time; Able terminates the process.
6. Able shows the exit status (or stopped/failed) and records command, time, exit status, and duration in the action log and the command in local history.
7. User may copy or share the output, save the command as a snippet, or clear history.

**Alternatives:** Shizuku missing, stopped, or denied → the tool is unavailable with the reason and a setup route; nothing runs. User cancels the dangerous-command confirmation → nothing runs, nothing logged as run. Shizuku disconnects during a run → status "failed/unknown", never "succeeded". Output very large → Able keeps the scrollback bounded and says that earlier output was truncated. No share target → offer copy.  
**Postconditions:** No command runs without the user pressing Run; output is not stored in the action log; history is not included in any backup.

## UC-39 — Turn the screen off with a double tap on empty home-screen space

**Primary actor:** End user; launcher; Android OS  
**Preconditions:** Able Accessibility service enabled (UC-07). Related: LOCK-003, LOCK-001.

1. User opens Screen lock → Home screen double tap.
2. Able checks whether a non-consuming touch-observation route is supported on this device/Android version and offers the modes: Light (timing-only watcher, may miss taps) and Precise (launcher-only overlay with touch replay, UC-47).
3. If supported, user enables it and runs the test: a double tap on empty space turns the screen off; a double tap on an icon or widget does not.
4. With the feature active, the user double-taps empty home-screen space; Able turns the screen off through the supported lock route, while every touch still reaches the launcher.

**Alternatives:** Non-consuming route unsupported → Able says so and offers the transparent widget: user places and resizes it over empty space; a double tap on the widget turns the screen off, single taps do nothing. Tap lands on an icon, widget, folder, dock, or search bar → ignored. Launcher not in the foreground → no detection. Accessibility service disabled or blocked (Advanced Protection) → feature inactive with a route to re-enable or an explanation. Launcher update breaks detection → the self-test fails and Able marks the context unsupported.  
**Postconditions:** In Light mode and the widget fallback Able never consumes launcher touches; in Precise mode (UC-47) touches are intercepted only on the launcher home and replayed; the screen turns off only after a user double tap.

## UC-40 — Protect an app with app lock

**Primary actor:** End user; Android OS (BiometricPrompt, Accessibility)  
**Preconditions:** User opens Strumenti → App lock. Related: APPLOCK-001, ADR-030.

1. Able checks that a device screen lock exists and that `BIOMETRIC_STRONG` or `DEVICE_CREDENTIAL` can authenticate.
2. Able explains how app lock works and its limits (not OS-level enforcement, Recents previews, Accessibility dependency) and the Accessibility disclosure.
3. User enables the Able Accessibility service in Android Settings and returns.
4. User selects apps to protect and a grace period (Immediately, 1, 5, 15 minutes).
5. Later, the user opens a protected app; Able starts its lock activity, which shows the system authentication prompt.
6. On success, the user returns to the app; the grace period starts.
7. When the screen turns off, all protected apps re-lock.

**Alternatives:** No screen lock → feature cannot be enabled; Able offers Android security settings. User cancels, presses back, or authentication errors/locks out → Able sends the user to Home. Accessibility disabled, revoked, or blocked by Advanced Protection → app lock shown unavailable/inactive, Home safety warning while apps are configured as protected. Protected app uninstalled → removed from the list with a note. Protected app opened in split-screen or picture-in-picture → behavior reported per T-APPLOCK-02 results; not promised.  
**Postconditions:** No overlay window is drawn; unlock state is held only until the grace period ends or the screen turns off.

## UC-41 — Map a hardware-key gesture to an action

**Primary actor:** End user; Android OS  
**Preconditions:** User opens Strumenti → Button shortcuts. Related: KEY-001, ADR-031.

1. Able explains the Accessibility key filtering, the screen-on limit, and any single-press delay on mapped keys.
2. User enables the Able Accessibility service if needed.
3. User picks a gesture: volume up + down together, double press volume up/down, or long press volume up/down.
4. User picks a target: an Action Catalog entry (torch, NFC, level, compass, screen lock, ringer mode…), camera launch, an app, or a launchable exported activity.
5. User runs the test; Able confirms the gesture was recognized and the target ran.
6. With the screen on, the user performs the gesture; Able consumes it and runs the target.

**Alternatives:** User looks for the power key → shown as unsupported with the reason. Screen off → no action promised. Target uninstalled or not launchable → failure reported and mapping marked unresolved. Target needs a permission (camera, NFC route) → requested in context when the mapping is saved. Destructive target → not selectable. Accessibility disabled or blocked → shortcuts shown inactive.  
**Postconditions:** Only gestures with an enabled mapping are consumed.

## UC-42 — Find an option with the app-wide search

**Primary actor:** End user  
**Preconditions:** Any primary destination is open. Related: SEARCH-001, ADR-032.

1. User selects the search entry point: the circular Search button next to the floating navigation bar on compact windows, or the search action in the top app bar on wider windows and TV (revised 2026-11-23).
2. Able shows recent searches (if any).
3. User types a query in Italian or English.
4. Able lists matching tools, battery options, apps features, settings, tiles & widgets, and Home customization entries, grouped by area.
5. User selects a result; Able opens the destination and highlights the option where possible, and saves the query to recent searches.

**Alternatives:** No match → empty state with suggestions. Result unavailable on the device → shown with its unavailable reason. User clears recent searches → list emptied. TV → search field and results are D-pad operable.  
**Postconditions:** Searching never changes a setting or starts an action; no network request is made.

## UC-43 — Create, edit, or reset a battery profile

**Primary actor:** End user  
**Preconditions:** User opens Battery → Profiles. Related: BAT-001, BAT-003, ADR-005 (revised 2026-11-22).

1. User selects Create profile, or opens an existing profile and selects Edit; a custom profile can also be duplicated.
2. User enters a unique name (built-in names are localized unless renamed).
3. User adds triggers and enables those to use: battery below (activation and reset thresholds, only while unplugged), schedule (start/end time, days of the week), screen off for N minutes, charging, manual. All start disabled.
4. User sets per-app actions (Osserva, Limita in background, Arresta quando esco) and exclusions; protected apps are shown as excluded.
5. Able previews affected apps, notification/alarm/sync impact, and apps where another profile already sets a more restrictive action.
6. User saves; Able stores the profile and shows whether it can activate automatically and its next trigger.
7. For a built-in profile, the user may choose Reset to default, which restores its default triggers (disabled) and actions after confirmation.
8. For a custom profile, the user may choose Delete; Able confirms, deactivates it if active, re-evaluates effective actions, and removes it.

**Alternatives:** Reset threshold not above activation threshold → save blocked with the reason. Schedule with no day selected → that trigger cannot be enabled. Duplicate name → rejected. Delete on a built-in → not offered. User cancels → nothing changes. Shizuku unavailable → only standard actions are selectable. No trigger enabled → saved but labelled "will not activate automatically".  
**Postconditions:** Built-ins always exist; only reviewed actions can run automatically; freeze/disable is never selectable as a profile action.

## UC-44 — Restrict or allow apps' background execution

**Primary actor:** End user; Shizuku service; Android AppOps  
**Preconditions:** User opens App → Background permissions. Related: APP-007, ADR-036.

1. Able checks Shizuku; if connected, it lists apps with the actual `RUN_ANY_IN_BACKGROUND` / `RUN_IN_BACKGROUND` mode and standby bucket.
2. User filters (user/system, restricted/allowed) and selects one app or several (select all user apps); protected and system/core apps are not included in bulk selection.
3. User chooses Restrict or Allow and optionally a standby bucket.
4. Able shows the affected apps and the risks (late notifications, missed alarms, no sync, stopped background playback); a system package shows its UAD removal level and needs a separate confirmation.
5. User confirms; Able applies the change per app, reads it back, and shows a per-app result.
6. Able logs each change with prior and new values; the user can restore the prior values of the batch from the log.

**Alternatives:** Shizuku missing, stopped, or denied → no change possible; Able explains and opens Android's per-app battery settings. A change is rejected or reverted by the OEM → shown failed with the actual value. Bucket setting unsupported → bucket option hidden. App uninstalled during the batch → skipped and reported. User cancels → nothing changes.  
**Postconditions:** Only confirmed changes are applied; actual state is displayed; nothing runs from a schedule or profile through this tool.

## UC-45 — Inspect RAM and stop processes

**Primary actor:** End user; Shizuku service; Android ActivityManager  
**Preconditions:** User opens Strumenti → RAM e processi (next to Storage). Related: MEM-001, BAT-007, ADR-037.

1. Able shows the RAM summary (total, available, used, low-memory threshold) with a text equivalent and a note that Android manages memory and free RAM is not a goal.
2. If Shizuku is connected, Able lists running apps and processes with memory and its source.
3. User selects an app and chooses Force-stop or Kill background processes.
4. Able shows a confirmation naming the consequences (notifications, alarms, sync may stop; the app may restart and use more battery).
5. User confirms; Able runs the action, re-lists processes to verify, and logs the result.
6. User may edit the block list (add/remove apps) and later choose Stop block list, confirm, and see a per-app result.
7. (Revised 2026-11-23) App rows show the app icon and a vertically centered selection checkbox; multi-select uses the same contextual bar, confirmation, and per-app result layout as Storage's per-app cache cleanup (UC-31).

**Alternatives:** Shizuku unavailable → summary only, process list unavailable with the reason. App protected, launcher, keyboard, Able, or core system process → cannot be added to the block list or stopped in bulk; a single core process shows no stop action. Process restarts immediately → reported "restarted" rather than success. Command rejected → failure reported.  
**Postconditions:** Nothing runs automatically; no battery-saving or "boost" claim is made; memory values shown are measurements.

## UC-46 — Turn the hotspot on or off and edit its configuration

**Primary actor:** End user; Shizuku service; Android tethering/Wi-Fi services  
**Preconditions:** User opens Strumenti → Hotspot. Related: HOT-001, HOT-002, ADR-038.

1. Able checks Shizuku and which SoftAP options the device and Android build expose.
2. Able shows the read-back state (off/starting/on/stopping/failed) and the configuration: SSID, password (hidden by default), security, band, hidden SSID, maximum clients, auto-off.
3. User toggles the hotspot; Able starts/stops tethering and shows the resulting state.
4. User edits configuration fields; Able validates them (SSID length, password rules for the security type) and applies them.
5. Able reads back every field and marks any rejected or unsupported field with the actual value and reason.
6. With the hotspot on, Able lists connected clients; on request it shows a join QR code.

**Alternatives:** Shizuku missing → readable state only; Able opens Android tethering settings for changes and claims no direct control. Hidden API missing on this build → "not supported on this Android build", no crash. Tethering blocked by carrier/OEM policy → failure with reason. Validation fails → nothing applied. Clients not exposed → "unavailable".  
**Postconditions:** The shown state equals the read-back state; the password is never logged, sent in crash reports, or included in the standard backup.

## UC-47 — Use the Precise home double-tap mode

**Primary actor:** End user; launcher; Android OS  
**Preconditions:** Able Accessibility service enabled with gesture dispatch capability; user opens Screen lock → Home screen double tap. Related: LOCK-003, ADR-039.

1. Able explains Light and Precise modes and the Precise trade-offs (delayed single taps, possible replay imperfections, launcher dependence).
2. User selects Precise and runs the self-test: double tap empty space, tap an icon, long press, swipe pages.
3. Able reports each result and the measured replay delay; the user keeps or reverts the mode.
4. With Precise active, when the launcher home is in the foreground, Able shows the overlay outside system gesture areas.
5. A double tap on empty space turns the screen off; any other touch is replayed to the launcher.
6. When the launcher loses the foreground, Able removes the overlay immediately.
7. (Revised 2026-11-23, ADR-039 revised) After a swipe is recognized, the first swipe is replayed with its recorded path and timing, and the overlay becomes touch-transparent for a short browsing window so further page swipes go directly to the launcher; the overlay never covers the dock/hotseat row, the search bar band, or system gesture insets.

**Alternatives:** Double tap during the pass-through window or in an excluded area → not detected (disclosed in setup and help). Pass-through window too short or too long on a launcher → reported by the self-test (first-swipe delay, page-change success rate) and tuned per device. Self-test fails(replay not delivered, launcher ignores dispatched gestures) → Able recommends Light mode or the widget fallback and does not enable Precise silently. Multi-touch gesture → disclosed as not reliably replayed. Accessibility disabled/blocked → inactive with reason. Launcher updated → self-test recommended; failures recorded in the log.  
**Postconditions:** The overlay never covers another app, the lock screen, or system gesture areas.

## UC-48 — Return to a section's start from its navigation item

**Primary actor:** End user  
**Preconditions:** User is inside a section (Home, Batteria, App, Strumenti), possibly in a nested screen, a detail pane, a lower scroll position, or a non-first tab. Related: SHELL-001, ADR-040.

1. User selects the navigation item of the section already shown (bottom bar, rail, or TV drawer).
2. Able pops nested screens to the section root, closes detail panes, scrolls to the top, and selects the first tab.
3. If the section is already at its start, Able only scrolls to the top.

**Alternatives:** A form on the stack is dirty → discard/keep prompt first; Keep cancels the return. "Remove animations" on → instant transition.  
**Postconditions:** Other sections keep their own back stacks.

## UC-49 — Simulate a location with Fake GPS

**Primary actor:** End user; Android location system; Shizuku service (optional); OpenStreetMap tile server  
**Preconditions:** User opens Strumenti → Fake GPS. Related: GPS-003, ADR-045 (added 2026-11-23).

1. Able checks whether it is the selected mock-location app. If not, it explains why this is needed and offers Developer options or, with Shizuku connected, a one-tap selection (`android:mock_location` allowed through AppOps) that it reads back.
2. Able shows the OpenStreetMap map with the "© OpenStreetMap contributors" attribution and the always-visible note that apps can detect mock locations.
3. User places the fake position by tapping the map, searching a place or coordinates, or choosing a saved point; the user may save the position as a point.
4. User optionally sets jitter, altitude, and accuracy, and chooses a mode: fixed position, joystick (with speed), or route (draws two or more points, picks a speed, optionally loop).
5. User selects Start; Able starts a foreground service with an ongoing notification and feeds the mock position (moving for joystick/route).
6. User pauses or resumes a route, or moves the joystick.
7. User selects Stop in the tool or the notification; Able removes the mock provider, stops the service, and confirms that real location is back.

**Alternatives:** Developer options disabled → Able explains how to enable them and cannot start. Shizuku AppOps change rejected → failure shown with the Developer options route. No network → no map tiles, with an explanation; coordinate entry and saved points still work. Tile server refuses or blocks requests → map shows an error and Able does not retry aggressively. Search finds nothing → empty state. Invalid coordinates → rejected with the valid ranges. Mock-location selection revoked or Developer options turned off while running → service stops and Able reports it. Notification permission denied → Able explains that the stop control stays available in the tool, and the foreground service still shows its required notification where Android allows. A target app refuses mock locations → outside Able's control; help explains detection.  
**Postconditions:** A mock position exists only while the service runs and the notification is shown; no automation can start it; only user-saved points and routes are stored.

## UC-50 — Move between Battery sub-sections

**Primary actor:** End user  
**Preconditions:** User opens Batteria. Related: BAT-UI-001, ADR-048 (added 2026-11-23).

1. Able shows the tab row Stato · Monitor · Salute · Diagnosi with Stato selected.
2. User swipes horizontally or taps a tab; Able shows the adjacent or selected sub-section and moves the tab indicator.
3. In Monitor, the user reads live status first, then statistics, sessions, and settings.
4. In Salute, the user reads the health summary first, then capacity, lifecycle, charging, and advanced data.

**Alternatives:** Swipe starts on a chart or chip row → that content scrolls instead of the pager. Swipe starts in a system back-gesture edge → system back, not a sub-section change. "Remove animations" on → instant change. TV → tabs focused with D-pad; no swipe needed. A value unavailable on the device → shown as unavailable with its source, without reordering the groups.  
**Postconditions:** Re-selecting Batteria returns to Stato; no data or setting changes by switching sub-sections.

## UC-51 — Let Able hold secure-settings access through Shizuku

**Primary actor:** End user; Shizuku service; Android PackageManager  
**Preconditions:** User opens setup for NFC, USB/Wireless debugging, or Private DNS, or the access checklist; Shizuku is connected. Related: SYS-001, SYS-003, SYS-004, ADR-044 (added 2026-11-23).

1. Able explains that `WRITE_SECURE_SETTINGS` lets it change secure system settings, which features use it, that it stays granted across updates until revoked, and how to revoke it.
2. User confirms; Able runs `pm grant` for its own package as the Shizuku shell user.
3. Able verifies the grant with `checkSelfPermission` and shows "Granted" with a Revoke action.
4. NFC, debugging, and Private DNS toggles now change state in-process and read the result back.
5. Later the user selects Revoke; Able runs `pm revoke` through Shizuku (or shows the ADB command when Shizuku is unavailable) and verifies the revoke.

**Alternatives:** User cancels → nothing changes; the features keep their settings-page fallback. `pm grant` fails or the readback shows no grant → failure with the output reason, no success claim. Shizuku unavailable at revoke time → Able shows the ADB revoke command and the uninstall option. Grant removed externally → features fall back and the checklist updates on resume.  
**Postconditions:** The grant is never exported; every secure-settings write is limited to the features the user operates and is read back.

## UC-52 — Browse the Tools hub

**Primary actor:** End user  
**Preconditions:** User opens Strumenti. Related: TOOLS-001, ADR-048 (added 2026-11-23).

1. Able shows every tool as a tile of the same size with icon, label, and capability state.
2. User scrolls the grid and opens a tool; the tile expands into the tool (UX-004).

**Alternatives:** Long label → wraps to two lines or ellipsizes; TalkBack reads the full name. Tool unavailable → same tile size with an unavailable badge and reason. Large font scale → all tiles grow together. TV → D-pad moves across the grid predictably.  
**Postconditions:** Tile size never depends on label, badge, or availability.
