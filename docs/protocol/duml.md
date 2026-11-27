# DUML v1 packet format

Status legend: **confirmed** = verified against a real packet; **documented** = taken from a public source, not yet seen on Mini 4K; **unknown** = still to be found.

## Frame

| Offset | Size | Field | Status |
| --- | --- | --- | --- |
| 0 | 1 | Start of frame, `0x55` | documented |
| 1 | 2 | Bits 0-9 whole packet length, bits 10-15 protocol version (LE) | documented |
| 3 | 1 | CRC-8 over bytes 0-2 | confirmed on NDSS example |
| 4 | 1 | Sender: bits 0-4 module type, bits 5-7 index | documented |
| 5 | 1 | Receiver: same encoding | documented |
| 6 | 2 | Sequence number (LE) | documented |
| 8 | 1 | Bit 7 response flag, bits 5-6 ack type, bits 0-2 encryption type | documented |
| 9 | 1 | Command set | documented |
| 10 | 1 | Command id | documented |
| 11 | n | Payload | documented |
| len-2 | 2 | CRC-16 over bytes 0 to len-3 (LE) | confirmed on NDSS example |

## Checksums

- CRC-8: reflected polynomial 0x8C, seed 0x77.
- CRC-16: reflected polynomial 0x8408, seed 0x3692.

Both are checked in `duml/src/test` against the example packet from Figure 2 of the NDSS 2023 paper:
`55 11 04 92 0a 03 40 61 40 03 df 01 00 00 00 18 bb` (PC -> FlyController, set 0x03, id 0xDF).

## Mini 4K / RC-N1C specifics

| Question | Status |
| --- | --- |
| Does the RC-N1C send DUML over the phone cable (accessory mode)? | unknown, test A |
| Does the RC-N1C expose a DUML bulk interface on its charging port? | documented for RC-N1 (model RM330, "mtp_bulk_adb") by dji-firmware-tools, test B |
| Are packets encrypted (encryption type field or the whole stream)? | unknown |
| Telemetry command ids for Mini 4K | unknown |

Sources: [o-gs/dji-firmware-tools](https://github.com/o-gs/dji-firmware-tools), [NDSS 2023 paper](https://www.ndss-symposium.org/wp-content/uploads/2023/02/ndss2023_f217_paper.pdf).
