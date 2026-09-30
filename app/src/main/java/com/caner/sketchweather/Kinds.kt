package com.caner.sketchweather

import android.content.Context

/** Widget boyutları ve her boyutun stilleri. */
enum class Kind(val cls: Class<out BaseWidget>, val title: String, val names: Array<String>, val layouts: IntArray) {
    WIDE(
        WideWidget::class.java, "Geniş (4x2)",
        arrayOf(
            "Klasik", "Merkez", "Büyük saat", "Hava + saatlik", "Minimal", "Yan yana",
            "Bento (asimetrik karolar)", "Taşan ikon", "Pixel tahmin"
        ),
        intArrayOf(
            R.layout.w_wide0, R.layout.w_wide1, R.layout.w_wide2, R.layout.w_wide3, R.layout.w_wide4,
            R.layout.w_wide5, R.layout.w_wide6, R.layout.w_wide7, R.layout.w_wide8
        )
    ),
    COMPACT(
        CompactWidget::class.java, "İnce (4x1)",
        arrayOf("İnce klasik", "Bir bakışta (Pixel)", "Hap saat + daire hava"),
        intArrayOf(R.layout.w_compact, R.layout.w_compact1, R.layout.w_compact2)
    ),
    SQUARE(
        SquareWidget::class.java, "Kare (2x2)",
        arrayOf("Klasik kare", "Yuvarlak kadran", "Yuvarlak derece göstergesi", "Üst üste saat", "Kurabiye hava"),
        intArrayOf(R.layout.w_square, R.layout.w_square1, R.layout.w_square2, R.layout.w_square3, R.layout.w_square4)
    ),
    LARGE(
        GlassWidget::class.java, "Büyük (5x2)",
        arrayOf("Şeffaf panel"),
        intArrayOf(R.layout.w_glass)
    );

    fun style(c: Context, id: Int): Int {
        val p = WeatherRepo.prefs(c)
        val def = if (this == WIDE) p.getInt("wide", 0) else 0
        return p.getInt("style_$id", def).coerceIn(0, layouts.size - 1)
    }

    companion object {
        fun ofClass(name: String?): Kind? = values().firstOrNull { it.cls.name == name }
    }
}
