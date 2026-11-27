package org.mini4k.duml

/**
 * Finds DUML packets in an arbitrary byte stream (for example raw USB reads).
 *
 * The parser never assumes the stream is DUML. It reports how many bytes it had to skip,
 * so a capture that is encrypted or uses another protocol shows up as "mostly skipped bytes"
 * instead of as false packets. A packet is accepted only when both the header CRC-8 and the
 * whole-packet CRC-16 are correct.
 *
 * Not thread safe: use one parser per USB endpoint / stream.
 */
class DumlStreamParser(private val onPacket: (DumlPacket) -> Unit = {}) {
    private var buf = ByteArray(4096)
    private var len = 0

    var packets: Long = 0; private set
    var bytesIn: Long = 0; private set
    var bytesSkipped: Long = 0; private set
    var headerCrcFailures: Long = 0; private set
    var bodyCrcFailures: Long = 0; private set
    var encryptedPackets: Long = 0; private set

    fun feed(data: ByteArray, offset: Int = 0, length: Int = data.size - offset) {
        bytesIn += length
        ensureCapacity(len + length)
        data.copyInto(buf, len, offset, offset + length)
        len += length
        drain()
    }

    private fun drain() {
        var pos = 0
        while (true) {
            // Find the next start-of-frame byte.
            val start = indexOfSof(pos)
            if (start < 0) {
                bytesSkipped += (len - pos)
                pos = len
                break
            }
            bytesSkipped += (start - pos)
            pos = start
            if (len - pos < 4) break // wait for the header

            if (DumlCrc.crc8(buf, pos, 3) != (buf[pos + 3].toInt() and 0xFF)) {
                headerCrcFailures++
                bytesSkipped++
                pos++
                continue
            }
            val pktLen = ((buf[pos + 1].toInt() and 0xFF) or ((buf[pos + 2].toInt() and 0xFF) shl 8)) and 0x3FF
            if (pktLen < DumlPacket.MIN_LENGTH) {
                headerCrcFailures++
                bytesSkipped++
                pos++
                continue
            }
            if (len - pos < pktLen) break // wait for the rest of the packet

            val expected = (buf[pos + pktLen - 2].toInt() and 0xFF) or ((buf[pos + pktLen - 1].toInt() and 0xFF) shl 8)
            if (DumlCrc.crc16(buf, pos, pktLen - 2) != expected) {
                bodyCrcFailures++
                bytesSkipped++
                pos++
                continue
            }
            val packet = DumlPacket(buf.copyOfRange(pos, pos + pktLen))
            packets++
            if (packet.encryptType != 0) encryptedPackets++
            onPacket(packet)
            pos += pktLen
        }
        // Compact the buffer.
        if (pos > 0) {
            buf.copyInto(buf, 0, pos, len)
            len -= pos
        }
    }

    private fun indexOfSof(from: Int): Int {
        for (i in from until len) if ((buf[i].toInt() and 0xFF) == DumlPacket.SOF) return i
        return -1
    }

    private fun ensureCapacity(n: Int) {
        if (n > buf.size) buf = buf.copyOf(maxOf(n, buf.size * 2))
    }

    /** Share of incoming bytes that belonged to valid DUML packets, 0.0 to 1.0. */
    val dumlRatio: Double
        get() = if (bytesIn == 0L) 0.0 else (bytesIn - bytesSkipped - len).coerceAtLeast(0).toDouble() / bytesIn

    fun stats(): String =
        "bytes=$bytesIn packets=$packets skipped=$bytesSkipped hdrCrcFail=$headerCrcFailures " +
            "bodyCrcFail=$bodyCrcFailures encrypted=$encryptedPackets duml=${"%.1f".format(dumlRatio * 100)}%"
}
