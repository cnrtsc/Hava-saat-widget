package com.caner.sketchweather

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/** Analog saat + yan bilgi paneli; tamamı tek görsel olarak çizilir. */
object Combo {
    val FACE_NAMES = arrayOf("Klasik çizgiler", "Minimal", "Rakamlı", "Noktalar", "Halka")
    val DIGITAL_NAMES = arrayOf("Dijital saat üstte", "Dijital saat altta", "Dijital saat yok")
    val COLOR_NAMES = arrayOf("Açık", "Koyu", "Renk paleti")
    val PANEL_NAMES = arrayOf("Yuvarlak panel", "Kare panel", "Panelsiz (sadece yazı)")
    val DAY_COUNTS = intArrayOf(0, 2, 3, 5)
    val DAY_NAMES = arrayOf("Gün gösterme", "2 gün", "3 gün", "5 gün")
    val SHOW_KEYS = arrayOf("loc", "date", "desc", "feels", "hl", "hum", "wind", "sun")
    val SHOW_NAMES = arrayOf("Konum", "Tarih", "Hava durumu yazısı", "Hissedilen", "En yüksek / en düşük", "Nem", "Rüzgâr", "Gün doğumu / batımı")

    private fun p(c: Context) = WeatherRepo.prefs(c)
    fun face(c: Context) = p(c).getInt("face", 0).coerceIn(0, FACE_NAMES.size - 1)
    fun digital(c: Context) = p(c).getInt("digital", 0).coerceIn(0, DIGITAL_NAMES.size - 1)
    fun color(c: Context) = p(c).getInt("faceColor", 0).coerceIn(0, COLOR_NAMES.size - 1)
    fun panel(c: Context) = p(c).getInt("panel", 0).coerceIn(0, PANEL_NAMES.size - 1)
    fun opacity(c: Context) = p(c).getInt("opacity", 100).coerceIn(0, 100)
    fun rim(c: Context) = p(c).getBoolean("rim", false)
    fun days(c: Context) = p(c).getInt("days", 1).coerceIn(0, DAY_COUNTS.size - 1)
    fun show(c: Context, key: String) = p(c).getBoolean("show_$key", key != "sun")

    private class Look(val fill: Int, val ink: Int, val sub: Int, val accent: Int, val shadow: Boolean, val rimCol: Int)

    private fun look(c: Context, pal: Pal): Look {
        val a = opacity(c) / 100f
        val col = color(c)
        val base = when (col) { 1 -> Color.rgb(24, 27, 34); 2 -> pal.container; else -> Color.rgb(236, 239, 243) }
        val fill = Color.argb((a * 255).toInt(), Color.red(base), Color.green(base), Color.blue(base))
        val onLight = a >= .45f && col != 1
        val ink = if (onLight) (if (col == 2) pal.onCont else Color.rgb(28, 30, 36)) else Color.WHITE
        val sub = if (onLight) (if (col == 2) pal.onContSub else Color.rgb(88, 94, 104)) else Color.rgb(222, 228, 236)
        val accent = if (onLight) pal.accent else pal.accentLight
        val rimCol = if (onLight) Color.argb(50, 0, 0, 0) else Color.argb(90, 255, 255, 255)
        return Look(fill, ink, sub, accent, a < .45f, rimCol)
    }

    private fun paint(color: Int, lk: Look) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = color
        if (lk.shadow) setShadowLayer(6f, 0f, 2f, 0x90000000.toInt())
    }

    private fun text(tf: Typeface, size: Float, color: Int, lk: Look, align: Paint.Align = Paint.Align.CENTER) =
        paint(color, lk).apply { typeface = tf; textSize = size; textAlign = align }

    private fun icon(c: Context, cv: Canvas, res: Int, cx: Float, cy: Float, size: Float) {
        try {
            val b = BitmapFactory.decodeResource(c.resources, res) ?: return
            cv.drawBitmap(b, null, RectF(cx - size / 2, cy - size / 2, cx + size / 2, cy + size / 2), Paint(Paint.FILTER_BITMAP_FLAG))
        } catch (e: Exception) { }
    }

    private fun drawClock(c: Context, cv: Canvas, cx: Float, cy: Float, r: Float, lk: Look, w: Weather?, withWeather: Boolean) {
        val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = lk.fill }
        cv.drawCircle(cx, cy, r, fill)
        if (rim(c)) cv.drawCircle(cx, cy, r, Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = r * .012f; color = lk.rimCol })
        val tp = paint(lk.ink, lk).apply { strokeCap = Paint.Cap.ROUND }
        val cal = Calendar.getInstance()
        val m = cal.get(Calendar.MINUTE)
        val h = cal.get(Calendar.HOUR) + m / 60f
        val textTf = Style.textTypeface(c, Style.font(c))
        val clockTf = Style.clockTypeface(c, Style.font(c))
        when (face(c)) {
            0 -> for (k in 0 until 60) {
                val a = Math.toRadians(k * 6.0 - 90); val major = k % 5 == 0
                tp.color = if (major) lk.ink else lk.sub; tp.strokeWidth = if (major) r * .035f else r * .013f
                val r1 = if (major) r * .80f else r * .86f; val r2 = r * .92f
                cv.drawLine(cx + r1 * cos(a).toFloat(), cy + r1 * sin(a).toFloat(), cx + r2 * cos(a).toFloat(), cy + r2 * sin(a).toFloat(), tp)
            }
            1 -> for (k in 0 until 12) {
                val a = Math.toRadians(k * 30.0 - 90); val q = k % 3 == 0
                tp.color = lk.ink; tp.strokeWidth = if (q) r * .05f else r * .025f
                val r1 = if (q) r * .74f else r * .84f; val r2 = r * .9f
                cv.drawLine(cx + r1 * cos(a).toFloat(), cy + r1 * sin(a).toFloat(), cx + r2 * cos(a).toFloat(), cy + r2 * sin(a).toFloat(), tp)
            }
            2 -> {
                val np = text(textTf, r * .2f, lk.ink, lk)
                for (k in 1..12) {
                    val a = Math.toRadians(k * 30.0 - 90)
                    val rr = r * .78f
                    np.textSize = if (k % 3 == 0) r * .2f else r * .13f
                    np.color = if (k % 3 == 0) lk.ink else lk.sub
                    cv.drawText(k.toString(), cx + rr * cos(a).toFloat(), cy + rr * sin(a).toFloat() + np.textSize * .36f, np)
                }
            }
            3 -> for (k in 0 until 12) {
                val a = Math.toRadians(k * 30.0 - 90)
                tp.color = if (k % 3 == 0) lk.accent else lk.ink
                cv.drawCircle(cx + r * .84f * cos(a).toFloat(), cy + r * .84f * sin(a).toFloat(), if (k % 3 == 0) r * .045f else r * .028f, tp)
            }
            else -> {
                val rp = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = r * .05f; strokeCap = Paint.Cap.ROUND }
                val rect = RectF(cx - r * .86f, cy - r * .86f, cx + r * .86f, cy + r * .86f)
                rp.color = Color.argb(60, Color.red(lk.ink), Color.green(lk.ink), Color.blue(lk.ink))
                cv.drawArc(rect, 0f, 360f, false, rp)
                rp.color = lk.accent
                cv.drawArc(rect, -90f, 360f * m / 60f, false, rp)
            }
        }
        // dijital saat ve (isteğe bağlı) hava
        val dig = digital(c)
        val digital = String.format(Locale.US, "%02d:%02d", cal.get(Calendar.HOUR_OF_DAY), m)
        val topY = cy - r * .36f
        val botY = cy + r * .46f
        if (dig != 2) cv.drawText(digital, cx, (if (dig == 0) topY else botY) + r * .09f, text(clockTf, r * .25f, lk.ink, lk))
        if (withWeather && w != null) {
            val y = if (dig == 0) botY else topY
            icon(c, cv, Style.icon(c, w.code, w.isDay), cx - r * .16f, y, r * .30f)
            cv.drawText("${w.temp}°", cx + r * .16f, y + r * .08f, text(textTf, r * .22f, lk.ink, lk))
        }
        // akrep, yelkovan
        fun hand(deg: Double, len: Float, width: Float, col: Int) {
            val a = Math.toRadians(deg - 90)
            tp.color = col; tp.strokeWidth = width
            cv.drawLine(cx, cy, cx + len * cos(a).toFloat(), cy + len * sin(a).toFloat(), tp)
        }
        hand(h * 30.0, r * .50f, r * .075f, lk.ink)
        hand(m * 6.0, r * .74f, r * .045f, lk.accent)
        cv.drawCircle(cx, cy, r * .06f, paint(lk.accent, lk))
    }

    private fun drawPanel(c: Context, cv: Canvas, box: RectF, lk: Look, w: Weather?, k: Float) {
        val shape = panel(c)
        val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = lk.fill }
        val rimP = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 2.5f * k / 2.5f * 1.2f; color = lk.rimCol }
        val content: RectF
        when (shape) {
            0 -> {
                val d = min(box.width(), box.height()); val cx = box.centerX(); val cy = box.centerY()
                cv.drawCircle(cx, cy, d / 2, fill)
                if (rim(c)) cv.drawCircle(cx, cy, d / 2, rimP)
                val s = d * .72f
                content = RectF(cx - s / 2, cy - s / 2, cx + s / 2, cy + s / 2)
            }
            1 -> {
                val rr = 22f * k
                cv.drawRoundRect(box, rr, rr, fill)
                if (rim(c)) cv.drawRoundRect(box, rr, rr, rimP)
                content = RectF(box.left + 12 * k, box.top + 10 * k, box.right - 12 * k, box.bottom - 10 * k)
            }
            else -> content = RectF(box.left + 4 * k, box.top, box.right, box.bottom)
        }
        val font = Style.font(c)
        val tf = Style.textTypeface(c, font)
        val cf = Style.clockTypeface(c, font)
        val tr = Locale("tr", "TR")
        // satırları topla: (tür, ağırlık)
        val rows = ArrayList<Pair<String, Float>>()
        if (show(c, "loc")) rows.add(Pair("loc", 1f))
        if (show(c, "date")) rows.add(Pair("date", .85f))
        rows.add(Pair("main", 2.3f))
        if (show(c, "desc")) rows.add(Pair("desc", .9f))
        val subParts = ArrayList<String>()
        if (w != null) {
            if (show(c, "feels")) w.feels?.let { subParts.add("Hissedilen $it°") }
            if (show(c, "hl")) w.days.firstOrNull()?.let { subParts.add("Y ${it.max}° · D ${it.min}°") }
        }
        if (subParts.isNotEmpty()) rows.add(Pair("sub", .8f))
        val stats = ArrayList<Pair<Int, String>>()
        if (w != null) {
            if (show(c, "hum")) w.humidity?.let { stats.add(Pair(R.drawable.st_drop, "%$it")) }
            if (show(c, "wind")) w.wind?.let { stats.add(Pair(R.drawable.st_wind, "$it km/s")) }
            if (show(c, "sun")) w.sunset?.let { stats.add(Pair(R.drawable.st_set, it)) }
        }
        if (stats.isNotEmpty()) rows.add(Pair("stats", 1.1f))
        val nd = DAY_COUNTS[days(c)]
        for (i in 1..nd) rows.add(Pair("day$i", .95f))
        val total = rows.sumOf { it.second.toDouble() }.toFloat() * 1.22f
        val u = min(content.height() / total, content.width() / 7.5f)
        var y = content.centerY() - total * u / 2
        val cx = content.centerX()
        for ((kind, wt) in rows) {
            val lineH = wt * 1.22f * u
            val base = y + lineH * .72f
            when (kind) {
                "loc" -> {
                    val tp = text(tf, u * 1.0f, lk.ink, lk)
                    val name = WeatherRepo.city(c)
                    val tw = tp.measureText(name)
                    try {
                        val pin = c.getDrawable(R.drawable.pin)!!.mutate()
                        pin.setTint(lk.ink)
                        val ps = u * 1.0f
                        pin.setBounds((cx - tw / 2 - ps * 1.05f).toInt(), (base - ps * .9f).toInt(), (cx - tw / 2 - ps * .05f).toInt(), (base + ps * .1f).toInt())
                        pin.draw(cv)
                    } catch (e: Exception) { }
                    cv.drawText(name, cx + u * .5f, base, tp)
                }
                "date" -> cv.drawText(SimpleDateFormat("d MMMM EEEE", tr).format(Date()), cx, base, text(tf, u * .85f, lk.sub, lk))
                "main" -> {
                    val tt = if (w != null) "${w.temp}°" else "--°"
                    val tp = text(cf, u * 2.1f, lk.ink, lk)
                    val tw = tp.measureText(tt)
                    val isz = u * 2.3f
                    val x0 = cx - (isz + tw) / 2
                    if (w != null) icon(c, cv, Style.icon(c, w.code, w.isDay), x0 + isz / 2, y + lineH / 2, isz)
                    tp.textAlign = Paint.Align.LEFT
                    cv.drawText(tt, x0 + isz, y + lineH * .74f, tp)
                }
                "desc" -> cv.drawText(if (w != null) WeatherRepo.label(w.code) else "Yükleniyor…", cx, base, text(tf, u * .9f, lk.ink, lk))
                "sub" -> cv.drawText(subParts.joinToString("  ·  "), cx, base, text(tf, u * .78f, lk.sub, lk))
                "stats" -> {
                    val tp = text(tf, u * .82f, lk.ink, lk, Paint.Align.LEFT)
                    val isz = u * .95f
                    val widths = stats.map { isz + u * .25f + tp.measureText(it.second) }
                    val gap = u * .8f
                    var x = cx - (widths.sum() + gap * (stats.size - 1)) / 2
                    stats.forEachIndexed { i, (res, v) ->
                        icon(c, cv, res, x + isz / 2, y + lineH / 2, isz)
                        cv.drawText(v, x + isz + u * .25f, y + lineH / 2 + u * .3f, tp)
                        x += widths[i] + gap
                    }
                }
                else -> {
                    val i = kind.removePrefix("day").toInt()
                    val d = w?.days?.getOrNull(i)
                    if (d != null) {
                        val name = LocalDate.parse(d.date).dayOfWeek.getDisplayName(TextStyle.SHORT, tr)
                        val tpL = text(tf, u * .85f, lk.ink, lk, Paint.Align.RIGHT)
                        val tpR = text(tf, u * .85f, lk.sub, lk, Paint.Align.LEFT)
                        cv.drawText(name, cx - u * .9f, base, tpL)
                        icon(c, cv, Style.icon(c, d.code, true), cx, y + lineH / 2, u * 1.1f)
                        cv.drawText("${d.max}° ${d.min}°", cx + u * .9f, base, tpR)
                    }
                }
            }
            y += lineH
        }
    }

    /** mode 0: saat + panel, 1: saat içinde hava, 2: sadece saat */
    fun render(c: Context, w: Weather?, wdp: Int, hdp: Int, mode: Int): Bitmap {
        val k = 2.5f
        val W = (wdp.coerceIn(80, 560) * k).toInt()
        val H = (hdp.coerceIn(60, 400) * k).toInt()
        val b = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888)
        val cv = Canvas(b)
        val lk = look(c, Palette.get(c))
        val pad = 6 * k
        if (mode == 0) {
            val r = min(H.toFloat(), W * .5f) / 2 - pad
            val cx = pad + r
            drawClock(c, cv, cx, H / 2f, r, lk, w, false)
            val box = RectF(cx + r + 10 * k, pad, W - pad, H - pad)
            drawPanel(c, cv, box, lk, w, k)
        } else {
            val r = min(W, H) / 2f - pad
            drawClock(c, cv, W / 2f, H / 2f, r, lk, w, mode == 1)
        }
        return b
    }
}
