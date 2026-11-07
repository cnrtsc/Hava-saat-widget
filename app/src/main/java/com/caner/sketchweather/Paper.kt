package com.caner.sketchweather

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import kotlin.math.min

/**
 * Elle yapılmış kağıt parçaları: yırtık karton, ataçla tutturulmuş kağıt, bantlı not.
 * Origami widget'larında zemin, Kolaj widget'ında parça olarak kullanılır.
 */
object Paper {
    const val TORN = 5; const val CLIP = 6; const val TAPE = 7

    private fun rnd(seed: Int): () -> Float { var x = seed.toLong().coerceAtLeast(1); return { x = (x * 16807) % 2147483647; x / 2147483647f } }

    /** Kenarları yırtık bir dikdörtgen yol: her kenar boyunca düzensiz zikzak. */
    fun tornPath(r: RectF, seed: Int, amp: Float, step: Float): Path {
        val n = rnd(seed)
        val p = Path()
        fun edge(x0: Float, y0: Float, x1: Float, y1: Float, nx: Float, ny: Float, first: Boolean) {
            val len = kotlin.math.hypot(x1 - x0, y1 - y0)
            val k = maxOf(3, (len / step).toInt())
            for (i in 0..k) {
                val t = i.toFloat() / k
                val j = if (i == 0 || i == k) 0f else (n() - .3f) * amp
                val x = x0 + (x1 - x0) * t + nx * j; val y = y0 + (y1 - y0) * t + ny * j
                if (first && i == 0) p.moveTo(x, y) else p.lineTo(x, y)
            }
        }
        edge(r.left, r.top, r.right, r.top, 0f, 1f, true)
        edge(r.right, r.top, r.right, r.bottom, -1f, 0f, false)
        edge(r.right, r.bottom, r.left, r.bottom, 0f, -1f, false)
        edge(r.left, r.bottom, r.left, r.top, 1f, 0f, false)
        p.close()
        return p
    }

    private fun speckle(cv: Canvas, r: RectF, seed: Int, col: Int, count: Int, size: Float, alpha: Int) {
        val n = rnd(seed); val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = col; this.alpha = alpha }
        repeat(count) { cv.drawCircle(r.left + n() * r.width(), r.top + n() * r.height(), size * (.4f + n()), p) }
    }

    /** Yırtık karton: kraft renkli, yırtılmış kenarda açık lif katmanı, oluklu iz ve benekler. */
    fun torn(cv: Canvas, r: RectF, seed: Int, a: Float = 1f, color: Int = Color.rgb(196, 158, 108)) {
        val s = min(r.width(), r.height())
        val amp = s * .045f; val step = s * .05f
        val outer = tornPath(RectF(r.left, r.top, r.right, r.bottom), seed, amp, step)
        val inner = tornPath(RectF(r.left + amp * .9f, r.top + amp * .9f, r.right - amp * .9f, r.bottom - amp * .9f), seed + 7, amp * .8f, step * .8f)
        val al = (255 * a).toInt()
        cv.drawPath(outer, Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = Color.rgb(236, 220, 192); alpha = al; setShadowLayer(s * .04f, 0f, s * .02f, 0x70000000) })
        cv.save(); cv.clipPath(inner)
        cv.drawPath(inner, Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color; alpha = al })
        // oluklu karton izleri
        val lp = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = Color.BLACK; alpha = (16 * a).toInt(); strokeWidth = s * .006f }
        var y = r.top; while (y < r.bottom) { cv.drawLine(r.left, y, r.right, y, lp); y += s * .045f }
        speckle(cv, r, seed + 3, Color.rgb(90, 60, 30), (r.width() * r.height() / (s * s) * 120).toInt(), s * .006f, (90 * a).toInt())
        speckle(cv, r, seed + 5, Color.WHITE, (r.width() * r.height() / (s * s) * 50).toInt(), s * .005f, (70 * a).toInt())
        cv.drawRect(r, Paint().apply { shader = LinearGradient(r.left, r.top, r.right, r.bottom, Color.argb((40 * a).toInt(), 255, 255, 255), Color.argb((50 * a).toInt(), 0, 0, 0), Shader.TileMode.CLAMP) })
        cv.restore()
    }

    /** Metal ataç: iç içe iki yuvarlatılmış döngü. */
    fun clip(cv: Canvas, x: Float, y: Float, h: Float, angle: Float) {
        cv.save(); cv.rotate(angle, x, y)
        val w = h * .36f
        val path = Path().apply {
            moveTo(x + w * .2f, y + h * .78f)
            lineTo(x + w * .2f, y + h * .18f)
            arcTo(RectF(x + w * .2f, y, x + w * .8f, y + w * .6f), 180f, 180f, false)
            lineTo(x + w * .8f, y + h * .9f)
            arcTo(RectF(x, y + h * .9f - w * .5f, x + w * .8f, y + h * .9f + w * .3f), 0f, 180f, false)
            lineTo(x, y + h * .1f)
            arcTo(RectF(x, y - w * .45f, x + w, y + w * .55f), 180f, 180f, false)
            lineTo(x + w, y + h * .55f)
        }
        val shadow = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = h * .055f; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND; color = 0x55000000 }
        cv.save(); cv.translate(h * .02f, h * .04f); cv.drawPath(path, shadow); cv.restore()
        val metal = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE; strokeWidth = h * .05f; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND
            shader = LinearGradient(x, y, x + w, y + h, intArrayOf(0xFFF4F6FA.toInt(), 0xFF9AA3B2.toInt(), 0xFFE2E6EE.toInt(), 0xFF8A93A3.toInt()), null, Shader.TileMode.MIRROR)
        }
        cv.drawPath(path, metal)
        cv.restore()
    }

    /** Ataçla tutturulmuş kağıt: hafif dönük beyaz yaprak, kıvrık köşe, üstte ataç. */
    fun clipped(cv: Canvas, r: RectF, seed: Int, a: Float = 1f, color: Int = Color.rgb(250, 249, 245), angle: Float = -2f) {
        val s = min(r.width(), r.height())
        val inset = RectF(r.left + s * .04f, r.top + s * .07f, r.right - s * .04f, r.bottom - s * .03f)
        cv.save(); cv.rotate(angle, inset.centerX(), inset.centerY())
        val al = (255 * a).toInt()
        val body = Path().apply {
            moveTo(inset.left, inset.top); lineTo(inset.right, inset.top)
            lineTo(inset.right, inset.bottom - s * .12f)
            quadTo(inset.right - s * .02f, inset.bottom - s * .02f, inset.right - s * .14f, inset.bottom)
            lineTo(inset.left, inset.bottom); close()
        }
        cv.drawPath(body, Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color; alpha = al; setShadowLayer(s * .045f, s * .01f, s * .025f, 0x66000000) })
        cv.save(); cv.clipPath(body)
        speckle(cv, inset, seed, Color.rgb(160, 150, 130), (inset.width() * inset.height() / (s * s) * 40).toInt(), s * .004f, (60 * a).toInt())
        cv.drawRect(inset, Paint().apply { shader = LinearGradient(inset.left, inset.top, inset.right, inset.bottom, 0x00000000, Color.argb((26 * a).toInt(), 0, 0, 0), Shader.TileMode.CLAMP) })
        cv.restore()
        // kıvrık köşe
        val curl = Path().apply {
            moveTo(inset.right, inset.bottom - s * .12f)
            quadTo(inset.right - s * .06f, inset.bottom - s * .07f, inset.right - s * .14f, inset.bottom)
            quadTo(inset.right - s * .05f, inset.bottom - s * .1f, inset.right, inset.bottom - s * .12f); close()
        }
        cv.drawPath(curl, Paint(Paint.ANTI_ALIAS_FLAG).apply { shader = LinearGradient(inset.right - s * .14f, inset.bottom, inset.right, inset.bottom - s * .12f, Color.rgb(214, 210, 200), Color.rgb(250, 248, 242), Shader.TileMode.CLAMP); alpha = al })
        cv.restore()
        clip(cv, r.left + r.width() * .46f, r.top + s * .01f, s * .34f, -8f)
    }

    /** Washi bant parçası. */
    fun tape(cv: Canvas, cx: Float, cy: Float, w: Float, h: Float, angle: Float, col: Int) {
        cv.save(); cv.rotate(angle, cx, cy)
        val r = RectF(cx - w / 2, cy - h / 2, cx + w / 2, cy + h / 2)
        val path = Path().apply {
            moveTo(r.left, r.top); lineTo(r.right, r.top)
            var y = r.top; var k = 0; while (y < r.bottom) { y += h / 6; lineTo(r.right + (if (k++ % 2 == 0) -h * .06f else 0f), minOf(y, r.bottom)) }
            lineTo(r.left, r.bottom)
            y = r.bottom; k = 0; while (y > r.top) { y -= h / 6; lineTo(r.left + (if (k++ % 2 == 0) h * .06f else 0f), maxOf(y, r.top)) }
            close()
        }
        cv.drawPath(path, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = col; alpha = 190; setShadowLayer(h * .1f, 0f, h * .05f, 0x33000000) })
        cv.save(); cv.clipPath(path)
        val st = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; alpha = 60; strokeWidth = h * .14f }
        var x = r.left - h; while (x < r.right + h) { cv.drawLine(x, r.bottom, x + h, r.top, st); x += h * .45f }
        cv.restore(); cv.restore()
    }

    /** Bantlı not: açık sarı not kağıdı, iki köşesinde bant. */
    fun taped(cv: Canvas, r: RectF, seed: Int, a: Float = 1f, color: Int = Color.rgb(253, 240, 178), angle: Float = 1.5f) {
        val s = min(r.width(), r.height())
        val inset = RectF(r.left + s * .05f, r.top + s * .06f, r.right - s * .05f, r.bottom - s * .04f)
        cv.save(); cv.rotate(angle, inset.centerX(), inset.centerY())
        val al = (255 * a).toInt()
        cv.drawRect(inset, Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color; alpha = al; setShadowLayer(s * .04f, 0f, s * .03f, 0x66000000) })
        cv.drawRect(inset, Paint().apply { shader = LinearGradient(inset.left, inset.top, inset.left, inset.bottom, Color.argb((30 * a).toInt(), 255, 255, 255), Color.argb((34 * a).toInt(), 120, 90, 0), Shader.TileMode.CLAMP) })
        cv.restore()
        tape(cv, inset.left + s * .08f, inset.top + s * .01f, s * .32f, s * .1f, -32f, Color.rgb(242, 140, 160))
        tape(cv, inset.right - s * .08f, inset.top + s * .01f, s * .32f, s * .1f, 32f, Color.rgb(120, 190, 220))
    }

    fun draw(kind: Int, cv: Canvas, r: RectF, seed: Int, a: Float) {
        when (kind) {
            TORN -> torn(cv, r, seed, a)
            CLIP -> clipped(cv, r, seed, a)
            else -> taped(cv, r, seed, a)
        }
    }

    /** Kağıda göre mürekkep renkleri (ana, ikincil). */
    fun ink(kind: Int): Pair<Int, Int> = when (kind) {
        TORN -> Pair(Color.rgb(48, 30, 18), Color.rgb(92, 64, 40))
        CLIP -> Pair(Color.rgb(28, 32, 44), Color.rgb(96, 102, 116))
        else -> Pair(Color.rgb(40, 36, 60), Color.rgb(110, 98, 70))
    }
}
