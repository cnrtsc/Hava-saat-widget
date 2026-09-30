package com.caner.sketchweather

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/** Temalı widget'lar: nokta matris, flip, İsviçre, ilerleme, 24 saat, terminal, gazete, neumorfik. */
object Themes {
    private val tr = Locale("tr", "TR")
    const val DOT = 0; const val FLIP = 1; const val SWISS = 2; const val PROG = 3
    const val SECTOR = 4; const val TERM = 5; const val PAPER = 6; const val NEU = 7

    private val DEF_ICONS = intArrayOf(12, 3, 10, 3, 3, 9, 10, 10)

    /** Tema kendi ikon setini önerir; widget'a özel seçim yapıldıysa o kullanılır. */
    private fun icon(c: Context, theme: Int, code: Int, day: Boolean): Int {
        val id = Cfg.current()
        val p = WeatherRepo.prefs(c)
        val set = if (id != null && p.contains(Cfg.wkey(id, "icons"))) Style.iconSet(c) else DEF_ICONS[theme]
        return Style.iconFrom(set.coerceIn(0, Style.ICONS.size - 1), code, day)
    }

    private fun font(c: Context, res: Int): Typeface = try { c.resources.getFont(res) } catch (e: Exception) { Typeface.DEFAULT }
    private fun tp(tf: Typeface, size: Float, col: Int, al: Paint.Align = Paint.Align.LEFT) =
        Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = tf; textSize = size; color = col; textAlign = al }
    private fun a(col: Int, f: Float) = Color.argb((Color.alpha(col) * f).toInt().coerceIn(0, 255), Color.red(col), Color.green(col), Color.blue(col))

    private fun pin(c: Context, cv: Canvas, x: Float, base: Float, size: Float, col: Int): Float {
        try {
            val d = c.getDrawable(R.drawable.pin)!!.mutate(); d.setTint(col)
            d.setBounds(x.toInt(), (base - size * .95f).toInt(), (x + size).toInt(), (base + size * .05f).toInt()); d.draw(cv)
        } catch (e: Exception) { }
        return x + size * 1.1f
    }

    private fun ic(c: Context, cv: Canvas, res: Int, cx: Float, cy: Float, s: Float) = Combo.icon(c, cv, res, cx, cy, s)

    fun render(c: Context, w: Weather?, wdp: Int, hdp: Int, theme: Int): Bitmap {
        val k = 2.5f
        val W = (wdp.coerceIn(100, 560) * k).toInt()
        val H = (hdp.coerceIn(80, 400) * k).toInt()
        val b = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888)
        val cv = Canvas(b)
        val op = Style.opacity(c) / 100f
        val Wf = W.toFloat(); val Hf = H.toFloat()
        val now = Date()
        val cal = Calendar.getInstance()
        val hh = SimpleDateFormat("HH", tr).format(now); val mm = SimpleDateFormat("mm", tr).format(now)
        val city = WeatherRepo.city(c)
        val temp = if (w != null) "${w.temp}°" else "--°"
        val desc = if (w != null) WeatherRepo.label(w.code) else ""
        val inter = font(c, R.font.f_inter_t); val interL = font(c, R.font.f_inter_c)
        val bg = Paint(Paint.ANTI_ALIAS_FLAG)
        when (theme) {
            DOT -> {
                bg.color = a(Color.rgb(12, 12, 12), op * .95f)
                cv.drawRoundRect(RectF(6f, 6f, Wf - 6, Hf - 6), Hf * .16f, Hf * .16f, bg)
                val pad = Hf * .1f
                // üst: konum + ikon + derece
                val lp = tp(inter, Hf * .085f, Color.rgb(210, 210, 210))
                val x1 = pin(c, cv, pad, pad + Hf * .08f, Hf * .085f, Color.rgb(210, 210, 210))
                cv.drawText(city.uppercase(tr), x1, pad + Hf * .08f, lp)
                cv.drawCircle(Wf - pad - Hf * .03f, pad + Hf * .045f, Hf * .03f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(215, 25, 32) })
                // nokta matris saat
                val text = "$hh:$mm"
                var cols = -1
                for (ch in text) cols += if (ch == ':') 2 else 6
                val sz = min(Wf * .62f / cols.toFloat(), Hf * .5f / 7f)
                dots(cv, pad, Hf * .3f, text, sz, Color.WHITE)
                // sağ: ikon + derece
                val ix = Wf - pad - Hf * .2f
                if (w != null) ic(c, cv, icon(c, theme, w.code, w.isDay), ix, Hf * .47f, Hf * .34f)
                cv.drawText(temp, ix, Hf * .8f, tp(inter, Hf * .13f, Color.WHITE, Paint.Align.CENTER))
                cv.drawText(SimpleDateFormat("EEE d MMM", tr).format(now).uppercase(tr) + "   ·   " + desc.uppercase(tr), pad, Hf - pad, tp(inter, Hf * .075f, Color.rgb(170, 170, 170)))
            }
            FLIP -> {
                val cardW = (Wf - Hf * .3f) / 2; val top = Hf * .06f; val cardH = Hf * .64f
                for ((i, t) in listOf(hh, mm).withIndex()) {
                    val x = Hf * .1f + i * (cardW + Hf * .1f)
                    val r = RectF(x, top, x + cardW, top + cardH)
                    bg.color = a(Color.rgb(28, 28, 30), op); cv.drawRoundRect(r, Hf * .07f, Hf * .07f, bg)
                    val hi = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = a(Color.rgb(40, 40, 44), op) }
                    cv.drawRoundRect(RectF(x, top, x + cardW, top + cardH / 2), Hf * .07f, Hf * .07f, hi)
                    val f = tp(font(c, R.font.f_outfitb_c), cardH * .78f, Color.rgb(238, 238, 238), Paint.Align.CENTER)
                    val fm = f.fontMetrics
                    cv.drawText(t, r.centerX(), r.centerY() - (fm.ascent + fm.descent) / 2, f)
                    cv.drawRect(x, r.centerY() - Hf * .006f, x + cardW, r.centerY() + Hf * .006f, Paint().apply { color = Color.rgb(8, 8, 8) })
                    val pinP = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(70, 70, 74) }
                    cv.drawCircle(x, r.centerY(), Hf * .018f, pinP); cv.drawCircle(x + cardW, r.centerY(), Hf * .018f, pinP)
                }
                val y = Hf * .86f
                val sh = tp(inter, Hf * .1f, Color.WHITE).apply { setShadowLayer(6f, 0f, 2f, 0x99000000.toInt()) }
                var x = Hf * .12f
                if (w != null) { ic(c, cv, icon(c, theme, w.code, w.isDay), x + Hf * .09f, y - Hf * .03f, Hf * .2f); x += Hf * .22f }
                x = pin(c, cv, x, y, Hf * .09f, Color.WHITE)
                cv.drawText("$city   ·   $temp  $desc", x, y, sh)
            }
            SWISS -> {
                bg.color = a(Color.rgb(238, 236, 230), op)
                cv.drawRoundRect(RectF(6f, 6f, Wf - 6, Hf - 6), Hf * .13f, Hf * .13f, bg)
                val ink = Color.rgb(20, 20, 20); val orange = Color.rgb(240, 90, 30)
                val r = Hf * .41f; val cx = Hf * .52f; val cy = Hf / 2
                cv.drawCircle(cx, cy, r, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(250, 249, 245) })
                cv.drawCircle(cx, cy, r, Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = r * .02f; color = ink })
                val lp = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = ink; strokeCap = Paint.Cap.BUTT }
                for (i in 0 until 60) {
                    val an = Math.toRadians(i * 6.0 - 90); val mj = i % 5 == 0
                    lp.strokeWidth = if (mj) r * .035f else r * .012f
                    val r1 = r * (if (mj) .8f else .88f)
                    cv.drawLine(cx + r1 * cos(an).toFloat(), cy + r1 * sin(an).toFloat(), cx + r * .94f * cos(an).toFloat(), cy + r * .94f * sin(an).toFloat(), lp)
                }
                val m = cal.get(Calendar.MINUTE); val h = cal.get(Calendar.HOUR) + m / 60f
                fun hand(deg: Double, len: Float, wd: Float, col: Int, back: Float = 0f) {
                    val an = Math.toRadians(deg - 90); lp.color = col; lp.strokeWidth = wd
                    cv.drawLine(cx - back * cos(an).toFloat(), cy - back * sin(an).toFloat(), cx + len * cos(an).toFloat(), cy + len * sin(an).toFloat(), lp)
                }
                hand(h * 30.0, r * .55f, r * .07f, ink); hand(m * 6.0, r * .8f, r * .045f, ink)
                hand(m * 6.0 + 180, r * .78f, r * .015f, orange, r * .2f)
                cv.drawCircle(cx, cy, r * .06f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = orange })
                val tx = Hf * 1.08f
                cv.drawText(city.uppercase(tr), tx + Hf * .1f, Hf * .2f, tp(inter, Hf * .07f, orange))
                pin(c, cv, tx, Hf * .2f, Hf * .075f, orange)
                if (w != null) ic(c, cv, icon(c, theme, w.code, w.isDay), Wf - Hf * .25f, Hf * .4f, Hf * .32f)
                cv.drawText(temp, tx, Hf * .52f, tp(interL, Hf * .26f, ink))
                cv.drawText(desc, tx, Hf * .68f, tp(inter, Hf * .085f, ink))
                w?.days?.firstOrNull()?.let { cv.drawText("Y ${it.max}°   D ${it.min}°", tx, Hf * .8f, tp(inter, Hf * .075f, Color.rgb(110, 110, 110))) }
                cv.drawText(SimpleDateFormat("EEEE d MMMM", tr).format(now), tx, Hf * .9f, tp(inter, Hf * .065f, Color.rgb(110, 110, 110)))
            }
            PROG -> {
                bg.color = a(Color.rgb(24, 26, 34), op * .88f)
                cv.drawRoundRect(RectF(6f, 6f, Wf - 6, Hf - 6), Hf * .13f, Hf * .13f, bg)
                val pad = Hf * .1f
                cv.drawText("$hh:$mm", pad, Hf * .24f, tp(font(c, R.font.f_outfit_c), Hf * .2f, Color.WHITE))
                val x1 = pin(c, cv, Wf * .42f, Hf * .2f, Hf * .08f, Color.rgb(210, 214, 222))
                cv.drawText(city, x1, Hf * .2f, tp(inter, Hf * .08f, Color.rgb(210, 214, 222)))
                if (w != null) ic(c, cv, icon(c, theme, w.code, w.isDay), Wf - pad - Hf * .3f, Hf * .15f, Hf * .22f)
                cv.drawText(temp, Wf - pad, Hf * .2f, tp(inter, Hf * .11f, Color.WHITE, Paint.Align.RIGHT))
                val dayF = (cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)) / 1440f
                val dow = (cal.get(Calendar.DAY_OF_WEEK) + 5) % 7
                val weekF = (dow + dayF) / 7f
                val monF = (cal.get(Calendar.DAY_OF_MONTH) - 1 + dayF) / cal.getActualMaximum(Calendar.DAY_OF_MONTH)
                val yearF = (cal.get(Calendar.DAY_OF_YEAR) - 1 + dayF) / cal.getActualMaximum(Calendar.DAY_OF_YEAR)
                val rows = listOf(Triple("Gün", dayF, Color.rgb(255, 176, 60)), Triple("Hafta", weekF, Color.rgb(120, 190, 255)),
                    Triple("Ay", monF, Color.rgb(150, 220, 150)), Triple("Yıl", yearF, Color.rgb(230, 130, 200)))
                val bx0 = pad + Hf * .42f; val bx1 = Wf - pad - Hf * .3f
                rows.forEachIndexed { i, (lab, f, col) ->
                    val y = Hf * .40f + i * Hf * .145f
                    cv.drawText(lab, pad, y + Hf * .03f, tp(inter, Hf * .075f, Color.rgb(210, 214, 222)))
                    val rr = Hf * .03f
                    cv.drawRoundRect(RectF(bx0, y - rr, bx1, y + rr), rr, rr, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x28FFFFFF })
                    cv.drawRoundRect(RectF(bx0, y - rr, bx0 + (bx1 - bx0) * f, y + rr), rr, rr, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = col })
                    cv.drawText("%${(f * 100).toInt()}", Wf - pad, y + Hf * .03f, tp(inter, Hf * .075f, Color.WHITE, Paint.Align.RIGHT))
                }
            }
            SECTOR -> {
                val cx = Wf / 2; val cy = Hf / 2; val RR = min(Wf, Hf) / 2 - 8
                bg.color = a(Color.rgb(22, 24, 32), op * .95f); cv.drawCircle(cx, cy, RR, bg)
                val rise = Mod.hm(w?.sunrise) ?: 420; val set = Mod.hm(w?.sunset) ?: 1140
                val rr = RR * .78f
                val arc = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = RR * .13f }
                val oval = RectF(cx - rr, cy - rr, cx + rr, cy + rr)
                arc.color = Color.rgb(255, 196, 90); cv.drawArc(oval, -90f + rise / 4f, (set - rise) / 4f, false, arc)
                arc.color = Color.rgb(70, 90, 150); cv.drawArc(oval, -90f + set / 4f, (1440 - set + rise) / 4f, false, arc)
                val tick = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(200, 200, 210); strokeWidth = RR * .015f }
                val nf = tp(inter, RR * .075f, Color.rgb(200, 200, 210), Paint.Align.CENTER)
                for (hr in 0 until 24) {
                    val an = Math.toRadians(hr * 15.0 - 90)
                    cv.drawLine(cx + RR * .6f * cos(an).toFloat(), cy + RR * .6f * sin(an).toFloat(), cx + RR * .66f * cos(an).toFloat(), cy + RR * .66f * sin(an).toFloat(), tick)
                    if (hr % 6 == 0) cv.drawText(hr.toString(), cx + RR * .52f * cos(an).toFloat(), cy + RR * .52f * sin(an).toFloat() + RR * .03f, nf)
                }
                val nowM = cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)
                val an = Math.toRadians(nowM / 4.0 - 90)
                cv.drawLine(cx, cy, cx + RR * .93f * cos(an).toFloat(), cy + RR * .93f * sin(an).toFloat(), Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; strokeWidth = RR * .03f; strokeCap = Paint.Cap.ROUND })
                cv.drawCircle(cx, cy, RR * .33f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = a(Color.rgb(22, 24, 32), .92f) })
                if (w != null) ic(c, cv, icon(c, theme, w.code, w.isDay), cx, cy - RR * .17f, RR * .22f)
                cv.drawText("$hh:$mm", cx, cy + RR * .07f, tp(font(c, R.font.f_outfit_c), RR * .16f, Color.WHITE, Paint.Align.CENTER))
                cv.drawText("$temp  $city", cx, cy + RR * .2f, tp(inter, RR * .075f, Color.rgb(210, 214, 222), Paint.Align.CENTER))
            }
            TERM -> {
                bg.color = a(Color.rgb(10, 14, 10), op * .94f)
                cv.drawRoundRect(RectF(6f, 6f, Wf - 6, Hf - 6), Hf * .08f, Hf * .08f, bg)
                val mono = font(c, R.font.f_mono_t)
                val g = Color.rgb(90, 240, 120); val g2 = Color.rgb(180, 255, 190)
                val pad = Hf * .1f
                val tomorrow = w?.days?.getOrNull(1)
                val lines = listOf(
                    "> saat" to "$hh:$mm", "> konum" to city.lowercase(tr), "> hava" to "$temp ${desc.lowercase(tr)}",
                    "> yarın" to (tomorrow?.let { "${it.max}°/${it.min}°" } ?: "--") + (w?.rainChance?.let { "  yağmur %$it" } ?: "")
                )
                val ls = (Hf - 2 * pad) / 4.8f
                lines.forEachIndexed { i, (l, v) ->
                    val y = pad + ls * (i + .8f)
                    val p = tp(mono, ls * .52f, if (i == 0) g2 else g)
                    cv.drawText(l, pad, y, p)
                    cv.drawText(v, pad + Wf * .26f, y, p)
                }
                val cursorOn = cal.get(Calendar.MINUTE) % 2 == 0
                if (cursorOn) cv.drawRect(pad, Hf - pad - ls * .5f, pad + ls * .3f, Hf - pad, Paint().apply { color = g })
                if (w != null) ic(c, cv, icon(c, theme, w.code, w.isDay), Wf - pad - Hf * .15f, pad + Hf * .15f, Hf * .3f)
            }
            PAPER -> {
                bg.color = a(Color.rgb(244, 238, 226), op)
                cv.drawRoundRect(RectF(6f, 6f, Wf - 6, Hf - 6), Hf * .06f, Hf * .06f, bg)
                val ink = Color.rgb(28, 26, 24); val grey = Color.rgb(110, 100, 92)
                val serif = font(c, R.font.f_serif_c)
                val pad = Hf * .09f
                cv.drawText("HAVA GAZETESİ", pad, pad + Hf * .05f, tp(inter, Hf * .055f, grey))
                cv.drawText("${cal.get(Calendar.YEAR)} · SAYI ${cal.get(Calendar.DAY_OF_YEAR)}", Wf - pad, pad + Hf * .05f, tp(inter, Hf * .055f, grey, Paint.Align.RIGHT))
                val rule = Paint().apply { color = ink; strokeWidth = Hf * .008f }
                cv.drawLine(pad, pad + Hf * .09f, Wf - pad, pad + Hf * .09f, rule)
                val colX = Wf * .6f
                cv.drawText(SimpleDateFormat("EEEE", tr).format(now), pad, Hf * .48f, tp(serif, Hf * .24f, ink))
                val x1 = pin(c, cv, pad, Hf * .62f, Hf * .07f, grey)
                cv.drawText(SimpleDateFormat("d MMMM", tr).format(now) + " · " + city, x1, Hf * .62f, tp(inter, Hf * .07f, grey))
                cv.drawText("$hh:$mm", pad, Hf * .86f, tp(serif, Hf * .16f, ink))
                cv.drawLine(colX - Hf * .06f, pad + Hf * .16f, colX - Hf * .06f, Hf - pad, Paint().apply { color = Color.rgb(170, 160, 150); strokeWidth = 2f })
                if (w != null) ic(c, cv, icon(c, theme, w.code, w.isDay), Wf - pad - Hf * .15f, Hf * .38f, Hf * .28f)
                cv.drawText(temp, colX, Hf * .47f, tp(serif, Hf * .2f, ink))
                cv.drawText("$desc.", colX, Hf * .62f, tp(inter, Hf * .065f, ink))
                val mood = when {
                    w == null -> ""
                    (w.rainChance ?: 0) >= 50 -> "Şemsiyeni unutma."
                    w.temp >= 25 -> "Günlük sıcak geçiyor."
                    w.temp <= 8 -> "Sıkı giyin."
                    else -> "Yürüyüş için güzel bir gün."
                }
                cv.drawText(mood, colX, Hf * .73f, tp(inter, Hf * .065f, ink))
                w?.days?.getOrNull(1)?.let { cv.drawText("Yarın ${it.max}°/${it.min}°", colX, Hf * .86f, tp(inter, Hf * .065f, grey)) }
            }
            else -> { // NEU
                val base = Color.rgb(226, 229, 236)
                bg.color = a(base, op)
                cv.drawRoundRect(RectF(6f, 6f, Wf - 6, Hf - 6), min(Wf, Hf) * .2f, min(Wf, Hf) * .2f, bg)
                val cx = Wf / 2; val cy = Hf / 2; val RR = min(Wf, Hf) * .34f
                val sh = Paint(Paint.ANTI_ALIAS_FLAG).apply { maskFilter = BlurMaskFilter(RR * .12f, BlurMaskFilter.Blur.NORMAL) }
                sh.color = a(Color.rgb(160, 168, 184), op); cv.drawCircle(cx + RR * .08f, cy + RR * .08f, RR, sh)
                sh.color = a(Color.WHITE, op); cv.drawCircle(cx - RR * .08f, cy - RR * .08f, RR, sh)
                cv.drawCircle(cx, cy, RR, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = base })
                val m = cal.get(Calendar.MINUTE)
                cv.drawArc(RectF(cx - RR * .86f, cy - RR * .86f, cx + RR * .86f, cy + RR * .86f), -90f, 360f * m / 60f, false,
                    Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = RR * .06f; strokeCap = Paint.Cap.ROUND; color = Color.rgb(120, 150, 255) })
                if (w != null) ic(c, cv, icon(c, theme, w.code, w.isDay), cx, cy - RR * .42f, RR * .38f)
                val quick = font(c, R.font.f_quick_c)
                cv.drawText("$hh:$mm", cx, cy + RR * .14f, tp(quick, RR * .36f, Color.rgb(70, 78, 96), Paint.Align.CENTER))
                cv.drawText("$temp · $city", cx, cy + RR * .45f, tp(quick, RR * .13f, Color.rgb(120, 128, 146), Paint.Align.CENTER))
            }
        }
        return b
    }

    private val G = mapOf(
        '0' to arrayOf("01110", "10001", "10001", "10001", "10001", "10001", "01110"),
        '1' to arrayOf("00100", "01100", "00100", "00100", "00100", "00100", "01110"),
        '2' to arrayOf("01110", "10001", "00001", "00010", "00100", "01000", "11111"),
        '3' to arrayOf("11110", "00001", "00001", "01110", "00001", "00001", "11110"),
        '4' to arrayOf("00010", "00110", "01010", "10010", "11111", "00010", "00010"),
        '5' to arrayOf("11111", "10000", "11110", "00001", "00001", "10001", "01110"),
        '6' to arrayOf("00110", "01000", "10000", "11110", "10001", "10001", "01110"),
        '7' to arrayOf("11111", "00001", "00010", "00100", "01000", "01000", "01000"),
        '8' to arrayOf("01110", "10001", "10001", "01110", "10001", "10001", "01110"),
        '9' to arrayOf("01110", "10001", "10001", "01111", "00001", "00010", "01100"),
        ':' to arrayOf("0", "0", "1", "0", "1", "0", "0")
    )

    private fun dots(cv: Canvas, x0: Float, y0: Float, text: String, sz: Float, col: Int) {
        val on = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = col }
        val off = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x16FFFFFF }
        var x = x0
        for (ch in text) {
            val g = G[ch] ?: continue
            for ((r, row) in g.withIndex()) for ((ci, v) in row.withIndex()) {
                val cx = x + ci * sz + sz / 2; val cy = y0 + r * sz + sz / 2
                if (v == '1') cv.drawCircle(cx, cy, sz * .38f, on) else cv.drawCircle(cx, cy, sz * .14f, off)
            }
            x += (g[0].length + 1) * sz
        }
    }
}
