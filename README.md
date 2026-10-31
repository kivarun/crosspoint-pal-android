# CrossPoint Pal Android

CrossPoint Pal Android is the Android companion application for
[CrossPoint Pal](https://github.com/kivarun/crosspoint-pal), an e-ink
companion device. The firmware repository owns device-side behavior; this
repository owns the Android application: its lifecycle, permissions, and user
experience. See [MANIFESTO.md](MANIFESTO.md) for the product boundary and
[AGENTS.md](AGENTS.md) for how agents work in this repository.

## Current status: Phase 1 (BLE companion v1)

The repository contains a working application skeleton with the first real
companion transport: a BLE GATT client for the CrossPoint Pal reader,
protected pairing, bounded text sending, and physical-button events.

What exists in Phase 1:

- BLE scan for the companion service UUID, device selection, and the system
  pairing dialog (six-digit passkey, bond + MITM + Secure Connections; no
  unencrypted fallback);
- one connection owner (`CompanionConnectionManager`) with the protocol
  sequencing from the firmware contract: MTU 517 negotiation, service
  discovery, Event-characteristic subscription, `hello`/`hello_ack`
  handshake, acknowledged `content` sends, and `button`/`error` handling;
- Home: scan, connect, live connection phases, real disconnect/reconnect,
  and the latest device button event;
- Send to Pal: title/body drafts bounded by the protocol byte limits,
  acknowledged sends (success only after `content_ack` for the exact
  contentId), in-flight and error states;
- Settings: live connection and pairing summaries, disconnect, and a
  Forget-device action that clears the app identity and removes the system
  bond where the platform permits it;
- notifications screen: still the Phase 0 demo; no Android notification
  access is involved.

Explicitly **not implemented** yet:

- Android Notification Access and background notification mirroring;
- background execution: the BLE session is user-initiated and
  foreground-scoped; there is no reconnect service or scan loop;
- Wi-Fi transport and BLE wake from deep sleep;
- multiple simultaneously connected devices.

Real BLE pairing, reconnect, and button-event behavior require a physical
X4 Classic and are verified by hardware UAT, not by host tests.

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
