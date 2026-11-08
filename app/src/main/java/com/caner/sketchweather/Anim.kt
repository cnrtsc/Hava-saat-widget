package com.caner.sketchweather

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.net.Uri
import android.widget.RemoteViews
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * Animasyonlu hava widget'ı. Arka plan efektleri 12 kare olarak PNG'ye yazılır,
 * ViewFlipper bunları sırayla döndürür; saat ve yazılar ayrı bir katmanda dakikada bir güncellenir.
 */
object Anim {
    const val N = 12
    const val SUN = 0; const val PARTLY = 1; const val CLOUD = 2; const val RAIN = 3; const val SNOW = 4; const val STORM = 5; const val FOG = 6; const val NIGHT = 7
    val FORCE_NAMES = arrayOf("Gerçek hava", "Güneşli", "Parçalı", "Bulutlu", "Yağmur", "Kar", "Fırtına", "Sis", "Gece")
    val SPEED_NAMES = arrayOf("Yavaş", "Normal", "Hızlı")
    private val FRAME_IDS = intArrayOf(R.id.af0, R.id.af1, R.id.af2, R.id.af3, R.id.af4, R.id.af5, R.id.af6, R.id.af7, R.id.af8, R.id.af9, R.id.af10, R.id.af11)
    private val tr = Locale("tr", "TR")

    fun force(c: Context) = Cfg.int(c, "animForce", 0).coerceIn(0, FORCE_NAMES.size - 1)
    fun speed(c: Context) = Cfg.int(c, "animSpeed", 1).coerceIn(0, 2)
    fun on(c: Context) = Cfg.bool(c, "animOn", true)

    private val SKY = arrayOf(
        intArrayOf(Color.rgb(70, 150, 230), Color.rgb(150, 205, 250)), intArrayOf(Color.rgb(96, 150, 214), Color.rgb(176, 206, 236)),
        intArrayOf(Color.rgb(92, 106, 128), Color.rgb(146, 158, 176)), intArrayOf(Color.rgb(44, 58, 82), Color.rgb(92, 108, 132)),
        intArrayOf(Color.rgb(150, 170, 196), Color.rgb(214, 224, 238)), intArrayOf(Color.rgb(22, 24, 40), Color.rgb(62, 60, 86)),
        intArrayOf(Color.rgb(168, 174, 182), Color.rgb(210, 212, 214)), intArrayOf(Color.rgb(10, 16, 44), Color.rgb(40, 52, 98)))
    private val BASE_MS = intArrayOf(160, 160, 200, 90, 140, 90, 200, 220)

    /** Hava kodundan sahne. light = çisenti / hafif yağış, clouds = gece bulutlu. */
    data class Scene(val kind: Int, val light: Boolean, val clouds: Boolean)

    fun scene(c: Context, w: Weather?): Scene {
        val f = force(c)
        if (f > 0) return Scene(f - 1, false, false)
        if (w == null) return Scene(CLOUD, false, false)
        return when (WeatherRepo.iconIndex(w.code, w.isDay)) {
            0 -> Scene(SUN, false, false); 1 -> Scene(NIGHT, false, false); 2 -> Scene(PARTLY, false, false); 3 -> Scene(NIGHT, false, true)
            4 -> Scene(CLOUD, false, false); 5 -> Scene(FOG, false, false); 6 -> Scene(RAIN, true, false); 7 -> Scene(RAIN, false, false)
            8 -> Scene(SNOW, false, false); else -> Scene(STORM, false, false)
        }
    }

    /* ---------------- widget'a uygula ---------------- */
    fun apply(c: Context, v: RemoteViews, w: Weather?, wdp: Int, hdp: Int) {
        val sc = scene(c, w)
        val k = 1.8f
        val W = (wdp.coerceIn(80, 560) * k).toInt(); val H = (hdp.coerceIn(60, 420) * k).toInt()
        val id = Cfg.current() ?: 0
        val uris = frames(c, id, sc, W, H)
        val animate = on(c)
        for (i in 0 until N) v.setImageViewUri(FRAME_IDS[i], if (animate || i == 0) uris[i] else uris[0])
        val ms = (BASE_MS[sc.kind] * floatArrayOf(1.6f, 1f, .7f)[speed(c)]).toInt()
        v.setInt(R.id.anim, "setFlipInterval", if (animate) ms else 60_000)
        v.setImageViewBitmap(R.id.dial, content(c, w, sc, wdp, hdp))
    }

    /** Kareleri gerekirse üretir, içerik adreslerini döndürür. */
    private fun frames(c: Context, id: Int, sc: Scene, W: Int, H: Int): List<Uri> {
        val dir = File(c.filesDir, "anim").apply { mkdirs() }
        val key = "w${id}_${sc.kind}${if (sc.light) "l" else ""}${if (sc.clouds) "c" else ""}_${W}x$H"
        val files = (0 until N).map { File(dir, "${key}_$it.png") }
        if (files.any { !it.exists() }) {
            dir.listFiles()?.filter { it.name.startsWith("w${id}_") && !it.name.startsWith(key) }?.forEach { it.delete() }
            for (i in 0 until N) {
                val b = frame(sc, i, W, H)
                FileOutputStream(files[i]).use { b.compress(Bitmap.CompressFormat.PNG, 100, it) }
                b.recycle()
            }
        }
        return files.map { Uri.parse("content://${c.packageName}.anim/${it.name}") }
    }

    /* ---------------- efektler ---------------- */
    private class Rnd(var s: Long) { fun f(): Float { s = (s * 6364136223846793005L + 1442695040888963407L); return ((s ushr 33) % 100000) / 100000f } }

    private fun cloudPath(cx: Float, cy: Float, s: Float): Path = Path().apply {
        val parts = arrayOf(floatArrayOf(-.5f, .1f, .38f), floatArrayOf(0f, -.15f, .55f), floatArrayOf(.5f, .1f, .4f), floatArrayOf(-.15f, .25f, .4f), floatArrayOf(.25f, .25f, .4f))
        for (p in parts) addCircle(cx + p[0] * s, cy + p[1] * s, p[2] * s, Path.Direction.CW)
    }

    fun frame(sc: Scene, i: Int, W: Int, H: Int): Bitmap {
        val b = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888)
        val cv = TCanvas(b)
        val Wf = W.toFloat(); val Hf = H.toFloat(); val s = Hf / 300f   // önizlemedeki ölçeğe göre
        val t = i / N.toFloat()
        val rng = Rnd(7)
        val kind = sc.kind
        val clip = Path().apply { addRoundRect(RectF(0f, 0f, Wf, Hf), 36 * s, 36 * s, Path.Direction.CW) }
        cv.clipPath(clip)
        val sky = SKY[kind]
        cv.drawRect(0f, 0f, Wf, Hf, Paint().apply { shader = LinearGradient(0f, 0f, 0f, Hf, sky[0], sky[1], Shader.TileMode.CLAMP) })
        val p = Paint(Paint.ANTI_ALIAS_FLAG)

        if (kind == SUN || kind == PARTLY) {
            val sx = Wf * .78f; val sy = Hf * .22f
            val pulse = 1 + .06f * sin(2 * PI * t).toFloat()
            val gr = 150 * s * pulse
            cv.drawCircle(sx, sy, gr, Paint(Paint.ANTI_ALIAS_FLAG).apply { shader = RadialGradient(sx, sy, gr, Color.argb(130, 255, 240, 190), Color.argb(0, 255, 240, 190), Shader.TileMode.CLAMP) })
            p.color = Color.argb(150, 255, 236, 160); p.strokeWidth = 5 * s; p.strokeCap = Paint.Cap.ROUND
            for (k in 0 until 12) {
                val a = 2 * PI * (k / 12.0 + t / 12.0)
                val r0 = 62 * s; val r1 = (120 + 18 * sin(2 * PI * (t + k / 4.0))).toFloat() * s
                cv.drawLine(sx + r0 * cos(a).toFloat(), sy + r0 * sin(a).toFloat(), sx + r1 * cos(a).toFloat(), sy + r1 * sin(a).toFloat(), p)
            }
            p.color = Color.rgb(255, 222, 120); cv.drawCircle(sx, sy, 48 * s, p)
            for (k in 0 until 18) {
                val x0 = rng.f() * Wf; val y0 = rng.f() * Hf; val sp = if (rng.f() > .5f) 2 else 1
                val y = ((y0 - t * sp * Hf * .25f) % Hf + Hf) % Hf
                val a = (120 * (.5 + .5 * sin(2 * PI * (t + k / 7.0)))).toInt()
                p.color = Color.argb(a, 255, 250, 220); cv.drawCircle(x0, y, (1.5f + rng.f() * 1.5f) * s, p)
            }
        }
        if (kind == NIGHT) {
            for (k in 0 until 46) {
                val x = rng.f() * Wf; val y = rng.f() * Hf * .8f; val ph = rng.f(); val m = if (rng.f() > .5f) 2 else 1
                val a = (80 + 175 * (.5 + .5 * sin(2 * PI * (t * m + ph)))).toInt()
                p.color = Color.argb(a, 255, 255, 255); cv.drawCircle(x, y, (1 + rng.f() * 1.6f) * s, p)
            }
            val mx = Wf * .8f; val my = Hf * .26f
            cv.drawCircle(mx, my, 90 * s, Paint(Paint.ANTI_ALIAS_FLAG).apply { shader = RadialGradient(mx, my, 90 * s, Color.argb(70, 200, 210, 255), Color.argb(0, 200, 210, 255), Shader.TileMode.CLAMP) })
            val moon = Path().apply { addCircle(mx, my, 40 * s, Path.Direction.CW) }
            moon.op(Path().apply { addCircle(mx + 20 * s, my - 14 * s, 38 * s, Path.Direction.CW) }, Path.Op.DIFFERENCE)
            p.color = Color.rgb(250, 244, 220); cv.drawPath(moon, p)
        }
        val layers: Array<FloatArray>? = when {
            kind == PARTLY -> arrayOf(floatArrayOf(.2f, 255f, 255f, 255f, 200f, 1f, 3f))
            kind == CLOUD -> arrayOf(floatArrayOf(.12f, 200f, 208f, 222f, 170f, 1f, 4f), floatArrayOf(.3f, 236f, 240f, 248f, 190f, 2f, 3f))
            kind == RAIN -> arrayOf(floatArrayOf(.06f, 64f, 78f, 102f, 220f, 1f, 4f), floatArrayOf(.16f, 90f, 104f, 128f, 200f, 2f, 3f))
            kind == STORM -> arrayOf(floatArrayOf(.05f, 36f, 36f, 56f, 230f, 1f, 4f), floatArrayOf(.15f, 58f, 56f, 80f, 210f, 2f, 3f))
            kind == SNOW -> arrayOf(floatArrayOf(.08f, 236f, 240f, 248f, 190f, 1f, 3f))
            kind == NIGHT && sc.clouds -> arrayOf(floatArrayOf(.18f, 120f, 128f, 170f, 170f, 1f, 3f))
            else -> null
        }
        layers?.forEach { L ->
            val cp = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(L[4].toInt(), L[1].toInt(), L[2].toInt(), L[3].toInt()); maskFilter = BlurMaskFilter(4 * s, BlurMaskFilter.Blur.NORMAL) }
            val n = L[6].toInt(); val sp = L[5]
            val path = Path()
            for (k in 0 until n) {
                val x = ((k / n.toFloat() + t * sp / n) % 1f) * (Wf + 300 * s) - 150 * s
                path.op(cloudPath(x, Hf * L[0] + (k % 2) * 14 * s, (58 + 14 * (k % 3)) * s), Path.Op.UNION)
            }
            cv.drawPath(path, cp)
        }
        if (kind == RAIN || kind == STORM) {
            val n = if (kind == STORM) 110 else if (sc.light) 30 else 70
            p.color = Color.argb(150, 200, 216, 240); p.strokeWidth = 2 * s; p.strokeCap = Paint.Cap.ROUND
            for (k in 0 until n) {
                val x0 = rng.f() * (Wf + 60 * s) - 60 * s; val y0 = rng.f() * Hf; val ln = (26 + rng.f() * 18) * s * (if (sc.light) .5f else 1f)
                val y = ((y0 + t * Hf * 2) % (Hf + ln)) - ln; val x = x0 + (y - y0) * .18f
                cv.drawLine(x, y, x + ln * .18f, y + ln, p)
            }
            val rp = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 2 * s }
            for (k in 0 until 8) {
                val x = rng.f() * Wf; val ph = (t + k / 8f) % 1f; val r = ph * 22 * s
                rp.color = Color.argb((180 * (1 - ph)).toInt(), 210, 226, 250)
                cv.drawOval(RectF(x - r, Hf - 18 * s - r * .3f, x + r, Hf - 18 * s + r * .3f), rp)
            }
        }
        if (kind == STORM && (i == 4 || i == 5)) {
            cv.drawColor(Color.argb(if (i == 4) 120 else 50, 255, 255, 255))
            if (i == 4) {
                val bx = Wf * .62f
                val bp = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(255, 250, 220); strokeWidth = 5 * s; style = Paint.Style.STROKE; strokeJoin = Paint.Join.ROUND }
                cv.drawPath(Path().apply { moveTo(bx, 0f); lineTo(bx - 30 * s, Hf * .3f); lineTo(bx + 10 * s, Hf * .32f); lineTo(bx - 40 * s, Hf * .7f) }, bp)
            }
        }
        if (kind == SNOW) {
            p.color = Color.argb(230, 255, 255, 255)
            for (k in 0 until 60) {
                val x0 = rng.f() * Wf; val y0 = rng.f() * Hf; val sp = if (rng.f() > .66f) 2 else 1; val r = (2.5f + rng.f() * 3f) * s
                val y = ((y0 + t * sp * Hf) % (Hf + 2 * r)) - r; val x = x0 + 14 * s * sin(2 * PI * (t + k / 9.0)).toFloat()
                cv.drawCircle(x, y, r, p)
            }
            val pile = Path().apply { moveTo(0f, Hf); var x = 0f; while (x <= Wf) { lineTo(x, Hf - (16 + 8 * sin(x / (60 * s))) * s); x += 20 * s }; lineTo(Wf, Hf); close() }
            p.color = Color.rgb(250, 252, 255); cv.drawPath(pile, p)
        }
        if (kind == FOG) {
            val fp = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(110, 255, 255, 255); maskFilter = BlurMaskFilter(16 * s, BlurMaskFilter.Blur.NORMAL) }
            for (k in 0 until 5) {
                val y = Hf * (.2f + k * .17f); val off = sin(2 * PI * (t + k / 5.0)).toFloat() * 60 * s
                cv.drawRoundRect(RectF(Wf * (.05f + .25f * (k % 3)) + off, y - 12 * s, Wf * (.55f + .25f * (k % 3)) + off, y + 12 * s), 12 * s, 12 * s, fp)
            }
        }
        return b
    }

    /* ---------------- yazı katmanı ---------------- */
    fun content(c: Context, w: Weather?, sc: Scene, wdp: Int, hdp: Int): Bitmap {
        val k = 2.5f
        val W = (wdp.coerceIn(80, 560) * k).toInt(); val H = (hdp.coerceIn(60, 420) * k).toInt()
        val b = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888)
        val cv = TCanvas(b)
        val Wf = W.toFloat(); val Hf = H.toFloat(); val s = Hf / 300f
        val lightBg = sc.kind == SNOW || sc.kind == FOG
        Ink.setBg(if (lightBg) Color.rgb(220, 226, 236) else SKY[sc.kind][0])
        val ink0 = if (lightBg) Color.rgb(30, 40, 60) else Color.WHITE
        val sub0 = if (lightBg) Color.argb(220, 50, 60, 80) else Color.argb(225, 235, 240, 250)
        val ink = Ink.get(c, Ink.TEXT) ?: ink0
        val sub = Ink.get(c, Ink.SUB) ?: sub0
        val shadow = !lightBg
        val lk = Combo.Look(0, ink, sub, ink, shadow, 0)
        val tf = Style.textTypeface(c, Style.font(c))
        fun tp(size: Float, col: Int, al: Paint.Align = Paint.Align.LEFT) = Combo.text(tf, size, col, lk, al)
        val tall = Hf / Wf > .7f
        val pad = 30 * s
        // konum
        val loc = WeatherRepo.city(c)
        val locCol = Ink.get(c, Ink.LOC) ?: ink
        val px = pad + 12 * s; val py = 46 * s
        val pin = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = locCol; if (shadow) setShadowLayer(4f, 0f, 1f, 0x55000000) }
        val pinPath = Path().apply { addCircle(px, py - 11 * s, 9 * s, Path.Direction.CW); moveTo(px - 8 * s, py - 8 * s); lineTo(px + 8 * s, py - 8 * s); lineTo(px, py + 7 * s); close() }
        cv.drawPath(pinPath, pin)
        cv.drawCircle(px, py - 11 * s, 3.5f * s, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = SKY[sc.kind][0] })
        cv.drawText(loc, px + 16 * s, py + 2 * s, tp(26 * s, locCol).apply { val m = measureText(loc); val mx = Wf * (if (tall) .82f else .6f); if (m > mx) textSize *= mx / m })
        val time = SimpleDateFormat("HH:mm", tr).format(Date())
        val tempS = if (w != null) "${w.temp}°" else "--°"
        val desc = if (w != null) WeatherRepo.label(w.code) else "Yükleniyor…"
        val date = SimpleDateFormat("EEEE, d MMMM", tr).format(Date())
        val tcol = Ink.get(c, Ink.TEMP) ?: ink
        if (!tall) {
            val cp = Lock.clockPaint(c, 104 * s, ink, lk).apply { textAlign = android.graphics.Paint.Align.LEFT }
            val mw = Ink.measure(c, time, cp); val maxW = Wf * .6f; if (mw > maxW) cp.textSize *= maxW / mw
            Ink.time(c, cv, time, pad, 170 * s, cp)
            cv.drawText(date, pad + 6 * s, 204 * s, tp(20 * s, sub))
            cv.drawText(tempS, Wf - pad, 236 * s, tp(64 * s * Ink.tempScale(c), tcol, Paint.Align.RIGHT).apply { typeface = Style.clockTypeface(c, Style.font(c)) })
            cv.drawText(desc, Wf - pad - 2 * s, 266 * s, tp(20 * s, sub, Paint.Align.RIGHT))
        } else {
            val cp = Lock.clockPaint(c, 84 * s, ink, lk).apply { textAlign = android.graphics.Paint.Align.LEFT }
            val mw = Ink.measure(c, time, cp); val maxW = Wf - pad * 2; if (mw > maxW) cp.textSize *= maxW / mw
            Ink.time(c, cv, time, pad, Hf * .5f, cp)
            cv.drawText(date, pad + 4 * s, Hf * .5f + 30 * s, tp(18 * s, sub).apply { val m = measureText(date); if (m > maxW) textSize *= maxW / m })
            cv.drawText(tempS, pad, Hf - pad - 20 * s, tp(56 * s * Ink.tempScale(c), tcol).apply { typeface = Style.clockTypeface(c, Style.font(c)) })
            cv.drawText(desc, pad + 2 * s, Hf - pad + 4 * s, tp(18 * s, sub))
        }
        return b
    }
}
