# Able adaptive UI and design specification

**Status:** Product direction accepted; component-level mockups and design tokens remain to be produced.

## Design system

- Built 100% with native Kotlin and Jetpack Compose, using `androidx.compose.material3.adaptive` (WindowSizeClass) for phones/tablets and Compose for TV (`androidx.tv:tv-material`, `androidx.tv:tv-foundation`) for Android TV / Google TV.
- Use Material 3 (Expressive) patterns and Android system dynamic colors as the primary palette, with a more distinctive expressive Able style (ADR-027). The open-source "Essentials" app by sameerasw is visual inspiration only; no branding or assets are copied.
- Brand assets (BRAND-001): Able logo, adaptive and monochrome (themed) launcher icon, and a curated Quick Settings tile icon set, legible in light, dark, and dynamic color themes. Custom tiles pick icons from this set. **Brand concept "Pulse" (decided 2026-11-23, ADR-027 revised):** a lowercase "a" built from a ring and a stem; the stem doubles as a level indicator with a mint segment. Tokens: ink `#101418`, mint `#1FC98E`; wordmark Google Sans Flex, weight 650, ROND 0. Style: calm, precise, flat (no gradients or gloss), generous spacing. The monochrome icon is ring and stem only; tile icons share the ring/stem stroke weight. In the app UI dynamic color stays the primary palette (ADR-004); ink and mint are brand tokens for the logo, launcher icon, splash, and the fallback palette's accent.
- Theme selector: System (default), Light, Dark.
- The UI must remain coherent when dynamic color is unavailable; use a Material 3 fallback palette with semantic colors for error, warning, success, and disabled states.
- Use Italian and English resource strings from v1. Never place user-facing copy only in code or images.
- Provide visible loading, empty, permission-denied, unsupported, partial, stale, error, and success states.

## Navigation model

Primary destinations are Home, Battery, Apps, and Tools. Settings and Help are secondary destinations reachable from the top app bar or a TV-appropriate menu. The options search (SEARCH-001) is reachable from every primary destination: from the circular Search button next to the floating navigation bar on compact windows, and from the top app bar on wider windows and TV. A phone uses a floating navigation bar in compact windows (revised 2026-11-23, ADR-042). A wide tablet window may use a navigation rail and list/detail panes. TV uses remote-first navigation and does not depend on touch navigation.

### Floating navigation bar (SHELL-001, revised 2026-11-23, ADR-042)

- **Shape and position:** a pill-shaped bar floating above the bottom edge and the system navigation inset, with side and bottom margins; it holds Home, Batteria, App, Strumenti with icon and label; the selected item uses the Material 3 Expressive active indicator.
- **Search button:** a separate small circular button to the right of the pill, same height family, search icon, TalkBack label "Cerca opzioni" / "Search options"; it opens the options search on every primary destination.
- **Blur:** a light blur of the content behind the bar and the button (Haze, Apache-2.0) with a tonal tint; contrast of labels and icons meets WCAG AA over any content. Where blur is unavailable, too costly, or turned off, an opaque tonal surface with the same contrast is used.
- **Content padding:** every scrollable screen adds bottom padding equal to the bar height, its margin, and the navigation inset, so the last row and primary actions scroll fully above the bar; snackbars and FABs sit above the bar; dialogs and bottom sheets are drawn above it.
- **Keyboard:** while the IME is shown the bar is hidden or lifted so it never covers a focused field.
- **Other windows:** medium/expanded windows keep the navigation rail; TV keeps the drawer; neither shows the floating bar.

Back behavior:

1. Dismiss an open dialog/sheet first.
2. Return from a detail screen to its parent list while preserving search/filter state.
3. If a form is dirty, ask whether to discard or keep editing.
4. At a top-level destination, return to the TV launcher or Android home according to platform behavior.

### Navigation re-select (SHELL-001, revised 2026-11-22, ADR-040)

- Selecting the navigation item of the section already shown (floating bar, navigation rail, TV drawer) returns to that section's start: pop nested screens to the root, close detail panes, scroll the root list to the top, and select the first tab. If already at the start, only scroll to the top (animated; instant when "Remove animations" is on).
- A dirty form on the stack shows the discard/keep prompt first; Keep cancels the return.
- Selecting a different section keeps each section's own back stack and scroll position.
- TalkBack announces the section title after the return; on TV, focus moves to the first focusable element of the section root.

### Options search entry point (SEARCH-001)

- **Placement (revised 2026-11-23):** on compact windows, the circular Search button to the right of the floating navigation bar (at least 48dp touch target, TalkBack label "Cerca opzioni" / "Search options"), visible on Home, Batteria, App, and Strumenti; on wider windows, a search icon button in the top app bar next to Settings and Help. On TV it is the first item of the top/menu row and reachable with D-pad.
- **Surface:** a full-screen search view on compact windows; a docked search bar with results pane on expanded windows. Recent searches show before typing, with a clear-all action.
- **Results:** grouped by area (Tools, Battery, Apps, Settings, Tiles & widgets, Home) with title, one-line path (for example "Strumenti › Torcia"), and an unavailable badge plus reason where the feature is not supported. Matching ignores case and accents and uses Italian and English keywords regardless of the UI language.
- **Selecting a result** opens the destination with the target option briefly highlighted (container color pulse, no motion when "Remove animations" is on) and focused for TalkBack/D-pad.
- **Empty state:** "Nessun risultato" / "No results" with a hint to try another word.

### Predictive back and motion (UX-004, revised 2026-11-22)

- Support Android's predictive back gesture: show the back preview on supported Android versions and fall back to standard back elsewhere. Rules 1–3 above still apply; a dirty form intercepts back before the preview completes.
- **Primary-destination transitions:** content slides horizontally in the direction of the tab order (Home → Batteria → App → Strumenti, left to right). Moving to a tab to the right slides the new content in from the right and the old content out to the left; moving left does the opposite; a multi-tab jump uses the same direction once, not one slide per tab. The spring has a very light overshoot (barely perceptible, no visible oscillation). The navigation rail on wide windows follows the same order (top to bottom maps to left to right). TV uses a short fade-through instead of horizontal travel.
- **Container transform for cards and menus:** opening a Home card, Tools tile, or menu entry expands the card's container into the full-screen destination, like launching an app from the launcher. Back and predictive back reverse it: during the gesture the screen shrinks toward the card, and on commit it settles into the card's current bounds (recomputed after scroll/rotation; fall back to a fade if the card is no longer visible).
- Use the Material 3 Expressive motion scheme for other transitions. Never rely on motion to convey state.
- When the system "Remove animations" accessibility setting is on, transitions are instant or a plain short fade: no horizontal slide, overshoot, or container transform.
- Proposed parameters (ADR-034, not accepted): slide spring damping ratio ≈ 0.85 with medium-low stiffness; container transform via Compose shared bounds.
- **Small-card defect (recorded 2026-11-22):** the container transform currently glitches on small tool cards (jumps, wrong return position). Fix criteria: no position or size jump at start or end; no flash or overlapping content; back and predictive back shrink into the originating card's current bounds (after scroll or rotation too); a cancelled predictive back restores the full screen without a jump; when the origin card is not visible, return with a fade. Verified by T-MOTION-03; fix proposals in ADR-041.

## Form-factor behavior

| Context | Layout | Input/interaction requirements |
| --- | --- | --- |
| Compact phone | One-column content; floating navigation bar with separate Search button and content padded beneath it; primary actions reachable without long scrolling. | Touch targets, clear hierarchy, gestures have visible alternatives. |
| Tablet / expanded window | Two-pane list/detail for apps, activities, profiles, and storage where useful; avoid simply enlarging phone controls. | Touch, keyboard, mouse, and resizing should preserve selection and draft state. |
| Android TV / Google TV | Large-screen home and tool summaries; reduced density; remote-first content. | Every visible primary action is reachable by D-pad, activated by Select, exited with Back; strong focus indication and no touch-only controls. |
| Unsupported capability | Omit from the default action surface or show an explicit disabled state and reason. | Do not show a control that looks actionable if no supported route exists. |

Layout decisions are based on the current app window size and input/capability availability, not a hard-coded “phone/tablet” label. Rotation, split-screen, and window resizing must not discard unsaved input. TV listing/distribution also requires a TV launcher entry and passing current Play TV quality review; it is a release target, not an assumed approval.

## Home hierarchy and modular cards

The Home screen uses a fully modular card-based layout. Every functional summary is encapsulated in an independent Material 3 card:

1. **Battery & active profile card:** battery percentage, charging indicator, active profile name, and quick profile switcher.
2. **Quick actions card:** user-configurable quick toggle buttons (torch, screen lock, hotspot, manual profile pause).
3. **Capability & permission status card:** Shizuku, Accessibility, Usage access, and notification permission states.
4. **Recent actions and warnings card:** action log of applied/skipped/failed automations and warnings. No "Open Battery" button (revised 2026-11-23); an optional "Mostra tutto" / "Show all" text button opens the full history in a bottom sheet (side sheet on wide windows).
5. **Storage summary card:** quick glance at internal storage utilization with shortcut to cleanup.
6. **Favorite shortcuts card:** user-curated shortcuts and collections pinned to Home.

**Customization and safety:**

- Users can enter a customization mode (or use Settings → Home layout) to reorder cards, hide individual cards, or add them back.
- A **Reset to default** action restores the factory ordering and visibility.
- **Top safety banners (revised 2026-11-23, HOME-003, ADR-048):** if a critical condition occurs (for example Shizuku permission missing or service disconnected, a required permission revoked, automation paused by an error, app lock inactive while apps are protected), Able renders a high-priority safety banner at the top of the feed, ensuring critical alerts are never hidden by custom card ordering; it stays while the condition persists.
- **Bottom failed-actions notice:** individual failed or skipped actions are summarized in one notice after the last card ("2 azioni non riuscite" / "2 actions failed", with Details and Nascondi / Hide). Hide removes it until a newer failure is recorded; Details opens the action history filtered to failures.
- Card preferences persist across app restarts and export/import.

## Interaction and content patterns

- Use cards for summaries, rows for searchable lists, and detail screens for high-impact controls.
- Destructive/restrictive actions show affected apps/items, consequences, explicit confirmation, and recovery route.
- Show actual status separately from desired status when applying a change is pending, unsupported, or rejected.
- Use labels plus icons; color alone cannot indicate active profile, success, or failure.
- Allow cancellation of long scans/import validation; show progress without freezing the UI.
- Do not use alarmist battery copy or claim a fixed amount of savings.

### Battery sub-sections (BAT-UI-001, added 2026-11-23, ADR-048)

- **Structure:** a primary tab row Stato · Monitor · Salute · Diagnosi over a horizontal pager; swiping left/right moves to the adjacent sub-section and the tab indicator follows; tapping a tab animates to it (instant with "Remove animations"). Inner horizontal scrollers (charts, chip rows) keep their drags; pager swipes never start in the system back-gesture edges. TV: tabs are focusable with D-pad, no swipe required. Re-selecting Batteria returns to Stato (ADR-040).
- **Monitor** (ordered by user question): 1) *Live status* — "Il monitor è attivo?" card with running state, current level, charging state, current rate where available, Start/Stop; 2) *Statistics* — screen-on, screen-off, charging summaries and drain per hour for the current period, each labelled as an estimate; 3) *Sessions* — list with partial markers, notes, and tap-to-detail; 4) *Settings* — outputs (notification/widget), reset rules, alert threshold, units, retention, Delete history now.
- **Salute / Health:** 1) *Summary* — one named state with source or "unavailable"; 2) *Capacity* — design vs full-charge capacity and SoH; 3) *Lifecycle* — cycle count, manufacture and first-use dates; 4) *Charging* — charging policy and recent charging-diary highlights; 5) *Advanced* — expandable raw values and sources (Shizuku/system).

### Battery Status and Diagnosis redesign (BAT-001–BAT-003, revised 2026-11-22)

UX intent for the concurrent-profile model (ADR-005 revised); layouts remain to be mocked up:

- **Status (Stato):** top card with battery level and charging state; an **Active profiles** list (chips or rows, one per active profile) each showing the trigger that activated it (for example "Batteria < 25%", "22:00–07:00 lun–ven", "Schermo spento da 20 min", "Manuale") and an end action for manual activations; when none is active, "Normale" with "Nessun profilo attivo". Below, **Effective app actions**: each affected app with its effective level, a "da: Notte, Risparmio" / "from: Night, Savings" source label, and a marker when a more restrictive profile overrode another; protected apps listed separately as excluded. A "Next trigger" row shows the next scheduled or threshold event.
- **Profiles:** list of built-in (badge "Predefinito" / "Default") and custom profiles with enabled-trigger count and active state; Create profile action; built-ins offer Edit and Reset to default, custom profiles Edit, Duplicate, Delete. The editor groups Triggers (each with its own switch, all off by default, inline validation for thresholds and days) and App actions (per-app level selector with protected apps locked).
- **Diagnosis (Diagnosi):** separates measured data from interpretation: monitor sessions and drain estimates, battery health, charging diary, and the anomaly checklist as cards; each value shows its source and confidence; profile before/after review labels results as observed changes. No savings claims.
- Wide windows show Status and the selected profile or app side by side; TV uses the same sections as focusable rows.

### Guided tour (UX-001)

- Offer the tour once, after first-run onboarding, with **Start** and **Skip**. Skipping is remembered and the tour is not offered again automatically.
- Each step highlights one section (Home, Batteria, App, Strumenti, Settings) and its main options in one or two short sentences, with Next, Back, and Exit.
- The tour never changes settings or requests permissions. Steps for features unavailable on the device say so instead of pointing at a missing control.
- Replay from **Settings → Help**. On TV, steps are operable with D-pad, Select, and Back.

### Contextual help (UX-002)

- Advanced or risky options (Shizuku actions, disable/uninstall/archive, system toggles, cache trim, experimental tools) show a "?" info button next to the control.
- The button opens a dialog with three short parts: **What it does**, **Risks**, **How to undo**. Opening it changes no state.
- The button has a TalkBack label naming the option and is reachable by D-pad.

### Haptic feedback (UX-003, revised 2026-11-23, ADR-043)

Every tap on an interactive element gives haptic feedback. Haptics map (kind is accepted; primitives and constants are proposals, ADR-049; final choice after Pixel 8 Pro evaluation):

| Component / event | Kind | Primitive composition (where supported) | Fallback constant |
| --- | --- | --- | --- |
| List row tap, chip select/deselect, tab change, slider step crossed (one per step) | Tick | `PRIMITIVE_TICK` (light) | `CLOCK_TICK` / `SEGMENT_TICK` (API 34+) |
| Button press (filled, tonal, outlined, icon), navigation item, Search button | Click | `PRIMITIVE_CLICK` | `VIRTUAL_KEY` / `KEYBOARD_TAP` |
| Switch, checkbox, toggle on / off | Toggle | on: `QUICK_RISE` + `TICK`; off: `QUICK_FALL` | `TOGGLE_ON` / `TOGGLE_OFF` (API 34+), else `CONTEXT_CLICK` |
| Confirmed or completed action, dialog confirm, slider release, spirit level 0° (SENS-001) | Confirm | `CLICK` + `TICK` | `CONFIRM` |
| Failed or rejected action, confirmation of a destructive operation (delete, uninstall, force-stop, kill, cache clear, shell dangerous command) | Reject / destructive | `THUD` (API 31+) or double `CLICK` | `REJECT` |
| Long press, drag-to-reorder start | Long press | `SLOW_RISE` + `CLICK` | `LONG_PRESS` / `DRAG_START` |

- Each event fires once per interaction; continuous drags throttle ticks to the step rate; no haptic repeats while a finger rests.
- Primitives are used only when `Vibrator.arePrimitivesSupported` reports every primitive of the pattern; otherwise the fallback constant plays.
- **Settings → Appearance → Haptic feedback:** segmented control **Off / Leggera / Normale / Forte** (Off / Light / Normal / Strong), default Normal, with a short preview pulse on change. On devices without primitive support a note says intensity may not change. Respect the system touch-feedback setting; devices without a vibrator hide the control or mark it unavailable and show no error.

### Expandable rows and progressive disclosure (SET-002, NFR-ACC-002)

- Access checklist rows are compact: access name, state pill, and one action on a single line. An expand affordance reveals purpose and consequences; collapsing restores the compact row. Expansion state is announced to TalkBack.
- Apply the same progressive disclosure to other long explanations: keep primary text concise and put details behind an expandable row, "?" dialog, or detail screen.

### Readability and layout rules (NFR-ACC-002)

- Minimum touch target 48dp × 48dp, including icon buttons and the "?" button.
- Text and essential icon contrast meet WCAG AA in light, dark, and dynamic color themes.
- Collapsing (large/medium) top app bars must never overlap content: content scrolls beneath the collapsed bar with correct insets, including at large font scale and on TV.
- **Card headers (revised 2026-11-22):** the title takes the available width first. A status pill or "?" button sits on the title row only if the title fits on at most two lines beside it without breaking words; otherwise the pill moves to its own row under the title. A title is never forced into one-word lines. Long package names wrap or ellipsize with full text on expand/copy.
- **Human-formatted values (revised 2026-11-22):** never show raw epoch timestamps, enum or integer codes, or raw byte counts as primary text. Show localized date/time (relative where helpful, "3 giorni fa" / "3 days ago"), named states ("Buona" / "Good" for battery health codes), and sizes in KB/MB/GB. Put the raw value in secondary text or the expanded row when useful for advanced users; unknown codes read "Sconosciuto (codice N)" / "Unknown (code N)".

### Proposed idle-drain alert offer (not v1 scope yet)

- Show the offer only after the personal idle-drain baseline is established; before then show progress/insufficient data, not an alert toggle prompt.
- Make the offer dismissible with **Enable alerts** and **Not now**. Do not enable notifications implicitly or repeat the offer automatically after dismissal.
- Keep an always-reachable alert setting in Battery settings so the user can opt in later.
- Request Android notification permission only after the user chooses Enable. If permission is denied or later revoked, leave alerts paused/off and provide a settings route without nagging.
- Explain that an alert reports an unusual completed idle session relative to the user's own range and does not identify a cause.

### Proposed contextual diagnosis (not v1 scope yet)

- Keep the baseline's primary view limited to battery level, screen state, charging state, and the calculated range.
- Put optional temperature/hotspot/network context behind a separate “Investigate factors” action.
- Explain each signal and its access requirement before enabling it; allow individual sources to remain off.
- Mark absent or denied signals as unavailable. Do not imply that missing context invalidates the baseline.

### Power tools screens (CMD-001, APPLOCK-001, KEY-001, LOCK-003)

- **Command shell:** monospace output area with stdout and stderr distinguished by label and color (not color alone), sticky input row with Run/Stop, exit-status chip, history and snippets in a bottom sheet (side pane on wide windows). Dangerous-command confirmation is a dialog naming the specific risk. Not offered on TV as a primary tool unless Shizuku is available there.
- **App lock:** setup checklist (screen lock, Accessibility, limits acknowledged), app picker with search, grace-period segmented control (Immediately/1/5/15 min). The lock activity shows only the Able logo and the protected app name behind the system prompt.
- **Button shortcuts:** list of the five gestures with current target and status; power key shown as a disabled row with the "unsupported" reason; target picker reuses the Action Catalog picker plus app/activity pickers; test button.
- **Home double tap:** status row per context (home empty space, lock screen) with supported/unsupported and the transparent widget fallback action. The home context has a segmented control **Leggera / Precisa** (Light / Precise); Light carries the label "può perdere tocchi" / "may miss taps"; Precise opens a disclosure (delayed single taps, replay limits, launcher dependence) and a self-test screen with per-gesture results and the measured replay delay (LOCK-003, ADR-039).

### Background, memory, and hotspot screens (APP-007, MEM-001, HOT-001, added 2026-11-22)

- **Background permissions (App):** searchable list with a state pill per app ("Limitato" / "Restricted", "Consentito" / "Allowed", bucket name), filters (user/system, restricted/allowed), multi-select with a contextual bar (Restrict, Allow, bucket), protected and system apps shown with a lock and excluded from select-all. The confirmation sheet lists apps and risks; results show per-app outcome with failed rows first and a Restore batch action. Shizuku-unavailable state with the battery-settings route.
- **RAM e processi (Strumenti, next to Storage):** RAM summary card with a bar chart and text equivalent and an always-visible honest note ("Android gestisce la memoria da solo; le app possono riavviarsi." / "Android manages memory itself; apps may restart."); process list sorted by memory with source label; per-app actions Force-stop and Kill in a detail sheet; block list editor; no "boost" visuals, no score, no animations suggesting cleaning.
- **App rows in Storage and RAM (revised 2026-11-23):** leading app icon (40dp), name and secondary value (memory or cache size), and a trailing checkbox vertically centered on the row in selection mode; long press or the select action enters multi-select; the same contextual bar (count, select all user apps, action) and the same confirmation and per-app result sheet (failed rows first) are used by RAM bulk stop and Storage per-app cache clear.
- **Large-file finder (STO-001, revised 2026-11-23):** filter row with chips for Type, Size (presets + Custom… dialog), Age (Any/7/30/90/365 days), Location (All/Download/DCIM/Choose folder…), and a Sort menu (size, date, name, direction); a search field filters by name; results show thumbnails for images/video and type icons otherwise, with size, date, and path; an empty result lists active filters with "Rimuovi filtri" / "Clear filters"; scan progress with Cancel.
- **Fake GPS (GPS-003, added 2026-11-23):** full-height osmdroid map with the OSM attribution always visible (bottom right, not covered by the floating bar or the joystick); a centred crosshair or draggable marker for the fake position; top search field (place or coordinates); bottom sheet with Start/Stop, saved points, Route (draw points, speed chips Walk/Bike/Car/Custom, loop, pause), and Realism (jitter, altitude, accuracy); an optional on-map joystick with speed; setup card when Able is not the mock-location app (Developer options route, Shizuku one-tap); always-visible "Le app possono rilevare posizioni simulate" / "Apps can detect mock locations" note; offline state "Mappa non disponibile senza rete" / "Map unavailable offline" with coordinate entry still available. TV: not offered unless the device has location and touch.
- **Hotspot:** large on/off control with the read-back state; configuration form (SSID, password with show/hide, security, band segmented control limited to supported bands, hidden SSID, max clients stepper, auto-off) with inline validation and an Apply action; read-back mismatch shown per field; clients list; QR behind a "Mostra QR" / "Show QR" action. Shizuku-unavailable and unsupported-build states.
- **NFC setup (revised 2026-11-23):** three route rows (Settings page, ADB grant with copyable command, Shizuku self-grant) with the active route highlighted; the Shizuku row opens a confirmation explaining that `WRITE_SECURE_SETTINGS` lets Able change secure system settings, that it also enables the debugging and Private DNS toggles, that it persists until revoked, and how to revoke; after the grant the row shows "Concesso" / "Granted" with Revoke; the tile shows real state.
- **Debugging toggles:** switches and tiles reflect `adb_enabled` / `adb_wifi_enabled` live (Settings observers), with no refresh action needed.

### Tools hub (TOOLS-001, added 2026-11-23, ADR-048)

- Grid of equal-size tiles (same width and height in a window), icon on top, label up to two lines, optional status badge inside the tile; unavailable tools keep the size with an unavailable badge. Column count adapts to the window width; at large font scale all tiles grow together. The container transform (UX-004) uses the uniform tile bounds.

## Accessibility and localization

- All controls expose role, label, state, and action to TalkBack.
- Focus order matches visual order for keyboard and D-pad.
- TV focus is visible from normal viewing distance and does not disappear after state changes.
- Support font scaling and avoid truncation of Italian/English labels; long package/class names wrap or can be expanded/copied.
- Localize units, dates, times, percentages, plurals, accessibility hints, notification text, and permission rationale.
- Theme, system locale, and user overrides persist across process/device restart.

## Outstanding design work

- Define typography, spacing, shape, elevation, icon set, motion (slide and container-transform specs), haptics compositions, blur parameters, and fallback palette tokens; produce the "Pulse" logo, adaptive/monochrome launcher icon, and tile icon set (BRAND-001, ADR-027 revised; decided 2026-11-23).
- Mock up the floating navigation bar with Search button (light, dark, dynamic color, blur fallback), the Home bottom failed-actions notice, the swipeable Battery sub-sections with the new Monitor and Health order, the uniform Tools hub, the Storage/RAM app rows and large-file finder filters, and the Fake GPS map screens, in Italian and English.
- Mock up the redesigned Battery Status, Profiles editor, and Diagnosis screens; Background permissions; RAM e processi; Hotspot configuration; and the Precise double-tap self-test, in Italian and English.
- Write tour step copy and "?" help dialog copy in Italian and English, including app-lock limits and dangerous-command warnings.
- Build the Italian/English search index entries and keywords for every searchable option.
- Create low/high fidelity flows for Home, profile editor, battery monitor, app detail, shortcut editor/collections, and TV Home.
- Finalize component-level layout specifications for the modular cards and the high-priority safety alert banner on phone, tablet, and TV.
- Confirm notification channel names and compact/detailed layouts in Italian and English.
