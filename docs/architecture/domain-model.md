# Able conceptual domain model

This is a product-level model, not a committed database schema. Field types, persistence library, migrations, and encryption details remain architecture decisions.

## Core entities

| Entity | Identity and key data | Invariants |
| --- | --- | --- |
| `Profile` | Stable ID, name, built-in key (for the five editable defaults) or custom, trigger set, per-app rules, active state with activating trigger | Several profiles may be active at once (revised 2026-11-22, ADR-005); built-ins cannot be deleted and can be reset; `Normale` is the no-restriction baseline shown when no other profile is active. |
| `Trigger` | Type (`battery_below` with activate/reset thresholds and only-unplugged flag, `schedule` with times and days, `screen_off_duration` with N minutes, `charging`, `manual`), parameters, enabled flag | Triggers are evaluated from system-observed state; no fake precision from polling gaps. Every trigger's enabled flag defaults to false (ADR-005). A profile's enabled triggers combine with OR semantics; reset threshold must exceed activation threshold. |
| `AppRule` | Package identifier, profile ID, action level, delay if applicable, exclusion/protection flags | Freeze/disable/uninstall are never executable as automatic profile actions. Missing packages become unresolved. |
| `EffectiveAppAction` | Package, effective level, source profile IDs, applied state | Derived: most restrictive level across active profiles (Osserva < Limita < Arresta); protected packages have none; recomputed when the active set changes. |
| `BackgroundOpsChange` | Package, op (`RUN_ANY_IN_BACKGROUND`, `RUN_IN_BACKGROUND`), prior/new mode, prior/new standby bucket, batch ID, result | Manual only (APP-007); stored in the action log for batch restore; actual value read back. |
| `ProcessBlockList` | Ordered package set | Excludes protected apps, launcher, keyboard, Able, and core system processes (MEM-001); used only by a confirmed manual action. |
| `HotspotConfig` (operational) | SSID, security, band, hidden, max clients, auto-off, password (secret) | Read from and written to the system through Shizuku (HOT-001); password is a secret and never exported or logged. |
| `CapabilityState` | Feature ID, supported/unsupported/permission-needed/service-disconnected state, reason, last checked | UI action availability derives from current capability, not a fixed device-type guess. |
| `ActionExecution` | Action ID, trigger/profile, package/target, previous state if known, desired state, result, timestamp, error/recovery metadata | Results distinguish applied, skipped, failed, partial, reverted, and unknown. Repeating an event does not duplicate harmful changes. |
| `BatterySession` | Session type (screen-on, screen-off, charging), start/end, summary metrics, optional user-enabled context, completeness, sampling provenance, expiry | Incomplete sessions are marked partial; summaries/context follow the user's 30-day/90-day-default/manual retention choice; usage observations are separate from user configuration. |
| `ChargingSession` | Session ID, start/end time, start/end battery %, min/max/avg temperature, peak wattage, thermal alert flag | Recorded during charging; flags unusually warm sessions without interfering with charging behavior. |
| `BatteryEventNote` | Note ID, session ID, timestamp, note text, tags (e.g., travel, gaming, hotspot, navigation) | User-authored context stored locally; follows session retention and is excluded from standard backup unless selected. |
| `BatteryHealthSnapshot` | Timestamp, cycle count, state of health %, design capacity mAh, full charge capacity mAh, manufacture date, first usage date, charging policy, source (`public_api` or `shizuku_system`) | Values missing or unexposed by OEM/kernel are recorded as unavailable; never simulated. |
| `ShortcutDefinition` | Stable ID, target type/data, label, icon reference, collection ID, favorite/history metadata, pin outcome | A saved definition is not the same as a launcher-pinned shortcut. Targets are revalidated on use/import. |
| `ShortcutCollection` | Stable ID, name, order, contained shortcut IDs | Deleting a collection prompts the user to either move contained shortcuts to Unsorted or delete them; definitions and custom assets are never deleted silently. |
| `HomeCardConfig` | Card ID, order index, visible boolean, custom parameters | All default cards can be reordered or hidden; critical failure state is rendered via a safety banner regardless of card visibility. |
| `GpsPoint` | Point ID, latitude, longitude, altitude, accuracy, timestamp, label, tags, notes | Strictly user-initiated capture; stored locally in Room; exportable in `.ablebackup` or shared via standard geo intents. |
| `DnsProfile` | Profile ID, name, mode (`off`, `opportunistic`, `hostname`), provider hostname | Local user configurations; applied via `WRITE_SECURE_SETTINGS` / Shizuku or fallback to Android Private DNS settings. |
| `CustomTileMapping` | Slot ID (1..N), assigned `AbleAction` ID, custom label, custom icon | Mapped dynamically to declared Android `TileService` slots. |
| `CustomWidgetConfig` | Widget instance ID, layout dimensions, slot-to-action bindings, visual style | Multi-action home-screen widget configuration powered by Glance/Compose; persists across restarts and export. |
| `ShellHistoryEntry` | Entry ID, command text, start time, duration, exit status (`exited(n)`, `stopped`, `failed`, `unknown`) | Local only; excluded from Auto Backup and `.ablebackup`; output is never persisted with it (CMD-001). |
| `ShellSnippet` | Snippet ID, name, command text, order | Selecting fills the input only; never executes automatically. Export section is open (ADR-034). |
| `AppLockConfig` | Protected package set, grace period (`immediate`, `1m`, `5m`, `15m`) | Enabled only when a device credential exists; unlock state is in memory and cleared on screen off (APPLOCK-001). |
| `KeyShortcutMapping` | Gesture (`volume_both`, `double_up`, `double_down`, `long_up`, `long_down`), target (`AbleAction` ID, camera, package, activity component), enabled flag | Destructive actions and shell commands are not valid targets; unresolved targets are marked, not dropped silently (KEY-001). |
| `ApkBackup` | Package, version code, file list (base + splits) with sizes and SHA-256, created time, installer of record | Stored in no-backup private storage; never exported; deleted with its archive entry or by the user (ADR-033). |
| `SearchEntry` / `RecentSearch` | Bundled entry ID, IT/EN titles and keywords, destination, capability gate / query text and time | Index is bundled and read-only; recent searches are local and excluded from export (SEARCH-001). |
| `BackupManifest` | Archive/schema version, Able version, creation time, selected sections, checksums | Untrusted input; no executable content; unsupported versions fail before mutation. New exports use schema 2; schema 1 and 2 are importable (ADR-046). |
| `FakeLocationPoint` / `FakeRoute` | Point: ID, name, latitude, longitude, optional altitude/accuracy. Route: ID, name, ordered points, speed, loop flag | User configuration for Fake GPS (GPS-003); exportable with the GPS section; no history of simulated positions is kept. |
| `MockLocationSession` | Mode (fixed, joystick, route), current position, jitter/altitude/accuracy settings, started time | Runtime state of the foreground service only; not persisted beyond process lifetime; removed on Stop. |

## Data boundaries

- **User configuration:** profiles (built-in and custom) and their triggers, app rules, RAM block list, protected list, Home layout, theme/language override, shortcut definitions/collections, selected custom icons, saved GPS points/tags, custom DNS profiles, custom tile mappings, and custom widget layouts. Exportable by section.
- **Operational state:** permissions, Shizuku connection, currently active profile, actual OS app/hotspot state. Device state is re-detected and never restored as if it were portable configuration.
- **Observations:** battery session summaries, usage data, action history. Local by default; battery summaries expire after 90 days unless changed to 30 days or manual retention; excluded from standard configuration export.
- **Secrets:** hotspot credentials and any future tokens. Never included in standard backup; cloud sync is not part of v1.

## State reconciliation

Before applying a profile action, compare desired configuration with observable Android state. Apply only the delta; read back the result where supported; persist the execution outcome. If the old state cannot be read or Android changes state independently, use `unknown`/`conflict` and ask before attempting guessed restoration.

## Backup migration

Internal persistence schema and portable archive schema are separate versioned contracts. Import performs parse → validate → map → preview → conflict resolution → rollback snapshot → apply → result report. Any failure before apply makes no changes. Apply failure must either roll back the transaction or report precisely which supported sections were applied and offer recovery.
