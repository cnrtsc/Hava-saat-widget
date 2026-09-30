package com.caner.sketchweather

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/** Hap dizisi: yan yana kapsüller, her birinin içeriği seçilebilir. */
object Pills {
    val CONTENT = arrayOf("Yok", "Konum", "Hava", "Saat", "Yağmur ihtimali", "Hissedilen", "Nem", "Rüzgâr",
        "Tarih", "Gün doğumu / batımı", "Yarın", "UV", "Pil")
    private val DEF = intArrayOf(1, 2, 3, 4)
    private val tr = Locale("tr", "TR")

    fun pill(c: Context, i: Int) = Cfg.int(c, "pill$i", DEF[i]).coerceIn(0, CONTENT.size - 1)
    fun weatherTint(c: Context) = Cfg.bool(c, "pillTint", false)

    private class Item(val icon: Int?, val pin: Boolean, val text: String, val clock: Boolean = false)

    private fun item(c: Context, which: Int, w: Weather?): Item? {
        val now = Date()
        val cal = Calendar.getInstance()
        val nowM = cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)
        return when (which) {
            1 -> Item(null, true, WeatherRepo.city(c))
            2 -> Item(if (w != null) Style.icon(c, w.code, w.isDay) else R.drawable.r_cloud, false, if (w != null) "${w.temp}°" else "--°")
            3 -> Item(null, false, SimpleDateFormat("HH:mm", tr).format(now), true)
            4 -> Item(R.drawable.st_drop, false, w?.rainChance?.let { "%$it" } ?: "--")
            5 -> Item(null, false, w?.feels?.let { "Hissedilen $it°" } ?: "--")
            6 -> Item(R.drawable.st_drop, false, w?.humidity?.let { "Nem %$it" } ?: "--")
            7 -> Item(R.drawable.st_wind, false, w?.wind?.let { "$it km/s" } ?: "--")
            8 -> Item(null, false, SimpleDateFormat("d MMM EEE", tr).format(now))
            9 -> {
                val rise = Mod.hm(w?.sunrise) ?: 420; val set = Mod.hm(w?.sunset) ?: 1140
                if (nowM in rise..set) Item(R.drawable.st_set, false, w?.sunset ?: "--") else Item(R.drawable.st_rise, false, w?.sunrise ?: "--")
            }
            10 -> w?.days?.getOrNull(1)?.let { Item(Style.icon(c, it.code, true), false, "Yarın ${it.max}°") } ?: Item(null, false, "Yarın --")
            11 -> Item(null, false, w?.uv?.let { "UV $it" } ?: "UV --")
            12 -> {
                val bi = try { c.applicationContext.registerReceiver(null, android.content.IntentFilter(android.content.Intent.ACTION_BATTERY_CHANGED)) } catch (e: Exception) { null }
                val l = bi?.let { val lv = it.getIntExtra(android.os.BatteryManager.EXTRA_LEVEL, -1); val sc = it.getIntExtra(android.os.BatteryManager.EXTRA_SCALE, 100); if (lv >= 0) lv * 100 / sc else -1 } ?: -1
                Item(null, false, if (l >= 0) "Pil %$l" else "Pil --")
            }
            else -> null
        }
    }

    fun render(c: Context, w: Weather?, wdp: Int, hdp: Int): Bitmap {
        val k = 2.5f
        val W = (wdp.coerceIn(160, 560) * k).toInt()
        val H = (hdp.coerceIn(40, 200) * k).toInt()
        val b = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888)
        val cv = Canvas(b)
        val op = Style.opacity(c) / 100f
        val font = Style.font(c)
        val tf = Style.textTypeface(c, font)
        val ink = Style.textColor(c) ?: Color.WHITE
        val lk = Combo.Look(Color.TRANSPARENT, ink, ink, ink, op < .3f, 0)
        val items = (0 until 4).mapNotNull { i -> item(c, pill(c, i), w)?.let { Pair(pill(c, i), it) } }
        if (items.isEmpty()) return b
        val ph = H * .7f
        val gap = H * .1f
        var fs = ph * .36f
        val isz = ph * .56f
        fun width(it: Item, f: Float): Float {
            val p = if (it.clock) Lock.clockPaint(c, f * 1.15f, ink, lk) else Combo.text(tf, f, ink, lk, Paint.Align.LEFT)
            return p.measureText(it.text) + (if (it.icon != null || it.pin) isz * .9f else 0f) + ph * .6f
        }
        var total = items.sumOf { width(it.second, fs).toDouble() }.toFloat() + gap * (items.size - 1)
        if (total > W - 4) { fs *= (W - 4 - gap * (items.size - 1)) / (total - gap * (items.size - 1)); total = W - 4f }
        val extra = ((W - 4) - total) / items.size
        var x = 2f
        val y0 = (H - ph) / 2
        for ((which, it) in items) {
            val pw = width(it, fs) + extra
            val r = RectF(x, y0, x + pw, y0 + ph)
            var bg = Color.argb((215 * op).toInt(), 22, 26, 34)
            var col = ink
            if (which == 2 && weatherTint(c)) {
                val (cc, darkText) = WeatherRepo.conditionColor(w)
                bg = Color.argb((235 * op).toInt(), Color.red(cc), Color.green(cc), Color.blue(cc))
                if (darkText && Style.textColor(c) == null) col = 0xFF1C2230.toInt()
            }
            cv.drawRoundRect(r, ph / 2, ph / 2, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = bg })
            if (Combo.rim(c)) cv.drawRoundRect(r, ph / 2, ph / 2, Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 2f; color = 0x55FFFFFF })
            val p = if (it.clock) Lock.clockPaint(c, fs * 1.15f, col, lk).apply { textAlign = Paint.Align.LEFT } else Combo.text(tf, fs, col, lk, Paint.Align.LEFT)
            val contentW = p.measureText(it.text) + (if (it.icon != null || it.pin) isz * .9f else 0f)
            var cx = r.centerX() - contentW / 2
            val cy = r.centerY()
            if (it.pin) {
                try {
                    val d = c.getDrawable(R.drawable.pin)!!.mutate(); d.setTint(0xFFFFB03C.toInt())
                    val s = isz * .7f
                    d.setBounds(cx.toInt(), (cy - s / 2).toInt(), (cx + s).toInt(), (cy + s / 2).toInt()); d.draw(cv)
                } catch (e: Exception) { }
                cx += isz * .9f
            } else if (it.icon != null) {
                Combo.icon(c, cv, it.icon, cx + isz * .4f, cy, isz)
                cx += isz * .9f
            }
            val fm = p.fontMetrics
            cv.drawText(it.text, cx, cy - (fm.ascent + fm.descent) / 2, p)
            x += pw + gap
        }
        return b
    }
}
