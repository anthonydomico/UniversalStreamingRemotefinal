package com.universalstreamingremote.app

import android.content.Context
import android.hardware.ConsumerIrManager

/** Common 32-bit consumer IR profiles; requires an Android phone with an IR blaster. */
object Infrared {
    private val samsung = mapOf(
        "POWER" to 0xE0E040BFu, "VOL_UP" to 0xE0E0E01Fu,
        "VOL_DOWN" to 0xE0E0D02Fu, "MUTE" to 0xE0E0F00Fu,
        "INPUT" to 0xE0E0807Fu, "UP" to 0xE0E006F9u,
        "DOWN" to 0xE0E08679u, "LEFT" to 0xE0E0A659u,
        "RIGHT" to 0xE0E046B9u, "OK" to 0xE0E016E9u,
        "BACK" to 0xE0E01AE5u, "HOME" to 0xE0E09E61u
    )
    private val lg = mapOf(
        "POWER" to 0x20DF10EFu, "VOL_UP" to 0x20DF40BFu,
        "VOL_DOWN" to 0x20DFC03Fu, "MUTE" to 0x20DF906Fu,
        "INPUT" to 0x20DFD02Fu, "UP" to 0x20DF02FDu,
        "DOWN" to 0x20DF827Du, "LEFT" to 0x20DFE01Fu,
        "RIGHT" to 0x20DF609Fu, "OK" to 0x20DF22DDu,
        "BACK" to 0x20DF14EBu, "HOME" to 0x20DF3EC1u
    )
    fun available(context: Context): Boolean =
        (context.getSystemService(Context.CONSUMER_IR_SERVICE) as? ConsumerIrManager)?.hasIrEmitter() == true

    fun supports(type: DeviceType, key: String) = codes(type).containsKey(key)

    private fun codes(type: DeviceType) = when (type) {
        DeviceType.SAMSUNG_IR -> samsung
        DeviceType.LG_IR -> lg
        else -> emptyMap()
    }

    /** NEC-style 38 kHz bursts, microseconds; Samsung uses a longer 4500us leader space. */
    private fun pattern(code: UInt, samsungProtocol: Boolean): IntArray {
        val parts = ArrayList<Int>(68)
        parts.add(4500)
        parts.add(if (samsungProtocol) 4500 else 4500)
        for (bit in 31 downTo 0) {
            parts.add(560)
            parts.add(if (((code shr bit) and 1u) == 1u) 1690 else 560)
        }
        parts.add(560)
        return parts.toIntArray()
    }

    fun send(context: Context, type: DeviceType, key: String): Boolean {
        val manager = context.getSystemService(Context.CONSUMER_IR_SERVICE) as? ConsumerIrManager ?: return false
        val code = codes(type)[key] ?: return false
        if (!manager.hasIrEmitter()) return false
        return try {
            manager.transmit(38000, pattern(code, type == DeviceType.SAMSUNG_IR))
            true
        } catch (_: Exception) { false }
    }
}
