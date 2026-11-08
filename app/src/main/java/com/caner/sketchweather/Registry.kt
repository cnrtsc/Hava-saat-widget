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
class W_Devsaat : BaseWidget()
class W_Editoryal : BaseWidget()
class W_Dikey : BaseWidget()
class W_Kolaj : BaseWidget()
class W_Noktamatris : BaseWidget()
class W_Flipsaat : BaseWidget()
class W_Isvicre : BaseWidget()
class W_Ilerleme : BaseWidget()
class W_Terminal : BaseWidget()
class W_Gazete : BaseWidget()
class W_Neumorfik : BaseWidget()
class W_Grafik : BaseWidget()
class W_Gunseridi : BaseWidget()
class W_Minihap : BaseWidget()
class W_Hapdizisi : BaseWidget()
class W_Konumserit : BaseWidget()
class W_Evsimdi : BaseWidget()
class W_Yagmurcizelge : BaseWidget()
class W_Cumle : BaseWidget()
class W_Pil : BaseWidget()
class W_Takvim : BaseWidget()
class W_Kilitince : BaseWidget()
class W_RefPd : BaseWidget()
class W_RefPl : BaseWidget()
class W_RefPb : BaseWidget()
class W_RefGd : BaseWidget()
class W_RefGl : BaseWidget()
class W_RefGb : BaseWidget()
class W_RefBd : BaseWidget()
class W_RefBl : BaseWidget()
class W_RefBb : BaseWidget()
class W_ProSd : BaseWidget()
class W_ProSl : BaseWidget()
class W_ProMd : BaseWidget()
class W_ProMl : BaseWidget()
class W_ProM3d : BaseWidget()
class W_ProM3l : BaseWidget()
class W_ProLd : BaseWidget()
class W_ProLl : BaseWidget()
class W_ProTd : BaseWidget()
class W_ProTl : BaseWidget()
class W_ProG : BaseWidget()
class W_Gs4Hero : BaseWidget()
class W_Gs4Diag : BaseWidget()
class W_Gs4Ring : BaseWidget()
class W_Gs4Cal : BaseWidget()
class W_Gs4Arch : BaseWidget()
class W_Gs4Stack : BaseWidget()
class W_Gs4Ticket : BaseWidget()
class W_Gs3Sqd : BaseWidget()
class W_Gs3Sql : BaseWidget()
class W_Gs3Round : BaseWidget()
class W_Gs3Minid : BaseWidget()
class W_Gs3Minil : BaseWidget()
class W_Gs3Towd : BaseWidget()
class W_Gs3Towl : BaseWidget()
class W_Gs3Editd : BaseWidget()
class W_Gs3Editl : BaseWidget()
class W_Gs3Twod : BaseWidget()
class W_Gs3Twol : BaseWidget()
class W_Gs2Lined : BaseWidget()
class W_Gs2Linew : BaseWidget()
class W_Gs2Split : BaseWidget()
class W_Gs2Score : BaseWidget()
class W_Gs2Jerw : BaseWidget()
class W_Gs2Jerd : BaseWidget()
class W_Gs2Clear : BaseWidget()
class W_Gs2Grad : BaseWidget()
class W_Gs2Cleand : BaseWidget()
class W_Gs2Cleanw : BaseWidget()
class W_Hizlibakis : BaseWidget()
class W_HizlibakisGenis : BaseWidget()
class W_GsBeyaz : BaseWidget()
class W_GsBeyazKare : BaseWidget()
class W_GsBeyazSerit : BaseWidget()
class W_GsSiyah : BaseWidget()
class W_GsSiyahKare : BaseWidget()
class W_GsSiyahSerit : BaseWidget()
class W_Animli : BaseWidget()
class W_AnimliKare : BaseWidget()
class W_CZarf : BaseWidget()
class W_CPola : BaseWidget()
class W_CBilet : BaseWidget()
class W_CTakvimy : BaseWidget()
class W_CAkor : BaseWidget()
class W_CEtiket : BaseWidget()
class W_CYigin : BaseWidget()
class W_CPolaKare : BaseWidget()
class W_CTakvimyKare : BaseWidget()
class W_CEtiketKare : BaseWidget()
class W_CYiginKare : BaseWidget()

class Entry(val cls: Class<out BaseWidget>, val label: String, val layout: Int, val size: String, val preview: Int)

object Registry {
    val SIZE_TITLES = mapOf("m" to "Mini · ek bilgi şeridi", "t" to "Uzun · 4x3", "w" to "Geniş · 4x2", "c" to "İnce · 4x1", "s" to "Kare · 2x2", "d" to "Dikey · 2x3", "x" to "Büyük · 4x4", "k" to "Küçük · 2x1", "l" to "Büyük · 5x2")
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
        Entry(W_Devsaat::class.java, "Dev saat", R.layout.w_asym_big, "w", R.drawable.prev_devsaat),
        Entry(W_Editoryal::class.java, "Editoryal", R.layout.w_asym_edit, "w", R.drawable.prev_editoryal),
        Entry(W_Dikey::class.java, "Dikey", R.layout.w_asym_vert, "s", R.drawable.prev_dikey),
        Entry(W_Kolaj::class.java, "Kolaj", R.layout.w_asym_collage, "w", R.drawable.prev_kolaj),
        Entry(W_Animli::class.java, "Animasyonlu hava", R.layout.w_anim, "w", R.drawable.prev_anim),
        Entry(W_AnimliKare::class.java, "Animasyonlu hava (kare)", R.layout.w_anim, "s", R.drawable.prev_anim_sq),
        Entry(W_GsBeyaz::class.java, "Sarı-kırmızı beyaz", R.layout.w_gs_white, "w", R.drawable.prev_gsw),
        Entry(W_GsBeyazKare::class.java, "Sarı-kırmızı beyaz (kare)", R.layout.w_gs_white, "s", R.drawable.prev_gsw_sq),
        Entry(W_GsBeyazSerit::class.java, "Sarı-kırmızı beyaz (şerit)", R.layout.w_gs_white, "c", R.drawable.prev_gsw_st),
        Entry(W_GsSiyah::class.java, "Sarı-kırmızı siyah", R.layout.w_gs_black, "w", R.drawable.prev_gsb),
        Entry(W_GsSiyahKare::class.java, "Sarı-kırmızı siyah (kare)", R.layout.w_gs_black, "s", R.drawable.prev_gsb_sq),
        Entry(W_GsSiyahSerit::class.java, "Sarı-kırmızı siyah (şerit)", R.layout.w_gs_black, "c", R.drawable.prev_gsb_st),
        Entry(W_Hizlibakis::class.java, "Hızlı bakış", R.layout.w_glance, "s", R.drawable.prev_glance),
        Entry(W_HizlibakisGenis::class.java, "Hızlı bakış (geniş)", R.layout.w_glance, "w", R.drawable.prev_glance_w),
        Entry(W_Gs2Lined::class.java, "Sade çizgi siyah", R.layout.w_gs2_lined, "c", R.drawable.prev_gs2_lined),
        Entry(W_Gs2Linew::class.java, "Sade çizgi beyaz", R.layout.w_gs2_linew, "c", R.drawable.prev_gs2_linew),
        Entry(W_Gs2Split::class.java, "İki renk kapsül", R.layout.w_gs2_split, "c", R.drawable.prev_gs2_split),
        Entry(W_Gs2Score::class.java, "Skor bandı", R.layout.w_gs2_score, "c", R.drawable.prev_gs2_score),
        Entry(W_Gs2Jerw::class.java, "Forma çizgileri beyaz", R.layout.w_gs2_jerw, "c", R.drawable.prev_gs2_jerw),
        Entry(W_Gs2Jerd::class.java, "Forma çizgileri siyah", R.layout.w_gs2_jerd, "c", R.drawable.prev_gs2_jerd),
        Entry(W_Gs2Clear::class.java, "Saydam sarı-kırmızı", R.layout.w_gs2_clear, "c", R.drawable.prev_gs2_clear),
        Entry(W_Gs2Grad::class.java, "Bordo degrade", R.layout.w_gs2_grad, "c", R.drawable.prev_gs2_grad),
        Entry(W_Gs2Cleand::class.java, "Sade geniş siyah", R.layout.w_gs2_cleand, "w", R.drawable.prev_gs2_cleand),
        Entry(W_Gs2Cleanw::class.java, "Sade geniş beyaz", R.layout.w_gs2_cleanw, "w", R.drawable.prev_gs2_cleanw),
        Entry(W_Gs3Sqd::class.java, "Zarif kare siyah", R.layout.w_gs3_sqd, "s", R.drawable.prev_gs3_sqd),
        Entry(W_Gs3Sql::class.java, "Zarif kare fildişi", R.layout.w_gs3_sql, "s", R.drawable.prev_gs3_sql),
        Entry(W_Gs3Round::class.java, "Zarif yuvarlak", R.layout.w_gs3_round, "s", R.drawable.prev_gs3_round),
        Entry(W_Gs3Minid::class.java, "Zarif küçük siyah", R.layout.w_gs3_minid, "k", R.drawable.prev_gs3_minid),
        Entry(W_Gs3Minil::class.java, "Zarif küçük fildişi", R.layout.w_gs3_minil, "k", R.drawable.prev_gs3_minil),
        Entry(W_Gs3Towd::class.java, "Zarif dikey siyah", R.layout.w_gs3_towd, "d", R.drawable.prev_gs3_towd),
        Entry(W_Gs3Towl::class.java, "Zarif dikey fildişi", R.layout.w_gs3_towl, "d", R.drawable.prev_gs3_towl),
        Entry(W_Gs3Editd::class.java, "Zarif editoryal siyah", R.layout.w_gs3_editd, "w", R.drawable.prev_gs3_editd),
        Entry(W_Gs3Editl::class.java, "Zarif editoryal fildişi", R.layout.w_gs3_editl, "w", R.drawable.prev_gs3_editl),
        Entry(W_Gs3Twod::class.java, "Zarif şerit siyah", R.layout.w_gs3_twod, "c", R.drawable.prev_gs3_twod),
        Entry(W_Gs3Twol::class.java, "Zarif şerit fildişi", R.layout.w_gs3_twol, "c", R.drawable.prev_gs3_twol),
        Entry(W_Gs4Hero::class.java, "İkon kahraman", R.layout.w_gs4_hero, "w", R.drawable.prev_gs4_hero),
        Entry(W_Gs4Diag::class.java, "Çapraz kare", R.layout.w_gs4_diag, "s", R.drawable.prev_gs4_diag),
        Entry(W_Gs4Ring::class.java, "Gün halkası", R.layout.w_gs4_ring, "s", R.drawable.prev_gs4_ring),
        Entry(W_Gs4Cal::class.java, "Takvim yaprağı GS", R.layout.w_gs4_cal, "s", R.drawable.prev_gs4_cal),
        Entry(W_Gs4Arch::class.java, "Kemer pencere", R.layout.w_gs4_arch, "d", R.drawable.prev_gs4_arch),
        Entry(W_Gs4Stack::class.java, "Kart yığını GS", R.layout.w_gs4_stack, "w", R.drawable.prev_gs4_stack),
        Entry(W_Gs4Ticket::class.java, "Bilet GS", R.layout.w_gs4_ticket, "c", R.drawable.prev_gs4_ticket),
        Entry(W_ProSd::class.java, "Pro küçük koyu", R.layout.w_pro_sd, "s", R.drawable.prev_pro_sd),
        Entry(W_ProSl::class.java, "Pro küçük açık", R.layout.w_pro_sl, "s", R.drawable.prev_pro_sl),
        Entry(W_ProMd::class.java, "Pro orta koyu", R.layout.w_pro_md, "w", R.drawable.prev_pro_md),
        Entry(W_ProMl::class.java, "Pro orta açık", R.layout.w_pro_ml, "w", R.drawable.prev_pro_ml),
        Entry(W_ProM3d::class.java, "Pro orta + 3 gün koyu", R.layout.w_pro_m3d, "t", R.drawable.prev_pro_m3d),
        Entry(W_ProM3l::class.java, "Pro orta + 3 gün açık", R.layout.w_pro_m3l, "t", R.drawable.prev_pro_m3l),
        Entry(W_ProLd::class.java, "Pro büyük koyu", R.layout.w_pro_ld, "x", R.drawable.prev_pro_ld),
        Entry(W_ProLl::class.java, "Pro büyük açık", R.layout.w_pro_ll, "x", R.drawable.prev_pro_ll),
        Entry(W_ProTd::class.java, "Pro ince koyu", R.layout.w_pro_td, "c", R.drawable.prev_pro_td),
        Entry(W_ProTl::class.java, "Pro ince açık", R.layout.w_pro_tl, "c", R.drawable.prev_pro_tl),
        Entry(W_ProG::class.java, "Pro bir bakışta", R.layout.w_pro_g, "w", R.drawable.prev_pro_g),
        Entry(W_RefPd::class.java, "Panel koyu", R.layout.w_ref_pd, "w", R.drawable.prev_ref_pd),
        Entry(W_RefPl::class.java, "Panel açık", R.layout.w_ref_pl, "w", R.drawable.prev_ref_pl),
        Entry(W_RefPb::class.java, "Panel bordo", R.layout.w_ref_pb, "w", R.drawable.prev_ref_pb),
        Entry(W_RefGd::class.java, "Altın çizgi koyu", R.layout.w_ref_gd, "w", R.drawable.prev_ref_gd),
        Entry(W_RefGl::class.java, "Altın çizgi açık", R.layout.w_ref_gl, "w", R.drawable.prev_ref_gl),
        Entry(W_RefGb::class.java, "Altın çizgi bordo", R.layout.w_ref_gb, "w", R.drawable.prev_ref_gb),
        Entry(W_RefBd::class.java, "Boya koyu", R.layout.w_ref_bd, "w", R.drawable.prev_ref_bd),
        Entry(W_RefBl::class.java, "Boya açık", R.layout.w_ref_bl, "w", R.drawable.prev_ref_bl),
        Entry(W_RefBb::class.java, "Boya bordo", R.layout.w_ref_bb, "w", R.drawable.prev_ref_bb),
        Entry(W_CZarf::class.java, "Zarf", R.layout.w_card_zarf, "w", R.drawable.prev_czarf),
        Entry(W_CPola::class.java, "Polaroid", R.layout.w_card_pola, "w", R.drawable.prev_cpola),
        Entry(W_CBilet::class.java, "Bilet", R.layout.w_card_bilet, "w", R.drawable.prev_cbilet),
        Entry(W_CTakvimy::class.java, "Takvim yaprağı", R.layout.w_card_takvimy, "w", R.drawable.prev_ctakvimy),
        Entry(W_CAkor::class.java, "Akordeon", R.layout.w_card_akor, "w", R.drawable.prev_cakor),
        Entry(W_CEtiket::class.java, "Etiket", R.layout.w_card_etiket, "w", R.drawable.prev_cetiket),
        Entry(W_CYigin::class.java, "Kart yığını", R.layout.w_card_yigin, "w", R.drawable.prev_cyigin),
        Entry(W_CPolaKare::class.java, "Polaroid (kare)", R.layout.w_card_pola, "s", R.drawable.prev_cpola_sq),
        Entry(W_CTakvimyKare::class.java, "Takvim yaprağı (kare)", R.layout.w_card_takvimy, "s", R.drawable.prev_ctakvimy_sq),
        Entry(W_CEtiketKare::class.java, "Etiket (kare)", R.layout.w_card_etiket, "s", R.drawable.prev_cetiket_sq),
        Entry(W_CYiginKare::class.java, "Kart yığını (kare)", R.layout.w_card_yigin, "s", R.drawable.prev_cyigin_sq),
        Entry(W_Noktamatris::class.java, "Nokta matris", R.layout.w_th_dot, "w", R.drawable.prev_noktamatris),
        Entry(W_Flipsaat::class.java, "Retro flip saat", R.layout.w_th_flip, "w", R.drawable.prev_flipsaat),
        Entry(W_Isvicre::class.java, "İsviçre saat", R.layout.w_th_swiss, "w", R.drawable.prev_isvicre),
        Entry(W_Ilerleme::class.java, "İlerleme çubukları", R.layout.w_th_prog, "w", R.drawable.prev_ilerleme),
        Entry(W_Terminal::class.java, "Terminal", R.layout.w_th_term, "w", R.drawable.prev_terminal),
        Entry(W_Gazete::class.java, "Gazete", R.layout.w_th_paper, "w", R.drawable.prev_gazete),
        Entry(W_Neumorfik::class.java, "Neumorfik", R.layout.w_th_neu, "s", R.drawable.prev_neumorfik),
        Entry(W_Grafik::class.java, "Sıcaklık grafiği", R.layout.w_graph, "w", R.drawable.prev_grafik),
        Entry(W_Gunseridi::class.java, "Gün şeridi", R.layout.w_strip, "c", R.drawable.prev_gunseridi),
        Entry(W_Minihap::class.java, "Mini haplar", R.layout.w_minipills, "m", R.drawable.prev_minihap),
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
