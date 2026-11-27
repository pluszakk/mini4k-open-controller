package org.mini4k.sniffer

import android.hardware.usb.UsbAccessory
import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbEndpoint
import android.hardware.usb.UsbInterface

/** Turns USB descriptors into readable text for the capture header and the screen. */
object UsbDescriber {

    fun describeDevice(d: UsbDevice): String = buildString {
        append("DEVICE ").append(d.deviceName)
        append(" vid=0x").append(hex4(d.vendorId))
        append(" pid=0x").append(hex4(d.productId))
        append(" class=").append(d.deviceClass).append('/').append(d.deviceSubclass).append('/').append(d.deviceProtocol)
        append('\n')
        // Names and serial need permission on Android 10+, so read them defensively.
        append("  manufacturer=").append(safe { d.manufacturerName })
        append(" product=").append(safe { d.productName })
        append(" version=").append(safe { d.version })
        append('\n')
        append("  configurations=").append(d.configurationCount)
        append(" interfaces=").append(d.interfaceCount).append('\n')
        for (c in 0 until d.configurationCount) {
            val cfg = d.getConfiguration(c)
            append("  config#").append(cfg.id).append(" name=").append(cfg.name)
            append(" maxPower=").append(cfg.maxPower).append("mA\n")
        }
        for (i in 0 until d.interfaceCount) {
            append(describeInterface(d.getInterface(i)))
        }
    }

    fun describeInterface(intf: UsbInterface): String = buildString {
        append("  IF#").append(intf.id).append(" alt=").append(intf.alternateSetting)
        append(" class=0x").append(hex2(intf.interfaceClass))
        append(" sub=0x").append(hex2(intf.interfaceSubclass))
        append(" proto=0x").append(hex2(intf.interfaceProtocol))
        append(" name=").append(intf.name)
        append(" (").append(guessInterface(intf)).append(")\n")
        for (e in 0 until intf.endpointCount) {
            append("    ").append(describeEndpoint(intf.getEndpoint(e))).append('\n')
        }
    }

    fun describeEndpoint(ep: UsbEndpoint): String {
        val dir = if (ep.direction == UsbConstants.USB_DIR_IN) "IN" else "OUT"
        val type = when (ep.type) {
            UsbConstants.USB_ENDPOINT_XFER_BULK -> "bulk"
            UsbConstants.USB_ENDPOINT_XFER_INT -> "interrupt"
            UsbConstants.USB_ENDPOINT_XFER_ISOC -> "iso"
            else -> "control"
        }
        return "EP 0x${hex2(ep.address)} $dir $type maxPacket=${ep.maxPacketSize}"
    }

    fun describeAccessory(a: UsbAccessory): String = buildString {
        append("ACCESSORY manufacturer=").append(a.manufacturer)
        append(" model=").append(a.model)
        append(" version=").append(a.version).append('\n')
        append("  description=").append(a.description)
        append(" uri=").append(a.uri)
        append(" serial=").append(safe { a.serial }).append('\n')
    }

    /** Rough label to make the descriptor dump easier to read. Not used for decisions. */
    fun guessInterface(intf: UsbInterface): String = when {
        intf.interfaceClass == 0xFF && intf.interfaceSubclass == 0x43 -> "likely DJI DUML bulk"
        intf.interfaceClass == 0xFF && intf.interfaceSubclass == 0x42 && intf.interfaceProtocol == 0x01 -> "ADB"
        intf.interfaceClass == 0x06 -> "MTP/PTP"
        intf.interfaceClass == 0x08 -> "mass storage"
        intf.interfaceClass == 0x02 || intf.interfaceClass == 0x0A -> "CDC serial"
        intf.interfaceClass == 0xFF -> "vendor specific"
        else -> "other"
    }

    private inline fun safe(block: () -> String?): String =
        try {
            block() ?: "null"
        } catch (e: SecurityException) {
            "<needs permission>"
        }

    fun hex2(v: Int): String = (v and 0xFF).toString(16).padStart(2, '0')
    fun hex4(v: Int): String = (v and 0xFFFF).toString(16).padStart(4, '0')
}
