package com.caner.sketchweather

class W_Klasik : BaseWidget()
class W_Merkez : BaseWidget()
class W_Buyuksaat : BaseWidget()
class W_Saatlik : BaseWidget()
class W_Minimal : BaseWidget()
class W_Yanyana : BaseWidget()
class W_Bento : BaseWidget()
class W_Tasan : BaseWidget()
class W_Pixel : BaseWidget()
class W_Eskiz : BaseWidget()
class W_Ince : BaseWidget()
class W_Bakista : BaseWidget()
class W_Hap : BaseWidget()
class W_Cift : BaseWidget()
class W_Kare : BaseWidget()
class W_Kadran : BaseWidget()
class W_Gosterge : BaseWidget()
class W_Ustuste : BaseWidget()
class W_Kurabiye : BaseWidget()
class W_Analog : BaseWidget()
class W_Panel : BaseWidget()

class Entry(val cls: Class<out BaseWidget>, val label: String, val layout: Int)

object Registry {
    val ALL = listOf(
        Entry(W_Klasik::class.java, "Klasik", R.layout.w_wide0),
        Entry(W_Merkez::class.java, "Merkez", R.layout.w_wide1),
        Entry(W_Buyuksaat::class.java, "Büyük saat", R.layout.w_wide2),
        Entry(W_Saatlik::class.java, "Hava + saatlik", R.layout.w_wide3),
        Entry(W_Minimal::class.java, "Minimal", R.layout.w_wide4),
        Entry(W_Yanyana::class.java, "Yan yana", R.layout.w_wide5),
        Entry(W_Bento::class.java, "Bento", R.layout.w_wide6),
        Entry(W_Tasan::class.java, "Taşan ikon", R.layout.w_wide7),
        Entry(W_Pixel::class.java, "Pixel tahmin", R.layout.w_wide8),
        Entry(W_Eskiz::class.java, "Eskiz not", R.layout.w_wide9),
        Entry(W_Ince::class.java, "İnce", R.layout.w_compact),
        Entry(W_Bakista::class.java, "Bir bakışta", R.layout.w_compact1),
        Entry(W_Hap::class.java, "Hap saat", R.layout.w_compact2),
        Entry(W_Cift::class.java, "Çift saat", R.layout.w_compact3),
        Entry(W_Kare::class.java, "Kare", R.layout.w_square),
        Entry(W_Kadran::class.java, "Yuvarlak kadran", R.layout.w_square1),
        Entry(W_Gosterge::class.java, "Derece göstergesi", R.layout.w_square2),
        Entry(W_Ustuste::class.java, "Üst üste saat", R.layout.w_square3),
        Entry(W_Kurabiye::class.java, "Kurabiye hava", R.layout.w_square4),
        Entry(W_Analog::class.java, "Analog saat", R.layout.w_square5),
        Entry(W_Panel::class.java, "Şeffaf panel", R.layout.w_glass)
    )
}
