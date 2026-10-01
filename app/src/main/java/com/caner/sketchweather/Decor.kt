package com.caner.sketchweather

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import kotlin.math.min

/** Her widget'a uygulanabilen ortak görünüm katmanı: ek zemin, köşe, çerçeve. */
object Decor {
    val BG_NAMES = arrayOf("Tasarımın kendi zemini", "Düz renk", "Degrade", "Havaya göre (detaylı)", "Gökyüzü resmi", "Kağıt dokusu")
    val SWATCH_NAMES = arrayOf("Gece", "Lacivert", "Mor", "Orman", "Kahve", "Bordo", "Grafit", "Siyah")
    private val SWATCH = intArrayOf(0xFF1E222B.toInt(), 0xFF22345A.toInt(), 0xFF3A2C5C.toInt(), 0xFF1D4038.toInt(),
        0xFF4A3426.toInt(), 0xFF5A1F2E.toInt(), 0xFF3A3F48.toInt(), 0xFF000000.toInt())
    val RADIUS_NAMES = arrayOf("Keskin", "Yumuşak", "Yuvarlak", "Ekstra yuvarlak")
    private val RADIUS = floatArrayOf(6f, 16f, 26f, 44f)
    val FRAME_NAMES = arrayOf("Yok", "İnce", "Kalın")
    val FRAME_COLOR_NAMES = arrayOf("Beyaz", "Siyah", "Vurgu rengi", "Altın")

    fun mode(c: Context) = Cfg.int(c, "ubg", 0).coerceIn(0, BG_NAMES.size - 1)
    fun swatch(c: Context) = Cfg.int(c, "ubgColor", 0).coerceIn(0, SWATCH.size - 1)
    fun op(c: Context) = Cfg.int(c, "ubgOp", 85).coerceIn(0, 100)
    fun radius(c: Context) = Cfg.int(c, "ubgRad", 2).coerceIn(0, RADIUS.size - 1)
    fun frame(c: Context) = Cfg.int(c, "ubgFrame", 0).coerceIn(0, FRAME_NAMES.size - 1)
    fun frameColor(c: Context) = Cfg.int(c, "ubgFrameCol", 0).coerceIn(0, FRAME_COLOR_NAMES.size - 1)
    fun active(c: Context) = mode(c) > 0 || frame(c) > 0

    /** Kaynak görselin arkasına zemin, önüne çerçeve ekler. */
    fun decorate(c: Context, w: Weather?, src: Bitmap): Bitmap {
        if (!active(c)) return src
        val out = Bitmap.createBitmap(src.width, src.height, Bitmap.Config.ARGB_8888)
        val cv = Canvas(out)
        val k = 2.5f
        val r = RectF(3 * k, 3 * k, src.width - 3 * k, src.height - 3 * k)
        val rad = min(RADIUS[radius(c)] * k, min(r.width(), r.height()) / 2)
        val clip = Path().apply { addRoundRect(r, rad, rad, Path.Direction.CW) }
        if (mode(c) > 0) {
            val layer = cv.saveLayerAlpha(0f, 0f, out.width.toFloat(), out.height.toFloat(), (op(c) * 2.55f).toInt())
            cv.save(); cv.clipPath(clip)
            drawBg(c, cv, r, w)
            cv.restore()
            cv.restoreToCount(layer)
        }
        cv.drawBitmap(src, 0f, 0f, null)
        if (frame(c) > 0) {
            val col = when (frameColor(c)) { 1 -> Color.BLACK; 2 -> Palette.get(c).accentLight; 3 -> 0xFFE3B65A.toInt(); else -> Color.WHITE }
            cv.drawPath(clip, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE; strokeWidth = if (frame(c) == 1) 1.2f * k else 3f * k
                color = col; alpha = if (frame(c) == 1) 140 else 220
            })
        }
        return out
    }

    /** Sadece zemin (yazı olmayan klasik düzenlerin arka planı için). */
    fun backgroundOnly(c: Context, w: Weather?, wdp: Int, hdp: Int): Bitmap {
        val k = 2.5f
        val b = Bitmap.createBitmap((wdp.coerceIn(60, 560) * k).toInt(), (hdp.coerceIn(40, 420) * k).toInt(), Bitmap.Config.ARGB_8888)
        return decorate(c, w, b)
    }

    private fun drawBg(c: Context, cv: Canvas, r: RectF, w: Weather?) {
        val base = SWATCH[swatch(c)]
        when (mode(c)) {
            1 -> cv.drawRect(r, Paint().apply { color = base })
            2 -> {
                val acc = Palette.get(c).accent
                cv.drawRect(r, Paint().apply { shader = LinearGradient(r.left, r.top, r.right, r.bottom, base, Color.argb(255, (Color.red(acc) * .7f).toInt(), (Color.green(acc) * .7f).toInt(), (Color.blue(acc) * .7f).toInt()), Shader.TileMode.CLAMP) })
            }
            3 -> weather(cv, r, w)
            4 -> sky(c, cv, r, w)
            else -> {
                cv.drawRect(r, Paint().apply { color = base })
                Origami.meshOn(cv, r, 31, 5, 1.2f)
            }
        }
    }

    private fun rnd(seed: Int): () -> Float { var x = seed.toLong(); return { x = (x * 16807) % 2147483647; x / 2147483647f } }

    /** Havaya göre detaylı zemin: degrade gökyüzü + duruma özel öğeler. */
    private fun weather(cv: Canvas, r: RectF, w: Weather?) {
        val idx = if (w != null) WeatherRepo.iconIndex(w.code, w.isDay) else 4
        val (top, bot) = when (idx) {
            0 -> Pair(0xFF2E7BD6.toInt(), 0xFFF2A65A.toInt())
            1 -> Pair(0xFF0B1230.toInt(), 0xFF283B78.toInt())
            2 -> Pair(0xFF3C86DA.toInt(), 0xFF8EC2F0.toInt())
            3 -> Pair(0xFF111A3D.toInt(), 0xFF34477E.toInt())
            4 -> Pair(0xFF4B5868.toInt(), 0xFF7F8B9C.toInt())
            5 -> Pair(0xFF5F6975.toInt(), 0xFF98A2AE.toInt())
            6, 7 -> Pair(0xFF1E2C47.toInt(), 0xFF4B6385.toInt())
            8 -> Pair(0xFF5A7392.toInt(), 0xFF9DB4CF.toInt())
            else -> Pair(0xFF1B1430.toInt(), 0xFF4A3970.toInt())
        }
        cv.drawRect(r, Paint().apply { shader = LinearGradient(0f, r.top, 0f, r.bottom, top, bot, Shader.TileMode.CLAMP) })
        val s = min(r.width(), r.height())
        val rn = rnd(idx * 101 + 7)
        val soft = Paint(Paint.ANTI_ALIAS_FLAG).apply { maskFilter = BlurMaskFilter(s * .08f, BlurMaskFilter.Blur.NORMAL) }
        fun cloud(cx: Float, cy: Float, sz: Float, a: Int) {
            soft.color = Color.argb(a, 255, 255, 255)
            cv.drawOval(RectF(cx - sz, cy - sz * .35f, cx + sz, cy + sz * .35f), soft)
            cv.drawCircle(cx - sz * .3f, cy - sz * .25f, sz * .38f, soft)
            cv.drawCircle(cx + sz * .25f, cy - sz * .32f, sz * .45f, soft)
        }
        fun stars(n: Int) {
            val p = Paint(Paint.ANTI_ALIAS_FLAG)
            repeat(n) {
                p.color = Color.WHITE; p.alpha = (90 + rn() * 165).toInt()
                cv.drawCircle(r.left + rn() * r.width(), r.top + rn() * r.height() * .7f, s * (.004f + rn() * .007f), p)
            }
        }
        when (idx) {
            0 -> {
                cv.drawCircle(r.right - s * .18f, r.top + s * .2f, s * .55f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    shader = RadialGradient(r.right - s * .18f, r.top + s * .2f, s * .55f, intArrayOf(0xCCFFF1C0.toInt(), 0x66FFC870, 0x00FFC870), floatArrayOf(0f, .35f, 1f), Shader.TileMode.CLAMP)
                })
                cv.drawCircle(r.right - s * .18f, r.top + s * .2f, s * .08f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFFF4D6.toInt() })
            }
            1 -> { stars(70); cv.drawCircle(r.right - s * .2f, r.top + s * .2f, s * .3f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    shader = RadialGradient(r.right - s * .2f, r.top + s * .2f, s * .3f, 0x55B8C8FF, 0x00B8C8FF, Shader.TileMode.CLAMP) })
                   cv.drawCircle(r.right - s * .2f, r.top + s * .2f, s * .055f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFF4EBCF.toInt() }) }
            2 -> { cv.drawCircle(r.right - s * .15f, r.top + s * .18f, s * .4f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    shader = RadialGradient(r.right - s * .15f, r.top + s * .18f, s * .4f, 0x99FFE9B0.toInt(), 0x00FFE9B0, Shader.TileMode.CLAMP) })
                   cloud(r.left + r.width() * .3f, r.bottom - s * .2f, s * .45f, 150); cloud(r.right - r.width() * .25f, r.bottom - s * .12f, s * .5f, 170) }
            3 -> { stars(40); cloud(r.left + r.width() * .35f, r.bottom - s * .18f, s * .45f, 80); cloud(r.right - r.width() * .2f, r.bottom - s * .1f, s * .5f, 90) }
            4 -> for (i in 0 until 4) cloud(r.left + r.width() * (.15f + i * .25f), r.top + s * (.25f + (i % 2) * .4f), s * .5f, 70)
            5 -> {
                val band = Paint(Paint.ANTI_ALIAS_FLAG).apply { maskFilter = BlurMaskFilter(s * .06f, BlurMaskFilter.Blur.NORMAL); color = 0x55FFFFFF }
                for (i in 0 until 5) cv.drawRect(r.left - 20, r.top + r.height() * (.15f + i * .18f), r.right + 20, r.top + r.height() * (.2f + i * .18f), band)
            }
            6, 7 -> {
                cloud(r.left + r.width() * .3f, r.top + s * .1f, s * .6f, 60); cloud(r.right - r.width() * .3f, r.top + s * .05f, s * .6f, 50)
                val rp = Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeWidth = s * .006f; strokeCap = Paint.Cap.ROUND }
                repeat(if (idx == 6) 40 else 90) {
                    val x = r.left + rn() * r.width(); val y = r.top + rn() * r.height(); val l = s * (.04f + rn() * .06f)
                    rp.color = Color.argb((60 + rn() * 80).toInt(), 200, 220, 255)
                    cv.drawLine(x, y, x - l * .3f, y + l, rp)
                }
            }
            8 -> { val p = Paint(Paint.ANTI_ALIAS_FLAG); repeat(80) { p.color = Color.WHITE; p.alpha = (120 + rn() * 130).toInt()
                    cv.drawCircle(r.left + rn() * r.width(), r.top + rn() * r.height(), s * (.006f + rn() * .012f), p) } }
            else -> {
                cloud(r.left + r.width() * .4f, r.top + s * .1f, s * .7f, 45)
                val bolt = Path().apply {
                    val bx = r.left + r.width() * .72f; val by = r.top + r.height() * .15f; val u = s * .3f
                    moveTo(bx, by); lineTo(bx - u * .35f, by + u * .55f); lineTo(bx - u * .05f, by + u * .55f)
                    lineTo(bx - u * .3f, by + u * 1.1f); lineTo(bx + u * .25f, by + u * .4f); lineTo(bx - u * .02f, by + u * .4f); close()
                }
                cv.drawPath(bolt, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x40FFD86A; maskFilter = BlurMaskFilter(s * .02f, BlurMaskFilter.Blur.NORMAL) })
            }
        }
    }

    private val SKY = intArrayOf(R.drawable.sky_sun, R.drawable.sky_moon, R.drawable.sky_partly, R.drawable.sky_partly_night,
        R.drawable.sky_cloud, R.drawable.sky_fog, R.drawable.sky_rain, R.drawable.sky_rain, R.drawable.sky_snow, R.drawable.sky_storm)

    private fun sky(c: Context, cv: Canvas, r: RectF, w: Weather?) {
        val idx = if (w != null) WeatherRepo.iconIndex(w.code, w.isDay) else 2
        try {
            val b = BitmapFactory.decodeResource(c.resources, SKY[idx])
            val sc = maxOf(r.width() / b.width, r.height() / b.height)
            val dw = b.width * sc; val dh = b.height * sc
            cv.drawBitmap(b, null, RectF(r.centerX() - dw / 2, r.centerY() - dh / 2, r.centerX() + dw / 2, r.centerY() + dh / 2), Paint(Paint.FILTER_BITMAP_FLAG))
            cv.drawRect(r, Paint().apply { color = 0x40000000 })
        } catch (e: Exception) { weather(cv, r, w) }
    }
}
