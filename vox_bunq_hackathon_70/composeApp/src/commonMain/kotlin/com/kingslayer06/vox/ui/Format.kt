package com.kingslayer06.vox.ui

import kotlin.math.abs
import kotlin.math.roundToLong

fun formatEur(value: Double): String {
    val cents = (value * 100.0).roundToLong()
    val sign = if (cents < 0) "-" else ""
    val abs = abs(cents)
    val whole = abs / 100
    val frac = abs % 100
    val wholeStr = formatThousands(whole)
    val fracStr = frac.toString().padStart(2, '0')
    return "$sign$wholeStr.$fracStr"
}

private fun formatThousands(n: Long): String {
    val s = n.toString()
    if (s.length <= 3) return s
    val sb = StringBuilder()
    var count = 0
    for (i in s.indices.reversed()) {
        sb.append(s[i])
        count++
        if (count == 3 && i != 0) {
            sb.append(',')
            count = 0
        }
    }
    return sb.reverse().toString()
}

fun parseHexColor(hex: String?): androidx.compose.ui.graphics.Color {
    if (hex.isNullOrBlank()) return VoxColors.Muted
    val s = hex.removePrefix("#")
    return runCatching {
        when (s.length) {
            6 -> androidx.compose.ui.graphics.Color(0xFF000000 or s.toLong(16))
            8 -> androidx.compose.ui.graphics.Color(s.toLong(16))
            else -> VoxColors.Muted
        }
    }.getOrDefault(VoxColors.Muted)
}
