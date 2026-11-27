package org.mini4k.sniffer

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbAccessory
import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbEndpoint
import android.hardware.usb.UsbInterface
import android.hardware.usb.UsbManager
import android.os.Build
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import androidx.core.content.ContextCompat
import org.mini4k.duml.DumlPacket
import org.mini4k.duml.DumlStreamParser
import org.mini4k.duml.toHex
import java.io.BufferedWriter
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Passive USB listener for the DJI remote controller.
 *
 * SAFETY: this class only ever READS from the controller. It never calls bulkTransfer on an
 * OUT endpoint, never opens an output stream on the accessory and never builds DUML packets.
 * Nothing it does can send a command to the controller or the drone.
 */
class Sniffer(context: Context) {
    private val app = context.applicationContext
    private val usb: UsbManager = app.getSystemService(UsbManager::class.java)
    private val lock = Any()

    // Everything below is guarded by `lock`.
    private var running = false
    private var status = "Gotowy. Podłącz kontroler i naciśnij Start."
    private val sources = LinkedHashMap<String, Source>()
    private val recent = ArrayDeque<String>()
    private var captureFile: File? = null
    private var writer: BufferedWriter? = null
    private var bytesWritten = 0L
    private var fileLimitHit = false
    private var startedAt = 0L
    private val workers = mutableListOf<Thread>()
    private var connection: UsbDeviceConnection? = null
    private val claimed = mutableListOf<UsbInterface>()
    private var accessoryFd: ParcelFileDescriptor? = null

    private class Source(val name: String) {
        val commands = HashMap<String, Int>()
        lateinit var parser: DumlStreamParser
    }

    data class SourceView(
        val name: String,
        val bytes: Long,
        val packets: Long,
        val encrypted: Long,
        val skipped: Long,
        val dumlPercent: Double,
        val topCommands: List<Pair<String, Int>>,
    )

    data class Snapshot(
        val running: Boolean,
        val status: String,
        val seconds: Long,
        val sources: List<SourceView>,
        val recent: List<String>,
        val file: File?,
    )

    private val permissionReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val granted = intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)
            if (granted) start() else setStatus("Brak zgody na dostęp do USB. Spróbuj jeszcze raz.")
        }
    }

    private val detachReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (isRunning()) {
                stop()
                setStatus("Kontroler odłączony. Nagranie zatrzymane i zapisane.")
            }
        }
    }

    init {
        ContextCompat.registerReceiver(
            app, permissionReceiver, IntentFilter(ACTION_PERMISSION), ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        val detach = IntentFilter().apply {
            addAction(UsbManager.ACTION_USB_DEVICE_DETACHED)
            addAction(UsbManager.ACTION_USB_ACCESSORY_DETACHED)
        }
        ContextCompat.registerReceiver(app, detachReceiver, detach, ContextCompat.RECEIVER_EXPORTED)
    }

    fun isRunning(): Boolean = synchronized(lock) { running }

    private fun setStatus(s: String) = synchronized(lock) { status = s }

    /** Descriptor dump of everything currently plugged in. Needs no permission. */
    fun describeAll(): String = buildString {
        val accessories = usb.accessoryList.orEmpty()
        val devices = usb.deviceList.values
        if (accessories.isEmpty() && devices.isEmpty()) {
            append("Nic nie wykryto na USB.\n")
            append("Sprawdź kabel i czy kontroler jest włączony.")
        }
        accessories.forEach { append(UsbDescriber.describeAccessory(it)) }
        devices.forEach { append(UsbDescriber.describeDevice(it)) }
    }

    /** Starts listening to the first DJI accessory or device, asking for permission if needed. */
    fun start() {
        if (isRunning()) return
        val accessory = usb.accessoryList?.firstOrNull()
        if (accessory != null) {
            if (!usb.hasPermission(accessory)) {
                setStatus("Czekam na zgodę na dostęp do kontrolera (akcesorium)...")
                usb.requestPermission(accessory, permissionIntent())
                return
            }
            startAccessory(accessory)
            return
        }
        val devices = usb.deviceList.values
        val device = devices.firstOrNull { it.vendorId == DJI_VENDOR_ID } ?: devices.firstOrNull()
        if (device == null) {
            setStatus("Nie wykryto kontrolera na USB.")
            return
        }
        if (!usb.hasPermission(device)) {
            setStatus("Czekam na zgodę na dostęp do urządzenia USB...")
            usb.requestPermission(device, permissionIntent())
            return
        }
        startDevice(device)
    }

    private fun permissionIntent(): PendingIntent {
        val intent = Intent(ACTION_PERMISSION).setPackage(app.packageName)
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0
        return PendingIntent.getBroadcast(app, 0, intent, flags)
    }

    private fun startAccessory(accessory: UsbAccessory) {
        val fd = usb.openAccessory(accessory)
        if (fd == null) {
            setStatus("Nie udało się otworzyć akcesorium. Czy DJI Fly jest zamknięte?")
            return
        }
        beginCapture("accessory", UsbDescriber.describeAccessory(accessory))
        synchronized(lock) { accessoryFd = fd }
        // Input stream only. We deliberately never create an output stream.
        val input = FileInputStream(fd.fileDescriptor)
        startReader("accessory-in") { buf ->
            try {
                val n = input.read(buf)
                if (n < 0) -2 else n // -1 = end of stream
            } catch (e: IOException) {
                -2 // closed by stop() or the cable was pulled
            }
        }
        setStatus("Nagrywam (tryb akcesorium).")
    }

    private fun startDevice(device: UsbDevice) {
        val conn = usb.openDevice(device)
        if (conn == null) {
            setStatus("Nie udało się otworzyć urządzenia USB.")
            return
        }
        beginCapture("host", UsbDescriber.describeDevice(device))
        synchronized(lock) { connection = conn }

        val targets = chooseInterfaces(device)
        var readers = 0
        val notes = StringBuilder()
        for (intf in targets) {
            if (!conn.claimInterface(intf, true)) {
                notes.append("# could not claim IF#${intf.id}\n")
                continue
            }
            synchronized(lock) { claimed += intf }
            for (e in 0 until intf.endpointCount) {
                val ep = intf.getEndpoint(e)
                if (ep.direction != UsbConstants.USB_DIR_IN) continue // never touch OUT endpoints
                if (ep.type != UsbConstants.USB_ENDPOINT_XFER_BULK) continue
                readers++
                startReader("if${intf.id}-ep0x${UsbDescriber.hex2(ep.address)}") { buf ->
                    readBulk(conn, ep, buf)
                }
            }
        }
        writeLine(notes.toString().trimEnd().ifEmpty { "# listening on $readers bulk IN endpoint(s)" })
        setStatus(
            if (readers == 0) "Urządzenie otwarte, ale nie znalazłem żadnego wejścia do nasłuchu. Wyślij nagranie (opis urządzenia jest w pliku)."
            else "Nagrywam (tryb host, $readers wejść).",
        )
    }

    private fun readBulk(conn: UsbDeviceConnection, ep: UsbEndpoint, buf: ByteArray): Int =
        conn.bulkTransfer(ep, buf, buf.size, 250) // -1 on timeout: just try again

    /**
     * Interfaces to listen on: the DJI DUML bulk interface (vendor class, subclass 0x43) when
     * present, otherwise every other interface with a bulk IN endpoint except ADB, MTP and
     * mass storage.
     */
    private fun chooseInterfaces(device: UsbDevice): List<UsbInterface> {
        val all = (0 until device.interfaceCount).map { device.getInterface(it) }
            .filter { it.alternateSetting == 0 }
            .filter { intf -> (0 until intf.endpointCount).any { isBulkIn(intf.getEndpoint(it)) } }
        val duml = all.filter { it.interfaceClass == 0xFF && it.interfaceSubclass == 0x43 }
        if (duml.isNotEmpty()) return duml
        return all.filterNot {
            (it.interfaceClass == 0xFF && it.interfaceSubclass == 0x42) || // ADB
                it.interfaceClass == 0x06 || // MTP / PTP
                it.interfaceClass == 0x08 // mass storage
        }
    }

    private fun isBulkIn(ep: UsbEndpoint) =
        ep.direction == UsbConstants.USB_DIR_IN && ep.type == UsbConstants.USB_ENDPOINT_XFER_BULK

    private fun beginCapture(mode: String, descriptor: String) {
        val dir = File(app.filesDir, "captures").apply { mkdirs() }
        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val file = File(dir, "capture_${stamp}_$mode.txt")
        synchronized(lock) {
            sources.clear()
            recent.clear()
            captureFile = file
            writer = file.bufferedWriter()
            bytesWritten = 0
            fileLimitHit = false
            startedAt = SystemClock.elapsedRealtime()
            running = true
        }
        writeLine("# Mini4K USB Sniffer capture v1")
        writeLine("# app=${BuildInfo.VERSION} phone=${Build.MANUFACTURER} ${Build.MODEL} android=${Build.VERSION.RELEASE} sdk=${Build.VERSION.SDK_INT}")
        writeLine("# started=${Date()} mode=$mode")
        descriptor.lines().filter { it.isNotBlank() }.forEach { writeLine("# $it") }
        writeLine("# format: <ms since start> <source> <byte count> <hex bytes>")
    }

    private fun startReader(name: String, read: (ByteArray) -> Int) {
        val source = Source(name)
        source.parser = DumlStreamParser { packet -> onPacket(source, packet) }
        synchronized(lock) { sources[name] = source }
        val t = Thread(Runnable {
            val buf = ByteArray(16 * 1024)
            while (isRunning()) {
                val n = read(buf)
                when {
                    n > 0 -> onData(source, buf, n)
                    n == -2 -> break
                }
            }
        }, "usb-$name")
        t.isDaemon = true
        synchronized(lock) { workers += t }
        t.start()
    }

    private fun onData(source: Source, buf: ByteArray, n: Int) {
        synchronized(lock) {
            if (!running) return
            val ms = SystemClock.elapsedRealtime() - startedAt
            writeLineLocked("$ms ${source.name} $n ${buf.toHex(0, n)}")
            source.parser.feed(buf, 0, n)
        }
    }

    // Called from DumlStreamParser.feed, which already runs under `lock`.
    private fun onPacket(source: Source, p: DumlPacket) {
        val key = "${p.sender}->${p.receiver} set=0x%02x id=0x%02x".format(p.cmdSet, p.cmdId)
        source.commands[key] = (source.commands[key] ?: 0) + 1
        recent.addFirst("${source.name}: ${p.summary()}")
        while (recent.size > MAX_RECENT) recent.removeLast()
    }

    private fun writeLine(line: String) = synchronized(lock) { writeLineLocked(line) }

    private fun writeLineLocked(line: String) {
        val w = writer ?: return
        if (fileLimitHit) return
        if (bytesWritten + line.length > MAX_FILE_BYTES) {
            fileLimitHit = true
            w.write("# file size limit reached, further data is counted but not saved\n")
            return
        }
        w.write(line)
        w.write("\n")
        bytesWritten += line.length + 1
    }

    fun stop() {
        val threads: List<Thread>
        synchronized(lock) {
            if (!running) return
            running = false
            threads = workers.toList()
            workers.clear()
        }
        try {
            accessoryFd?.close() // unblocks a pending accessory read
        } catch (e: IOException) {
            // already closed
        }
        threads.forEach { it.join(1500) }
        synchronized(lock) {
            connection?.let { c ->
                claimed.forEach { c.releaseInterface(it) }
                c.close()
            }
            claimed.clear()
            connection = null
            accessoryFd = null
            val seconds = (SystemClock.elapsedRealtime() - startedAt) / 1000
            fileLimitHit = false
            writeLineLocked("# stopped after ${seconds}s")
            sources.values.forEach { s ->
                writeLineLocked("# stats ${s.name}: ${s.parser.stats()}")
                s.commands.entries.sortedByDescending { it.value }.take(30).forEach {
                    writeLineLocked("#   ${it.value}x ${it.key}")
                }
            }
            writer?.close()
            writer = null
            status = "Zatrzymano. Nagranie zapisane, możesz je udostępnić."
        }
    }

    fun snapshot(): Snapshot = synchronized(lock) {
        Snapshot(
            running = running,
            status = status,
            seconds = if (running) (SystemClock.elapsedRealtime() - startedAt) / 1000 else 0,
            sources = sources.values.map { s ->
                SourceView(
                    name = s.name,
                    bytes = s.parser.bytesIn,
                    packets = s.parser.packets,
                    encrypted = s.parser.encryptedPackets,
                    skipped = s.parser.bytesSkipped,
                    dumlPercent = s.parser.dumlRatio * 100,
                    topCommands = s.commands.entries.sortedByDescending { it.value }.take(8).map { it.key to it.value },
                )
            },
            recent = recent.toList(),
            file = captureFile,
        )
    }

    companion object {
        const val DJI_VENDOR_ID = 0x2CA3
        private const val ACTION_PERMISSION = "org.mini4k.sniffer.USB_PERMISSION"
        private const val MAX_RECENT = 100
        private const val MAX_FILE_BYTES = 60L * 1024 * 1024
    }
}

object BuildInfo {
    const val VERSION = "0.1.0"
}
