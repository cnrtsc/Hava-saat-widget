package com.caner.sketchweather

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/** Gün şeridi (4x1) ve sıcaklık grafiği (4x2). */
object Extra {
    private val tr = Locale("tr", "TR")

    private fun canvas(wdp: Int, hdp: Int): Pair<Bitmap, Canvas> {
        val k = 2.5f
        val b = Bitmap.createBitmap((wdp.coerceIn(120, 560) * k).toInt(), (hdp.coerceIn(40, 400) * k).toInt(), Bitmap.Config.ARGB_8888)
        return Pair(b, Canvas(b))
    }

    private fun card(c: Context, cv: Canvas, W: Float, H: Float, lk: Combo.Look, r: Float) {
        val rect = RectF(8f, 8f, W - 8f, H - 8f)
        cv.drawRoundRect(rect, r, r, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = lk.fill })
        if (Combo.rim(c)) cv.drawRoundRect(rect, r, r, Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 3f; color = lk.rimCol })
    }

    fun strip(c: Context, w: Weather?, wdp: Int, hdp: Int): Bitmap {
        val (b, cv) = canvas(wdp, hdp)
        val W = b.width.toFloat(); val H = b.height.toFloat()
        val lk = Combo.look(c, Palette.get(c), 0)
        card(c, cv, W, H, lk, H / 2)
        val font = Style.font(c)
        val tf = Style.textTypeface(c, font)
        val cp = Lock.clockPaint(c, H * .5f, lk.ink, lk).apply { textAlign = Paint.Align.LEFT }
        val time = SimpleDateFormat("HH:mm", tr).format(Date())
        val fm = cp.fontMetrics
        cv.drawText(time, H * .35f, H / 2 - (fm.ascent + fm.descent) / 2, cp)
        val x0 = H * .45f + cp.measureText(time) + H * .35f
        val x1 = W - H * .9f
        val y = H * .56f
        val cal = Calendar.getInstance()
        val nowM = cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)
        val rise = Mod.hm(w?.sunrise) ?: 420; val set = Mod.hm(w?.sunset) ?: 1140
        val frac = ((nowM - rise).toFloat() / (set - rise).coerceAtLeast(1)).coerceIn(0f, 1f)
        val lp = Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeWidth = H * .045f; strokeCap = Paint.Cap.ROUND }
        lp.color = Color.argb(70, Color.red(lk.ink), Color.green(lk.ink), Color.blue(lk.ink))
        cv.drawLine(x0, y, x1, y, lp)
        lp.color = lk.accent
        cv.drawLine(x0, y, x0 + (x1 - x0) * frac, y, lp)
        cv.drawCircle(x0 + (x1 - x0) * frac, y, H * .09f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFFB42A.toInt() })
        val sp = Combo.text(tf, H * .15f, lk.sub, lk)
        sp.textAlign = Paint.Align.LEFT; cv.drawText(w?.sunrise ?: "--:--", x0, y + H * .28f, sp)
        sp.textAlign = Paint.Align.RIGHT; cv.drawText(w?.sunset ?: "--:--", x1, y + H * .28f, sp)
        sp.textAlign = Paint.Align.LEFT; cv.drawText(WeatherRepo.city(c), x0, y - H * .18f, Combo.text(tf, H * .16f, lk.ink, lk, Paint.Align.LEFT))
        if (w != null) {
            Combo.icon(c, cv, Style.icon(c, w.code, w.isDay), W - H * .55f, H * .4f, H * .5f)
            cv.drawText("${w.temp}°", W - H * .55f, H * .86f, Combo.text(tf, H * .2f, lk.ink, lk))
        }
        return b
    }

    fun graph(c: Context, w: Weather?, wdp: Int, hdp: Int): Bitmap {
        val (b, cv) = canvas(wdp, hdp)
        val W = b.width.toFloat(); val H = b.height.toFloat()
        val lk = Combo.look(c, Palette.get(c))
        card(c, cv, W, H, lk, H * .14f)
        val font = Style.font(c)
        val tf = Style.textTypeface(c, font)
        val cf = Style.clockTypeface(c, font)
        val pad = W * .05f
        // başlık
        cv.drawText(WeatherRepo.city(c), pad, H * .16f, Combo.text(tf, H * .075f, lk.ink, lk, Paint.Align.LEFT))
        val sub = if (w != null) "${w.temp}°  ·  ${WeatherRepo.label(w.code)}" else "Yükleniyor…"
        cv.drawText(sub, pad, H * .26f, Combo.text(tf, H * .06f, lk.sub, lk, Paint.Align.LEFT))
        cv.drawText(SimpleDateFormat("HH:mm", tr).format(Date()), W - pad, H * .24f, Lock.clockPaint(c, H * .16f, lk.ink, lk).apply { textAlign = Paint.Align.RIGHT })
        val hrs = w?.hours?.take(13) ?: return b
        if (hrs.size < 2) return b
        val top = H * .52f; val bot = H * .80f
        val mn = hrs.minOf { it.temp }; val mx = hrs.maxOf { it.temp }
        val span = (mx - mn).coerceAtLeast(3)
        fun xp(i: Int) = pad + (W - 2 * pad) * i / (hrs.size - 1)
        fun yp(t: Int) = bot - (bot - top) * (t - mn) / span
        val path = Path(); path.moveTo(xp(0), yp(hrs[0].temp))
        for (i in 1 until hrs.size) {
            val xm = (xp(i - 1) + xp(i)) / 2
            path.cubicTo(xm, yp(hrs[i - 1].temp), xm, yp(hrs[i].temp), xp(i), yp(hrs[i].temp))
        }
        val fill = Path(path); fill.lineTo(xp(hrs.size - 1), bot + H * .06f); fill.lineTo(xp(0), bot + H * .06f); fill.close()
        val acc = lk.accent
        cv.drawPath(fill, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(0f, top, 0f, bot + H * .06f, Color.argb(110, Color.red(acc), Color.green(acc), Color.blue(acc)), Color.argb(0, Color.red(acc), Color.green(acc), Color.blue(acc)), Shader.TileMode.CLAMP)
        })
        cv.drawPath(path, Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = H * .014f; color = acc; strokeCap = Paint.Cap.ROUND })
        for (i in hrs.indices step 3) {
            val h = hrs[i]
            val x = xp(i); val y = yp(h.temp)
            cv.drawCircle(x, y, H * .018f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = lk.ink })
            cv.drawText("${h.temp}°", x, y - H * .04f, Combo.text(tf, H * .055f, lk.ink, lk))
            Combo.icon(c, cv, Style.icon(c, h.code, h.isDay), x, H * .38f, H * .1f)
            cv.drawText(if (i == 0) "Şimdi" else h.time.substring(0, 5), x, H * .92f, Combo.text(tf, H * .05f, lk.sub, lk))
        }
        return b
    }
}
