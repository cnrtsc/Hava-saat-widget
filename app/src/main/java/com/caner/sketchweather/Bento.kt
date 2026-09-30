package com.caner.sketchweather

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.min

/** İçeriği seçilebilir bento: her kutuya istediğin modülü koy. */
object Bento {
    val CONTENT = arrayOf(
        "Dijital saat", "Analog saat", "Hava", "Hissedilen", "Nem ve rüzgâr", "Konum ve tarih",
        "Güneş", "Pil", "Takvim", "Yarın", "Yağmur ihtimali", "UV ve hava kalitesi", "Sonraki saatler", "Ay evresi"
    )
    val ARRANGE = arrayOf("Büyük kutu solda", "Büyük kutu sağda", "Dört eşit kutu")
    val ALIGN = arrayOf("Sola yaslı", "Ortalı")
    val CLOCK_STYLE = arrayOf("Saat ve dakika alt alta", "Tek satır")
    private val DEF = intArrayOf(0, 2, 3, 4)
    private val tr = Locale("tr", "TR")

    fun tile(c: Context, i: Int) = Cfg.int(c, "tile$i", DEF[i]).coerceIn(0, CONTENT.size - 1)
    fun arrange(c: Context, square: Boolean) = if (square) 2 else Cfg.int(c, "arrange", 0).coerceIn(0, 2)
    fun align(c: Context) = Cfg.int(c, "tileAlign", 0).coerceIn(0, 1)
    fun clockStyle(c: Context) = Cfg.int(c, "tileClock", 0).coerceIn(0, 1)
    fun weatherColor(c: Context) = Cfg.bool(c, "weatherTint", true)

    private class Tone(val bg: Int, val ink: Int, val sub: Int, val accent: Int)

    private fun withAlpha(col: Int, f: Float) = Color.argb((Color.alpha(col) * f).toInt(), Color.red(col), Color.green(col), Color.blue(col))

    private fun look(t: Tone): Combo.Look = Combo.Look(Color.TRANSPARENT, t.ink, t.sub, t.accent, false, 0)

    fun render(c: Context, w: Weather?, wdp: Int, hdp: Int, square: Boolean): Bitmap {
        val k = 2.5f
        val W = (wdp.coerceIn(100, 560) * k).toInt()
        val H = (hdp.coerceIn(80, 400) * k).toInt()
        val b = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888)
        val cv = Canvas(b)
        val pal = Palette.get(c)
        val op = Style.opacity(c) / 100f
        val g = 6 * k
        val tc = Style.textColor(c)
        val dark = Tone(withAlpha(pal.dark, op), tc ?: pal.onDark, pal.onDarkSub, pal.accentLight)
        val accent = Tone(withAlpha(pal.accent, op), tc ?: pal.onAccent, withAlpha(pal.onAccent, .8f), pal.accentLight)
        val light = Tone(withAlpha(pal.container, op), tc ?: pal.onCont, pal.onContSub, pal.accent)
        val rects: List<Pair<RectF, Int>>   // kutu ve şekil türü (0 yuvarlatılmış, 1 hap, 2 daire)
        val Wf = W.toFloat(); val Hf = H.toFloat()
        when (arrange(c, square)) {
            2 -> {
                val cw = (Wf - 3 * g) / 2; val ch = (Hf - 3 * g) / 2
                rects = listOf(
                    Pair(RectF(g, g, g + cw, g + ch), 0), Pair(RectF(2 * g + cw, g, Wf - g, g + ch), 0),
                    Pair(RectF(g, 2 * g + ch, g + cw, Hf - g), 0), Pair(RectF(2 * g + cw, 2 * g + ch, Wf - g, Hf - g), 0)
                )
            }
            else -> {
                val bigW = (Wf - 3 * g) * .55f
                val left = arrange(c, square) == 0
                val bx0 = if (left) g else Wf - g - bigW
                val sx0 = if (left) 2 * g + bigW else g
                val sx1 = if (left) Wf - g else Wf - 2 * g - bigW
                val topH = (Hf - 3 * g) * .5f
                val bh = Hf - 3 * g - topH
                val circleD = min(bh, (sx1 - sx0 - g) * .58f)
                rects = listOf(
                    Pair(RectF(bx0, g, bx0 + bigW, Hf - g), 0),
                    Pair(RectF(sx0, g, sx1, g + topH), 1),
                    Pair(RectF(sx0, 2 * g + topH, sx0 + circleD, 2 * g + topH + circleD), 2),
                    Pair(RectF(sx0 + circleD + g, 2 * g + topH, sx1, Hf - g), 1)
                )
            }
        }
        val tones = arrayOf(dark, accent, light, dark)
        val shapeOverride = Cfg.int(c, "boxShape", 0)
        for (i in 0 until 4) {
            val (r, shape0) = rects[i]
            val content = tile(c, i)
            var tone = tones[i]
            if (content == 2 && weatherColor(c)) {
                val (col, darkText) = WeatherRepo.conditionColor(w)
                tone = if (darkText) Tone(withAlpha(col, op), tc ?: 0xFF1C2230.toInt(), 0xFF4A5566.toInt(), 0xFF1C2230.toInt())
                else Tone(withAlpha(col, op), tc ?: Color.WHITE, 0xDDFFFFFF.toInt(), 0xFFFFE3A6.toInt())
            }
            val shape = when (shapeOverride) { 1 -> 0; 2 -> 3; 3 -> 1; else -> shape0 }
            val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = tone.bg }
            when (shape) {
                2 -> cv.drawCircle(r.centerX(), r.centerY(), min(r.width(), r.height()) / 2, p)
                1 -> cv.drawRoundRect(r, min(r.width(), r.height()) / 2, min(r.width(), r.height()) / 2, p)
                3 -> cv.drawRoundRect(r, 8 * k, 8 * k, p)
                else -> cv.drawRoundRect(r, 26 * k, 26 * k, p)
            }
            if (Combo.rim(c)) {
                val rp = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 2.5f; color = withAlpha(tone.ink, .35f) }
                when (shape) {
                    2 -> cv.drawCircle(r.centerX(), r.centerY(), min(r.width(), r.height()) / 2, rp)
                    else -> cv.drawRoundRect(r, 20 * k, 20 * k, rp)
                }
            }
            val inner = if (shape == 2) {
                val s = min(r.width(), r.height()) * .72f
                RectF(r.centerX() - s / 2, r.centerY() - s / 2, r.centerX() + s / 2, r.centerY() + s / 2)
            } else RectF(r.left + 12 * k, r.top + 10 * k, r.right - 12 * k, r.bottom - 10 * k)
            drawContent(c, cv, inner, content, tone, w)
        }
        return b
    }

    private fun drawContent(c: Context, cv: Canvas, r: RectF, which: Int, t: Tone, w: Weather?) {
        val font = Style.font(c)
        val tf = Style.textTypeface(c, font)
        val cf = Style.clockTypeface(c, font)
        val lk = look(t)
        val wide = r.width() > r.height() * 1.5f
        val cx = r.centerX(); val cy = r.centerY()
        val s = min(r.width(), r.height())
        val now = Date()
        val center = align(c) == 1
        fun txt(tfc: android.graphics.Typeface, size: Float, col: Int, al: Paint.Align = Paint.Align.CENTER) = Combo.text(tfc, size, col, lk, al)
        // her dakika hafif süzülme (hareketli ikon ayarı açıksa)
        val bob = if (Style.anim(c) > 0) (if (Calendar.getInstance().get(Calendar.MINUTE) % 2 == 0) -s * .03f else s * .03f) else 0f
        when (which) {
            0 -> {
                val hh = SimpleDateFormat("HH", tr).format(now); val mm = SimpleDateFormat("mm", tr).format(now)
                val al = if (center) Paint.Align.CENTER else Paint.Align.LEFT
                val x = if (center) cx else r.left
                if (clockStyle(c) == 0 && !wide) {
                    val top = SimpleDateFormat("EEEE", tr).format(now)
                    val big = min(r.height() * .38f, r.width() * .48f)
                    cv.drawText(top, x, r.top + r.height() * .1f, txt(tf, r.height() * .07f, t.sub, al))
                    cv.drawText(hh, x, r.top + r.height() * .55f, Lock.clockPaint(c, big, t.ink, lk).apply { textAlign = al })
                    cv.drawText(mm, x, r.top + r.height() * .97f, Lock.clockPaint(c, big, t.accent, lk).apply { textAlign = al })
                } else {
                    val size = min(r.height() * .5f, r.width() * .3f)
                    val cp = Lock.clockPaint(c, size, t.ink, lk).apply { textAlign = al }
                    val fm = cp.fontMetrics
                    cv.drawText("$hh:$mm", x, cy - (fm.ascent + fm.descent) / 2, cp)
                    if (!wide) cv.drawText(SimpleDateFormat("EEEE, d MMM", tr).format(now), x, r.bottom - r.height() * .05f, txt(tf, r.height() * .08f, t.sub, al))
                }
            }
            1 -> Combo.drawClock(c, cv, cx, cy, s * .5f, lk, null, false)
            2 -> {
                val res = if (w != null) Style.icon(c, w.code, w.isDay) else R.drawable.r_cloud
                val temp = if (w != null) "${w.temp}°" else "--°"
                if (wide) {
                    Combo.icon(c, cv, res, r.left + r.height() * .5f, cy + bob, r.height() * 1.0f)
                    cv.drawText(temp, r.right, cy + r.height() * .12f, txt(cf, r.height() * .5f, t.ink, Paint.Align.RIGHT))
                    if (w != null) cv.drawText(WeatherRepo.label(w.code), r.right, cy + r.height() * .42f, txt(tf, r.height() * .16f, t.sub, Paint.Align.RIGHT))
                } else {
                    Combo.icon(c, cv, res, cx, r.top + r.height() * .32f + bob, s * .58f)
                    cv.drawText(temp, cx, r.top + r.height() * .82f, txt(cf, s * .26f, t.ink))
                    if (w != null) cv.drawText(WeatherRepo.label(w.code), cx, r.top + r.height() * .98f, txt(tf, s * .09f, t.sub))
                }
            }
            3 -> {
                cv.drawText(w?.feels?.let { "$it°" } ?: "--°", cx, cy + s * .08f, txt(cf, s * .36f, t.ink))
                cv.drawText("hissedilen", cx, cy + s * .3f, txt(tf, s * .12f, t.sub))
            }
            4 -> {
                cv.drawText(w?.humidity?.let { "%$it" } ?: "--", cx, cy - s * .02f, txt(tf, s * .22f, t.ink))
                cv.drawText(w?.wind?.let { "$it km/s" } ?: "--", cx, cy + s * .26f, txt(tf, s * .14f, t.sub))
            }
            5 -> {
                cv.drawText(WeatherRepo.city(c), cx, cy - s * .02f, txt(tf, s * .2f, t.ink))
                cv.drawText(SimpleDateFormat("d MMMM EEEE", tr).format(now), cx, cy + s * .24f, txt(tf, s * .12f, t.sub))
            }
            6 -> {
                Combo.icon(c, cv, R.drawable.st_rise, cx - s * .3f, cy - s * .18f, s * .22f)
                cv.drawText(w?.sunrise ?: "--:--", cx + s * .12f, cy - s * .1f, txt(tf, s * .17f, t.ink))
                Combo.icon(c, cv, R.drawable.st_set, cx - s * .3f, cy + s * .2f, s * .22f)
                cv.drawText(w?.sunset ?: "--:--", cx + s * .12f, cy + s * .28f, txt(tf, s * .17f, t.ink))
            }
            7 -> {
                val bi = try { c.applicationContext.registerReceiver(null, android.content.IntentFilter(android.content.Intent.ACTION_BATTERY_CHANGED)) } catch (e: Exception) { null }
                val lvl = bi?.let { val l = it.getIntExtra(android.os.BatteryManager.EXTRA_LEVEL, -1); val sc = it.getIntExtra(android.os.BatteryManager.EXTRA_SCALE, 100); if (l >= 0) l * 100 / sc else -1 } ?: -1
                val rr = RectF(cx - s * .42f, cy - s * .42f, cx + s * .42f, cy + s * .42f)
                val ap = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = s * .07f; strokeCap = Paint.Cap.ROUND; color = withAlpha(t.ink, .25f) }
                cv.drawArc(rr, 120f, 300f, false, ap)
                ap.color = t.accent; cv.drawArc(rr, 120f, 300f * lvl.coerceAtLeast(0) / 100f, false, ap)
                cv.drawText(if (lvl >= 0) "%$lvl" else "--", cx, cy + s * .1f, txt(cf, s * .26f, t.ink))
            }
            8 -> {
                cv.drawText(SimpleDateFormat("EEEE", tr).format(now), cx, cy - s * .22f, txt(tf, s * .12f, t.accent))
                cv.drawText(SimpleDateFormat("d", tr).format(now), cx, cy + s * .18f, txt(cf, s * .42f, t.ink))
                cv.drawText(SimpleDateFormat("MMMM", tr).format(now), cx, cy + s * .38f, txt(tf, s * .11f, t.sub))
            }
            9 -> {
                val d = w?.days?.getOrNull(1)
                cv.drawText("Yarın", cx, r.top + s * .16f, txt(tf, s * .12f, t.sub))
                if (d != null) {
                    Combo.icon(c, cv, Style.icon(c, d.code, true), cx, cy + bob, s * .42f)
                    cv.drawText("${d.max}° / ${d.min}°", cx, r.bottom - s * .04f, txt(tf, s * .14f, t.ink))
                }
            }
            10 -> {
                Combo.icon(c, cv, R.drawable.st_drop, cx, cy - s * .2f, s * .22f)
                cv.drawText(w?.rainChance?.let { "%$it" } ?: "--", cx, cy + s * .16f, txt(cf, s * .26f, t.ink))
                cv.drawText("yağmur ihtimali", cx, cy + s * .34f, txt(tf, s * .1f, t.sub))
            }
            11 -> {
                val uv = w?.uv; val aq = w?.aqi
                cv.drawText(uv?.let { "UV $it" } ?: "UV --", cx, cy - s * .08f, txt(tf, s * .2f, t.ink))
                cv.drawText(uv?.let { WeatherRepo.uvLabel(it) } ?: "", cx, cy + s * .08f, txt(tf, s * .1f, t.sub))
                cv.drawText(aq?.let { "Hava: ${WeatherRepo.aqiLabel(it)}" } ?: "", cx, cy + s * .3f, txt(tf, s * .11f, t.ink))
            }
            12 -> {
                val hrs = w?.hours ?: return
                for (i in 0 until 3) {
                    val h = hrs.getOrNull(1 + i * 2) ?: continue
                    val x = r.left + r.width() * (i + .5f) / 3
                    cv.drawText(h.time.substring(0, 2), x, r.top + r.height() * .2f, txt(tf, s * .12f, t.sub))
                    Combo.icon(c, cv, Style.icon(c, h.code, h.isDay), x, cy + bob, min(r.width() / 3.4f, s * .36f))
                    cv.drawText("${h.temp}°", x, r.bottom - r.height() * .03f, txt(tf, s * .14f, t.ink))
                }
            }
            else -> {
                val ph = Mod.moonPhase()
                val R = s * .24f; val my = cy - s * .1f
                cv.drawCircle(cx, my, R, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(60, 64, 76) })
                val lit = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(246, 232, 196) }
                val kx = kotlin.math.cos(2 * Math.PI * ph).toFloat()
                cv.drawArc(RectF(cx - R, my - R, cx + R, my + R), if (ph < .5) -90f else 90f, 180f, true, lit)
                cv.drawOval(RectF(cx - R * kotlin.math.abs(kx), my - R, cx + R * kotlin.math.abs(kx), my + R),
                    if (kx > 0) Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(60, 64, 76) } else lit)
                val illum = ((1 - kotlin.math.cos(2 * Math.PI * ph)) / 2 * 100).toInt()
                cv.drawText("%$illum aydınlık", cx, cy + s * .34f, txt(tf, s * .11f, t.ink))
            }
        }
    }
}
