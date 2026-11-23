# Able user stories and acceptance criteria

Stories are grouped by product area. `MUST` stories belong to v1 unless a platform limitation is documented. Each story links to a use case and functional requirement IDs in the traceability matrix.

## Setup, shell, and UI

### US-PRIV-01 — Review and choose whether to send a crash report

As an Able user, I want to see a crash report and decide myself whether to email it to Drago Labs, so that I can help fix problems without anything leaving my device automatically.

- Given the crash-report setting in Settings → Privacy is off, when Able crashes, then no crash dialog is shown and nothing is sent.
- Given the setting is on, when Able restarts after a crash, then it shows the ACRA report content and offers Send and Discard.
- Given I choose Send, when Android can open an email composer, then the draft is addressed to `info.dragolabs@gmail.com` and nothing is sent until I send it from the email app.
- Given I review a report, when I read its content, then it contains no account identifiers, message/file contents, hotspot credentials, or installed-app list.
- Given no email app is available, when I choose Send, then Able offers to copy the report and address and reports the outcome.
- Given I turn the setting off, when the change is saved, then stored unsent reports are deleted.
- Given I export my Able configuration, when the archive is created, then the crash-report setting and stored reports are not included.
- Unsupported/error states: Able makes no network request for diagnostics; there is no feature-use analytics; a report that cannot be generated is skipped without blocking Able.

**Links:** UC-18; PRIV-001; NFR-PRIV-001, NFR-PRIV-003.

### US-ABOUT-01 — Reach Drago Labs project and support information

As an Able user, I want direct links to the project repository, Drago Labs website, and support email, so that I can inspect the project or contact its owner.

- Given I open Settings → About, when the page loads, then it shows the Able GitHub repository `https://github.com/dragolab/able` (configured 2026-11-22), `dragolabs.it`, `info.dragolabs@gmail.com`, and the UAD-ng attribution with license and snapshot commit.
- Given I select the website or configured repository, when Android can open a browser, then Able opens that destination only after my selection.
- Given I select the support email, when Android can open an email composer, then the recipient is prefilled as `info.dragolabs@gmail.com` and no message is sent automatically.
- Given no browser or email handler can open a destination, when Able reports the failure, then I can copy the exact URL or address.
- Given I select the repository row, when it opens, then the destination is exactly `https://github.com/dragolab/able`, never another repository.

**Links:** UC-19; ABOUT-001; NFR-UI-001, NFR-I18N-001.

### US-SET-01 — Keep Able in my language and theme

As an Able user, I want Able to follow my Android language and theme or let me override them, so that the interface feels consistent and readable.

- Given the system language is Italian or English and Able is set to System, when I open Able, then all supported content appears in that language.
- Given I select English or Italian, when Able restarts, then my explicit choice remains active until I select System again.
- Given I choose System, Light, or Dark, when I navigate through Able, then all supported screens and widgets follow that choice.

**Links:** UC-02; SET-001.

### US-SET-02 — Scan the access checklist quickly

As a user, I want compact access rows that I can expand for details, so that I can see at a glance what is granted without reading long text.

- Given I open the access checklist, when it loads, then each row shows the access name, current state, and an action in one compact line.
- Given I expand a row, when the explanation appears, then it describes the purpose and consequences, and collapsing it restores the compact view.
- Given TalkBack or D-pad, when I focus a row, then its state and expand/collapse action are announced and operable.
- Given an access cannot be checked on this device, when the row renders, then it shows "unknown" or "unavailable" rather than granted.

**Links:** UC-01; SET-002; NFR-ACC-002.

### US-UX-01 — Learn Able with a short guided tour

As a new user, I want an optional tour of each section after onboarding, so that I know where things are without reading documentation.

- Given onboarding has finished, when the tour is offered, then I can start or skip it.
- Given the tour is running, when I exit at any step, then nothing in my settings changes and no permission is requested.
- Given I skipped or finished the tour, when I open Settings → Help, then I can replay it.
- Given a feature is unavailable on my device, when its step appears, then the tour says it is unavailable instead of pointing at a missing control.
- Given Android TV, when I take the tour, then every step is operable with D-pad, Select, and Back.

**Links:** UC-34; UX-001; NFR-ACC-002, NFR-TV-001.

### US-UX-02 — Understand risky options before using them

As a user, I want a "?" button on advanced or risky options, so that I understand what they do, their risks, and how to undo them.

- Given an option is advanced or risky, when I view it, then a "?" info button is shown beside it.
- Given I select "?", when the dialog opens, then it explains what the option does, the risks, and how to undo it, and changes no state.
- Given I close the dialog, when I return, then the screen state is unchanged.
- Given help text is missing for my language, when the dialog opens, then it falls back to the default language rather than showing an empty dialog.

**Links:** UC-35; UX-002; NFR-ACC-002.

### US-UX-03 — Feel confirmations and control haptics

As a user, I want haptic feedback on every tap that feels different for different kinds of actions, with an intensity I choose, so that actions feel immediate and I can tell a success from a destructive or failed action without looking.

- Given haptics are at Normal (default), when I tap any interactive element, then Able gives one short pattern of the matching kind: tick (rows, chips, tabs), click (buttons, navigation items), toggle (distinct on and off), confirm, reject/destructive, or long press (revised 2026-11-23).
- Given I confirm a destructive operation or an action fails, when Able responds, then I feel the reject pattern, never the confirm pattern.
- Given I choose Light, Normal, or Strong in Settings, when I tap, then the intensity changes accordingly; given my device lacks vibration primitives, then Settings tells me intensity may not change and the fallback pattern still plays.
- Given I drag a slider, when it crosses a step, then I feel one light tick per step (not continuous buzzing), and when I release it, one confirm pulse.
- Given I tap a button, chip, switch, or list row, when the interaction registers, then Able gives the pattern defined in the haptics map once.
- Given an action is rejected or fails, when Able reports it, then the haptic pattern differs from the success pattern.
- Given I set Haptic feedback to Off in Settings, when I act, then no haptics are produced.
- Given system touch feedback is disabled or the device has no vibrator, when I act, then no haptics occur and no error is shown; without a vibrator the intensity control is hidden or marked unavailable.

**Links:** UC-36; UX-003 (revised 2026-11-22, 2026-11-23); ADR-043.

### US-UX-04 — Navigate with predictive back and smooth motion

As a user, I want predictive back and expressive transitions, so that navigation feels clear, while respecting my accessibility preferences.

- Given a supported Android version, when I start a back gesture, then Able previews the destination and completes or cancels the navigation according to my gesture.
- Given I switch to a tab to the right of the current one, when the transition runs, then content slides in from the right (and from the left for a tab to the left) with a barely perceptible spring overshoot that settles without oscillating.
- Given I open a card or menu, when the destination appears, then it expands from the card's bounds to full screen, and back or predictive back shrinks it into the same card.
- Given I open a small tool card (Strumenti grid, Home quick action), when the transform runs in either direction, then there is no position or size jump, no flash, and no overlapping content, and it returns into that card's current bounds even after scrolling or rotation (defect criteria, 2026-11-22).
- Given I start and then cancel a predictive back gesture, when I release, then the full screen is restored without a jump.
- Given the originating card is no longer visible, when I go back, then the return uses a fade instead of shrinking toward an off-screen point.
- Given I have unsaved edits, when I go back, then Able still asks whether to discard or keep editing.
- Given "Remove animations" is enabled, when I navigate, then transitions are instant or a plain fade, without slide, overshoot, or container transform.
- Given an Android version without predictive back, when I press back, then standard back behavior applies.

**Links:** UC-36; UX-004 (revised 2026-11-22); NFR-ACC-002.

### US-ACC-01 — Read and act on screens immediately

As a user, I want concise text, large touch targets, and readable contrast, so that I understand each screen at a glance.

- Given any primary screen, when it loads, then primary text is concise and long explanations are behind progressive disclosure.
- Given any interactive control, when I tap it, then its touch target is at least 48dp.
- Given light, dark, or dynamic color themes, when text is shown, then contrast meets WCAG AA.
- Given a collapsing header, when I scroll, then it never overlaps content.
- Given a card header with a status pill or "?" button, when the title does not fit beside them (long Italian text, large font), then the pill/button moves under the title and the title is never broken into one-word lines.
- Given a screen shows a system value such as an epoch timestamp, enum code, or byte count, when I read it, then I see a human-formatted value (localized date/time, named state, KB/MB/GB), with the raw value only as secondary text.
- Unsupported/error states: an unknown enum code is shown as "Unknown (code N)", never as a bare number or a guessed name.

**Links:** UC-03; NFR-ACC-002 (revised 2026-11-22), NFR-ACC-001.

### US-BRAND-01 — Recognize Able everywhere

As a user, I want a distinctive Able icon, logo, and tile icons, so that I can find Able on my home screen and in Quick Settings.

- Given my launcher supports adaptive or themed icons, when I view Able, then the adaptive or monochrome Able icon is shown.
- Given I add Able tiles, when they appear in Quick Settings, then each uses an icon from the Able icon set.
- Given light, dark, or dynamic color, when I view the logo or tile icons, then they remain legible.
- Given a launcher without themed icons, when I view Able, then the standard adaptive icon is shown without broken artwork.
- Given the "Pulse" concept (decided 2026-11-23), when I see the launcher icon, then it shows a lowercase "a" of a ring and a stem with a mint level segment (ink `#101418`, mint `#1FC98E`), and the themed icon shows ring and stem only.
- Given I see the wordmark, when it renders, then it uses Google Sans Flex at weight 650 with ROND 0.
- Unsupported/error states: a launcher that crops or tints icons still shows a recognizable ring and stem; small tile sizes keep the mark legible.

**Links:** UC-37; BRAND-001; ADR-027 (revised 2026-11-23).

### US-HOME-01 — See battery status, useful actions, and customize modular cards

As a user, I want a modular card-based Home where battery level, active profile, and quick actions are shown first by default, and I can customize which cards appear and in what order, so that I can tailor the dashboard to my needs while preserving quick actions.

- Given the default Home layout, when Home opens, then battery/profile and primary quick action cards appear first without switching destination.
- Given I customize Home, when I reorder, hide, or reveal cards, then my preferences persist across app restarts and backup export/import.
- Given I want to restore the recommended setup, when I select Reset to default, then the standard card order and visibility are restored immediately.
- Given a critical condition (for example Shizuku permission missing or automation paused by an error), when Home opens, then a safety banner is shown at the top even if other cards were reordered, and I cannot hide it while the condition persists.
- Given some actions failed, when Home opens, then a failed-actions notice appears at the bottom of Home with the count and Details; when I hide it, it stays hidden until a new failure is recorded (revised 2026-11-23).
- Given the Recent actions card, when I look at it, then it has no "Open Battery" button; if "Show all" is offered, it opens the full history in a sheet.
- Given a tool is unavailable on the current device, when I view Home, then Able does not present it as working.
- Unsupported/error states: hiding the notice never deletes log entries; an empty history shows an empty state in the sheet.

**Links:** UC-01, UC-03, UC-20; HOME-001, HOME-002, HOME-003 (revised 2026-11-23); ADR-048.

### US-UI-01 — Use Able with the current input method

As a user of a phone, tablet, or TV, I want layouts suited to my screen and input method, so that every available action remains usable.

- Given a wide tablet window, when I open a list/detail tool, then Able can use a two-pane layout without stretching compact controls.
- Given Android TV, when I navigate with a D-pad, then every visible primary action can receive focus and be activated with Select.
- Given a window resize or orientation change, when the layout changes, then unsaved user input is preserved.

**Links:** UC-03; SHELL-001, capability requirements, NFR-UI-001.

### US-UI-02 — Jump back to the start of a section

As a user deep inside a section, I want to tap that section's navigation item again to return to its start, so that I don't have to press back repeatedly.

- Given I am in a nested screen of a section, when I select that section's navigation item, then Able pops to the section root, closes any detail pane, scrolls to the top, and selects the first tab.
- Given I am already at the section start, when I select its item again, then the list scrolls to the top.
- Given a form with unsaved edits is open, when I re-select the item, then Able asks whether to discard or keep editing first.
- Given I switch to another section and come back, when I return, then that section's previous position is kept (re-select is only for the current section).
- Unsupported/error states: same behavior on the bottom bar, the navigation rail, and the TV drawer (D-pad Select).

**Links:** UC-48, UC-03; SHELL-001; NFR-UI-001, NFR-TV-001; ADR-040.

### US-UI-03 — Navigate with a floating bar that never hides content

As a phone user, I want a compact floating navigation bar with a separate Search button, so that the app feels light while every item stays reachable.

- Given a compact window, when any primary destination is shown, then a floating pill bar with Home, Batteria, App, Strumenti and a separate circular Search button on its right are visible above the system navigation area.
- Given content behind the bar, when I look at it, then it is lightly blurred and the bar's labels and icons stay readable (WCAG AA); given blur is unavailable, then the bar is an opaque tonal surface.
- Given I scroll to the end of any list, when the last item arrives, then it and any primary action sit fully above the bar.
- Given I tap the Search button, when it opens, then the options search starts (SEARCH-001).
- Given the keyboard opens, when I type, then the bar never covers the field.
- Given a tablet or TV, when Able shows navigation, then the rail or drawer is used, not the floating bar.
- Unsupported/error states: TalkBack names each item and the Search button; re-selecting an item still returns to the section start (US-UI-02).

**Links:** UC-03; SHELL-001 (revised 2026-11-23), SEARCH-001; NFR-UI-001, NFR-ACC-002; ADR-042.

### US-TOOLS-01 — Scan a tidy Tools hub

As a user, I want every tool tile in Strumenti to have the same size, so that the hub looks orderly and I find tools quickly.

- Given the Tools hub, when it loads, then every tile has the same width and height whatever its label, badge, or availability.
- Given a long label or large font, when tiles render, then labels wrap to at most two lines or ellipsize and all tiles grow together.
- Given a tool is unavailable, when I look at its tile, then it keeps the same size and shows the reason.
- Unsupported/error states: TalkBack reads the full tool name; D-pad moves predictably across the grid on TV.

**Links:** UC-52; TOOLS-001; UX-004; NFR-ACC-002, NFR-TV-001; ADR-048.

### US-SEARCH-01 — Find any option by searching

As a user, I want to search all of Able's options from any main screen, so that I reach a setting without knowing where it lives.

- Given I am on Home, Batteria, App, or Strumenti, when I look for search, then the circular Search button next to the floating navigation bar is there on a phone, and a search action in the top bar on a tablet or TV (revised 2026-11-23).
- Given I type a query in Italian or English, when results load, then matching tools, battery options, apps features, settings, tiles & widgets, and Home customization entries appear, whichever language the keyword is in.
- Given I select a result, when the destination opens, then the option is highlighted where possible.
- Given a result is unavailable on my device, when it is listed, then it shows the reason instead of opening a dead screen.
- Given I searched before, when I open search, then recent searches appear; I can clear them; they are not exported.
- Unsupported/error states: no matches → empty state; search makes no network request and never changes a setting; TalkBack and D-pad can operate it.

**Links:** UC-42; SEARCH-001; NFR-ACC-001, NFR-ACC-002, NFR-TV-001, NFR-I18N-001.

## Battery

### US-BAT-01 — Set a profile without surprise restrictions

As a user, I want to review triggers, protected apps, and per-app actions before enabling a battery profile, so that I know what Able will change.

- Given a new profile, when I have not configured an app action, then Able observes but does not restrict that app.
- Given I enable a restrictive profile, when the preview appears, then it lists affected apps and likely notification/sync impact.
- Given an app is protected, when a profile is evaluated, then no configured restriction is applied unless I explicitly remove that protection.
- Given a fresh install, when I open Battery, then every trigger of every profile is off and Normale is the effective profile.
- Given I enable a trigger, when its condition occurs, then only that enabled trigger can activate its profile.
- Given a profile has several enabled triggers (for example Notte's schedule and screen-off), when any of them is true, then the profile is active (revised 2026-11-22).
- Given I save a profile without enabling any trigger, when I return to Battery, then Able states that the profile will not activate automatically.

**Links:** UC-04, UC-43; BAT-001, BAT-003, BAT-004.

### US-BAT-02 — Let approved automation run and tell me what happened

As a user, I want pre-authorized profile actions to run automatically with a notification and log, so that I don't need to approve the same action every time.

- Given I reviewed and enabled a profile, when a trigger starts, then Able applies configured supported actions without a second confirmation.
- Given an action is skipped or fails, when the profile changes, then its notification/log distinguishes that result from success.
- Given a rule would freeze or disable an app, when a profile triggers, then Able does not run it automatically.
- Given I pause automation, when a future trigger occurs, then Able makes no profile action until I resume it.

**Links:** UC-05; BAT-005, BAT-006, BAT-008.

### US-BAT-07 — Create and tailor my own battery profiles

As a user, I want to create profiles with my own triggers and edit the built-in ones, so that automation matches my routine.

- Given I select Create profile, when I name it and save, then it appears in the profile list with no trigger enabled and Able says it will not activate automatically.
- Given I edit a profile, when I add triggers, then I can choose battery below (activation and reset thresholds, only while unplugged), schedule (times and days), screen off for N minutes, charging, and manual, each disabled until I enable it.
- Given I enter a reset threshold not above the activation threshold, or a schedule without days, when I try to save or enable it, then Able blocks it and explains why.
- Given a built-in profile (Normale, Notte, Risparmio, Emergenza, Pausa app), when I open its menu, then I can edit and Reset to default but not delete it; reset restores its default triggers (disabled) and actions after confirmation.
- Given a custom profile, when I delete it and confirm, then Able deactivates it first, re-evaluates app actions, and removes it.
- Unsupported/error states: duplicate names rejected; Shizuku unavailable → only standard actions selectable; freeze/disable never selectable as a profile action.

**Links:** UC-43; BAT-001, BAT-003; ADR-005.

### US-BAT-08 — Run several profiles together with predictable results

As a user, I want several profiles to be active at once and know which one decides each app, so that overlapping situations (night and low battery) behave predictably.

- Given two profiles are active, when they set different actions for the same app, then the most restrictive action applies (Osserva < Limita in background < Arresta quando esco).
- Given an app is protected, when any profile is active, then no profile action is applied to it.
- Given I open Battery → Status, when profiles are active, then I see each active profile with the trigger that activated it, and each applied app action with its source profile(s).
- Given one of two active profiles ends, when Able re-evaluates, then it keeps actions still required by the other profile and reverts only Able-attributed changes no longer required.
- Given the same event activates two profiles, when Able applies them, then each app change is made once.
- Unsupported/error states: an app action rejected by Android is shown failed with its source profile; no profile active → Normale shown as effective.

**Links:** UC-05; BAT-002, BAT-003, BAT-008; ADR-005.

### US-BAT-09 — Swipe through Battery and find answers in a sensible order

As a user, I want to swipe between Status, Monitor, Health, and Diagnosis and see each screen ordered by the questions I have, so that I get the answer I need first.

- Given Batteria is open, when I swipe left or right, then the adjacent sub-section appears and the tab row follows; tapping a tab also works.
- Given I open Monitor, when it loads, then I see live status first, then statistics, then sessions, then settings.
- Given I open Health, when it loads, then I see the health summary first, then capacity, lifecycle, charging, and advanced data.
- Given I swipe on a chart or chip row, when I drag, then that content scrolls instead of changing sub-section; swipes from the back-gesture edge still go back.
- Given "Remove animations" is on, when I change sub-section, then the change is instant.
- Unsupported/error states: unavailable values show "unavailable on this device" with their source; on TV tabs are D-pad focusable and no swipe is needed; re-selecting Batteria returns to Stato.

**Links:** UC-50; BAT-UI-001; MON-001–MON-009, BAT-HLT-001–BAT-HLT-002; NFR-ACC-002, NFR-TV-001; ADR-048.

### US-MON-01 — Understand screen-on and idle battery use

As a user, I want separate screen-on, screen-off, and charging measurements, so that I can distinguish usage patterns.

- Given a valid recorded interval, when Able shows drain per hour, then it labels the value as an estimate and identifies the interval.
- Given sample data is insufficient or partial, when I view the monitor, then Able says “not enough data” or “partial” instead of inventing a rate.
- Given deep-sleep or current data is unavailable, when I view details, then Able marks it unavailable.
- Given I enable monitoring, when I stop it, then the service and persistent notification stop.

**Links:** UC-06; MON-001–MON-008.

### US-MON-02 — Control how long battery history is kept

As a user, I want to choose how long Able keeps battery-session history and delete it immediately, so that I control retention of device observations.

- Given I have not changed the setting, when Able stores completed battery sessions, then summaries expire after 90 days.
- Given I select 30 days or Until manually deleted, when sessions are retained, then Able applies that choice and shows it in settings.
- Given I choose Delete history now, when deletion completes, then session summaries and the derived idle-drain baseline are removed while profiles and app rules remain.
- Given high-frequency samples are used for live calculations, when a session summary is calculated, then raw samples are not retained as long-term history.

**Links:** UC-06; MON-009; NFR-PRIV-002.

### US-BASE-01 — Choose whether to receive idle-drain alerts

As a user, I want Able to offer personal idle-drain alerts only after it has learned my normal range, so that I can opt in based on meaningful data instead of receiving premature notifications.

- Given the baseline is not established, when I use the monitor, then Able does not offer or send baseline alerts.
- Given the baseline is established, when Able presents the offer, then I can enable alerts or choose Not now; Able does not enable them automatically.
- Given I choose Not now, when later nights are measured, then Able does not repeat the offer automatically, but I can enable alerts in Battery settings.
- Given I enable alerts, when notification permission is needed, then Able requests it in context; a denial leaves alerts off.
- Given notification permission is later revoked, when a qualifying night completes, then Able does not send an alert and shows a route to Android Settings.
- Given alerts are enabled, when a valid night ends outside the calibrated personal range, then Able sends at most one alert and does not claim a specific app caused the drain.

**Links:** UC-16; BASE-001; NFR-SEC-002.

### US-BASE-02 — Add context only when investigating an anomaly

As a user, I want optional context such as temperature or network/hotspot state to help interpret an unusual night, while keeping the baseline itself usable without those extra signals.

- Given I have not enabled contextual diagnosis, when Able calculates the baseline, then it uses only battery level, screen state, and charging state.
- Given I open a contextual diagnosis, when Able offers context sources, then each is optional and explained before any additional permission request.
- Given a context source is unsupported or permission is denied, when Able displays the result, then it marks that source unavailable and still shows the baseline.
- Given I delete battery history, when deletion completes, then contextual observations associated with those sessions are also deleted.

**Links:** UC-17; BASE-002; NFR-SEC-002, NFR-PRIV-002.

### US-BAT-03 — Review app impact and compare profile results honestly

As a user, I want to review system-reported activity for observed apps and compare battery performance before and after applying a profile, so that I can make informed decisions without misleading claims.

- Given I inspect an app in Battery impact, when Able displays metrics, then it shows system-reported background/foreground time with a confidence level and links to Android settings.
- Given I compare profile windows, when the review is generated, then Able pairs comparable sessions matching duration and screen state, labeling differences as observed correlations without claiming proven causal savings.
- Given usage data is unavailable, when I open impact review, then Able clearly marks it as incomplete.

**Links:** UC-22; BAT-REV-001, BAT-REV-002; NFR-PWR-002.

### US-BAT-04 — Track charging sessions and annotate battery events

As a user, I want a diary of charging sessions and the ability to add context notes, so that I can track charging speed, thermal behavior, and real-world usage conditions.

- Given charging begins, when I inspect the diary, then Able logs start time, percentage, and recorded temperature/voltage/wattage metrics where hardware permits.
- Given a charging session exceeds thermal thresholds, when it completes, then Able flags it with a thermal notice without interfering with hardware charging.
- Given I add a note or tag (e.g., travel, gaming, hotspot), when I save it, then it is stored locally with the session and deleted when session history is expired or cleared.

**Links:** UC-23; BAT-CHG-001, BAT-NOTE-001; NFR-PRIV-002.

### US-BAT-05 — Troubleshoot battery drain with the anomaly checklist

As a user, I want an interactive checklist to help me find reasons for high battery drain, so that I can take corrective action without granting invasive background monitoring.

- Given I open the anomaly checklist, when I review factors, then Able checks available signals (signal strength, hotspot state, screen time, top apps) without continuous polling.
- Given a factor is identified, when I tap it, then Able provides advice and direct intents to Android settings.

**Links:** UC-24; BAT-CHK-001; NFR-UI-001.

### US-BAT-06 — Inspect hardware battery health, cycle count, and manufacture metadata

As a user, I want to see my battery cycle count, health percentage, capacity, and manufacturing dates (inspired by aBattery), so that I know the physical condition of my device's battery.

- Given my device runs Android 14+, when I view Battery health, then Able displays `EXTRA_CYCLE_COUNT` from the public `ACTION_BATTERY_CHANGED` broadcast when the device exposes it.
- Given Shizuku or system access is granted, when I view advanced health, then Able displays State of Health %, design vs full charge capacity in mAh, manufacture date, first usage date, and charging policy.
- Given the hardware, OEM security layer (e.g., Knox), or HAL does not populate these fields, when I view health, then Able states that the metric is unsupported by the hardware/ROM rather than guessing or faking a 100% score.

**Links:** UC-25; BAT-HLT-001, BAT-HLT-002; NFR-COMPAT-001, NFR-PWR-002.

## Device tools

### US-LOCK-01 — Turn off the screen with a double tap on home and lock screen

As a user, I want to turn off and lock the phone by double-tapping the launcher home screen or the lock screen, so that I can turn off the display without reaching the power button.

- Given Able needs the Accessibility service, when setup begins, then it explains why before opening Android Settings.
- Given the setup returns, when I choose home screen, lock screen, or both, then each context can be enabled and tested separately.
- Given I run the test for a context, when I double tap, then Able records whether the gesture was detected and the lock call succeeded.
- Given a launcher or the OS blocks detection in a context, when setup ends, then Able marks that context unsupported, offers the Quick Settings tile or shortcut, and does not claim it works.
- Given the Accessibility service is later disabled, when I open Screen lock, then Able shows the feature as inactive with a route to re-enable it.

**Links:** UC-07; LOCK-001, LOCK-002.

### US-LOCK-02 — Double tap empty home-screen space to turn off the screen

As a user, I want to double tap an empty part of my home screen to turn the screen off, without breaking my launcher, so that I can turn off the display quickly.

- Given the non-consuming route is supported and enabled, when I double tap empty home-screen space, then the screen turns off.
- Given the feature is active, when I tap, scroll, or long press icons and widgets, then the launcher behaves exactly as without Able, and a double tap on an icon or widget does not turn the screen off.
- Given another app is in the foreground, when I double tap, then nothing happens.
- Given my device does not support non-consuming touch observation, when I open the setting, then Able says the direct route is unsupported and offers the transparent widget; a double tap on the widget turns the screen off and a single tap does nothing.
- Given the Accessibility service is disabled or blocked, when I open Screen lock, then the feature is shown inactive with the reason.
- Given I choose Light mode, when I read its label, then Able says it may miss taps (revised 2026-11-22).

**Links:** UC-39; LOCK-003, LOCK-001; NFR-COMPAT-001.

### US-LOCK-03 — Use a precise home double tap that still lets my launcher work

As a Pixel user for whom the light mode misses taps, I want a precise mode that reliably detects a double tap on empty home space while my icons, long presses, and swipes keep working.

- Given I select Precise, when setup opens, then Able explains that single taps are slightly delayed, some gestures may not replay perfectly, and launcher updates can affect it.
- Given I run the self-test, when it completes, then Able shows the result for double tap, icon tap, long press, and page swipe, and the measured replay delay.
- Given Precise is active and the launcher home is in front, when I double tap empty space, then the screen turns off; when I tap an icon, it opens; when I long press, the launcher menu appears; when I swipe, the page changes.
- Given I use back, home, or Recents gestures, when Precise is active, then they work unchanged because system gesture areas are not covered.
- Given I open an app, Recents, the shade, or the keyboard, when the launcher loses the foreground, then the overlay is gone and nothing is intercepted.
- Given Precise is active, when I swipe between home pages several times in a row, then after the first swipe further swipes go straight to the launcher at native speed, and the first swipe changes the page with its own velocity (revised 2026-11-23).
- Given I read the Precise setup, when it lists trade-offs, then it says that double taps right after a swipe and on the dock or search bar are not detected.
- Unsupported/error states: self-test failure → Able recommends Light mode or the widget and does not enable Precise silently; the self-test shows first-swipe delay and page-change success rate; multi-touch gestures are disclosed as unreliable; Accessibility disabled → inactive.

**Links:** UC-47; LOCK-003 (revised 2026-11-23); NFR-COMPAT-001; ADR-039 (revised).

### US-TORCH-01 — Control the torch safely

As a user, I want to toggle and adjust the torch using the levels my device supports, so that I can access it quickly without misleading controls.

- Given the device has no torch, when I open Tools, then Torch is hidden or marked unavailable.
- Given intensity is unsupported, when I open Torch, then Able offers on/off but no false brightness slider.
- Given SOS/Morse/interval mode is running, when I need to stop it, then a visible in-app or notification action is available.

**Links:** UC-08; TORCH-001, TORCH-002.

### US-HOT-01 — Control and automate hotspot where the system allows

As a user, I want to inspect hotspot settings and optionally react to a selected Bluetooth device, so that tethering is convenient and predictable.

- Given a setting is unsupported or OS-rejected, when I change it, then Able shows the actual value and error rather than success.
- Given a Bluetooth rule is enabled, when the selected device connects/disconnects, then Able follows the configured on/off behavior and records the outcome.
- Given Shizuku disconnects, when the rule next runs, then Able reports that it could not apply the advanced operation.

**Links:** UC-09; HOT-001, HOT-002.

### US-HOT-02 — Turn the hotspot on and configure it directly

As a user with Shizuku, I want to turn the hotspot on/off and change its name, password, security, band, and limits from Able, so that I don't have to dig through system settings.

- Given Shizuku is connected, when I toggle the hotspot, then Able shows the read-back state (starting, on, stopping, off) or the failure reason.
- Given I edit SSID, password, security, band, hidden SSID, maximum clients, or auto-off, when I apply, then each field is read back and any rejected field shows the actual value and reason.
- Given an invalid SSID or password for the chosen security, when I apply, then nothing is applied and Able explains the rule.
- Given the device does not support an option (for example 6 GHz), when the screen loads, then that option is not offered or is marked unsupported.
- Given the hotspot is on, when devices connect or disconnect, then the clients list updates, or shows "unavailable" if not exposed.
- Given I request the QR code, when it appears, then it joins the hotspot, and the password never appears in logs, crash reports, or the standard backup.
- Unsupported/error states: Shizuku missing → readable state only and a route to Android tethering settings; hidden API missing on this build → "not supported on this Android build" without a crash.

**Links:** UC-46; HOT-001, HOT-002; NFR-SEC-003, NFR-COMPAT-001.

### US-PIX-01 — Configure experimental Pixel tools with honest capability gating

As a user, I want to explore advanced device tools (AOD on notifications/charging, Recents close-all, screen-off fingerprint, ringtone tile, and orientation camera launch) and understand if my device supports them, so that I can use device-specific shortcuts safely without false claims.

- Given an experimental tool is unsupported on my device or Android build, when I view the tool, then Able clearly marks it as unsupported and explains the reason.
- Given I enable AOD on notification or charging, when a qualifying event occurs, then Able triggers AOD only if the underlying system setting or service permits it.
- Given I use the ringtone-mode Quick Settings tile, when I tap it, then Able cycles through Normal, Vibrate, and Silent, updating the tile state immediately.
- Given I enable orientation camera quick-launch, when I wake the screen, then Able evaluates orientation without continuous background sensor drain and launches the camera only when the configured orientation is matched.
- Given an experimental operation fails or is rejected by Android, when Able executes it, then it logs the failure and does not report false success.

**Links:** UC-21; PIX-001–PIX-006; NFR-COMPAT-001, NFR-PWR-001.

### US-GPS-01 — Save, categorize, and share location points

As a user, I want to save my current GPS coordinates with custom labels and tags and share them easily, so that I can keep track of locations without continuous tracking.

- Given I tap Save Location, when location permission is granted, then Able acquires a fix and displays coordinates, accuracy, and altitude without running background tracking.
- Given I enter a label, tag, or note, when I save the point, then it is stored locally in the catalog and survives app restarts.
- Given I select a saved point, when I tap Open in Maps or Share, then Able launches the target map app via `geo:` intent or formats the coordinates for quick sharing.

**Links:** UC-26; GPS-001, GPS-002; NFR-PRIV-002, NFR-SEC-002.

### US-GPS-02 — Simulate my location for testing

As a user testing location-based apps, I want to set a fake position on a map, move it with a joystick, or follow a route, so that I can test behavior without travelling.

- Given Able is not the mock-location app, when I open Fake GPS, then Able explains why and offers Developer options or, with Shizuku, a one-tap selection whose result is checked.
- Given the map is open, when I tap a point or search a place or coordinates, then the fake position moves there; I can save it as a point.
- Given I start a mock position, when it is active, then an ongoing notification shows it with Stop, and stopping returns my device to its real location.
- Given I use the joystick or a route, when I choose a speed, then the position moves at that speed; routes can pause, resume, loop, and stop.
- Given I set jitter, altitude, or accuracy, when the position is fed, then those values are applied.
- Given the map, when I look at it, then the OpenStreetMap attribution is always visible and Able tells me that the tile server sees the map area I view.
- Given I read setup or "?" help, when the limits are listed, then Able says that apps can detect mock locations and may refuse to work, and that I must not use it to deceive people or services.
- Unsupported/error states: no network → no tiles, coordinate entry still works; Developer options off or selection revoked while running → the service stops and Able reports it; invalid coordinates rejected; no profile, tile, widget, or key gesture can start a mock position.

**Links:** UC-49; GPS-003; NFR-PRIV-001, NFR-SEC-002, NFR-COMPAT-001; ADR-045.

### US-SENS-01 — Measure angles, distances, and directions with sensors

As a user, I want a spirit level, screen ruler, and compass, so that I can perform everyday measurements using my phone's hardware.

- Given I open the Spirit level, when I place the phone on a surface, then Able displays 2D bubble alignment, pitch/roll angles, and provides haptic feedback at 0°.
- Given I open the Screen ruler, when I calibrate the slider with a physical card, then the on-screen markings match real-world centimeters and inches.
- Given I open the Compass, when the sensor is calibrated, then Able displays the heading in degrees with a cardinal indicator.
- Given I leave a measurement tool or turn off the screen, when the app pauses, then all sensor listeners are immediately unregistered.

**Links:** UC-27; SENS-001–SENS-003; NFR-COMPAT-001, NFR-PWR-001.

### US-SYS-01 — Toggle system utilities with graceful permission fallbacks

As a user, I want Quick Settings tiles and in-app switches for NFC, auto-brightness, USB/wireless debugging, and Private DNS, so that I can toggle system settings quickly.

- Given `WRITE_SECURE_SETTINGS` or Shizuku is granted, when I tap the NFC, debugging, or Private DNS tile, then Able toggles the system state directly and updates the tile icon.
- Given privileged permissions are ungranted, when I tap a system tile, then Able opens the matching native Android settings screen instead of failing silently.
- Given I use the Auto-brightness tile, when I tap it with `WRITE_SETTINGS` granted, then Able toggles between automatic and manual brightness modes immediately.
- Given I granted `WRITE_SECURE_SETTINGS` via ADB, when I tap the NFC tile, then NFC toggles directly; given I did not but Shizuku is connected, then Able offers to grant the permission to itself, and after I confirm and the grant is verified, NFC toggles directly; given neither, then the NFC settings page opens (revised 2026-11-23; `svc nfc` through Shizuku is never used because it fails on Android 16).
- Given Able holds the grant, when I open the access checklist, then I see it as granted with what it allows and a Revoke action; after revoking, Able uses the next available route.
- Given USB or Wireless debugging changes in Android Settings or via ADB, when I look at Able's switch or tile, then it shows the new state within about a second without reopening the screen (revised 2026-11-23).
- Given NFC changes from any source, when I look at the tile, then it shows the real state, including turning on/off.
- Given I open NFC setup, when I want advanced mode, then Able shows the ADB grant command with a copy action and how to revoke it.
- Given I choose wireless debugging "details", when the Wireless debugging page can be opened, then it opens directly; otherwise Developer options, otherwise Android Settings, with an explanation.
- Unsupported/error states: no NFC hardware → tile unavailable; developer options disabled → Able explains how to enable them; self-grant failed or cancelled → reason shown and settings fallback.

**Links:** UC-28, UC-51; SYS-001–SYS-004 (SYS-001, SYS-003 revised 2026-11-22 and 2026-11-23); NFR-COMPAT-001, NFR-SEC-002; ADR-044.

### US-CMD-01 — Run shell commands safely through Shizuku

As an advanced user, I want a shell that runs my commands with Shizuku and shows live output, so that I can use ADB commands on the device without a computer.

- Given Shizuku is not available, when I open Command shell, then Able shows why it is unavailable and how to set up Shizuku, and nothing can run.
- Given Shizuku is connected, when I run a command, then stdout and stderr appear while it runs and the exit status appears when it ends.
- Given a command is running, when I press Stop, then the process ends and the result is recorded as stopped.
- Given my command matches the dangerous list, when I press Run, then Able warns me with the specific risk and runs it only if I confirm; cancelling runs nothing.
- Given I ran commands, when I open history, then I see them locally, can reuse or clear them, and they are not included in Auto Backup or `.ablebackup`.
- Given I save a snippet, when I select it later, then it only fills the input and does not run.
- Given I want the output elsewhere, when I choose Copy or Share, then Able uses the clipboard or share sheet only on my action.
- Given I check the action log, when I look at a shell entry, then it shows the command, time, exit status, and duration, never the output.
- Unsupported/error states: Shizuku lost during a run → failed/unknown; output too long → scrollback bounded with a truncation notice; no command is ever started by a trigger, tile, widget, key gesture, or import.

**Links:** UC-38; CMD-001; NFR-SEC-002, NFR-REL-001, NFR-PRIV-002.

### US-APPLOCK-01 — Lock selected apps behind my device authentication

As a user, I want chosen apps to ask for my fingerprint, face, or screen lock when opened, so that someone holding my unlocked phone cannot casually open them.

- Given my device has no screen lock, when I open App lock, then I cannot enable it and Able offers Android's security settings.
- Given app lock is set up, when I open a protected app, then Able's screen shows the system authentication prompt; no overlay is drawn over the app.
- Given I authenticate, when the prompt closes, then I return to the app; given I cancel or press back, then I go to the Home screen.
- Given a grace period of 1, 5, or 15 minutes, when I reopen the app within it, then no prompt appears; after it, the prompt appears; with Immediately, it always appears.
- Given the screen turns off, when I reopen any protected app, then the prompt appears.
- Given setup, when I read the explanation and "?" help, then Able states that app lock is not OS-level enforcement, that Recents previews may show content, and that it depends on Accessibility.
- Given the Accessibility service is disabled, revoked, or blocked by Advanced Protection, when I open Able, then app lock is shown inactive or unavailable and Home shows a warning while apps are configured as protected.
- Unsupported/error states: authentication lockout → Home; protected app uninstalled → removed with a note; Able Settings and Android Settings are never locked in a way that stops me disabling app lock.

**Links:** UC-40; APPLOCK-001; NFR-SEC-002, NFR-COMPAT-001.

### US-KEY-01 — Trigger actions with volume-key gestures

As a user, I want volume-key gestures to run actions such as the torch or an app, so that I can reach them without unlocking menus.

- Given Button shortcuts, when I configure gestures, then I can bind volume up + down together, double press volume up/down, and long press volume up/down independently to an Action Catalog action, camera launch, an app, or a launchable activity.
- Given a gesture is bound and the screen is on, when I perform it, then the target runs once and the key press does not change volume.
- Given a key has no mapping, when I press it, then volume changes as usual; given a key has a double/long-press mapping, then a single press still changes volume, with any delay disclosed in setup.
- Given I look for the power key, when the gesture list loads, then it is listed as unsupported with the reason.
- Given the screen is off, when I read the setup, then Able states that shortcuts are not promised with the screen off.
- Given a target is uninstalled or no longer launchable, when I perform the gesture, then Able reports failure and marks the mapping unresolved.
- Unsupported/error states: destructive targets (package changes, shell commands) are not selectable; the hold-both-keys system accessibility shortcut keeps working; Accessibility disabled → shortcuts inactive.

**Links:** UC-41; KEY-001; NFR-COMPAT-001, NFR-SEC-002.

### US-CUST-01 — Build personalized Quick Settings tiles and multi-action home widgets

As a user, I want customizable Quick Settings tiles and multi-button home-screen widgets, so that I can trigger my favorite combinations of Able tools from anywhere.

- Given I configure a custom tile slot, when I assign an action from the Able Action Catalog, then the tile updates its label and icon to match the chosen action.
- Given I add a multi-action widget to my home screen, when I customize its action slots, then each button executes its assigned Able feature on tap.
- Given I reboot or export my settings, when I check my custom tiles and widgets, then my configuration is preserved and restored.
- Given I pick a tile icon, when the chooser opens, then it offers the curated Able icon set.
- Given I create a custom tile or widget from an Action Catalog entry, when the action requires an ungranted permission, then the tile/widget shows an alert state and opens the rationale in Able instead of failing silently.

**Links:** UC-29; CUST-001, CUST-002, BRAND-001; NFR-UI-001, NFR-MAINT-001.

## Apps, storage, and shortcuts

### US-APP-01 — Inspect and manage apps with a recovery path

As a user, I want to see app rules and available battery evidence, and make a deliberate app-management change, so that I can control background behavior without accidental breakage.

- Given usage access is absent, when I open an app detail, then Able marks battery evidence as incomplete rather than requesting unrelated access.
- Given I choose force-stop, disable, or uninstall, when confirmation appears, then it explains the consequences and requires a separate confirmation.
- Given a package action is attempted, when Android rejects it, then the app remains marked unchanged/failed and the action log reflects that.
- Given a profile is automatic, when it runs, then it never freezes, disables, or uninstalls an app.
- Given the build holds `QUERY_ALL_PACKAGES`, when I open Apps, then all installed packages are listed; given a build without it, then Able states that the list is limited.

**Links:** UC-10; APP-001–APP-005.

### US-APP-04 — Restrict background execution for many apps at once

As a user, I want one screen to restrict or allow background execution for each app or many apps at once, so that I don't have to open every app's battery settings.

- Given Shizuku is connected, when I open Background permissions, then each app shows its actual background mode and, where supported, its standby bucket.
- Given I select several apps and choose Restrict, when the confirmation appears, then it lists the apps and the risks (late notifications, missed alarms, no sync) and requires my confirmation.
- Given I use bulk selection, when I select all, then protected apps and system/core apps are not included; a system app changed individually needs a separate confirmation showing its UAD removal level.
- Given Able applies the change, when it completes, then each app shows the read-back value and any rejected or reverted change is shown failed with the actual value.
- Given I made a bulk change, when I open the action log, then I can restore the previous values of that batch.
- Unsupported/error states: Shizuku missing → no change possible, route to Android's per-app battery settings; bucket unsupported → bucket option hidden; app uninstalled mid-batch → skipped and reported.

**Links:** UC-44; APP-007; NFR-REL-001, NFR-REL-002; ADR-036.

### US-MEM-01 — See memory use and stop an app when I choose

As a user, I want to see RAM use and running apps and stop selected ones myself, with honest explanations, so that I can deal with a misbehaving app without false promises.

- Given I open RAM e processi, when it loads, then I see total, available, and used memory with a note that Android manages memory and free RAM is not a goal.
- Given Shizuku is connected, when the list loads, then I see running apps/processes with their memory and the data source.
- Given I choose Force-stop or Kill, when the confirmation appears, then it states that notifications, alarms, and sync may stop and the app may restart and use more battery; after confirming, Able verifies and reports the result.
- Given I edit the block list, when I add apps, then protected apps, the launcher, the keyboard, Able, and core system processes cannot be added; Stop block list asks for confirmation and reports per app.
- Given any screen of this tool, when I read it, then there is no "boost", "clean RAM", or battery-saving claim.
- Given the app list, when I select apps, then each row shows the app icon and a vertically centered checkbox, and the bulk bar and result sheet look and work like Storage's per-app cache cleanup (revised 2026-11-23).
- Unsupported/error states: Shizuku missing → summary only with the reason; an app restarts right away → reported as restarted; nothing runs automatically from a profile, schedule, tile, or widget.

**Links:** UC-45; MEM-001, BAT-007; NFR-PWR-002; ADR-037.

### US-APP-02 — Archive removed apps and restore them

As a user, I want every app I disable or uninstall for my user to appear in an Able archive with a restore button, so that I can undo debloating mistakes.

- Given Shizuku is connected, when I disable or uninstall a package for my user and confirm, then Able reads back the state and adds it to the archive only after verified success.
- Given the package has a UAD removal level of Expert or Unsafe, when the confirmation appears, then the warning is prominent and requires a separate confirmation.
- Given a package is in the archive, when I choose Restore, then Able runs `cmd package install-existing` (system app), installs the backed-up APK(s) through Shizuku (user app), or runs `pm enable`, and confirms the restored state by readback.
- Given I uninstall a user (non-system) app, when the confirmation appears, then it shows the APK backup size, and Able uninstalls only after the backup is complete and verified.
- Given the APK backup fails, when Able reports it, then uninstall proceeds only if I explicitly confirm that Able will not be able to restore the app.
- Given a user app has no usable backup, when I choose Restore, then Able offers reinstall from the original store, explains that data was kept where Android retained it, and claims no success.
- Given backups exist, when I open the archive, then I see the space used per app and in total and can delete each backup.
- Given Android 15+ native archiving is available for a package, when I choose Archive, then Able uses it and explains that restore depends on the installer; where unavailable, the option is hidden.
- Given restore fails, when Able reports the result, then it shows the failure reason and recovery guidance and keeps the archive entry.
- Given Shizuku is missing, when I open the archive, then privileged restore is unavailable with a reason.

**Links:** UC-32; APP-004 (revised 2026-11-22, ADR-033), APP-005; NFR-REL-001, NFR-REL-002.

### US-APP-03 — Know what a package is before touching it

As a user, I want package descriptions, removal levels, and dependencies from the UAD-ng list with filters, so that I can decide safely what to disable.

- Given I open Apps, when I filter by state, user/system, UAD list, or removal level, then the list shows only matching packages and filters can be combined.
- Given I open a package detail, when it is in the snapshot, then Able shows the description, list, removal level, dependencies, needed-by packages, and links.
- Given a package is not in the snapshot, when I view it, then Able shows "no information", never "safe".
- Given I am offline, when I browse the knowledge base, then it works fully from the bundled snapshot.
- Given the snapshot asset is missing or corrupt, when I open Apps, then the list still works and knowledge-base fields are marked unavailable.

**Links:** UC-33; APP-006, ABOUT-001.

### US-STO-01 — Free storage after reviewing what will be removed

As a user, I want to filter and inspect large files (>100 MB default) and old downloads (>30 days default) and choose what to delete, so that cleanup does not remove data unexpectedly.

- Given I scan for large files, when the list loads, then Able filters files >100 MB by default and allows switching filter chips (50MB, 100MB, 500MB, 1GB) or entering a custom size.
- Given I scan for old downloads, when the list loads, then Able filters downloads older than 30 days by default with quick filters for 7, 30, 90, and 365 days.
- Given the large-file finder (revised 2026-11-23), when I combine type, size, age, location (all, Downloads, DCIM, or a folder I pick), and a name search, then only matching files are listed, every accessible matching file is found, and I can sort by size, date, or name.
- Given images or videos in the results, when they are listed, then they show thumbnails.
- Given no file matches, when the result is empty, then Able tells me which filters are active and lets me clear them.
- Given the file picker or delete confirmation is cancelled, when Able returns, then no unselected file is deleted.
- Given a file disappears before deletion, when Able refreshes, then it reports partial completion and continues safely.
- Given a file is inside another app's private data, when I scan storage, then Able does not claim it can clean that data.
- Unsupported/error states: invalid custom size rejected with a message; cancelled scan labelled partial; revoked folder access asks me to pick the folder again.

**Links:** UC-11; STO-001 (revised 2026-11-23), STO-002; ADR-019.

### US-STO-02 — See what fills my storage

As a user, I want a chart of used/free space, a category breakdown, and the largest folders and files, so that I know where my space goes.

- Given I open Storage, when the scan completes, then Able shows used/free space as a chart with a text equivalent.
- Given Android exposes a category, when the breakdown loads, then apps, images, video, audio, documents, downloads, and other are shown with sizes; unexposed categories are labelled unavailable.
- Given I open the largest items, when I select one to delete, then the STO-001 review-before-delete flow and Android confirmation apply.
- Given I cancel the scan, when results are shown, then they are labelled partial.

**Links:** UC-30; STO-003, STO-001; NFR-PERF-001.

### US-STO-03 — Clear all app caches with an honest result

As a user, I want to clear the caches of all apps in one action and see how much space was actually freed, so that I can recover space quickly.

- Given Shizuku is connected, when I confirm Clean all app caches, then Able trims caches and reports the measured before/after difference.
- Given Shizuku is not available, when I choose Clean all app caches, then Able explains why and opens Android's storage settings.
- Given the command fails or is rejected, when Able returns, then it reports failure and no freed amount.
- Given I clean Able's own cache, when it completes, then Able reports the freed space directly.
- Given Shizuku is connected, when I open the per-app cache list, select apps (each row with icon and centered checkbox), and choose Clear cache, then a confirmation lists the apps and total cache size, and the result shows the measured freed space per app with failed rows first, like the RAM tool's bulk action (revised 2026-11-23).

**Links:** UC-31; STO-002 (revised 2026-11-23); NFR-REL-001.

### US-ACT-01 — Open an activity or turn it into a shortcut

As a user, I want to search launchable activities and either open one or create a shortcut, so that the two actions remain clear.

- Given an activity is private, disabled, or not normally launchable, when I search, then Able does not expose it through a bypass.
- Given I choose Create shortcut, when the activity detail opens, then the shared Shortcut Maker editor is used.
- Given I choose Open and Android rejects the launch, when control returns, then Able reports failure without changing the activity configuration.

**Links:** UC-12; ACT-001, ACT-002, SHORT-001.

### US-SHORT-01 — Create and organize shortcuts

As a user, I want to customize shortcuts and sort them into collections, so that I can keep useful actions on my home screen and organized in Able. The Shortcut Maker editor, modelled on the referenced Shortcut Maker app, is in scope for v1 implementation.

- Given a supported target, when I create a shortcut, then I can preview and edit its label/icon before Android's pin flow.
- Given I create a collection, when I rename, reorder, delete, or move a shortcut, then Able preserves the resulting organization.
- Given I delete a collection containing shortcuts, when the confirmation dialog appears, then I can choose to move shortcuts to 'Unsorted' or delete them permanently; cancelling makes no changes.
- Given the launcher cancels pinning, when Able returns, then it reports cancellation and does not claim a home-screen icon exists.

**Links:** UC-13; SHORT-001–SHORT-003.

### US-CFG-01 — Choose what configuration to export

As a user, I want to choose which sections go into each backup file, so that I control what I transfer.

- Given I begin an export, when the section chooser appears, then I can independently select battery profiles, app rules, Home/appearance, shortcut definitions/collections, custom icons, and GPS points (revised 2026-11-23).
- Given I export, when privacy exclusions appear, then usage history, permission grants, Shizuku state, app data, and hotspot password are excluded, and battery history is excluded unless I select it.
- Given the file is saved, when I inspect its result, then Able reports location/name and does not upload it to a cloud account.

**Links:** UC-14; CFG-001–CFG-003 (revised 2026-11-23).

### US-CFG-03 — Move my battery history to a new phone when I choose

As a user changing phones, I want to optionally include my battery history and measurements in a backup, so that my statistics and baseline continue on the new device.

- Given I begin an export, when the section chooser appears, then the battery history section is unselected; when I select it, then Able shows a privacy notice before continuing.
- Given I start another export later, when the chooser appears, then battery history is unselected again.
- Given I import a file with battery history, when the preview appears, then it shows how many sessions are new, how many duplicates will be skipped, and warns if sessions are older than my retention period and will be deleted.
- Given I import the same file twice, when the second import completes, then no session is duplicated.
- Given an old backup (schema 1), when I import it, then Able accepts it and shows how profiles were mapped.
- Unsupported/error states: invalid sessions (bad times, ranges, overlaps) are rejected and listed; a schema newer than 2 is refused with an "update Able" message and no changes.

**Links:** UC-14, UC-15; CFG-001–CFG-004 (revised 2026-11-23), MON-009; NFR-PRIV-002, NFR-SEC-001; ADR-046.

### US-CFG-02 — Preview and safely import a configuration

As a user, I want to validate and preview a backup before applying it, so that I can transfer settings without unexpected overwrites.

- Given a file is malformed or unsupported, when I select it, then Able makes no configuration changes; files with schema 1 or 2 are accepted (revised 2026-11-23).
- Given a valid file, when the import preview appears, then neither Merge nor Replace is preselected.
- Given conflicting settings, when I select Merge, then Able asks which conflicting values to keep.
- Given I select Replace, when I confirm, then Able first saves a rollback snapshot and tells me what will be overwritten.
- Given apps/activities are missing on this device, when import completes, then Able marks those items unresolved and does not install or disable anything.

**Links:** UC-15; CFG-004–CFG-007.
