package com.caner.sketchweather

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlin.math.min

/** İnce (4x1) widget'lar: konum şeridi, ev ↔ şu an, yağmur çizelgesi, cümle. */
object Thin {
    const val LOC = 0; const val HOME = 1; const val RAIN = 2; const val SENTENCE = 3
    private val tr = Locale("tr", "TR")

    /** Türkçe bulunma eki: Kadıköy'de, Hamburg'da, Paris'te */
    fun locative(name: String): String {
        val low = name.lowercase(tr)
        val lastV = low.lastOrNull { it in "aeıioöuü" } ?: 'e'
        val back = lastV in "aıou"
        val hard = low.lastOrNull()?.let { it in "çfhkpsşt" } ?: false
        return name + "'" + (if (hard) "t" else "d") + (if (back) "a" else "e")
    }

    private fun fit(p: Paint, text: String, maxW: Float): Paint {
        val w = p.measureText(text)
        if (w > maxW && w > 0) p.textSize = p.textSize * maxW / w
        return p
    }

    private fun pin(c: Context, cv: Canvas, x: Float, base: Float, s: Float): Float {
        try {
            val d = c.getDrawable(R.drawable.pin)!!.mutate(); d.setTint(0xFFFFB03C.toInt())
            d.setBounds(x.toInt(), (base - s * .95f).toInt(), (x + s).toInt(), (base + s * .05f).toInt()); d.draw(cv)
        } catch (e: Exception) { }
        return x + s * 1.1f
    }

    fun render(c: Context, w: Weather?, wdp: Int, hdp: Int, kind: Int): Bitmap {
        val k = 2.5f
        val W = (wdp.coerceIn(160, 560) * k).toInt()
        val H = (hdp.coerceIn(40, 200) * k).toInt()
        val b = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888)
        val cv = Canvas(b)
        val Wf = W.toFloat(); val Hf = H.toFloat()
        val op = Style.opacity(c) / 100f
        val ink = Style.textColor(c) ?: Color.WHITE
        val sub = Color.argb(200, Color.red(ink), Color.green(ink), Color.blue(ink))
        val shadow = op < .3f
        val lk = Combo.Look(Color.TRANSPARENT, ink, sub, ink, shadow, 0)
        val tf = Style.textTypeface(c, Style.font(c))
        val cf = Style.clockTypeface(c, Style.font(c))
        fun t(face: android.graphics.Typeface, size: Float, col: Int, al: Paint.Align = Paint.Align.LEFT) = Combo.text(face, size, col, lk, al)
        val pad = 6f
        val r = RectF(pad, pad, Wf - pad, Hf - pad)
        if (op > 0f) cv.drawRoundRect(r, r.height() / 2, r.height() / 2, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb((215 * op).toInt(), 22, 26, 34) })
        val inX = r.left + r.height() * .42f
        val inR = r.right - r.height() * .42f
        val now = Date()
        val city = WeatherRepo.city(c)
        val temp = if (w != null) "${w.temp}°" else "--°"
        val icon = if (w != null) Style.icon(c, w.code, w.isDay) else Style.iconOf(0, 6, 3, true)
        when (kind) {
            LOC -> {
                val x1 = pin(c, cv, inX, Hf * .5f, Hf * .26f)
                val c2 = WeatherRepo.city2(c)
                val right = inR
                val tp = t(cf, Hf * .32f, ink, Paint.Align.RIGHT)
                val tw = tp.measureText(temp)
                val iconS = Hf * .62f
                val leftLimit = right - tw - iconS - Hf * .1f
                cv.drawText(city, x1, Hf * (if (c2.isNotEmpty()) .5f else .6f), fit(t(tf, Hf * .3f, ink).apply { isFakeBoldText = true }, city, leftLimit - x1 - Hf * .1f))
                if (c2.isNotEmpty()) cv.drawText(c2, x1, Hf * .74f, fit(t(tf, Hf * .17f, sub), c2, leftLimit - x1))
                Combo.icon(c, cv, icon, right - tw - iconS / 2 - Hf * .06f, Hf / 2, iconS)
                cv.drawText(temp, right, Hf * .52f, tp)
                cv.drawText(SimpleDateFormat("HH:mm", tr).format(now), right, Hf * .76f, t(tf, Hf * .16f, sub, Paint.Align.RIGHT))
            }
            HOME -> {
                val p = WeatherRepo.prefs(c)
                val mid = Wf / 2
                // ev şehri
                val hf = SimpleDateFormat("HH:mm", tr).apply { timeZone = TimeZone.getTimeZone(Style.homeTz(c)) }
                cv.drawText("EV · " + Style.homeName(c), inX, Hf * .36f, fit(t(tf, Hf * .15f, sub), "EV · " + Style.homeName(c), mid - inX - Hf * .2f))
                val clk = Lock.clockPaint(c, Hf * .34f, ink, lk).apply { textAlign = Paint.Align.LEFT }
                cv.drawText(hf.format(now), inX, Hf * .76f, clk)
                val hx = inX + clk.measureText(hf.format(now)) + Hf * .08f
                if (p.getInt("homeIdx", -1) == Style.home(c)) {
                    Combo.icon(c, cv, Style.icon(c, p.getInt("homeCode", 2), p.getBoolean("homeDay", true)), hx + Hf * .16f, Hf * .62f, Hf * .32f)
                    cv.drawText("${p.getInt("homeTemp", 0)}°", hx + Hf * .34f, Hf * .74f, t(tf, Hf * .2f, ink))
                }
                // ortadaki ok
                val ap = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = sub; strokeWidth = Hf * .025f; style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND }
                cv.drawLine(mid - Hf * .1f, Hf * .44f, mid + Hf * .1f, Hf * .44f, ap); cv.drawLine(mid + Hf * .05f, Hf * .39f, mid + Hf * .1f, Hf * .44f, ap)
                cv.drawLine(mid - Hf * .1f, Hf * .58f, mid + Hf * .1f, Hf * .58f, ap); cv.drawLine(mid - Hf * .05f, Hf * .63f, mid - Hf * .1f, Hf * .58f, ap)
                // şu an
                val rx = mid + Hf * .25f
                val x1 = pin(c, cv, rx, Hf * .36f, Hf * .15f)
                cv.drawText("ŞU AN · $city", x1, Hf * .36f, fit(t(tf, Hf * .15f, sub), "ŞU AN · $city", inR - x1))
                cv.drawText(SimpleDateFormat("HH:mm", tr).format(now), rx, Hf * .76f, clk)
                val lx = rx + clk.measureText(SimpleDateFormat("HH:mm", tr).format(now)) + Hf * .08f
                Combo.icon(c, cv, icon, lx + Hf * .16f, Hf * .62f, Hf * .32f)
                cv.drawText(temp, lx + Hf * .34f, Hf * .74f, t(tf, Hf * .2f, ink))
            }
            RAIN -> {
                val rain = w?.rain15 ?: emptyList()
                val start = w?.rain15Start
                val firstWet = rain.indexOfFirst { it >= .05f }
                val msg = when {
                    w == null -> "Yükleniyor…"
                    rain.isEmpty() -> "Yağış verisi yok"
                    firstWet < 0 -> "Önümüzdeki 2 saat yağış yok"
                    firstWet == 0 -> {
                        val stop = rain.indexOfFirst { it < .05f }
                        if (stop < 0) "Yağmur sürüyor, 2 saat dinmeyecek" else "Yağmur yaklaşık ${addMin(start, stop * 15)}'te diniyor"
                    }
                    else -> "Yağmur yaklaşık ${addMin(start, firstWet * 15)}'te başlıyor"
                }
                val x1 = pin(c, cv, inX, Hf * .38f, Hf * .17f)
                cv.drawText("$city · $msg", x1, Hf * .38f, fit(t(tf, Hf * .17f, ink), "$city · $msg", inR - x1))
                val n = if (rain.isEmpty()) 8 else rain.size
                val mx = maxOf(.6f, rain.maxOrNull() ?: 0f)
                val bw = (inR - inX) / n
                val base = Hf * .78f; val maxH = Hf * .3f
                val bp = Paint(Paint.ANTI_ALIAS_FLAG)
                for (i in 0 until n) {
                    val v = rain.getOrNull(i) ?: 0f
                    val h = if (v < .05f) Hf * .025f else maxOf(Hf * .06f, maxH * min(1f, v / mx))
                    bp.color = if (v < .05f) Color.argb(70, Color.red(ink), Color.green(ink), Color.blue(ink)) else 0xFF5AA8FF.toInt()
                    val x = inX + i * bw + bw * .15f
                    cv.drawRoundRect(RectF(x, base - h, x + bw * .7f, base), bw * .2f, bw * .2f, bp)
                }
                cv.drawText("Şimdi", inX, Hf * .92f, t(tf, Hf * .11f, sub))
                cv.drawText("+2 sa", inR, Hf * .92f, t(tf, Hf * .11f, sub, Paint.Align.RIGHT))
            }
            else -> {
                val iconS = Hf * .7f
                Combo.icon(c, cv, icon, inX + iconS / 2 - Hf * .05f, Hf / 2, iconS)
                val tx = inX + iconS + Hf * .05f
                val d = w?.days?.firstOrNull()
                val l1 = if (w != null) "${locative(city)} şu an ${w.temp}°, ${WeatherRepo.label(w.code).lowercase(tr)}." else "Hava bilgisi yükleniyor…"
                val l2 = when {
                    w == null -> ""
                    (w.rainChance ?: 0) >= 50 -> "Yağmur ihtimali %${w.rainChance}, şemsiyeni al."
                    d != null && d.max >= 28 -> "Bugün sıcak, en yüksek ${d.max}°. Bol su iç."
                    d != null && d.min <= 5 -> "Soğuk; en düşük ${d.min}°. Sıkı giyin."
                    (w.wind ?: 0) >= 30 -> "Rüzgârlı bir gün: ${w.wind} km/s."
                    w.sunset != null -> "Gün batımı ${w.sunset}" + (d?.let { ", en yüksek ${it.max}°." } ?: ".")
                    else -> ""
                }
                cv.drawText(l1, tx, Hf * .46f, fit(t(tf, Hf * .19f, ink).apply { isFakeBoldText = true }, l1, inR - tx))
                cv.drawText(l2, tx, Hf * .72f, fit(t(tf, Hf * .16f, sub), l2, inR - tx))
            }
        }
        return b
    }

    private fun addMin(start: String?, m: Int): String {
        if (start == null) return "+${m} dk"
        return try {
            val t = start.substring(0, 2).toInt() * 60 + start.substring(3, 5).toInt() + m
            String.format(Locale.US, "%02d:%02d", (t / 60) % 24, t % 60)
        } catch (e: Exception) { "+${m} dk" }
    }
}
