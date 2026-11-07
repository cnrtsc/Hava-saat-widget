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
import android.graphics.SweepGradient
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/** Tek bilgiye odaklanan küçük widget'lar: UV, güneş yayı, hava kalitesi, yağmur radarı, konfor. Hepsinde küçük saat var. */
object Glance {
    val KIND_NAMES = arrayOf("UV endeksi", "Gün doğumu / batımı", "Hava kalitesi", "Yağmur (2 saat)", "Konfor")
    private val TITLES = arrayOf("UV", "GÜNEŞ", "HAVA KALİTESİ", "YAĞMUR", "KONFOR")
    fun kind(c: Context) = Cfg.int(c, "glance", 0).coerceIn(0, KIND_NAMES.size - 1)
    private val tr = Locale("tr", "TR")

    private class X(val c: Context, val w: Weather?, val W: Float, val H: Float, val s: Float,
                    val bg: Int, val ink: Int, val sub: Int, val acc: Int, val tf: android.graphics.Typeface, val cf: android.graphics.Typeface)

    fun render(c: Context, w: Weather?, wdp: Int, hdp: Int): Bitmap {
        val k = 2.5f
        val Wi = (wdp.coerceIn(60, 560) * k).toInt(); val Hi = (hdp.coerceIn(60, 420) * k).toInt()
        val b = Bitmap.createBitmap(Wi, Hi, Bitmap.Config.ARGB_8888)
        val cv = Canvas(b)
        val pal = Palette.get(c)
        val W = Wi.toFloat(); val H = Hi.toFloat(); val s = min(W, H) / 400f
        val op = Style.opacity(c)
        val bg = pal.container
        Ink.setBg(if (op >= 45) bg else null)
        val ink = Style.textColor(c) ?: pal.onCont
        val sub = Ink.get(c, Ink.SUB) ?: pal.onContSub
        val acc = Ink.get(c, Ink.ACC) ?: pal.accentLight
        val x = X(c, w, W, H, s, bg, ink, sub, acc, Style.textTypeface(c, Style.font(c)), Style.clockTypeface(c, Style.font(c)))
        val r = RectF(4 * s, 4 * s, W - 4 * s, H - 4 * s)
        if (op > 0) cv.drawRoundRect(r, 56 * s, 56 * s, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = bg; alpha = (op * 2.55f).toInt() })
        // üst satır: saat + başlık
        val pad = 34 * s
        val cp = Lock.clockPaint(c, 46 * s, Ink.get(c, Ink.CLOCK) ?: ink, Combo.Look(0, ink, sub, acc, op < 45, 0)).apply { textAlign = android.graphics.Paint.Align.LEFT }
        Ink.time(c, cv, now("HH:mm"), pad, pad + 40 * s, cp)
        cv.drawText(TITLES[kind(c)], W - pad, pad + 34 * s, p(x, 22 * s, sub, Paint.Align.RIGHT).apply { letterSpacing = .14f })
        val body = RectF(pad, pad + 70 * s, W - pad, H - pad)
        when (kind(c)) {
            0 -> uv(cv, x, body)
            1 -> sun(cv, x, body)
            2 -> aqi(cv, x, body)
            3 -> rain(cv, x, body)
            else -> comfort(cv, x, body)
        }
        return b
    }

    private fun now(f: String) = SimpleDateFormat(f, tr).format(Date())
    private fun p(x: X, size: Float, col: Int, al: Paint.Align = Paint.Align.LEFT, big: Boolean = false) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = if (big) x.cf else x.tf; textSize = size; color = col; textAlign = al; Ink.shadowOverride(this)
    }
    private fun fit(pp: Paint, t: String, mw: Float): Paint { val m = pp.measureText(t); if (m > mw) pp.textSize *= mw / m; return pp }

    /** Yarım daire gösterge; v 0..1. */
    private fun gauge(cv: Canvas, x: X, cx: Float, cy: Float, rad: Float, v: Float, cols: IntArray) {
        val st = 20 * x.s
        val rr = RectF(cx - rad, cy - rad, cx + rad, cy + rad)
        val pp = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = st; strokeCap = Paint.Cap.ROUND }
        val pos = FloatArray(cols.size) { .5f + .5f * it / (cols.size - 1) }
        pp.shader = SweepGradient(cx, cy, cols, pos)
        cv.drawArc(rr, 180f, 180f, false, pp)
        val a = PI + PI * v.coerceIn(0f, 1f)
        val dx = cx + rad * cos(a).toFloat(); val dy = cy + rad * sin(a).toFloat()
        cv.drawCircle(dx, dy, st * .75f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; setShadowLayer(6f, 0f, 2f, 0x66000000) })
    }

    private fun uv(cv: Canvas, x: X, r: RectF) {
        val uv = x.w?.uv
        val rad = min(r.width() * .42f, r.height() * .62f)
        val cx = r.centerX(); val cy = r.top + rad + 18 * x.s
        gauge(cv, x, cx, cy, rad, (uv ?: 0) / 11f, intArrayOf(Color.rgb(90, 190, 90), Color.rgb(240, 210, 60), Color.rgb(250, 150, 50), Color.rgb(230, 60, 60), Color.rgb(150, 80, 200)))
        cv.drawText(uv?.toString() ?: "–", cx, cy, p(x, 96 * x.s, x.ink, Paint.Align.CENTER, true))
        val (lvl, tip) = when (uv ?: -1) {
            -1 -> "Veri yok" to ""
            in 0..2 -> "Düşük" to "Koruma gerekmez"
            in 3..5 -> "Orta" to "Öğlen gölge tercih et"
            in 6..7 -> "Yüksek" to "Güneş kremi sür"
            in 8..10 -> "Çok yüksek" to "Krem + şapka şart"
            else -> "Aşırı" to "Öğlen dışarı çıkma"
        }
        cv.drawText(lvl, cx, r.bottom - 34 * x.s, p(x, 30 * x.s, x.ink, Paint.Align.CENTER))
        cv.drawText(tip, cx, r.bottom, fit(p(x, 22 * x.s, x.sub, Paint.Align.CENTER), tip, r.width()))
    }

    private fun sun(cv: Canvas, x: X, r: RectF) {
        val rise = Mod.hm(x.w?.sunrise) ?: 420; val set = Mod.hm(x.w?.sunset) ?: 1140
        val cal = Calendar.getInstance(); val nowM = cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)
        val rad = min(r.width() * .44f, r.height() * .6f)
        val cx = r.centerX(); val cy = r.top + rad + 10 * x.s
        val arc = RectF(cx - rad, cy - rad, cx + rad, cy + rad)
        cv.drawArc(arc, 180f, 180f, false, Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 4 * x.s; color = x.sub; pathEffect = android.graphics.DashPathEffect(floatArrayOf(10 * x.s, 10 * x.s), 0f) })
        cv.drawLine(cx - rad - 20 * x.s, cy, cx + rad + 20 * x.s, cy, Paint().apply { color = x.sub; strokeWidth = 2 * x.s })
        val day = nowM in rise..set
        val f = if (day) (nowM - rise).toFloat() / (set - rise) else 0f
        if (day) {
            val a = PI + PI * f
            val sx = cx + rad * cos(a).toFloat(); val sy = cy + rad * sin(a).toFloat()
            cv.drawArc(arc, 180f, 180f * f, false, Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 6 * x.s; color = Color.rgb(255, 196, 60); strokeCap = Paint.Cap.ROUND })
            cv.drawCircle(sx, sy, 30 * x.s, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(70, 255, 210, 90) })
            cv.drawCircle(sx, sy, 18 * x.s, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(255, 204, 70) })
        } else {
            cv.drawCircle(cx, cy - rad * .4f, 16 * x.s, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(230, 230, 250) })
            cv.drawCircle(cx + 8 * x.s, cy - rad * .4f - 6 * x.s, 14 * x.s, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = x.bg })
        }
        val tp = p(x, 26 * x.s, x.ink)
        cv.drawText(x.w?.sunrise ?: "--:--", r.left, cy + 40 * x.s, tp)
        cv.drawText(x.w?.sunset ?: "--:--", r.right, cy + 40 * x.s, p(x, 26 * x.s, x.ink, Paint.Align.RIGHT))
        val left = if (day) set - nowM else ((rise - nowM) + 1440) % 1440
        val msg = (if (day) "Gün batımına " else "Gün doğumuna ") + (if (left >= 60) "${left / 60} sa ${left % 60} dk" else "$left dk")
        cv.drawText(msg, r.centerX(), r.bottom, fit(p(x, 24 * x.s, x.sub, Paint.Align.CENTER), msg, r.width()))
    }

    private fun aqi(cv: Canvas, x: X, r: RectF) {
        val a = x.w?.aqi
        val (lvl, col) = when (a ?: -1) {
            -1 -> "Veri yok" to x.sub
            in 0..20 -> "İyi" to Color.rgb(80, 200, 140)
            in 21..40 -> "Makul" to Color.rgb(150, 210, 90)
            in 41..60 -> "Orta" to Color.rgb(240, 200, 60)
            in 61..80 -> "Kötü" to Color.rgb(250, 140, 60)
            in 81..100 -> "Çok kötü" to Color.rgb(230, 70, 70)
            else -> "Aşırı kötü" to Color.rgb(150, 70, 170)
        }
        cv.drawText(a?.toString() ?: "–", r.left, r.top + r.height() * .5f, p(x, 110 * x.s, x.ink, big = true))
        cv.drawText(lvl, r.left, r.top + r.height() * .5f + 46 * x.s, p(x, 34 * x.s, col))
        val by = r.bottom - 26 * x.s
        val bar = RectF(r.left, by - 10 * x.s, r.right, by + 10 * x.s)
        cv.drawRoundRect(bar, 10 * x.s, 10 * x.s, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(bar.left, 0f, bar.right, 0f, intArrayOf(Color.rgb(80, 200, 140), Color.rgb(240, 200, 60), Color.rgb(250, 140, 60), Color.rgb(230, 70, 70), Color.rgb(150, 70, 170)), null, Shader.TileMode.CLAMP) })
        val mx = bar.left + bar.width() * ((a ?: 0) / 120f).coerceIn(0f, 1f)
        cv.drawCircle(mx, by, 16 * x.s, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; setShadowLayer(6f, 0f, 2f, 0x66000000) })
        cv.drawText("Avrupa AQI", r.right, r.top + 26 * x.s, p(x, 20 * x.s, x.sub, Paint.Align.RIGHT))
    }

    private fun rain(cv: Canvas, x: X, r: RectF) {
        val v = x.w?.rain15 ?: emptyList()
        val start = Mod.hm(x.w?.rain15Start)
        val firstWet = v.indexOfFirst { it >= .1f }
        val msg = when {
            v.isEmpty() -> "Veri yok"
            firstWet < 0 -> "2 saat yağış yok"
            firstWet == 0 -> "Şu an yağıyor"
            start != null -> { val m = (start + firstWet * 15) % 1440; String.format("%02d:%02d'te başlıyor", m / 60, m % 60) }
            else -> "Yakında yağış var"
        }
        cv.drawText(msg, r.left, r.top + 34 * x.s, fit(p(x, 34 * x.s, if (firstWet >= 0) x.acc else x.ink), msg, r.width()))
        val chart = RectF(r.left, r.top + 70 * x.s, r.right, r.bottom - 34 * x.s)
        val n = max(8, v.size); val bw = chart.width() / n
        val mx = max(2f, v.maxOrNull() ?: 0f)
        val bp = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(90, 160, 240) }
        val empty = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(60, Color.red(x.sub), Color.green(x.sub), Color.blue(x.sub)) }
        for (i in 0 until n) {
            val value = v.getOrNull(i) ?: 0f
            val h = if (value < .05f) 6 * x.s else max(10 * x.s, chart.height() * (value / mx))
            val l = chart.left + i * bw + bw * .14f; val rr = chart.left + (i + 1) * bw - bw * .14f
            cv.drawRoundRect(RectF(l, chart.bottom - h, rr, chart.bottom), 6 * x.s, 6 * x.s, if (value < .05f) empty else bp)
        }
        val sp = p(x, 20 * x.s, x.sub)
        cv.drawText("Şimdi", chart.left, r.bottom, sp)
        cv.drawText("+1 sa", chart.centerX(), r.bottom, p(x, 20 * x.s, x.sub, Paint.Align.CENTER))
        cv.drawText("+2 sa", chart.right, r.bottom, p(x, 20 * x.s, x.sub, Paint.Align.RIGHT))
    }

    private fun comfort(cv: Canvas, x: X, r: RectF) {
        val w = x.w
        val feels = w?.feels ?: w?.temp
        val hum = w?.humidity
        val label = when {
            feels == null -> "Veri yok"
            feels <= 0 -> "Dondurucu"
            feels <= 8 -> "Soğuk"
            feels <= 15 -> "Serin"
            feels <= 23 -> if ((hum ?: 0) > 75) "Nemli ılık" else "Ilık, rahat"
            feels <= 29 -> if ((hum ?: 0) > 65) "Bunaltıcı" else "Sıcak"
            else -> "Çok sıcak"
        }
        cv.drawText(if (feels != null) "$feels°" else "–", r.left, r.top + r.height() * .42f, p(x, 96 * x.s, x.ink, big = true))
        cv.drawText("hissedilen", r.left + 4 * x.s, r.top + r.height() * .42f + 30 * x.s, p(x, 22 * x.s, x.sub))
        cv.drawText(label, r.right, r.top + r.height() * .3f, fit(p(x, 30 * x.s, x.acc, Paint.Align.RIGHT), label, r.width() * .45f))
        val rows = listOf("Nem" to ((hum ?: 0) / 100f) to "%${hum ?: 0}", "Rüzgâr" to (((w?.wind ?: 0) / 50f)) to "${w?.wind ?: 0} km/sa")
        var y = r.bottom - 74 * x.s
        for ((pair, value) in rows) {
            val (name, f) = pair
            cv.drawText(name, r.left, y, p(x, 22 * x.s, x.sub))
            cv.drawText(value, r.right, y, p(x, 22 * x.s, x.ink, Paint.Align.RIGHT))
            val bar = RectF(r.left, y + 10 * x.s, r.right, y + 18 * x.s)
            cv.drawRoundRect(bar, 4 * x.s, 4 * x.s, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(60, Color.red(x.sub), Color.green(x.sub), Color.blue(x.sub)) })
            cv.drawRoundRect(RectF(bar.left, bar.top, bar.left + bar.width() * f.coerceIn(0f, 1f), bar.bottom), 4 * x.s, 4 * x.s, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = x.acc })
            y += 52 * x.s
        }
    }
}
