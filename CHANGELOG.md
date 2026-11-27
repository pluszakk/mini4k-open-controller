# Changelog

## 0.1.0 - 2026-11-27

### Added
- `duml` library: CRC-8/CRC-16, packet decoder and builder, stream parser that tolerates noise and split reads. Tests use a published real packet.
- "Mini4K USB Sniffer" Android app (read only): detects the controller as a USB accessory or USB device, records raw bytes to a text file, shows live DUML statistics and a plain-language verdict, shares the capture file.
- GitHub Actions: runs tests, builds the APK and publishes it as the `latest` release on `main`.
- Test instructions in Polish.
