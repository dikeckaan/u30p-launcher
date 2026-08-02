package com.kaandikec.u30plauncher.ui

import android.content.Context
import android.graphics.Canvas
import com.kaandikec.u30plauncher.R
import com.kaandikec.u30plauncher.core.Fmt
import com.kaandikec.u30plauncher.core.Snapshot
import com.kaandikec.u30plauncher.ui.theme.ThemeUtil

/**
 * Sayfa 4: cihaz durumu — CPU, bellek, depolama, calisma suresi, yuk.
 *
 * Yuzdeler ayrica ince birer cubukla gosteriliyor; 240x240'ta rakami okumak
 * icin odaklanmak gerekiyor, cubuk goz ucuyla "dolu mu bos mu" sorusunu
 * hemen yanitliyor.
 */
class SystemPage(ctx: Context) : PageView(ctx) {
    private val sb = StringBuilder(24)
    private val title = ThemeUtil.text(10f, Palette.DIM2)
    private val label = ThemeUtil.text(9f, Palette.DIM2)
    private val value = ThemeUtil.text(14f, Palette.FG)
    private val hair = ThemeUtil.hairline(Palette.HAIRLINE)
    private val barTrack = ThemeUtil.fill(Palette.TRACK)
    private val barFill = ThemeUtil.fill(Palette.DOWN)

    override fun draw(c: Canvas, s: Snapshot) {
        Geom.centerText(c, str(R.string.system_title), 22f, title)
        Geom.hairline(c, 40f, 50f, hair)

        // CPU ve RAM: yuzde + cubuk
        cell(c, Geom.COL_L, 54f, str(R.string.cpu), s.cpuPercent)
        cell(c, Geom.COL_R, 54f, str(R.string.ram), ramPercent(s))

        // Bellek ve depolama: kullanilan / toplam
        sb.setLength(0)
        if (s.ramTotalKb > 0) {
            Fmt.appendBytes(sb, s.ramUsedKb * 1024)
            sb.append(" / ")
            Fmt.appendBytes(sb, s.ramTotalKb * 1024)
        } else sb.append(str(R.string.unknown))
        Geom.centerText(c, str(R.string.memory), 106f, label)
        Geom.centerText(c, sb, 118f, value)

        sb.setLength(0)
        if (s.storageTotalBytes > 0) {
            Fmt.appendBytes(sb, s.storageUsedBytes)
            sb.append(" / ")
            Fmt.appendBytes(sb, s.storageTotalBytes)
        } else sb.append(str(R.string.unknown))
        Geom.centerText(c, str(R.string.storage), 142f, label)
        Geom.centerText(c, sb, 154f, value)

        // Calisma suresi ve yuk
        sb.setLength(0)
        appendUptime(sb, s.uptimeSec)
        Geom.kvCell(c, Geom.COL_L, 180f, str(R.string.uptime), sb, label, value)

        sb.setLength(0)
        if (s.loadAvgX100 == Snapshot.UNKNOWN) sb.append(str(R.string.unknown)) else {
            sb.append(s.loadAvgX100 / 100)
            sb.append('.')
            val f = s.loadAvgX100 % 100
            if (f < 10) sb.append('0')
            sb.append(f)
        }
        Geom.kvCell(c, Geom.COL_R, 180f, str(R.string.load), sb, label, value)
    }

    private fun ramPercent(s: Snapshot): Int =
        if (s.ramTotalKb <= 0) Snapshot.UNKNOWN
        else (s.ramUsedKb * 100 / s.ramTotalKb).toInt()

    /** Etiket, yuzde ve altinda ince doluluk cubugu. */
    private fun cell(c: Canvas, cx: Float, y: Float, name: String, pct: Int) {
        Geom.centerText(c, name, y, label, cx)
        sb.setLength(0)
        if (pct == Snapshot.UNKNOWN) sb.append(str(R.string.unknown)) else {
            sb.append(pct)
            sb.append('%')
        }
        Geom.centerText(c, sb, y + 12f, value, cx)

        if (pct == Snapshot.UNKNOWN) return
        val w = 46f
        val x = cx - w / 2f
        val by = y + 30f
        c.drawRoundRect(x, by, x + w, by + 3f, 1.5f, 1.5f, barTrack)
        val filled = w * pct.coerceIn(0, 100) / 100f
        if (filled > 0.5f) {
            barFill.color = when {
                pct >= 90 -> Palette.ERR
                pct >= 70 -> Palette.WARN
                else -> Palette.DOWN
            }
            c.drawRoundRect(x, by, x + filled, by + 3f, 1.5f, 1.5f, barFill)
        }
    }

    /** "3.2 sa" / "12 dk" — gun asarsa "2 g 4 sa". */
    private fun appendUptime(sb: StringBuilder, sec: Long) {
        val minutes = (sec / 60).toInt()
        if (minutes >= 24 * 60) {
            sb.append(minutes / (24 * 60))
            sb.append(' ')
            sb.append(str(R.string.day_short))
            sb.append(' ')
            sb.append((minutes % (24 * 60)) / 60)
            sb.append(' ')
            sb.append(str(R.string.hour_short))
            return
        }
        Fmt.appendDuration(sb, minutes, str(R.string.hour_short), str(R.string.minute_short))
    }
}
