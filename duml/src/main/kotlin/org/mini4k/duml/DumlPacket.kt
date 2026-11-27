package org.mini4k.duml

/**
 * One decoded DUML v1 packet.
 *
 * Layout (little endian), as documented by o-gs/dji-firmware-tools and the NDSS 2023 paper:
 *
 * | offset | size | field                                                        |
 * |--------|------|--------------------------------------------------------------|
 * | 0      | 1    | start of frame, always 0x55                                  |
 * | 1      | 2    | bits 0-9: whole packet length, bits 10-15: protocol version  |
 * | 3      | 1    | CRC-8 of bytes 0..2                                          |
 * | 4      | 1    | sender: bits 0-4 module type, bits 5-7 module index          |
 * | 5      | 1    | receiver: same encoding as sender                            |
 * | 6      | 2    | sequence number                                              |
 * | 8      | 1    | bit 7 packet type (0 request, 1 response), bits 5-6 ack type,|
 * |        |      | bits 0-2 encryption type                                     |
 * | 9      | 1    | command set                                                  |
 * | 10     | 1    | command id                                                   |
 * | 11     | n    | payload                                                      |
 * | len-2  | 2    | CRC-16 of bytes 0..len-3                                     |
 */
class DumlPacket(val raw: ByteArray) {
    init {
        require(raw.size >= MIN_LENGTH) { "DUML packet too short: ${raw.size}" }
    }

    val length: Int get() = u16(1) and 0x3FF
    val version: Int get() = u16(1) ushr 10
    val senderType: Int get() = u8(4) and 0x1F
    val senderIndex: Int get() = u8(4) ushr 5
    val receiverType: Int get() = u8(5) and 0x1F
    val receiverIndex: Int get() = u8(5) ushr 5
    val sequence: Int get() = u16(6)
    val isResponse: Boolean get() = (u8(8) ushr 7) == 1
    val ackType: Int get() = (u8(8) ushr 5) and 0x3
    val encryptType: Int get() = u8(8) and 0x7
    val cmdSet: Int get() = u8(9)
    val cmdId: Int get() = u8(10)
    val payload: ByteArray get() = raw.copyOfRange(HEADER_LENGTH, raw.size - 2)

    val sender: String get() = "${DeviceType.nameOf(senderType)}[$senderIndex]"
    val receiver: String get() = "${DeviceType.nameOf(receiverType)}[$receiverIndex]"

    fun summary(): String = buildString {
        append(sender).append(" -> ").append(receiver)
        append(" seq=").append(sequence)
        append(if (isResponse) " RSP" else " REQ")
        append(" set=0x").append(hex2(cmdSet))
        append(" id=0x").append(hex2(cmdId))
        if (encryptType != 0) append(" enc=").append(EncryptType.nameOf(encryptType))
        append(" payload=").append(raw.size - MIN_LENGTH).append("B")
    }

    private fun u8(i: Int): Int = raw[i].toInt() and 0xFF
    private fun u16(i: Int): Int = u8(i) or (u8(i + 1) shl 8)

    companion object {
        const val SOF: Int = 0x55
        const val HEADER_LENGTH: Int = 11
        const val MIN_LENGTH: Int = HEADER_LENGTH + 2
        const val MAX_LENGTH: Int = 0x3FF

        /**
         * Builds a packet with valid checksums. Used by tests and the protocol analysis
         * tooling only. The Android app never sends packets to the controller.
         */
        fun build(
            senderType: Int,
            senderIndex: Int,
            receiverType: Int,
            receiverIndex: Int,
            sequence: Int,
            isResponse: Boolean,
            ackType: Int,
            encryptType: Int,
            cmdSet: Int,
            cmdId: Int,
            payload: ByteArray = ByteArray(0),
            version: Int = 1,
        ): DumlPacket {
            val len = MIN_LENGTH + payload.size
            require(len <= MAX_LENGTH) { "payload too long" }
            val b = ByteArray(len)
            val verLen = (len and 0x3FF) or ((version and 0x3F) shl 10)
            b[0] = SOF.toByte()
            b[1] = verLen.toByte()
            b[2] = (verLen ushr 8).toByte()
            b[3] = DumlCrc.crc8(b, 0, 3).toByte()
            b[4] = ((senderType and 0x1F) or ((senderIndex and 0x7) shl 5)).toByte()
            b[5] = ((receiverType and 0x1F) or ((receiverIndex and 0x7) shl 5)).toByte()
            b[6] = sequence.toByte()
            b[7] = (sequence ushr 8).toByte()
            b[8] = (((if (isResponse) 1 else 0) shl 7) or ((ackType and 0x3) shl 5) or (encryptType and 0x7)).toByte()
            b[9] = cmdSet.toByte()
            b[10] = cmdId.toByte()
            payload.copyInto(b, HEADER_LENGTH)
            val crc = DumlCrc.crc16(b, 0, len - 2)
            b[len - 2] = crc.toByte()
            b[len - 1] = (crc ushr 8).toByte()
            return DumlPacket(b)
        }
    }
}

/** Module types seen in the sender/receiver fields (names from o-gs/dji-firmware-tools). */
object DeviceType {
    private val names = mapOf(
        0 to "Any", 1 to "Camera", 2 to "MobileApp", 3 to "FlyController", 4 to "Gimbal",
        5 to "CenterBoard", 6 to "RemoteRadio", 7 to "Wifi", 8 to "LbDm3xxSky", 9 to "LbMcuSky",
        10 to "PC", 11 to "Battery", 12 to "ESC", 13 to "Dm368Ground", 14 to "OfdmGround",
        15 to "Lb68013Sky", 16 to "Ser68013Ground", 17 to "MVO", 18 to "SVO", 19 to "LbFpgaSky",
        20 to "FpgaGround", 21 to "FpgaSim", 22 to "Station", 23 to "XU", 24 to "WTF", 25 to "IMU",
        26 to "GPS", 27 to "WifiGround", 28 to "SigCvt", 29 to "PMU", 30 to "Unknown30", 31 to "Wm330OrWm220",
    )

    fun nameOf(type: Int): String = names[type] ?: "Type$type"
}

object EncryptType {
    private val names = mapOf(0 to "none", 1 to "AES128", 2 to "SelfDef", 3 to "XOR", 4 to "DES56")
    fun nameOf(type: Int): String = names[type] ?: "enc$type"
}

internal fun hex2(v: Int): String = (v and 0xFF).toString(16).padStart(2, '0')

fun ByteArray.toHex(offset: Int = 0, length: Int = size - offset): String {
    val sb = StringBuilder(length * 3)
    for (i in offset until offset + length) {
        if (i > offset) sb.append(' ')
        sb.append(hex2(this[i].toInt()))
    }
    return sb.toString()
}

fun hexToBytes(hex: String): ByteArray {
    val clean = hex.filter { !it.isWhitespace() }
    require(clean.length % 2 == 0) { "odd hex length" }
    return ByteArray(clean.length / 2) { clean.substring(it * 2, it * 2 + 2).toInt(16).toByte() }
}
