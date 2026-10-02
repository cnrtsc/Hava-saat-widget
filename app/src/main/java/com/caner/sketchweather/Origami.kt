package com.caner.sketchweather

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.min

/** Origami hava widget'ları: katlanmış kağıt zemin ve origami ikonlar. */
object Origami {
    const val CARD = 0; const val STRIP = 1; const val SQUARE = 2; const val DIAG = 3
    val PAPER_NAMES = arrayOf("Koyu kağıt", "Krem kağıt", "Havaya göre", "Şeffaf")
    const val ICON_SET = 15
    private val tr = Locale("tr", "TR")

    fun paper(c: Context) = Cfg.int(c, "paperColor", 0).coerceIn(0, PAPER_NAMES.size - 1)

    private fun icon(c: Context, code: Int, day: Boolean): Int {
        val id = Cfg.current()
        val p = WeatherRepo.prefs(c)
        return Style.iconPref(c, 0, 1, code, day)
    }

    private class Tone(val bg: Int?, val ink: Int, val sub: Int, val shadow: Boolean)

    private fun tone(c: Context, w: Weather?): Tone {
        val op = Style.opacity(c) / 100f
        fun a(col: Int) = Color.argb((255 * op).toInt(), Color.red(col), Color.green(col), Color.blue(col))
        val tc = Style.textColor(c)
        return when (paper(c)) {
            1 -> Tone(a(Color.rgb(239, 230, 214)), tc ?: Color.rgb(42, 38, 34), Color.rgb(107, 98, 88), false)
            2 -> {
                val (cc, _) = WeatherRepo.conditionColor(w)
                val dk = Color.rgb((Color.red(cc) * .72f).toInt(), (Color.green(cc) * .72f).toInt(), (Color.blue(cc) * .72f).toInt())
                Tone(a(dk), tc ?: Color.WHITE, Color.rgb(220, 226, 236), false)
            }
            3 -> Tone(null, tc ?: Color.WHITE, Color.rgb(226, 230, 238), true)
            else -> Tone(a(Color.rgb(43, 49, 60)), tc ?: Color.WHITE, Color.rgb(201, 208, 219), false)
        }
    }

    /** Düşük poligonlu kat dokusu: sol üstten ışık alan açık/koyu üçgenler. */
    private fun mesh(cv: Canvas, r: RectF, seed: Int, n: Int, k: Float) {
        var x = seed.toLong().coerceAtLeast(1)
        fun rnd(): Float { x = (x * 16807) % 2147483647; return x / 2147483647f }
        val nx = maxOf(2, (n * r.width() / r.height()).toInt())
        val pts = Array((n + 1) * (nx + 1)) { FloatArray(2) }
        for (j in 0..n) for (i in 0..nx) {
            val edge = i == 0 || j == 0 || i == nx || j == n
            pts[j * (nx + 1) + i][0] = r.left + i * r.width() / nx + if (edge) 0f else (rnd() - .5f) * r.width() / nx * .7f
            pts[j * (nx + 1) + i][1] = r.top + j * r.height() / n + if (edge) 0f else (rnd() - .5f) * r.height() / n * .7f
        }
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        val path = Path()
        for (j in 0 until n) for (i in 0 until nx) {
            val a = pts[j * (nx + 1) + i]; val b = pts[j * (nx + 1) + i + 1]; val cc = pts[(j + 1) * (nx + 1) + i]; val d = pts[(j + 1) * (nx + 1) + i + 1]
            val tris = if (rnd() > .5f) listOf(arrayOf(a, b, d), arrayOf(a, d, cc)) else listOf(arrayOf(a, b, cc), arrayOf(b, d, cc))
            for (t in tris) {
                val cx = (t[0][0] + t[1][0] + t[2][0]) / 3; val cy = (t[0][1] + t[1][1] + t[2][1]) / 3
                val v = ((1 - (cx - r.left) / r.width()) * .4f + (1 - (cy - r.top) / r.height()) * .6f - .5f) * .3f * k + (rnd() - .5f) * .2f * k
                p.color = if (v > 0) Color.WHITE else Color.BLACK
                p.alpha = (min(.38f, abs(v)) * 255).toInt()
                path.reset(); path.moveTo(t[0][0], t[0][1]); path.lineTo(t[1][0], t[1][1]); path.lineTo(t[2][0], t[2][1]); path.close()
                cv.drawPath(path, p)
            }
        }
    }

    fun meshOn(cv: Canvas, r: RectF, seed: Int, n: Int, k: Float) = mesh(cv, r, seed, n, k)

    private fun card(cv: Canvas, r: RectF, rad: Float, t: Tone, seed: Int) {
        val bg = t.bg ?: return
        val sh = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = bg; setShadowLayer(rad * .3f, 0f, rad * .15f, 0x80000000.toInt()) }
        cv.drawRoundRect(r, rad, rad, sh)
        val clip = Path().apply { addRoundRect(r, rad, rad, Path.Direction.CW) }
        cv.save(); cv.clipPath(clip)
        mesh(cv, r, seed, 6, 1.8f * Color.alpha(bg) / 255f)
        // ana kat: köşegen boyunca açık ve koyu iki yüz + kat izi
        val a = Color.alpha(bg) / 255f
        val tri1 = Path().apply { moveTo(r.left, r.top); lineTo(r.right, r.top); lineTo(r.left, r.bottom); close() }
        val tri2 = Path().apply { moveTo(r.right, r.top); lineTo(r.right, r.bottom); lineTo(r.left, r.bottom); close() }
        cv.drawPath(tri1, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; alpha = (30 * a).toInt() })
        cv.drawPath(tri2, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK; alpha = (40 * a).toInt() })
        cv.drawLine(r.right, r.top, r.left, r.bottom, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK; alpha = (70 * a).toInt(); strokeWidth = 2.5f })
        cv.drawLine(r.right - 2f, r.top + 2f, r.left + 2f, r.bottom - 2f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; alpha = (45 * a).toInt(); strokeWidth = 1.5f })
        // ikinci, daha yumuşak kat
        cv.drawLine(r.left, r.top + r.height() * .35f, r.right, r.top + r.height() * .55f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK; alpha = (35 * a).toInt(); strokeWidth = 2f })
        cv.restore()
    }

    private fun txt(tf: android.graphics.Typeface, size: Float, col: Int, t: Tone, al: Paint.Align = Paint.Align.LEFT) =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = tf; textSize = size; color = col; textAlign = al
            if (t.shadow) setShadowLayer(size * .08f, 0f, size * .04f, 0x99000000.toInt())
        }

    private fun pin(c: Context, cv: Canvas, x: Float, base: Float, s: Float): Float {
        try {
            val d = c.getDrawable(R.drawable.pin)!!.mutate(); d.setTint(0xFFFFB03C.toInt())
            d.setBounds(x.toInt(), (base - s * .95f).toInt(), (x + s).toInt(), (base + s * .05f).toInt()); d.draw(cv)
        } catch (e: Exception) { }
        return x + s * 1.15f
    }

    fun render(c: Context, w: Weather?, wdp: Int, hdp: Int, kind: Int): Bitmap {
        val k = 2.5f
        val W = (wdp.coerceIn(80, 560) * k).toInt()
        val H = (hdp.coerceIn(40, 400) * k).toInt()
        val b = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888)
        val cv = Canvas(b)
        val Wf = W.toFloat(); val Hf = H.toFloat()
        val t = tone(c, w)
        val font = Style.font(c)
        val tf = Style.textTypeface(c, font)
        val cf = Style.clockTypeface(c, font)
        val now = Date()
        val city = WeatherRepo.city(c)
        val temp = if (w != null) "${w.temp}°" else "--°"
        val desc = if (w != null) WeatherRepo.label(w.code) else "Yükleniyor…"
        val ic = if (w != null) icon(c, w.code, w.isDay) else R.drawable.i_a2_cloud
        val pad = 8f
        when (kind) {
            CARD -> {
                card(cv, RectF(pad, pad, Wf - pad, Hf - pad), Hf * .15f, t, 41)
                Combo.icon(c, cv, ic, Hf * .36f, Hf * .36f, Hf * .62f)
                cv.drawText(temp, Hf * .72f, Hf * .42f, txt(cf, Hf * .28f, t.ink, t))
                cv.drawText(desc, Hf * .73f, Hf * .54f, txt(tf, Hf * .075f, t.ink, t))
                w?.days?.firstOrNull()?.let { cv.drawText("Y ${it.max}°  D ${it.min}°", Hf * .73f, Hf * .64f, txt(tf, Hf * .065f, t.sub, t)) }
                val right = Wf - Hf * .12f
                val cp = txt(tf, Hf * .08f, t.ink, t, Paint.Align.RIGHT).apply { isFakeBoldText = true }
                val cw = cp.measureText(city)
                pin(c, cv, right - cw - Hf * .1f, Hf * .2f, Hf * .085f)
                cv.drawText(city, right, Hf * .2f, cp)
                cv.drawText(SimpleDateFormat("HH:mm", tr).format(now), right, Hf * .42f, Lock.clockPaint(c, Hf * .18f, t.ink, Combo.Look(0, t.ink, t.sub, t.ink, t.shadow, 0)).apply { textAlign = Paint.Align.RIGHT })
                cv.drawText(SimpleDateFormat("EEEE, d MMMM", tr).format(now), right, Hf * .52f, txt(tf, Hf * .062f, t.sub, t, Paint.Align.RIGHT))
                cv.drawLine(Hf * .12f, Hf * .71f, Wf - Hf * .12f, Hf * .71f, Paint().apply { color = t.ink; alpha = 30; strokeWidth = 2f })
                for (i in 0 until 3) {
                    val d = w?.days?.getOrNull(i + 1) ?: continue
                    val cx = Hf * .14f + i * (Wf - Hf * .28f) / 3
                    val y = Hf * .87f
                    val name = LocalDate.parse(d.date).dayOfWeek.getDisplayName(TextStyle.SHORT, tr)
                    val np = txt(tf, Hf * .068f, t.ink, t).apply { isFakeBoldText = true }
                    cv.drawText(name, cx, y, np)
                    val ix = cx + np.measureText(name) + Hf * .1f
                    Combo.icon(c, cv, icon(c, d.code, true), ix, y - Hf * .025f, Hf * .17f)
                    cv.drawText("${d.max}° ${d.min}°", ix + Hf * .1f, y, txt(tf, Hf * .064f, t.sub, t))
                }
            }
            STRIP -> {
                val third = (Wf - 2 * pad) / 3
                val y0 = Hf * .12f; val y1 = Hf * .02f; val y2 = Hf * .98f; val y3 = Hf * .88f
                if (t.bg != null) {
                    val sh = Paint(Paint.ANTI_ALIAS_FLAG).apply { setShadowLayer(Hf * .08f, 0f, Hf * .05f, 0x80000000.toInt()) }
                    val base = t.bg
                    val dark = Color.argb(Color.alpha(base), (Color.red(base) * .78f).toInt(), (Color.green(base) * .78f).toInt(), (Color.blue(base) * .78f).toInt())
                    val panels = listOf(
                        floatArrayOf(pad, y0, pad + third, y1, pad + third, y2, pad, y3),
                        floatArrayOf(pad + third, y1, pad + 2 * third, y0, pad + 2 * third, y3, pad + third, y2),
                        floatArrayOf(pad + 2 * third, y0, Wf - pad, y1, Wf - pad, y2, pad + 2 * third, y3))
                    panels.forEachIndexed { i, q ->
                        val path = Path().apply { moveTo(q[0], q[1]); lineTo(q[2], q[3]); lineTo(q[4], q[5]); lineTo(q[6], q[7]); close() }
                        sh.color = if (i == 1) dark else base
                        cv.drawPath(path, sh)
                        cv.save(); cv.clipPath(path); mesh(cv, RectF(q[0], 0f, q[2], Hf), 77 + i, 3, 1.5f)
                        // panel içinde kata doğru koyulaşan gölge: katlanmanın derinliği
                        val g = if (i == 1) android.graphics.LinearGradient(q[0], 0f, q[2], 0f, 0x38000000, 0x00000000, android.graphics.Shader.TileMode.CLAMP)
                                else android.graphics.LinearGradient(q[0], 0f, q[2], 0f, 0x22FFFFFF, 0x30000000, android.graphics.Shader.TileMode.CLAMP)
                        cv.drawPath(path, Paint().apply { shader = g }); cv.restore()
                    }
                    val fold = Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeWidth = 2f }
                    fold.color = 0x30FFFFFF; cv.drawLine(pad + third, y1, pad + third, y2, fold)
                    fold.color = 0x55000000; cv.drawLine(pad + 2 * third, y0, pad + 2 * third, y3, fold)
                }
                // her panelin eğimi: yazılar kağıdın katına göre açılı durur
                val slope = (y1 - y0) / third
                val deg = Math.toDegrees(kotlin.math.atan(slope.toDouble())).toFloat() * .3f  // hafif eğim
                val look = Combo.Look(0, t.ink, t.sub, t.ink, t.shadow, 0)
                // 1. panel: konum (yükselen kat)
                cv.save(); cv.rotate(deg, pad + third / 2, Hf / 2)
                val cp = txt(tf, Hf * .18f, t.ink, t).apply { isFakeBoldText = true }
                val c2 = WeatherRepo.city2(c)
                val blockW = Hf * .2f + cp.measureText(city)
                val x0 = pad + (third - blockW) / 2
                val x1 = pin(c, cv, x0, Hf * (if (c2.isNotEmpty()) .47f else .58f), Hf * .19f)
                cv.drawText(city, x1, Hf * (if (c2.isNotEmpty()) .47f else .58f), cp)
                if (c2.isNotEmpty()) cv.drawText(c2, pad + third / 2, Hf * .7f, txt(tf, Hf * .13f, t.sub, t, Paint.Align.CENTER))
                cv.restore()
                // 2. panel: saat (alçalan kat, ortada)
                cv.save(); cv.rotate(-deg, Wf / 2, Hf / 2)
                cv.drawText(SimpleDateFormat("HH:mm", tr).format(now), Wf / 2, Hf * .58f, Lock.clockPaint(c, Hf * .38f, t.ink, look))
                cv.drawText(SimpleDateFormat("EEE, d MMM", tr).format(now), Wf / 2, Hf * .78f, txt(tf, Hf * .12f, t.sub, t, Paint.Align.CENTER))
                cv.restore()
                // 3. panel: hava (yükselen kat)
                cv.save(); cv.rotate(deg, pad + 2.5f * third, Hf / 2)
                val tp = txt(cf, Hf * .3f, t.ink, t)
                val iw = Hf * .7f
                val gx = pad + 2 * third + (third - iw - tp.measureText(temp)) / 2
                Combo.icon(c, cv, ic, gx + iw / 2, Hf / 2, iw)
                cv.drawText(temp, gx + iw, Hf * .6f, tp)
                cv.restore()
            }
            SQUARE -> {
                val s = min(Wf, Hf)
                val ox = (Wf - s) / 2; val oy = (Hf - s) / 2
                card(cv, RectF(ox + pad, oy + pad, ox + s - pad, oy + s - pad), s * .16f, t, 9)
                val cp = txt(tf, s * .075f, t.ink, t, Paint.Align.LEFT).apply { isFakeBoldText = true }
                val cw = cp.measureText(city) + s * .09f
                val x1 = pin(c, cv, ox + (s - cw) / 2, oy + s * .17f, s * .075f)
                cv.drawText(city, x1, oy + s * .17f, cp)
                Combo.icon(c, cv, ic, ox + s / 2, oy + s * .46f, s * .54f)
                cv.drawText(temp, ox + s / 2, oy + s * .83f, txt(cf, s * .18f, t.ink, t, Paint.Align.CENTER))
                cv.drawText(desc, ox + s / 2, oy + s * .93f, txt(tf, s * .062f, t.sub, t, Paint.Align.CENTER))
            }
            else -> {
                val s = min(Wf, Hf)
                val ox = (Wf - s) / 2; val oy = (Hf - s) / 2
                if (t.bg != null) {
                    val r = RectF(ox + pad, oy + pad, ox + s - pad, oy + s - pad)
                    val sh = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = t.bg; setShadowLayer(s * .04f, 0f, s * .02f, 0x80000000.toInt()) }
                    cv.drawRect(r, sh)
                    val tri = Path().apply { moveTo(r.left, r.top); lineTo(r.right, r.top); lineTo(r.left, r.bottom); close() }
                    cv.drawPath(tri, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; alpha = 14 })
                    cv.drawLine(r.right, r.top, r.left, r.bottom, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK; alpha = 64; strokeWidth = 2f })
                }
                Combo.icon(c, cv, ic, ox + s * .3f, oy + s * .3f, s * .52f)
                val right = ox + s * .92f
                cv.drawText(temp, right, oy + s * .67f, txt(cf, s * .22f, t.ink, t, Paint.Align.RIGHT))
                val cp = txt(tf, s * .07f, t.ink, t, Paint.Align.RIGHT).apply { isFakeBoldText = true }
                pin(c, cv, right - cp.measureText(city) - s * .085f, oy + s * .8f, s * .07f)
                cv.drawText(city, right, oy + s * .8f, cp)
                w?.days?.firstOrNull()?.let { cv.drawText("Y ${it.max}°  D ${it.min}°", right, oy + s * .9f, txt(tf, s * .058f, t.sub, t, Paint.Align.RIGHT)) }
            }
        }
        return b
    }
}
