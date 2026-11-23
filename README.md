<p align="center">
  <img src="docs/assets/able-icon.svg" width="128" height="128" alt="Able logo">
</p>

<h1 align="center">able</h1>

<p align="center">
  <b>One calm, honest toolbox for your Android phone.</b><br>
  Battery, apps, storage and everyday device tools — without the bloat, the fake promises or the bulk permission requests.
</p>

<p align="center">
  <a href="https://github.com/dragolab/able/releases"><img alt="Release" src="https://img.shields.io/github/v/release/dragolab/able?include_prereleases&label=release&color=1FC98E"></a>
  <img alt="Android 13+" src="https://img.shields.io/badge/Android-13%2B-101418">
  <a href="LICENSE"><img alt="License GPLv3" src="https://img.shields.io/badge/license-GPLv3-101418"></a>
</p>

---

## Why Able

- **Too many single-purpose apps.** Torch, battery monitor, app manager, storage cleaner, hotspot settings, shortcut maker, GPS tools… Able puts them in one place with one consistent design.
- **Battery tools that overpromise.** Able shows what Android actually reports, labels estimates as estimates, and never claims savings it can't measure.
- **System settings buried five menus deep.** Quick Settings tiles, widgets, gestures and volume-key shortcuts get you there in one tap.
- **Power tools that feel risky.** Advanced actions are optional (Shizuku), explained with a "?" next to them, confirmed before running, verified afterwards and reversible where Android allows it.
- **Privacy.** Everything stays on the device. No account, no cloud, no analytics. Crash reports are opt-in and sent only if you choose to email them.

## Features

**Home** · a modular dashboard: battery and active profiles, quick actions, access status, storage, favourite shortcuts and recent actions — reorder or hide any card.

**Battery** · custom battery profiles you create from scratch (several can be active together; nothing triggers until you enable it), live monitor with screen-on/off drain, charging diary, battery health (cycles, capacity, dates), diagnostics checklist.

**Apps** · every installed package with descriptions and safety levels (Universal Android Debloater list), filters, disable / uninstall with an archive and one-tap restore, background-activity restrictions, force stop, per-profile rules.

**Tools**
- Torch with brightness, SOS and Morse · Hotspot control, QR and connected devices
- Storage overview, system cache cleanup and a large-file finder · RAM and running apps
- Double tap to turn the screen off (home screen, lock screen, widget) · App lock with the system fingerprint/face/PIN prompt
- Volume-key shortcuts · System toggles (NFC, auto-brightness, USB/wireless debugging, Private DNS)
- Shortcut Maker · Activity Launcher · Command shell
- GPS points catalog · Fake GPS on a map · Spirit level · Screen ruler · Compass

**Everywhere** · custom Quick Settings tiles and widgets, app-wide search, in-app updates from GitHub, Material 3 Expressive design with dynamic colors, haptics, Italian and English, phone / tablet / Android TV layouts, local `.ablebackup` export and import.

> Some features need [Shizuku](https://shizuku.rikka.app/) or an Accessibility service. Able asks only when you open the feature that needs it, and tells you honestly when a device or Android version doesn't support something.

## Download

Get the latest APK from **[Releases](https://github.com/dragolab/able/releases)**. Able is in **beta** (current: 0.9.1) — expect rough edges and please report issues. From 0.9, Able can update itself from Settings → Updates.

---

<details>
<summary><b>For developers</b></summary>

### Build

Requirements: Android Studio with JDK 21, Android SDK platform 37.1.

```bash
./gradlew :app:assembleDebug        # APK in app/build/outputs/apk/debug/
./gradlew testDebugUnitTest         # JVM unit tests
```

### Stack

Kotlin, Jetpack Compose, Material 3 Expressive, Navigation Compose, Room, DataStore, Glance widgets, Compose for TV · AGP 9, Gradle 9.6 · minSdk 33, targetSdk 35.

### Modules

| Module | Role |
| --- | --- |
| `:app` | Application, adaptive shell and navigation, Accessibility service, tiles, widgets, search |
| `:core:model` / `:core:common` | Pure Kotlin models and utilities |
| `:core:database` | Room database and preferences |
| `:core:capability` | Hardware, permission, Shizuku and system-settings adapters |
| `:core:data` | Dependency container and shared repositories |
| `:core:designsystem` | Theme, brand and shared Compose components |
| `:feature:home`, `:battery`, `:apps`, `:tools`, `:settings` | Feature screens |

### Documentation

Product and engineering docs live in [`docs/`](docs): start from the [product specification](docs/product/ABLE_PRODUCT_SPECIFICATION.md), the [code structure](docs/architecture/code-structure.md) and the [decision log](docs/architecture/decisions.md). Contributors should read [`AGENTS.md`](AGENTS.md) first: requirement IDs are stable and every user-visible change updates the spec, stories, use cases and traceability together.

</details>

## License

Source code: [GNU GPL v3](LICENSE). The **Able** and **Drago Labs** names, logo and brand assets are reserved and not covered by the GPL — see [`NOTICE.md`](NOTICE.md).

Made by [Drago Labs](https://dragolabs.it).
