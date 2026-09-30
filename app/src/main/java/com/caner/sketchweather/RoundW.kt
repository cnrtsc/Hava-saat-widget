package com.caner.sketchweather

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Date
import java.util.Locale
import kotlin.math.min

/** Sade, okunaklı yuvarlak hava tasarımları: 0 sade, 1 detaylı, 2 üç günlük. */
object RoundW {
    private fun arrow(cv: Canvas, x: Float, y: Float, s: Float, up: Boolean, p: Paint) {
        val path = Path()
        val dir = if (up) -1 else 1
        path.moveTo(x, y - dir * s * .5f)
        path.lineTo(x, y + dir * s * .48f)
        path.moveTo(x - s * .32f, y + dir * s * .12f)
        path.lineTo(x, y + dir * s * .48f)
        path.lineTo(x + s * .32f, y + dir * s * .12f)
        val sp = Paint(p).apply { style = Paint.Style.STROKE; strokeWidth = s * .13f; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND }
        cv.drawPath(path, sp)
    }

    private fun hiLo(cv: Canvas, cx: Float, y: Float, d: Float, tf: android.graphics.Typeface, lk: Combo.Look, hi: Int, lo: Int) {
        val tp = Combo.text(tf, d * .052f, lk.ink, lk, Paint.Align.LEFT)
        val a = "$hi°"; val b = "$lo°"
        val s = d * .05f
        val wA = s + d * .012f + tp.measureText(a)
        val wB = s + d * .012f + tp.measureText(b)
        val gap = d * .07f
        var x = cx - (wA + gap + wB) / 2
        arrow(cv, x + s / 2, y - s * .35f, s, true, tp); cv.drawText(a, x + s + d * .012f, y, tp)
        x += wA + gap
        arrow(cv, x + s / 2, y - s * .35f, s, false, tp); cv.drawText(b, x + s + d * .012f, y, tp)
    }

    /** d: dairenin çapı; içerik dairenin ortasına göre yerleşir. */
    fun content(c: Context, cv: Canvas, cx: Float, cy: Float, d: Float, lk: Combo.Look, w: Weather?, variant: Int, rect: Boolean = false, box: RectF? = null) {
        val font = Style.font(c)
        val tf = Style.textTypeface(c, font)
        val cf = Style.clockTypeface(c, font)
        val tr = Locale("tr", "TR")
        // konum
        val city = WeatherRepo.city(c)
        val cp = Combo.text(tf, d * .062f, lk.ink, lk)
        val tw = cp.measureText(city)
        try {
            val pin = c.getDrawable(R.drawable.pin)!!.mutate()
            pin.setTint(lk.ink)
            val ps = d * .062f
            val py = cy - d * .355f
            pin.setBounds((cx - tw / 2 - ps * 1.1f + ps * .45f).toInt(), (py - ps * .85f).toInt(), (cx - tw / 2 - ps * .1f + ps * .45f).toInt(), (py + ps * .15f).toInt())
            pin.draw(cv)
        } catch (e: Exception) { }
        cv.drawText(city, cx + d * .03f, cy - d * .355f, cp)
        cv.drawText(SimpleDateFormat("d MMMM EEEE", tr).format(Date()), cx, cy - d * .285f, Combo.text(tf, d * .042f, lk.sub, lk))
        // ikon + derece + durum
        if (w != null) Combo.icon(c, cv, Style.icon(c, w.code, w.isDay), cx - d * .17f, cy - d * .07f, d * .30f)
        val tt = if (w != null) "${w.temp}°" else "--°"
        cv.drawText(tt, cx + d * .0f, cy + d * .03f, Combo.text(cf, d * .23f, lk.ink, lk, Paint.Align.LEFT))
        cv.drawText(if (w != null) WeatherRepo.label(w.code) else "Yükleniyor…", cx + d * .17f, cy + d * .10f, Combo.text(tf, d * .046f, lk.ink, lk))
        if (w == null) return
        val today = w.days.firstOrNull()
        val line = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = lk.sub; alpha = 90; strokeWidth = d * .004f }
        when (variant) {
            0 -> {
                cv.drawLine(cx - d * .30f, cy + d * .165f, cx + d * .30f, cy + d * .165f, line)
                if (today != null) hiLo(cv, cx, cy + d * .265f, d, tf, lk, today.max, today.min)
            }
            1 -> {
                cv.drawLine(cx - d * .33f, cy + d * .15f, cx + d * .33f, cy + d * .15f, line)
                val items = listOf(
                    Triple(R.drawable.st_wind, w.wind?.let { "$it km/s" } ?: "--", "Rüzgâr"),
                    Triple(R.drawable.st_drop, w.humidity?.let { "%$it" } ?: "--", "Nem"),
                    Triple(R.drawable.st_set, w.sunset ?: "--", "Gün batımı")
                )
                items.forEachIndexed { i, (ic, v, lab) ->
                    val x = cx + (i - 1) * d * .25f
                    val vp = Combo.text(tf, d * .045f, lk.ink, lk, Paint.Align.LEFT)
                    val iw = d * .05f
                    val total = iw + d * .012f + vp.measureText(v)
                    Combo.icon(c, cv, ic, x - total / 2 + iw / 2, cy + d * .205f, iw)
                    cv.drawText(v, x - total / 2 + iw + d * .012f, cy + d * .222f, vp)
                    cv.drawText(lab, x, cy + d * .265f, Combo.text(tf, d * .032f, lk.sub, lk))
                    if (i < 2) cv.drawLine(x + d * .125f, cy + d * .185f, x + d * .125f, cy + d * .265f, line)
                }
                if (today != null) hiLo(cv, cx, cy + d * .345f, d, tf, lk, today.max, today.min)
            }
            else -> {
                cv.drawLine(cx - d * .33f, cy + d * .15f, cx + d * .33f, cy + d * .15f, line)
                for (i in 0 until 3) {
                    val day = w.days.getOrNull(i + 1) ?: continue
                    val x = cx + (i - 1) * d * .22f
                    val name = LocalDate.parse(day.date).dayOfWeek.getDisplayName(TextStyle.SHORT, tr)
                    cv.drawText(name, x, cy + d * .205f, Combo.text(tf, d * .038f, lk.sub, lk))
                    Combo.icon(c, cv, Style.icon(c, day.code, true), x, cy + d * .26f, d * .085f)
                    val tp = Combo.text(tf, d * .036f, lk.ink, lk, Paint.Align.RIGHT)
                    cv.drawText("${day.max}°", x, cy + d * .34f, tp)
                    cv.drawText(" ${day.min}°", x, cy + d * .34f, Combo.text(tf, d * .036f, lk.sub, lk, Paint.Align.LEFT))
                }
            }
        }
    }

    fun render(c: Context, w: Weather?, wdp: Int, hdp: Int, variant: Int): Bitmap {
        val k = 2.5f
        val W = (wdp.coerceIn(80, 400) * k).toInt()
        val H = (hdp.coerceIn(80, 400) * k).toInt()
        val b = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888)
        val cv = Canvas(b)
        val lk = Combo.look(c, Palette.get(c))
        val d = min(W, H) - 8 * k
        val cx = W / 2f; val cy = H / 2f
        cv.drawCircle(cx, cy, d / 2, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = lk.fill })
        if (Combo.rim(c)) cv.drawCircle(cx, cy, d / 2, Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = d * .006f; color = lk.rimCol })
        content(c, cv, cx, cy, d, lk, w, variant)
        return b
    }
}
