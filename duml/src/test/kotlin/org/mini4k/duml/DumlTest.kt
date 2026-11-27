package org.mini4k.duml

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DumlTest {
    /**
     * Example packet printed in Figure 2 of the NDSS 2023 paper
     * "Drone Security and the Mysterious Case of DJI's DroneID" (Schiller et al.).
     * It is a real packet with valid checksums, so it checks both CRC algorithms.
     */
    private val ndssExample = hexToBytes("55110492 0a034061 4003df 01000000 18bb")

    @Test
    fun headerCrcMatchesPublishedPacket() {
        assertEquals(0x92, DumlCrc.crc8(ndssExample, 0, 3))
    }

    @Test
    fun bodyCrcMatchesPublishedPacket() {
        val expected = (ndssExample[15].toInt() and 0xFF) or ((ndssExample[16].toInt() and 0xFF) shl 8)
        assertEquals(expected, DumlCrc.crc16(ndssExample, 0, ndssExample.size - 2))
    }

    @Test
    fun decodesPublishedPacketFields() {
        val p = DumlPacket(ndssExample)
        assertEquals(17, p.length)
        assertEquals(1, p.version)
        assertEquals(10, p.senderType) // PC
        assertEquals(0, p.senderIndex)
        assertEquals(3, p.receiverType) // FlyController
        assertEquals(0x6140, p.sequence)
        assertFalse(p.isResponse)
        assertEquals(2, p.ackType)
        assertEquals(0, p.encryptType)
        assertEquals(0x03, p.cmdSet)
        assertEquals(0xDF, p.cmdId)
        assertEquals("01 00 00 00", p.payload.toHex())
    }

    @Test
    fun buildProducesPublishedPacket() {
        val built = DumlPacket.build(
            senderType = 10, senderIndex = 0, receiverType = 3, receiverIndex = 0,
            sequence = 0x6140, isResponse = false, ackType = 2, encryptType = 0,
            cmdSet = 0x03, cmdId = 0xDF, payload = hexToBytes("01000000"),
        )
        assertEquals(ndssExample.toHex(), built.raw.toHex())
    }

    @Test
    fun parserFindsPacketsInNoisyStreamSplitAcrossReads() {
        val found = mutableListOf<DumlPacket>()
        val parser = DumlStreamParser { found += it }
        val p2 = DumlPacket.build(6, 0, 2, 0, 7, true, 0, 0, 0x06, 0x01, hexToBytes("aabbcc"))
        val stream = hexToBytes("00ff55aa") + ndssExample + hexToBytes("1234") + p2.raw + hexToBytes("55")
        // Feed one byte at a time, the worst case for reassembly.
        for (b in stream) parser.feed(byteArrayOf(b))
        assertEquals(2, found.size)
        assertEquals(0xDF, found[0].cmdId)
        assertEquals(0x01, found[1].cmdId)
        assertTrue(found[1].isResponse)
        assertEquals(2, parser.packets)
        assertEquals(0, parser.encryptedPackets)
    }

    @Test
    fun parserRejectsCorruptedPacket() {
        val bad = ndssExample.copyOf()
        bad[12] = 0x7F // flip a payload byte, CRC-16 no longer matches
        val parser = DumlStreamParser()
        parser.feed(bad)
        assertEquals(0, parser.packets)
        assertTrue(parser.bodyCrcFailures >= 1)
    }

    @Test
    fun randomNoiseProducesNoPackets() {
        val rnd = Random(42)
        val parser = DumlStreamParser()
        parser.feed(ByteArray(200_000) { rnd.nextInt(256).toByte() })
        // A random 4-byte header passes CRC-8 about 1 in 256 times and then also needs a
        // matching CRC-16, so random (or encrypted) data almost never yields packets.
        assertTrue(parser.packets <= 1, "packets=${parser.packets}")
        assertTrue(parser.dumlRatio < 0.01)
    }

    @Test
    fun countsEncryptedFlag() {
        val parser = DumlStreamParser()
        parser.feed(DumlPacket.build(2, 0, 3, 0, 1, false, 0, 1, 0x03, 0x01).raw)
        assertEquals(1, parser.encryptedPackets)
    }
}
