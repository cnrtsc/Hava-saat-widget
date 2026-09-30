package com.caner.sketchweather

import android.app.WallpaperManager
import android.content.Context
import android.graphics.Color

class Pal(
    val container: Int, val containerHigh: Int, val onCont: Int, val onContSub: Int,
    val accent: Int, val onAccent: Int, val accentLight: Int,
    val dark: Int, val onDark: Int, val onDarkSub: Int
)

object Palette {
    val NAMES = arrayOf("Otomatik (duvar kağıdından)", "Şeftali", "Okyanus", "Orman", "Lavanta", "Gül", "Grafit")
    private val PRESET_HUE = floatArrayOf(25f, 25f, 205f, 140f, 265f, 340f, 220f)
    private val PRESET_SAT = floatArrayOf(.6f, .6f, .6f, .45f, .5f, .55f, .08f)

    fun choice(c: Context): Int = Cfg.int(c, "palette", 0).coerceIn(0, NAMES.size - 1)

    private fun hsl(h: Float, s: Float, l: Float, alpha: Int = 255): Int {
        val q = if (l < .5f) l * (1 + s) else l + s - l * s
        val p = 2 * l - q
        fun ch(t0: Float): Int {
            var t = t0
            if (t < 0) t += 1f
            if (t > 1) t -= 1f
            val v = when {
                t < 1f / 6 -> p + (q - p) * 6 * t
                t < 1f / 2 -> q
                t < 2f / 3 -> p + (q - p) * (2f / 3 - t) * 6
                else -> p
            }
            return (v * 255).toInt().coerceIn(0, 255)
        }
        val hh = h / 360f
        return Color.argb(alpha, ch(hh + 1f / 3), ch(hh), ch(hh - 1f / 3))
    }

    private fun seed(c: Context): Pair<Float, Float> {
        val i = choice(c)
        if (i > 0) return Pair(PRESET_HUE[i], PRESET_SAT[i])
        try {
            val wc = WallpaperManager.getInstance(c).getWallpaperColors(WallpaperManager.FLAG_SYSTEM)
            val col = wc?.primaryColor?.toArgb()
            if (col != null) {
                val hsv = FloatArray(3)
                Color.colorToHSV(col, hsv)
                return Pair(hsv[0], (hsv[1] * 1.2f).coerceIn(.08f, .7f))
            }
        } catch (e: Exception) {
            // duvar kağıdı rengi alınamazsa varsayılan
        }
        return Pair(PRESET_HUE[1], PRESET_SAT[1])
    }

    fun get(c: Context): Pal {
        val (h, s) = seed(c)
        return Pal(
            container = hsl(h, s, .90f),
            containerHigh = hsl(h, s, .84f),
            onCont = hsl(h, (s * .8f).coerceAtMost(.6f), .14f),
            onContSub = hsl(h, s * .45f, .36f),
            accent = hsl(h, (s * 1.1f).coerceAtMost(.75f), .42f),
            onAccent = Color.WHITE,
            accentLight = hsl(h, (s * 1.2f).coerceAtMost(.85f), .80f),
            dark = hsl(h, s * .35f, .11f, 215),
            onDark = Color.WHITE,
            onDarkSub = hsl(h, s * .3f, .78f)
        )
    }
}
