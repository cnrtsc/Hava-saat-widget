package com.caner.sketchweather

class W_Origamikart : BaseWidget()
class W_Origamiserit : BaseWidget()
class W_Origamikare : BaseWidget()
class W_Origamicapraz : BaseWidget()
class W_Saatbilgi : BaseWidget()
class W_Saatbilgi5 : BaseWidget()
class W_Klasik : BaseWidget()
class W_Merkez : BaseWidget()
class W_Buyuksaat : BaseWidget()
class W_Saatlik : BaseWidget()
class W_Minimal : BaseWidget()
class W_Yanyana : BaseWidget()
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
class W_Analogsade : BaseWidget()
class W_Yuvhava : BaseWidget()
class W_Panel : BaseWidget()
class W_Yuvsade : BaseWidget()
class W_Yuvdetay : BaseWidget()
class W_Yuv3gun : BaseWidget()
class W_Kilit : BaseWidget()
class W_Ikili : BaseWidget()
class W_Noktamatris : BaseWidget()
class W_Flipsaat : BaseWidget()
class W_Isvicre : BaseWidget()
class W_Ilerleme : BaseWidget()
class W_Terminal : BaseWidget()
class W_Gazete : BaseWidget()
class W_Neumorfik : BaseWidget()
class W_Grafik : BaseWidget()
class W_Gunseridi : BaseWidget()
class W_Hapdizisi : BaseWidget()
class W_Konumserit : BaseWidget()
class W_Evsimdi : BaseWidget()
class W_Yagmurcizelge : BaseWidget()
class W_Cumle : BaseWidget()
class W_Pil : BaseWidget()
class W_Takvim : BaseWidget()
class W_Kilitince : BaseWidget()

class Entry(val cls: Class<out BaseWidget>, val label: String, val layout: Int, val size: String, val preview: Int)

object Registry {
    val SIZE_TITLES = mapOf("t" to "Uzun · 4x3", "w" to "Geniş · 4x2", "c" to "İnce · 4x1", "s" to "Kare · 2x2", "l" to "Büyük · 5x2")
    val ALL = listOf(
        Entry(W_Origamikart::class.java, "Origami kart", R.layout.w_ori_card, "w", R.drawable.prev_origamikart),
        Entry(W_Origamiserit::class.java, "Origami şerit", R.layout.w_ori_strip, "c", R.drawable.prev_origamiserit),
        Entry(W_Origamikare::class.java, "Origami kare", R.layout.w_ori_square, "s", R.drawable.prev_origamikare),
        Entry(W_Origamicapraz::class.java, "Origami çapraz", R.layout.w_ori_diag, "s", R.drawable.prev_origamicapraz),
        Entry(W_Saatbilgi::class.java, "Analog saat + bilgi", R.layout.w_combo, "w", R.drawable.prev_saatbilgi),
        Entry(W_Saatbilgi5::class.java, "Analog saat + bilgi (geniş)", R.layout.w_combo, "l", R.drawable.prev_saatbilgi5),
        Entry(W_Klasik::class.java, "Klasik", R.layout.w_wide0, "w", R.drawable.prev_klasik),
        Entry(W_Merkez::class.java, "Merkez", R.layout.w_wide1, "w", R.drawable.prev_merkez),
        Entry(W_Buyuksaat::class.java, "Büyük saat", R.layout.w_wide2, "w", R.drawable.prev_buyuksaat),
        Entry(W_Saatlik::class.java, "Hava + saatlik", R.layout.w_wide3, "w", R.drawable.prev_saatlik),
        Entry(W_Minimal::class.java, "Minimal", R.layout.w_wide4, "w", R.drawable.prev_minimal),
        Entry(W_Yanyana::class.java, "Yan yana", R.layout.w_wide5, "w", R.drawable.prev_yanyana),
        Entry(W_Tasan::class.java, "Taşan ikon", R.layout.w_wide7, "w", R.drawable.prev_tasan),
        Entry(W_Pixel::class.java, "Pixel tahmin", R.layout.w_wide8, "w", R.drawable.prev_pixel),
        Entry(W_Eskiz::class.java, "Eskiz not", R.layout.w_wide9, "w", R.drawable.prev_eskiz),
        Entry(W_Ince::class.java, "İnce", R.layout.w_compact, "c", R.drawable.prev_ince),
        Entry(W_Bakista::class.java, "Bir bakışta", R.layout.w_compact1, "c", R.drawable.prev_bakista),
        Entry(W_Hap::class.java, "Hap saat", R.layout.w_compact2, "c", R.drawable.prev_hap),
        Entry(W_Cift::class.java, "Çift saat", R.layout.w_compact3, "c", R.drawable.prev_cift),
        Entry(W_Kare::class.java, "Kare", R.layout.w_square, "s", R.drawable.prev_kare),
        Entry(W_Kadran::class.java, "Yuvarlak kadran", R.layout.w_square1, "s", R.drawable.prev_kadran),
        Entry(W_Gosterge::class.java, "Derece göstergesi", R.layout.w_square2, "s", R.drawable.prev_gosterge),
        Entry(W_Ustuste::class.java, "Üst üste saat", R.layout.w_square3, "s", R.drawable.prev_ustuste),
        Entry(W_Kurabiye::class.java, "Kurabiye hava", R.layout.w_square4, "s", R.drawable.prev_kurabiye),
        Entry(W_Analog::class.java, "Analog saat", R.layout.w_square5, "s", R.drawable.prev_analog),
        Entry(W_Analogsade::class.java, "Analog (sadece saat)", R.layout.w_square6, "s", R.drawable.prev_analogsade),
        Entry(W_Yuvhava::class.java, "Yuvarlak hava", R.layout.w_square7, "s", R.drawable.prev_yuvhava),
        Entry(W_Panel::class.java, "Şeffaf panel", R.layout.w_glass, "l", R.drawable.prev_panel),
        Entry(W_Yuvsade::class.java, "Yuvarlak hava · sade", R.layout.w_roundw0, "s", R.drawable.prev_yuvsade),
        Entry(W_Yuvdetay::class.java, "Yuvarlak hava · detaylı", R.layout.w_roundw1, "s", R.drawable.prev_yuvdetay),
        Entry(W_Yuv3gun::class.java, "Yuvarlak hava · 3 gün", R.layout.w_roundw2, "s", R.drawable.prev_yuv3gun),
        Entry(W_Kilit::class.java, "Kilit ekranı saati", R.layout.w_lock, "w", R.drawable.prev_kilit),
        Entry(W_Ikili::class.java, "İkili yuvarlak", R.layout.w_duo, "w", R.drawable.prev_ikili),
        Entry(W_Noktamatris::class.java, "Nokta matris", R.layout.w_th_dot, "w", R.drawable.prev_noktamatris),
        Entry(W_Flipsaat::class.java, "Retro flip saat", R.layout.w_th_flip, "w", R.drawable.prev_flipsaat),
        Entry(W_Isvicre::class.java, "İsviçre saat", R.layout.w_th_swiss, "w", R.drawable.prev_isvicre),
        Entry(W_Ilerleme::class.java, "İlerleme çubukları", R.layout.w_th_prog, "w", R.drawable.prev_ilerleme),
        Entry(W_Terminal::class.java, "Terminal", R.layout.w_th_term, "w", R.drawable.prev_terminal),
        Entry(W_Gazete::class.java, "Gazete", R.layout.w_th_paper, "w", R.drawable.prev_gazete),
        Entry(W_Neumorfik::class.java, "Neumorfik", R.layout.w_th_neu, "s", R.drawable.prev_neumorfik),
        Entry(W_Grafik::class.java, "Sıcaklık grafiği", R.layout.w_graph, "w", R.drawable.prev_grafik),
        Entry(W_Gunseridi::class.java, "Gün şeridi", R.layout.w_strip, "c", R.drawable.prev_gunseridi),
        Entry(W_Hapdizisi::class.java, "Hap dizisi", R.layout.w_pills, "c", R.drawable.prev_hapdizisi),
        Entry(W_Konumserit::class.java, "Konum şeridi", R.layout.w_thin_loc, "c", R.drawable.prev_konumserit),
        Entry(W_Evsimdi::class.java, "Ev ↔ şu an", R.layout.w_thin_home, "c", R.drawable.prev_evsimdi),
        Entry(W_Yagmurcizelge::class.java, "Yağmur çizelgesi", R.layout.w_thin_rain, "c", R.drawable.prev_yagmurcizelge),
        Entry(W_Cumle::class.java, "Cümle", R.layout.w_thin_sentence, "c", R.drawable.prev_cumle),
        Entry(W_Pil::class.java, "Pil", R.layout.w_mod_pil, "s", R.drawable.prev_pil),
        Entry(W_Takvim::class.java, "Takvim", R.layout.w_mod_takvim, "s", R.drawable.prev_takvim),
        Entry(W_Kilitince::class.java, "Kilit ekranı · ince", R.layout.w_lockc, "c", R.drawable.prev_kilitince)
    )
    fun ofClass(name: String?): Entry? = ALL.firstOrNull { it.cls.name == name }
}
