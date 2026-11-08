package com.caner.sketchweather

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ComposeShader
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.Shader

/**
 * Rakam ve yazı dokuları. Widget'lar [TCanvas] üstüne çizer; her drawText çağrısında
 * seçilen doku boyaya shader olarak eklenir. Saat (Ink.time ya da çok büyük yazı) ve diğer yazılar ayrı seçilir.
 */
object Tex {
    val NAMES = arrayOf("Yok", "Tebeşir", "Eskitme", "Grenli", "Altın varak", "Gümüş", "Bakır", "Gül altın",
        "Beyaz mermer", "Siyah mermer", "Ahşap", "Karbon", "Holografik", "Sarı-kırmızı şerit", "Yağlı boya", "Gün batımı", "Okyanus")
    /** 1-3: rengi korur, sadece pütür/aşınma ekler. */
    fun keepsColor(k: Int) = k in 1..3

    private class St(val clock: Int, val text: Int, val scale: Float)
    private val st = ThreadLocal<St?>()
    private val inClock = ThreadLocal<Boolean>()
    private var app: Context? = null
    private val cache = HashMap<Int, Bitmap>()

    fun clockTex(c: Context) = Cfg.int(c, "clockTex", 0).coerceIn(0, NAMES.size - 1)
    fun textTex(c: Context) = Cfg.int(c, "textTex", 0).coerceIn(0, NAMES.size - 1)
    fun scale(c: Context) = Cfg.int(c, "texScale", 100).coerceIn(40, 250)

    /** Her widget çiziminden önce (Ink.begin içinden) çağrılır. */
    fun begin(c: Context) {
        app = c.applicationContext
        val a = clockTex(c); val b = textTex(c)
        st.set(if (a == 0 && b == 0) null else St(a, b, scale(c) / 100f))
    }
    /** Varsayılan doku öneren tasarımlar için: kullanıcı seçmediyse bunu kullan. */
    fun suggest(c: Context, clock: Int, text: Int) {
        val id = Cfg.current(); val p = WeatherRepo.prefs(c)
        fun has(k: String) = (id != null && p.contains(Cfg.wkey(id, k))) || p.contains(k)
        val a = if (has("clockTex")) clockTex(c) else clock
        val b = if (has("textTex")) textTex(c) else text
        st.set(if (a == 0 && b == 0) null else St(a, b, scale(c) / 100f))
    }
    fun clock(on: Boolean) = inClock.set(on)
    /** Ayar ekranındaki örnek kutucuklar için. */
    fun forPreview(k: Int) = st.set(if (k == 0) null else St(k, k, 1f))

    private fun bmp(id: Int): Bitmap? = cache[id] ?: try {
        BitmapFactory.decodeResource(app!!.resources, id, BitmapFactory.Options().apply { inScaled = false })?.also { cache[id] = it }
    } catch (e: Exception) { null }

    private fun tile(id: Int, x: Float, y: Float, px: Float): Shader? {
        val b = bmp(id) ?: return null
        val sh = BitmapShader(b, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT)
        val m = Matrix(); val s = px / b.width
        m.setScale(s, s); m.postTranslate(x, y); sh.setLocalMatrix(m)
        return sh
    }

    /** Boyaya dokuyu uygular; doku yoksa aynı boyayı döndürür. */
    fun wrap(p: Paint, x: Float, y: Float, canvasH: Int): Paint {
        val s = st.get() ?: return p
        val big = inClock.get() == true || p.textSize > canvasH * .3f
        val k = if (big) s.clock else s.text
        if (k == 0) return p
        val q = Paint(p)
        val size = q.textSize.coerceAtLeast(4f)
        val top = y - size * .78f; val bot = y + size * .06f
        val px = size * 3.2f * s.scale
        val col = q.color
        fun solid(cl: Int): Shader = LinearGradient(0f, 0f, 0f, 1f, cl or (0xFF shl 24), cl or (0xFF shl 24), Shader.TileMode.CLAMP)
        fun metal(vararg cols: Int): Shader {
            val g = LinearGradient(0f, top, 0f, bot, cols, null, Shader.TileMode.CLAMP)
            val br = tile(R.drawable.tx_brush, x, y, px) ?: return g
            return ComposeShader(g, br, PorterDuff.Mode.MULTIPLY)
        }
        val sh: Shader? = when (k) {
            1, 2, 3 -> {
                val res = when (k) { 1 -> R.drawable.tx_chalk; 2 -> R.drawable.tx_worn; else -> R.drawable.tx_grain }
                val g = tile(res, x, y, if (k == 1) size * 2.2f * s.scale else px) ?: return p
                ComposeShader(q.shader ?: solid(col), g, PorterDuff.Mode.DST_IN)
            }
            4 -> metal(0xFFFFF0B8.toInt(), 0xFFE9C25A.toInt(), 0xFFB8862A.toInt(), 0xFFF6D77E.toInt(), 0xFF9C6A1C.toInt())
            5 -> metal(0xFFFFFFFF.toInt(), 0xFFD8DCE2.toInt(), 0xFF8E949E.toInt(), 0xFFE6E9EE.toInt(), 0xFF6E747E.toInt())
            6 -> metal(0xFFFFD2B0.toInt(), 0xFFD88A55.toInt(), 0xFF9A4E26.toInt(), 0xFFE7A276.toInt(), 0xFF7A3A1A.toInt())
            7 -> metal(0xFFFFE6E0.toInt(), 0xFFF0B2A2.toInt(), 0xFFC07A6A.toInt(), 0xFFF6CABE.toInt(), 0xFF9E5E52.toInt())
            8 -> tile(R.drawable.tx_marble, x, y, px * 1.4f)
            9 -> tile(R.drawable.tx_marble_k, x, y, px * 1.4f)
            10 -> tile(R.drawable.tx_wood, x, y, px * 1.2f)
            11 -> tile(R.drawable.tx_carbon, x, y, size * 1.6f * s.scale)
            12 -> {
                val g = LinearGradient(x, top, x + size * 2.4f, bot, intArrayOf(0xFFFFB3D9.toInt(), 0xFFB5E8FF.toInt(), 0xFFD7FFC4.toInt(), 0xFFFFF1B0.toInt(), 0xFFE0C2FF.toInt()), null, Shader.TileMode.MIRROR)
                val br = tile(R.drawable.tx_brush, x, y, px); if (br != null) ComposeShader(g, br, PorterDuff.Mode.MULTIPLY) else g
            }
            13 -> { val w = size * .32f * s.scale
                LinearGradient(x, top, x + w, top + w, intArrayOf(0xFFFDB912.toInt(), 0xFFFDB912.toInt(), 0xFFA90432.toInt(), 0xFFA90432.toInt()), floatArrayOf(0f, .5f, .5f, 1f), Shader.TileMode.REPEAT) }
            14 -> tile(R.drawable.tex_paint_light, x, y, px * 2f)
            15 -> LinearGradient(0f, top, 0f, bot, intArrayOf(0xFFFFE08A.toInt(), 0xFFFF9A5A.toInt(), 0xFFE8456B.toInt(), 0xFF7A3A9A.toInt()), null, Shader.TileMode.CLAMP)
            16 -> LinearGradient(0f, top, 0f, bot, intArrayOf(0xFFB8F4FF.toInt(), 0xFF4FC3E8.toInt(), 0xFF1F6FB8.toInt(), 0xFF173E7A.toInt()), null, Shader.TileMode.CLAMP)
            else -> null
        }
        if (sh == null) return p
        q.shader = sh
        if (!keepsColor(k)) q.color = Color.argb(Color.alpha(col), 255, 255, 255)
        return q
    }
}

/** drawText çağrılarına yazı dokusunu ekleyen tuval. */
class TCanvas(b: Bitmap) : Canvas(b) {
    private val h = b.height
    override fun drawText(text: String, x: Float, y: Float, paint: Paint) = super.drawText(text, x, y, Tex.wrap(paint, x, y, h))
    override fun drawText(text: String, start: Int, end: Int, x: Float, y: Float, paint: Paint) = super.drawText(text, start, end, x, y, Tex.wrap(paint, x, y, h))
    override fun drawText(text: CharSequence, start: Int, end: Int, x: Float, y: Float, paint: Paint) = super.drawText(text, start, end, x, y, Tex.wrap(paint, x, y, h))
    override fun drawText(text: CharArray, index: Int, count: Int, x: Float, y: Float, paint: Paint) = super.drawText(text, index, count, x, y, Tex.wrap(paint, x, y, h))
}
