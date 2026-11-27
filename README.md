# Mini 4K Open Controller

Research project toward an independent, open Android app for the DJI Mini 4K, without the DJI Mobile SDK.

**Status: phase 2, protocol analysis.** Nothing here can fly a drone yet. The current app only listens.

## What is in this repository

| Module | What it does |
| --- | --- |
| [`duml/`](duml/) | Pure Kotlin library that finds and decodes DJI DUML v1 packets in a byte stream. No Android dependency, unit tested. |
| [`app/`](app/) | "Mini4K USB Sniffer": an Android app that passively records what the RC-N1C remote controller sends over USB and checks whether it is readable DUML. |

## Safety rules

- The sniffer app never writes to USB. It has no code path that can send a command.
- Flight commands will only ever be added after the matching telemetry and commands are confirmed from recorded traces, and they will start in simulation mode.
- We never bypass DJI safety systems (geofencing, altitude limits, obstacle avoidance, RTH).

## Building

- Protocol library and tests, no Android SDK needed: `SKIP_ANDROID=1 ./gradlew :duml:test`
- Android app: `./gradlew :app:assembleDebug` (needs the Android SDK).
- GitHub Actions builds both on every push. On `main`, the APK is published as the [`latest` release](../../releases/tag/latest).

## Documentation

- [Test instructions (Polish)](docs/INSTRUKCJA_TESTU.md)
- [DUML packet format](docs/protocol/duml.md)
- [Changelog](CHANGELOG.md)

## Sources

- [o-gs/dji-firmware-tools](https://github.com/o-gs/dji-firmware-tools): DUML tooling, CRC parameters, USB bulk interface discovery for DJI controllers.
- Schiller et al., [Drone Security and the Mysterious Case of DJI's DroneID](https://www.ndss-symposium.org/wp-content/uploads/2023/02/ndss2023_f217_paper.pdf), NDSS 2023: DUML structure and an example packet used in our tests.
