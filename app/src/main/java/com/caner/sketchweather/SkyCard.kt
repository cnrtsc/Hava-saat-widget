package com.caner.sketchweather

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.SweepGradient
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/** Gökyüzü kartı: solda havaya göre gökyüzü resmi, sağda halkalı saat, altta seçilebilir 3 bilgi. */
object SkyCard {
    val ITEMS = arrayOf("Yarın", "Gün doğumu / batımı", "Yağmur ihtimali", "Rüzgâr", "Nem", "Hissedilen", "UV", "Hava kalitesi")
    private val DEF = intArrayOf(0, 1, 2)
    private val tr = Locale("tr", "TR")
    private val SKY = intArrayOf(R.drawable.sky_sun, R.drawable.sky_moon, R.drawable.sky_partly, R.drawable.sky_partly_night,
        R.drawable.sky_cloud, R.drawable.sky_fog, R.drawable.sky_rain, R.drawable.sky_rain, R.drawable.sky_snow, R.drawable.sky_storm)

    fun item(c: Context, i: Int) = Cfg.int(c, "sky$i", DEF[i]).coerceIn(0, ITEMS.size - 1)
    fun showIcon(c: Context) = Cfg.bool(c, "skyIcon", true)

    private fun a(col: Int, f: Float) = Color.argb((Color.alpha(col) * f).toInt().coerceIn(0, 255), Color.red(col), Color.green(col), Color.blue(col))

    fun render(c: Context, w: Weather?, wdp: Int, hdp: Int): Bitmap {
        val k = 2.5f
        val W = (wdp.coerceIn(160, 560) * k).toInt()
        val H = (hdp.coerceIn(120, 420) * k).toInt()
        val b = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888)
        val cv = Canvas(b)
        val Wf = W.toFloat(); val Hf = H.toFloat()
        val op = Style.opacity(c) / 100f
        val font = Style.font(c)
        val tf = Style.textTypeface(c, font)
        val lk = Combo.Look(Color.TRANSPARENT, Style.textColor(c) ?: Color.WHITE, 0xD8FFFFFF.toInt(), 0xFFFFD9A8.toInt(), true, 0)
        val u = min(Wf, Hf * 1.35f)
        val pad = u * .035f
        // ana kart
        val card = RectF(pad * .4f, pad * .4f, Wf - pad * .4f, Hf - pad * .4f)
        val cr = u * .11f
        cv.drawRoundRect(card, cr, cr, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = a(Color.rgb(28, 33, 44), op * .82f) })
        if (Combo.rim(c) || true) cv.drawRoundRect(card, cr, cr, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE; strokeWidth = u * .003f; color = a(Color.WHITE, .22f * op.coerceAtLeast(.3f))
        })
        // alt şerit sınırı
        val barTop = Hf * .71f
        // sol gökyüzü kutusu
        val x0 = card.left; val y0 = card.top; val x1 = Wf * .52f; val yb = barTop + u * .01f
        val r = u * .11f
        val path = Path().apply {
            moveTo(x0, y0 + r); quadTo(x0, y0, x0 + r, y0)
            lineTo(x1 - r, y0); quadTo(x1, y0, x1, y0 + r)
            lineTo(x1, yb - u * .2f)
            cubicTo(x1, yb - u * .05f, x1 - u * .08f, yb - u * .02f, x1 - u * .22f, yb - u * .02f)
            lineTo(x0 + r * .6f, yb - u * .02f)
            quadTo(x0, yb - u * .02f, x0, yb + r * .3f)
            close()
        }
        val idx = if (w != null) WeatherRepo.iconIndex(w.code, w.isDay) else 2
        cv.save(); cv.clipPath(path)
        try {
            val sky = BitmapFactory.decodeResource(c.resources, SKY[idx])
            val bw = x1 - x0; val bh = yb - y0
            val s = maxOf(bw / sky.width, bh / sky.height)
            val dw = sky.width * s; val dh = sky.height * s
            cv.drawBitmap(sky, null, RectF(x1 - dw, y0, x1, y0 + dh), Paint(Paint.FILTER_BITMAP_FLAG))
        } catch (e: Exception) { cv.drawColor(Color.rgb(60, 100, 160)) }
        cv.drawRect(x0, y0, x1, yb, Paint().apply {
            shader = LinearGradient(x0, 0f, x1, 0f, 0x88000000.toInt(), 0x00000000, Shader.TileMode.CLAMP)
        })
        cv.restore()
        // sol metinler
        val tx = x0 + u * .07f
        val city = WeatherRepo.city(c); val city2 = WeatherRepo.city2(c)
        cv.drawText(city, tx, y0 + u * .12f, Combo.text(tf, u * .065f, lk.ink, lk, Paint.Align.LEFT))
        if (city2.isNotEmpty()) {
            try {
                val pin = c.getDrawable(R.drawable.pin)!!.mutate(); pin.setTint(lk.sub)
                val ps = u * .045f; val by = y0 + u * .19f
                pin.setBounds(tx.toInt(), (by - ps * .95f).toInt(), (tx + ps).toInt(), (by + ps * .05f).toInt()); pin.draw(cv)
            } catch (e: Exception) { }
            cv.drawText(city2, tx + u * .055f, y0 + u * .19f, Combo.text(tf, u * .045f, lk.sub, lk, Paint.Align.LEFT))
        }
        val tempStr = if (w != null) "${w.temp}°" else "--°"
        val tp = Lock.clockPaint(c, u * .19f, lk.ink, lk).apply { textAlign = Paint.Align.LEFT }
        cv.drawText(tempStr, tx - u * .01f, y0 + u * .41f, tp)
        val desc = if (w != null) WeatherRepo.label(w.code) else ""
        if (showIcon(c) && w != null) {
            Combo.icon(c, cv, Style.icon(c, w.code, w.isDay), tx + u * .03f, y0 + u * .48f, u * .07f)
            cv.drawText(desc, tx + u * .08f, y0 + u * .5f, Combo.text(tf, u * .05f, lk.ink, lk, Paint.Align.LEFT))
        } else cv.drawText(desc, tx, y0 + u * .5f, Combo.text(tf, u * .05f, lk.ink, lk, Paint.Align.LEFT))
        // sağ halkalı saat
        val rcx = (x1 + card.right) / 2; val rcy = (y0 + barTop) / 2
        val R = min((card.right - x1) * .42f, (barTop - y0) * .44f)
        cv.drawCircle(rcx, rcy, R * 1.08f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = a(Color.rgb(20, 24, 33), op * .75f) })
        val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = R * .09f; strokeCap = Paint.Cap.ROUND }
        ring.color = 0x30FFFFFF
        cv.drawCircle(rcx, rcy, R, ring)
        val cal = Calendar.getInstance()
        val m = cal.get(Calendar.MINUTE)
        val sweep = 360f * (m + 1) / 60f
        val sg = SweepGradient(rcx, rcy, intArrayOf(0xFFB9D2FF.toInt(), 0xFFFFE2B8.toInt(), 0xFFB9D2FF.toInt()), floatArrayOf(0f, .5f, 1f))
        val mx = android.graphics.Matrix(); mx.setRotate(-90f, rcx, rcy); sg.setLocalMatrix(mx)
        ring.shader = sg
        cv.drawArc(RectF(rcx - R, rcy - R, rcx + R, rcy + R), -90f, sweep, false, ring)
        val an = Math.toRadians(-90.0 + sweep)
        cv.drawCircle(rcx + R * cos(an).toFloat(), rcy + R * sin(an).toFloat(), R * .075f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFFE8C8.toInt() })
        val tick = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x88FFFFFF.toInt(); strokeWidth = R * .02f; strokeCap = Paint.Cap.ROUND }
        for (q in 0 until 4) {
            val t = Math.toRadians(q * 90.0 - 90)
            cv.drawLine(rcx + R * .78f * cos(t).toFloat(), rcy + R * .78f * sin(t).toFloat(), rcx + R * .84f * cos(t).toFloat(), rcy + R * .84f * sin(t).toFloat(), tick)
        }
        val now = Date()
        val cp = Lock.clockPaint(c, R * .42f, lk.ink, lk)
        cv.drawText(SimpleDateFormat("HH:mm", tr).format(now), rcx, rcy + R * .1f, cp)
        cv.drawText(SimpleDateFormat("d MMMM EEEE", tr).format(now), rcx, rcy + R * .38f, Combo.text(tf, R * .13f, lk.sub, lk))
        // alt şerit
        val bar = RectF(card.left + pad, barTop + pad * .5f, card.right - pad, card.bottom - pad)
        val bh = bar.height()
        cv.drawRoundRect(bar, bh / 2, bh / 2, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = a(Color.rgb(18, 22, 30), op * .7f) })
        val cw = bar.width() / 3
        for (i in 0 until 3) {
            val cx0 = bar.left + cw * i
            val icx = cx0 + bh * .6f; val icy = bar.centerY()
            cv.drawCircle(icx, icy, bh * .34f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x22FFFFFF })
            val (res, lab, v) = itemData(c, item(c, i), w)
            Combo.icon(c, cv, res, icx, icy, bh * .42f)
            val lx = icx + bh * .48f
            cv.drawText(lab, lx, icy - bh * .06f, Combo.text(tf, bh * .17f, lk.sub, lk, Paint.Align.LEFT))
            cv.drawText(v, lx, icy + bh * .2f, Combo.text(tf, bh * .22f, lk.ink, lk, Paint.Align.LEFT))
            if (i < 2) cv.drawLine(cx0 + cw, bar.top + bh * .25f, cx0 + cw, bar.bottom - bh * .25f, Paint().apply { color = 0x30FFFFFF; strokeWidth = 2f })
        }
        return b
    }

    private fun itemData(c: Context, which: Int, w: Weather?): Triple<Int, String, String> {
        val cal = Calendar.getInstance()
        val nowM = cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)
        return when (which) {
            0 -> {
                val d = w?.days?.getOrNull(1)
                Triple(if (d != null) Style.icon(c, d.code, true) else R.drawable.st_drop, "Yarın", if (d != null) "${d.max}° / ${d.min}°" else "--")
            }
            1 -> {
                val set = Mod.hm(w?.sunset) ?: 1140; val rise = Mod.hm(w?.sunrise) ?: 420
                if (nowM in rise..set) Triple(R.drawable.st_set, "Gün batımı", w?.sunset ?: "--")
                else Triple(R.drawable.st_rise, "Gün doğumu", w?.sunrise ?: "--")
            }
            2 -> Triple(R.drawable.st_drop, "Yağmur", w?.rainChance?.let { "%$it" } ?: "--")
            3 -> Triple(R.drawable.st_wind, "Rüzgâr", w?.wind?.let { "$it km/s" } ?: "--")
            4 -> Triple(R.drawable.st_drop, "Nem", w?.humidity?.let { "%$it" } ?: "--")
            5 -> Triple(if (w != null) Style.icon(c, w.code, w.isDay) else R.drawable.st_drop, "Hissedilen", w?.feels?.let { "$it°" } ?: "--")
            6 -> Triple(R.drawable.st_rise, "UV", w?.uv?.let { "$it · ${WeatherRepo.uvLabel(it)}" } ?: "--")
            else -> Triple(R.drawable.st_wind, "Hava kalitesi", w?.aqi?.let { WeatherRepo.aqiLabel(it) } ?: "--")
        }
    }
}
