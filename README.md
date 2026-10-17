# CrossPoint Pal Android

CrossPoint Pal Android is the Android companion application for
[CrossPoint Pal](https://github.com/kivarun/crosspoint-pal), an e-ink
companion device. The firmware repository owns device-side behavior; this
repository owns the Android application: its lifecycle, permissions, and user
experience. See [MANIFESTO.md](MANIFESTO.md) for the product boundary and
[AGENTS.md](AGENTS.md) for how agents work in this repository.

## Current status: Phase 0 (UI skeleton)

The repository contains a working application skeleton: four root screens,
navigation, a light/dark "e-ink paper" design system, and demo state. All
device-facing behavior is intentionally simulated by an in-memory demo state
holder.

What exists in Phase 0:

- Home / device screen with a device card, status indicators, and primary
  actions, including a no-device state;
- Send to Pal screen with bounded title/body fields, character counts, an
  approximate e-ink preview, and a demo send action;
- Notifications screen with a mirroring master switch, demo app sources, and
  a demo sync status (no Android notification access is involved);
- Settings screen with a working theme preference (System / Light / Dark,
  persisted locally), connection and pairing summaries, privacy pointer,
  about row, and a confirmable demo "Forget device" action;
- bottom navigation across the four root screens with state preservation;
- light and dark themes built on a warm paper / charcoal palette;
- unit tests and Robolectric-hosted Compose UI tests.

Explicitly **not implemented** in this phase:

- Bluetooth Low Energy: no scanning, no GATT, no pairing (demo state only);
- Android Notification Access: not requested, no `NotificationListenerService`;
- background sync: no background workers or services;
- Wi-Fi: no Wi-Fi permissions or transfer.

## Building

Requirements: JDK 21, Android SDK with platform 37.0.

```sh
./gradlew assembleDebug   # debug build
./gradlew test            # unit tests, including Robolectric UI tests
./gradlew lint            # Android lint
```

Robolectric UI tests require a glibc-based JDK environment. On musl-based
hosts (for example Alpine), run them in a glibc container.

Continuous integration runs the same checks on GitHub Actions without any
secrets; see [.github/workflows/ci.yml](.github/workflows/ci.yml).

## License

This project is licensed under the GNU Affero General Public License v3 as
published by the Free Software Foundation; see [LICENSE](LICENSE).
