package org.mini4k.duml

/**
 * Checksums used by DJI DUML (v1) packets.
 *
 * Parameters (polynomial, reflection and seed values) are taken from the public
 * description of the format in o-gs/dji-firmware-tools (comm_dat2pcap.py) and the
 * NDSS 2023 paper "Drone Security and the Mysterious Case of DJI's DroneID".
 * The implementation below is our own bit-wise version, not a copy of those tables.
 *
 * - Header CRC-8: reflected polynomial 0x8C (Dallas/Maxim 1-Wire), seed 0x77,
 *   computed over the first 3 bytes of the packet.
 * - Packet CRC-16: reflected polynomial 0x8408 (CCITT), seed 0x3692,
 *   computed over every byte except the last two.
 */
object DumlCrc {
    const val CRC8_SEED: Int = 0x77
    const val CRC16_SEED: Int = 0x3692

    fun crc8(data: ByteArray, offset: Int = 0, length: Int = data.size - offset, seed: Int = CRC8_SEED): Int {
        var crc = seed and 0xFF
        for (i in offset until offset + length) {
            crc = crc xor (data[i].toInt() and 0xFF)
            repeat(8) {
                crc = if (crc and 1 != 0) (crc ushr 1) xor 0x8C else crc ushr 1
            }
        }
        return crc and 0xFF
    }

    fun crc16(data: ByteArray, offset: Int = 0, length: Int = data.size - offset, seed: Int = CRC16_SEED): Int {
        var crc = seed and 0xFFFF
        for (i in offset until offset + length) {
            crc = crc xor (data[i].toInt() and 0xFF)
            repeat(8) {
                crc = if (crc and 1 != 0) (crc ushr 1) xor 0x8408 else crc ushr 1
            }
        }
        return crc and 0xFFFF
    }
}
