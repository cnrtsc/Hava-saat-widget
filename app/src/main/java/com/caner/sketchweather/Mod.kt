package com.caner.sketchweather

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.os.BatteryManager
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/** Yuvarlak "modüller": her daire bunlardan birini gösterebilir. */
object Mod {
    val NAMES = arrayOf("Analog saat", "Dijital saat", "Hava · sade", "Hava · detaylı", "Hava · 3 gün", "Güneş", "Pil", "Ay evresi", "Takvim")
    private val tr = Locale("tr", "TR")

    fun slot(c: Context, key: String, def: Int) = Cfg.int(c, key, def).coerceIn(0, NAMES.size - 1)

    fun hm(t: String?): Int? = try {
        if (t == null) null else t.substring(0, 2).toInt() * 60 + t.substring(3, 5).toInt()
    } catch (e: Exception) { null }

    private fun bg(c: Context, cv: Canvas, cx: Float, cy: Float, d: Float, lk: Combo.Look) {
        cv.drawCircle(cx, cy, d / 2, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = lk.fill })
        if (Combo.rim(c)) cv.drawCircle(cx, cy, d / 2, Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = d * .006f; color = lk.rimCol })
    }

    fun draw(c: Context, cv: Canvas, cx: Float, cy: Float, d: Float, lk: Combo.Look, w: Weather?, which: Int) {
        if (which == 0) { Combo.drawClock(c, cv, cx, cy, d / 2, lk, w, false); return }
        bg(c, cv, cx, cy, d, lk)
        val font = Style.font(c)
        val tf = Style.textTypeface(c, font)
        val cf = Style.clockTypeface(c, font)
        when (which) {
            1 -> {
                val now = Date()
                cv.drawText(WeatherRepo.city(c), cx, cy - d * .22f, Combo.text(tf, d * .06f, lk.sub, lk))
                val cp = Lock.clockPaint(c, d * .26f, lk.ink, lk)
                cv.drawText(SimpleDateFormat("HH:mm", tr).format(now), cx, cy + d * .08f, cp)
                cv.drawText(SimpleDateFormat("d MMMM EEEE", tr).format(now), cx, cy + d * .2f, Combo.text(tf, d * .05f, lk.sub, lk))
            }
            2, 3, 4 -> RoundW.content(c, cv, cx, cy, d, lk, w, which - 2)
            5 -> sun(c, cv, cx, cy, d, lk, w, tf)
            6 -> battery(c, cv, cx, cy, d, lk, tf, cf)
            7 -> moon(cv, cx, cy, d, lk, tf)
            else -> {
                val now = Date()
                cv.drawText(SimpleDateFormat("EEEE", tr).format(now), cx, cy - d * .2f, Combo.text(tf, d * .065f, lk.accent, lk))
                cv.drawText(SimpleDateFormat("d", tr).format(now), cx, cy + d * .12f, Combo.text(cf, d * .36f, lk.ink, lk))
                cv.drawText(SimpleDateFormat("MMMM yyyy", tr).format(now), cx, cy + d * .24f, Combo.text(tf, d * .055f, lk.sub, lk))
                val wk = Calendar.getInstance(tr).get(Calendar.WEEK_OF_YEAR)
                cv.drawText("$wk. hafta", cx, cy + d * .33f, Combo.text(tf, d * .045f, lk.sub, lk))
            }
        }
    }

    private fun sun(c: Context, cv: Canvas, cx: Float, cy: Float, d: Float, lk: Combo.Look, w: Weather?, tf: android.graphics.Typeface) {
        val cal = Calendar.getInstance()
        val nowM = cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)
        val rise = hm(w?.sunrise) ?: 7 * 60
        val set = hm(w?.sunset) ?: 19 * 60
        val frac = ((nowM - rise).toFloat() / (set - rise).coerceAtLeast(1)).coerceIn(0f, 1f)
        val hy = cy + d * .06f; val R = d * .30f
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = d * .012f; strokeCap = Paint.Cap.ROUND }
        p.color = Color.argb(90, Color.red(lk.sub), Color.green(lk.sub), Color.blue(lk.sub))
        p.pathEffect = android.graphics.DashPathEffect(floatArrayOf(d * .008f, d * .03f), 0f)
        cv.drawArc(RectF(cx - R, hy - R, cx + R, hy + R), 180f, 180f, false, p)
        p.pathEffect = null; p.color = lk.accent; p.strokeWidth = d * .02f
        cv.drawArc(RectF(cx - R, hy - R, cx + R, hy + R), 180f, 180f * frac, false, p)
        p.color = lk.sub; p.strokeWidth = d * .008f
        cv.drawLine(cx - R - d * .05f, hy, cx + R + d * .05f, hy, p)
        val ang = Math.toRadians(180.0 + 180.0 * frac)
        val day = nowM in rise..set
        cv.drawCircle(cx + R * cos(ang).toFloat(), hy + R * sin(ang).toFloat(), d * .045f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = if (day) 0xFFFFB42A.toInt() else lk.sub })
        cv.drawText(w?.sunrise ?: "--:--", cx - R + d * .02f, hy + d * .09f, Combo.text(tf, d * .055f, lk.ink, lk))
        cv.drawText(w?.sunset ?: "--:--", cx + R - d * .02f, hy + d * .09f, Combo.text(tf, d * .055f, lk.ink, lk))
        val left = if (day) set - nowM else ((rise + 24 * 60 - nowM) % (24 * 60))
        cv.drawText(if (day) "Gün batımına" else "Gün doğumuna", cx, cy + d * .25f, Combo.text(tf, d * .045f, lk.sub, lk))
        cv.drawText("${left / 60} sa ${left % 60} dk", cx, cy + d * .33f, Combo.text(tf, d * .06f, lk.ink, lk))
    }

    private fun battery(c: Context, cv: Canvas, cx: Float, cy: Float, d: Float, lk: Combo.Look, tf: android.graphics.Typeface, cf: android.graphics.Typeface) {
        val bi = try { c.applicationContext.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED)) } catch (e: Exception) { null }
        val level = bi?.let { val l = it.getIntExtra(BatteryManager.EXTRA_LEVEL, -1); val s = it.getIntExtra(BatteryManager.EXTRA_SCALE, 100); if (l >= 0) l * 100 / s else -1 } ?: -1
        val st = bi?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val charging = st == BatteryManager.BATTERY_STATUS_CHARGING || st == BatteryManager.BATTERY_STATUS_FULL
        val r = RectF(cx - d * .40f, cy - d * .40f, cx + d * .40f, cy + d * .40f)
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = d * .05f; strokeCap = Paint.Cap.ROUND }
        p.color = Color.argb(60, Color.red(lk.ink), Color.green(lk.ink), Color.blue(lk.ink))
        cv.drawArc(r, 120f, 300f, false, p)
        p.color = when { charging -> 0xFF3CC878.toInt(); level in 0..19 -> 0xFFE8504A.toInt(); else -> lk.accent }
        cv.drawArc(r, 120f, 300f * (level.coerceAtLeast(0)) / 100f, false, p)
        cv.drawText(if (level >= 0) "%$level" else "--", cx, cy + d * .06f, Combo.text(cf, d * .2f, lk.ink, lk))
        cv.drawText(if (charging) "⚡ Şarj oluyor" else "Pil", cx, cy + d * .17f, Combo.text(tf, d * .055f, lk.sub, lk))
        cv.drawText(SimpleDateFormat("HH:mm", tr).format(Date()), cx, cy + d * .33f, Combo.text(tf, d * .055f, lk.sub, lk))
    }

    /** 0 = yeni ay, 0.5 = dolunay */
    fun moonPhase(): Double {
        val days = System.currentTimeMillis() / 86_400_000.0 + 2440587.5 - 2451550.1
        val p = (days / 29.530588853) % 1.0
        return if (p < 0) p + 1 else p
    }

    private fun moon(cv: Canvas, cx: Float, cy: Float, d: Float, lk: Combo.Look, tf: android.graphics.Typeface) {
        val ph = moonPhase()
        val R = d * .2f
        val my = cy - d * .1f
        val dark = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(60, 64, 76) }
        val lit = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(246, 232, 196) }
        cv.drawCircle(cx, my, R, dark)
        val waxing = ph < .5
        val k = cos(2 * Math.PI * ph).toFloat()        // 1 yeni ay, -1 dolunay
        val half = RectF(cx - R, my - R, cx + R, my + R)
        cv.drawArc(half, if (waxing) -90f else 90f, 180f, true, lit)
        val ex = RectF(cx - R * kotlin.math.abs(k), my - R, cx + R * kotlin.math.abs(k), my + R)
        cv.drawOval(ex, if (k > 0) dark else lit)
        val names = arrayOf("Yeni ay", "Büyüyen hilal", "İlk dördün", "Büyüyen şişkin ay", "Dolunay", "Küçülen şişkin ay", "Son dördün", "Küçülen hilal")
        val idx = ((ph * 8 + .5).toInt()) % 8
        val illum = ((1 - cos(2 * Math.PI * ph)) / 2 * 100).toInt()
        cv.drawText(names[idx], cx, cy + d * .2f, Combo.text(tf, d * .065f, lk.ink, lk))
        val toFull = ((if (ph <= .5) .5 - ph else 1.5 - ph) * 29.53).toInt()
        cv.drawText("%$illum aydınlık · dolunaya $toFull gün", cx, cy + d * .29f, Combo.text(tf, d * .045f, lk.sub, lk))
    }

    /** Tek daire widget'ı (2x2) veya iki daire (4x2). */
    fun render(c: Context, w: Weather?, wdp: Int, hdp: Int, slots: IntArray): Bitmap {
        val k = 2.5f
        val W = (wdp.coerceIn(80, 560) * k).toInt()
        val H = (hdp.coerceIn(60, 400) * k).toInt()
        val b = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888)
        val cv = Canvas(b)
        val lk = Combo.look(c, Palette.get(c))
        val pad = 6 * k
        if (slots.size == 1) {
            val d = min(W, H) - 2 * pad
            draw(c, cv, W / 2f, H / 2f, d, lk, w, slots[0])
        } else {
            val d = min(H - 2 * pad, (W - 3 * pad) / 2f)
            val gap = (W - 2 * d) / 3f
            draw(c, cv, gap + d / 2, H / 2f, d, lk, w, slots[0])
            draw(c, cv, W - gap - d / 2, H / 2f, d, lk, w, slots[1])
        }
        return b
    }
}
