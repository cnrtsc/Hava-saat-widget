package com.caner.sketchweather

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.os.BatteryManager
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Date
import java.util.Locale
import kotlin.math.min

/** Mini haplar: küçük, simgeli kapsüller. Başka bir widget'ın altına ek bilgi şeridi olarak düşünüldü. */
object MiniPills {
    val CONTENT = arrayOf("Yok", "Konum", "Hava", "Saat", "Tarih", "Yağmur ihtimali", "Hissedilen", "Nem", "Rüzgâr",
        "Gün doğumu / batımı", "UV", "Pil", "Hava kalitesi")
    val DAYS = arrayOf("Yok", "2 gün", "3 gün")
    val SIZES = arrayOf("Küçük", "Orta", "Büyük")
    val ALIGN = arrayOf("Sola", "Ortaya", "Sağa")
    private val DEF = intArrayOf(2, 5, 11, 0, 0, 0)
    private val tr = Locale("tr", "TR")
    const val SLOTS = 6

    fun slot(c: Context, i: Int) = Cfg.int(c, "mp$i", DEF[i]).coerceIn(0, CONTENT.size - 1)
    fun days(c: Context) = Cfg.int(c, "mpDays", 0).coerceIn(0, 2)
    fun size(c: Context) = Cfg.int(c, "mpSize", 1).coerceIn(0, 2)
    fun align(c: Context) = Cfg.int(c, "mpAlign", 0).coerceIn(0, 2)

    /** glyph: 0 yok, >0 drawable, <0 çizilen simge (-1 pil, -2 takvim, -3 termometre, -4 yaprak, -5 konum) */
    private class Pill(val glyph: Int, val text: String, val level: Int = -1, val charging: Boolean = false)

    private fun battery(c: Context): Pair<Int, Boolean> {
        val bi = try { c.applicationContext.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED)) } catch (e: Exception) { null }
        val l = bi?.let { val lv = it.getIntExtra(BatteryManager.EXTRA_LEVEL, -1); val sc = it.getIntExtra(BatteryManager.EXTRA_SCALE, 100); if (lv >= 0) lv * 100 / sc else -1 } ?: -1
        val st = bi?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        return Pair(l, st == BatteryManager.BATTERY_STATUS_CHARGING || st == BatteryManager.BATTERY_STATUS_FULL)
    }

    private fun pill(c: Context, which: Int, w: Weather?): Pill? {
        val now = Date()
        return when (which) {
            1 -> Pill(-5, WeatherRepo.city(c))
            2 -> Pill(if (w != null) Style.icon(c, w.code, w.isDay) else Style.iconOf(0, 6, 4, true), w?.let { "${it.temp}°" } ?: "--°")
            3 -> Pill(0, SimpleDateFormat("HH:mm", tr).format(now))
            4 -> Pill(-2, SimpleDateFormat("d MMM", tr).format(now))
            5 -> Pill(R.drawable.st_drop, w?.rainChance?.let { "%$it" } ?: "--")
            6 -> Pill(-3, w?.feels?.let { "$it°" } ?: "--")
            7 -> Pill(R.drawable.st_drop, w?.humidity?.let { "%$it" } ?: "--")
            8 -> Pill(R.drawable.st_wind, w?.wind?.let { "$it" } ?: "--")
            9 -> {
                val cal = java.util.Calendar.getInstance()
                val nowM = cal.get(java.util.Calendar.HOUR_OF_DAY) * 60 + cal.get(java.util.Calendar.MINUTE)
                val rise = Mod.hm(w?.sunrise) ?: 420; val set = Mod.hm(w?.sunset) ?: 1140
                if (nowM in rise..set) Pill(R.drawable.st_set, w?.sunset ?: "--") else Pill(R.drawable.st_rise, w?.sunrise ?: "--")
            }
            10 -> Pill(Style.iconOf(0, 6, 0, true), w?.uv?.let { "$it" } ?: "--")
            11 -> { val (l, ch) = battery(c); Pill(-1, if (l >= 0) "$l" else "--", l, ch) }
            12 -> Pill(-4, w?.aqi?.let { WeatherRepo.aqiLabel(it) } ?: "--")
            else -> null
        }
    }

    private fun drawGlyph(c: Context, cv: Canvas, p: Pill, cx: Float, cy: Float, s: Float, ink: Int) {
        if (p.glyph > 0) { Combo.icon(c, cv, p.glyph, cx, cy, s * 1.15f); return }
        val st = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = s * .1f; color = ink; strokeJoin = Paint.Join.ROUND; strokeCap = Paint.Cap.ROUND }
        val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = ink }
        when (p.glyph) {
            -1 -> {
                val r = RectF(cx - s * .5f, cy - s * .27f, cx + s * .42f, cy + s * .27f)
                cv.drawRoundRect(r, s * .08f, s * .08f, st)
                cv.drawRect(r.right, cy - s * .1f, r.right + s * .08f, cy + s * .1f, fill)
                val lv = (p.level.coerceIn(0, 100)) / 100f
                fill.color = when { p.charging -> 0xFF4CD07D.toInt(); p.level in 0..19 -> 0xFFFF5A4E.toInt(); else -> ink }
                cv.drawRoundRect(RectF(r.left + s * .1f, r.top + s * .1f, r.left + s * .1f + (r.width() - s * .2f) * lv, r.bottom - s * .1f), s * .03f, s * .03f, fill)
            }
            -2 -> {
                val r = RectF(cx - s * .42f, cy - s * .36f, cx + s * .42f, cy + s * .42f)
                cv.drawRoundRect(r, s * .1f, s * .1f, st)
                cv.drawRect(r.left, r.top, r.right, r.top + s * .22f, fill)
            }
            -3 -> {
                cv.drawLine(cx, cy - s * .42f, cx, cy + s * .12f, st.apply { strokeWidth = s * .18f })
                cv.drawCircle(cx, cy + s * .26f, s * .2f, fill)
            }
            -4 -> {
                val path = Path().apply {
                    moveTo(cx - s * .38f, cy + s * .38f)
                    cubicTo(cx - s * .45f, cy - s * .2f, cx, cy - s * .45f, cx + s * .42f, cy - s * .42f)
                    cubicTo(cx + s * .45f, cy + s * .05f, cx + s * .1f, cy + s * .42f, cx - s * .38f, cy + s * .38f)
                }
                cv.drawPath(path, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF6BD08A.toInt() })
            }
            -5 -> try {
                val d = c.getDrawable(R.drawable.pin)!!.mutate(); d.setTint(0xFFFFB03C.toInt())
                d.setBounds((cx - s * .45f).toInt(), (cy - s * .5f).toInt(), (cx + s * .45f).toInt(), (cy + s * .5f).toInt()); d.draw(cv)
            } catch (e: Exception) { }
        }
    }

    fun render(c: Context, w: Weather?, wdp: Int, hdp: Int): Bitmap {
        val k = 2.5f
        val W = (wdp.coerceIn(80, 560) * k).toInt()
        val H = (hdp.coerceIn(24, 300) * k).toInt()
        val b = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888)
        val cv = Canvas(b)
        val op = Style.opacity(c) / 100f
        val ink = Style.textColor(c) ?: Color.WHITE
        val lk = Combo.Look(Color.TRANSPARENT, ink, ink, ink, op < .3f, 0)
        val tf = Style.textTypeface(c, Style.font(c))
        val list = ArrayList<Pill>()
        for (i in 0 until SLOTS) pill(c, slot(c, i), w)?.let { list.add(it) }
        val nd = intArrayOf(0, 2, 3)[days(c)]
        for (i in 1..nd) w?.days?.getOrNull(i)?.let { d ->
            list.add(Pill(Style.icon(c, d.code, true), LocalDate.parse(d.date).dayOfWeek.getDisplayName(TextStyle.SHORT, tr) + " ${d.max}°"))
        }
        if (list.isEmpty()) return b
        val ph = min(floatArrayOf(22f, 28f, 34f)[size(c)] * k, H - 4f)
        val fs = ph * .46f
        val gs = ph * .58f
        val gap = ph * .22f
        val padX = ph * .34f
        val tp = Combo.text(tf, fs, ink, lk, Paint.Align.LEFT)
        fun pw(p: Pill) = padX * 2 + (if (p.glyph != 0) gs + ph * .14f else 0f) + tp.measureText(p.text)
        // satırlara yerleştir; sığmayanlar alt satıra, yer yoksa gizlenir
        val rows = ArrayList<ArrayList<Pill>>()
        val maxRows = maxOf(1, ((H + gap) / (ph + gap)).toInt())
        var cur = ArrayList<Pill>(); var cw = 0f
        for (p in list) {
            val wv = pw(p)
            if (cur.isNotEmpty() && cw + gap + wv > W - 4) { rows.add(cur); cur = ArrayList(); cw = 0f }
            if (rows.size >= maxRows) break
            if (cur.isNotEmpty()) cw += gap
            cur.add(p); cw += wv
        }
        if (cur.isNotEmpty() && rows.size < maxRows) rows.add(cur)
        val totalH = rows.size * ph + (rows.size - 1) * gap
        var y = (H - totalH) / 2
        val bg = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb((215 * op).toInt(), 22, 26, 34) }
        for (row in rows) {
            val rw = row.sumOf { pw(it).toDouble() }.toFloat() + gap * (row.size - 1)
            var x = when (align(c)) { 1 -> (W - rw) / 2; 2 -> W - 2f - rw; else -> 2f }
            for (p in row) {
                val wv = pw(p)
                val r = RectF(x, y, x + wv, y + ph)
                if (op > 0f) cv.drawRoundRect(r, ph / 2, ph / 2, bg)
                var tx = x + padX
                if (p.glyph != 0) { drawGlyph(c, cv, p, tx + gs / 2, y + ph / 2, gs, ink); tx += gs + ph * .14f }
                val fm = tp.fontMetrics
                cv.drawText(p.text, tx, y + ph / 2 - (fm.ascent + fm.descent) / 2, tp)
                x += wv + gap
            }
            y += ph + gap
        }
        return b
    }
}
