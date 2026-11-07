package com.caner.sketchweather

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

/**
 * Tek bir widget'ın düzenleme ekranı. Bölümler her zaman aynı sırada ve aynı adla:
 * Tasarım → İçerik → Arka plan → Kenarlar → Yazılar → Hava ikonları.
 */
class EditActivity : Activity() {
    private var wid = 0
    private lateinit var root: LinearLayout
    private lateinit var preview: FrameLayout
    private var wdp = 320
    private var hdp = 170

    private val p get() = WeatherRepo.prefs(this)
    private fun <T> read(block: () -> T): T = Cfg.with(wid) { block() }
    private fun intOf(k: String, def: Int) = read { Cfg.int(this, k, def) }
    private fun putI(k: String, v: Int) { p.edit().putInt(Cfg.wkey(wid, k), v).apply(); changed() }
    private fun putB(k: String, v: Boolean) { p.edit().putBoolean(Cfg.wkey(wid, k), v).apply(); changed() }
    private fun dp(v: Int) = Ui.dp(this, v)

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: android.content.Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != 77 || resultCode != RESULT_OK) return
        val uri = data?.data ?: return
        try {
            val src = contentResolver.openInputStream(uri)?.use { android.graphics.BitmapFactory.decodeStream(it) } ?: return
            val sc = minOf(1f, 512f / maxOf(src.width, src.height))
            val b = android.graphics.Bitmap.createScaledBitmap(src, (src.width * sc).toInt().coerceAtLeast(1), (src.height * sc).toInt().coerceAtLeast(1), true)
            java.io.FileOutputStream(GS.logoFile(this)).use { b.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
            Toast.makeText(this, "Logo kaydedildi", Toast.LENGTH_SHORT).show()
            changed(); build()
        } catch (e: Exception) { Toast.makeText(this, "Görsel açılamadı", Toast.LENGTH_SHORT).show() }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        wid = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, 0)
        val o = AppWidgetManager.getInstance(this).getAppWidgetOptions(wid)
        wdp = o.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 0).takeIf { it > 0 } ?: 320
        hdp = o.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 0).takeIf { it > 0 } ?: 170
        build()
    }

    private fun entry(): Entry? = Registry.ofClass(AppWidgetManager.getInstance(this).getAppWidgetInfo(wid)?.provider?.className)
    private fun layout(): Int { val e = entry() ?: return 0; return Widgets.layoutFor(this, wid, e) }
    private fun current(): Entry? { val l = layout(); val e = entry(); return Registry.ALL.firstOrNull { it.layout == l && it.size == e?.size } }

    private fun category(l: Int): String = when (l) {
        R.layout.w_square5, R.layout.w_square6 -> "clock"
        R.layout.w_combo -> "combo"
        R.layout.w_roundw0, R.layout.w_roundw1, R.layout.w_roundw2, R.layout.w_square7, R.layout.w_square1, R.layout.w_square2,
        R.layout.w_strip, R.layout.w_graph -> "round"
        R.layout.w_lock, R.layout.w_lockc -> "lock"
        R.layout.w_duo -> "duo"
        R.layout.w_mod_pil, R.layout.w_mod_takvim -> "mod"
        R.layout.w_pills -> "pills"
        R.layout.w_minipills -> "mini"
        R.layout.w_ori_card, R.layout.w_ori_strip, R.layout.w_ori_square, R.layout.w_ori_diag -> "origami"
        R.layout.w_thin_loc, R.layout.w_thin_home, R.layout.w_thin_rain, R.layout.w_thin_sentence -> "thin"
        R.layout.w_asym_big, R.layout.w_asym_edit, R.layout.w_asym_vert, R.layout.w_asym_collage -> "asym"
        R.layout.w_card_zarf, R.layout.w_card_pola, R.layout.w_card_bilet, R.layout.w_card_takvimy, R.layout.w_card_akor, R.layout.w_card_etiket, R.layout.w_card_yigin -> "card"
        R.layout.w_anim -> "anim"
        R.layout.w_gs4_hero, R.layout.w_gs4_diag, R.layout.w_gs4_ring, R.layout.w_gs4_cal, R.layout.w_gs4_arch, R.layout.w_gs4_stack, R.layout.w_gs4_ticket -> "gs"
        R.layout.w_gs3_sqd, R.layout.w_gs3_sql, R.layout.w_gs3_round, R.layout.w_gs3_minid, R.layout.w_gs3_minil, R.layout.w_gs3_towd, R.layout.w_gs3_towl, R.layout.w_gs3_editd, R.layout.w_gs3_editl, R.layout.w_gs3_twod, R.layout.w_gs3_twol -> "gs"
        R.layout.w_gs2_lined, R.layout.w_gs2_linew, R.layout.w_gs2_split, R.layout.w_gs2_score, R.layout.w_gs2_jerw, R.layout.w_gs2_jerd, R.layout.w_gs2_clear, R.layout.w_gs2_grad, R.layout.w_gs2_cleand, R.layout.w_gs2_cleanw -> "gs"
        R.layout.w_glance -> "glance"
        R.layout.w_gs_white, R.layout.w_gs_black -> "gs"
        R.layout.w_th_dot, R.layout.w_th_flip, R.layout.w_th_swiss, R.layout.w_th_prog, R.layout.w_th_term, R.layout.w_th_paper,
        R.layout.w_th_neu -> "theme"
        R.layout.w_wide7, R.layout.w_wide8, R.layout.w_compact1, R.layout.w_compact2, R.layout.w_square3, R.layout.w_square4 -> "palette"
        else -> "standard"
    }

    private fun changed() { sendBroadcast(Widgets.refreshIntent(this)); refreshPreview() }

    private fun refreshPreview() {
        preview.removeAllViews()
        try {
            val rv = Widgets.preview(this, wid, wdp, hdp) ?: return
            val v = rv.apply(this, preview)
            preview.addView(v, FrameLayout.LayoutParams(dp(wdp), dp(hdp)).apply { gravity = Gravity.CENTER })
        } catch (e: Exception) {
            preview.addView(TextView(this).apply { text = "Önizleme gösterilemedi"; setTextColor(Ui.MUTED) })
        }
    }

    /* ---------- küçük yardımcılar ---------- */
    private fun section(title: String, what: String): LinearLayout = Ui.section(this, title, what).also { root.addView(it) }
    private fun row(s: LinearLayout, label: String, hint: String? = null) {
        s.addView(TextView(this).apply { text = label; textSize = 14f; setTextColor(Ui.FG); setPadding(0, dp(12), 0, 0) })
        if (hint != null) s.addView(TextView(this).apply { text = hint; textSize = 12f; setTextColor(Ui.MUTED) })
    }
    private fun chips(s: LinearLayout, names: Array<String>, cur: Int, pick: (Int) -> Unit) = s.addView(Ui.chips(this, names, cur) { pick(it); build() })

    private var tab = 0
    private var builtTab = -1
    private var scroll: ScrollView? = null
    private val TABS = arrayOf("Tasarım", "İçerik", "Yazı", "Renk", "İkon", "Zemin")

    private fun build() {
        val prevY = if (builtTab == tab) scroll?.scrollY ?: 0 else 0
        val page = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(Ui.BG); setPadding(dp(14), dp(28), dp(14), 0) }
        setContentView(page)
        val cur = current()
        page.addView(TextView(this).apply { text = cur?.label ?: "Widget"; textSize = 20f; setTextColor(Ui.FG) })
        preview = FrameLayout(this).apply {
            background = GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(Color.rgb(96, 70, 58), Color.rgb(40, 48, 62), Color.rgb(28, 32, 42))).apply { cornerRadius = dp(20).toFloat() }
            setPadding(dp(8), dp(10), dp(8), dp(10))
            layoutParams = LinearLayout.LayoutParams(-1, dp(minOf(hdp, 220) + 20)).apply { topMargin = dp(8) }
        }
        page.addView(preview); refreshPreview()
        // sekmeler: önizleme hep üstte kalır, altta seçili sekmenin ayarları kayar
        page.addView(Ui.chips(this, TABS, tab) { tab = it; build() })
        val sv = ScrollView(this)
        root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(0, 0, 0, dp(40)) }
        sv.addView(root); page.addView(sv, LinearLayout.LayoutParams(-1, 0, 1f))
        scroll = sv
        val cat = category(layout())
        when (tab) {
            0 -> { designSection(cur); actions() }
            1 -> { contentSection(cat); locationSection() }
            2 -> { textSection(cat); sizeSection(cat) }
            3 -> colorSection(cat)
            4 -> iconSection()
            else -> { backgroundSection(cat); edgesSection() }
        }
        if (root.childCount == 0) root.addView(TextView(this).apply { text = "Bu tasarımda bu sekmede ayar yok."; setTextColor(Ui.MUTED); setPadding(0, dp(16), 0, 0) })
        builtTab = tab
        sv.post { sv.scrollTo(0, prevY) }
    }

    /** Gizle / geri getir (uzun basınca). */
    private fun hideToggle(kind: String, key: String, name: String) {
        val was = Hide.has(this, kind, key)
        Hide.toggle(this, kind, key)
        Toast.makeText(this, if (was) "$name geri geldi" else "$name gizlendi (uzun basarak geri getirebilirsin)", Toast.LENGTH_SHORT).show()
        build()
    }
    private fun visible(kind: String, key: String) = Hide.showHidden(this) || !Hide.has(this, kind, key)
    private fun hiddenToggleRow(s: LinearLayout) {
        val n = Hide.all(this, Hide.DESIGN).size + Hide.all(this, Hide.FONT).size + Hide.all(this, Hide.ICON).size
        if (n > 0) s.addView(Ui.check(this, "Gizlediklerimi de göster ($n)", Hide.showHidden(this)) { Hide.setShowHidden(this, it); build() })
        else s.addView(TextView(this).apply { text = "İpucu: beğenmediğin bir seçeneğe uzun bas, gizlensin."; textSize = 12f; setTextColor(Ui.MUTED); setPadding(0, dp(6), 0, 0) })
    }

    /* ---------- 1. Tasarım ---------- */
    private fun designSection(cur: Entry?) {
        val s = section("Tasarım", "Widget'ın genel düzeni. Aynı boydaki başka bir tasarıma geçebilirsin; ayarların korunur.")
        val size = entry()?.size ?: "w"
        val r = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        Registry.ALL.forEachIndexed { idx, e ->
            if (e.size != size) return@forEachIndexed
            val dk = Hide.designKey(e)
            if (e != cur && !visible(Hide.DESIGN, dk)) return@forEachIndexed
            r.addView(LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL; setPadding(dp(5), dp(5), dp(5), dp(5))
                if (Hide.has(this@EditActivity, Hide.DESIGN, dk)) alpha = .4f
                setOnLongClickListener { hideToggle(Hide.DESIGN, dk, e.label); true }
                if (e == cur) background = GradientDrawable().apply { setStroke(dp(2), Ui.ACCENT); cornerRadius = dp(12).toFloat() }
                setOnClickListener { p.edit().putInt("ov_$wid", idx).apply(); sendBroadcast(Widgets.refreshIntent(this@EditActivity)); build() }
                addView(ImageView(this@EditActivity).apply { setImageResource(e.preview); adjustViewBounds = true; layoutParams = LinearLayout.LayoutParams(dp(if (size == "s" || size == "d" || size == "k") 96 else 180), -2) })
                addView(TextView(this@EditActivity).apply { text = e.label; setTextColor(if (e == cur) Ui.ACCENT else Ui.FG); textSize = 12f })
            })
        }
        s.addView(HorizontalScrollView(this).apply { addView(r) })
        hiddenToggleRow(s)
    }

    /* ---------- 2. İçerik ---------- */
    private fun contentSection(cat: String) {
        when (cat) {
            "clock", "combo" -> {
                val s = section("İçerik", "Saatin nasıl görüneceği" + if (cat == "combo") " ve yanındaki panelde ne yazacağı." else ".")
                clockRows(s)
                if (cat == "combo") {
                    row(s, "Panel şekli", "Saatin yanındaki bilgi alanının biçimi.")
                    chips(s, arrayOf("Yuvarlak", "Kare", "Panelsiz"), read { Combo.panel(this) }) { putI("panel", it) }
                    row(s, "Panel içeriği", "Panelde hangi bilgilerin yer alacağı.")
                    chips(s, arrayOf("Sade", "Detaylı", "3 günlük", "Kendim seçeyim"), read { Combo.panelStyle(this) }) { putI("panelStyle", it) }
                    if (read { Combo.panelStyle(this) } == 3) {
                        Combo.SHOW_KEYS.forEachIndexed { i, k -> s.addView(Ui.check(this, Combo.SHOW_NAMES[i], read { Combo.show(this, k) }) { putB("show_$k", it) }) }
                        row(s, "Sonraki günler")
                        chips(s, Combo.DAY_NAMES, read { Combo.days(this) }) { putI("days", it) }
                    }
                }
            }
            "duo" -> {
                val s = section("İçerik", "İki dairenin içinde ne görüneceği.")
                row(s, "Sol daire"); chips(s, Mod.NAMES, read { Mod.slot(this, "slotL", 0) }) { putI("slotL", it) }
                row(s, "Sağ daire"); chips(s, Mod.NAMES, read { Mod.slot(this, "slotR", 2) }) { putI("slotR", it) }
                if (read { Mod.slot(this, "slotL", 0) == 0 || Mod.slot(this, "slotR", 2) == 0 }) clockRows(s)
            }
            "mod" -> {
                val def = if (layout() == R.layout.w_mod_pil) 6 else 8
                val s = section("İçerik", "Dairenin içinde ne görüneceği.")
                chips(s, Mod.NAMES, read { Mod.slot(this, "slot", def) }) { putI("slot", it) }
                if (read { Mod.slot(this, "slot", def) } == 0) clockRows(s)
            }
            "pills" -> {
                val s = section("İçerik", "Her kapsülde ne yazacağı. \"Yok\" seçilen kapsül gizlenir; sığmayan son kapsül de kendiliğinden gizlenir.")
                for (i in 0 until 4) { row(s, "${i + 1}. kapsül"); chips(s, Pills.CONTENT, read { Pills.pill(this, i) }) { putI("pill$i", it) } }
                s.addView(Ui.check(this, "Hava kapsülü havaya göre renklensin", read { Pills.weatherTint(this) }) { putB("pillTint", it) })
            }
            "mini" -> {
                val s = section("İçerik", "Küçük kapsüller. Kısa bilgiler simgeyle gösterilir; sığmayan kapsül alt satıra geçer, yer yoksa gizlenir.")
                for (i in 0 until MiniPills.SLOTS) { row(s, "${i + 1}. kapsül"); chips(s, MiniPills.CONTENT, read { MiniPills.slot(this, i) }) { putI("mp$i", it) } }
                row(s, "Gün tahmini", "Sona \"Per ☁ 21°\" gibi kapsüller ekler.")
                chips(s, MiniPills.DAYS, read { MiniPills.days(this) }) { putI("mpDays", it) }
                row(s, "Kapsül boyutu"); chips(s, MiniPills.SIZES, read { MiniPills.size(this) }) { putI("mpSize", it) }
                row(s, "Hizalama"); chips(s, MiniPills.ALIGN, read { MiniPills.align(this) }) { putI("mpAlign", it) }
            }
            "asym" -> {
                val s = section("İçerik", "Asimetrik düzen: büyük öğe bir kenara yaslanır, öbür taraf boş kalır.")
                if (layout() != R.layout.w_asym_collage) {
                    row(s, "Vurgu rengi", "Dakika, konum ve ayrıntılarda kullanılan renk.")
                    chips(s, Asym.ACCENT_NAMES, read { Asym.accentIdx(this) }) { putI("asAccent", it) }
                }
                row(s, "Yön", "Düzeni aynalar.")
                chips(s, Asym.FLIP_NAMES, read { if (Asym.flip(this)) 1 else 0 }) { putI("asFlip", it) }
            }
            "glance" -> {
                val s = section("İçerik", "Tek bir bilgiye odaklanır; üstte her zaman küçük saat vardır.")
                row(s, "Gösterilecek bilgi")
                chips(s, Glance.KIND_NAMES, read { Glance.kind(this) }) { putI("glance", it) }
            }
            "gs" -> {
                val s = section("Logo ve detaylar", "Logo tüm sarı-kırmızı widget'larda ortak kullanılır.")
                s.addView(TextView(this).apply {
                    text = if (GS.logoFile(this@EditActivity).exists()) "Logoyu değiştir" else "Logo seç (galeriden)"
                    textSize = 15f; setTextColor(android.graphics.Color.rgb(30, 22, 10)); gravity = Gravity.CENTER
                    setPadding(dp(16), dp(12), dp(16), dp(12))
                    background = GradientDrawable().apply { setColor(Ui.ACCENT); cornerRadius = dp(22).toFloat() }
                    layoutParams = LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8) }
                    setOnClickListener {
                        val i = android.content.Intent(android.content.Intent.ACTION_OPEN_DOCUMENT).apply { addCategory(android.content.Intent.CATEGORY_OPENABLE); type = "image/*" }
                        try { startActivityForResult(i, 77) } catch (e: Exception) { Toast.makeText(this@EditActivity, "Galeri açılamadı", Toast.LENGTH_SHORT).show() }
                    }
                })
                if (GS.logoFile(this).exists()) s.addView(TextView(this).apply {
                    text = "Logoyu kaldır"; textSize = 13f; setTextColor(Ui.MUTED); setPadding(0, dp(10), 0, dp(4))
                    setOnClickListener { GS.logoFile(this@EditActivity).delete(); changed(); build() }
                })
                row(s, "Logo görünümü", "Şeffaf arka planlı (PNG) logolar dairesiz de güzel durur.")
                chips(s, GS.LOGO_BG_NAMES, read { GS.logoBg(this) }) { putI("gsLogoBg", it) }
                s.addView(Ui.slider(this, { "Logo boyutu: %${50 + it * 120 / 100}" }, (read { GS.logoSize(this) } - 50) * 100 / 120) { putI("gsLogoSize", 50 + it * 120 / 100) })
                row(s, "Sarı-kırmızı çizgi", "Zarif serideki ince çizginin bölünüşü.")
                chips(s, GS.LINE_NAMES, read { GS.lineStyle(this) }) { putI("gsLine", it) }
                row(s, "Gösterilecekler")
                s.addView(Ui.check(this, "Konum", read { Combo.show(this, "loc") }) { putB("show_loc", it) })
                s.addView(Ui.check(this, "Tarih", read { Combo.show(this, "date") }) { putB("show_date", it) })
                s.addView(Ui.check(this, "Hava durumu yazısı", read { Combo.show(this, "desc") }) { putB("show_desc", it) })
                s.addView(Ui.check(this, "3 günlük tahmin (geniş tasarımlarda)", read { Combo.show(this, "fc") }) { putB("show_fc", it) })
            }
            "anim" -> {
                val s = section("Animasyon", "Arka plan o anki havaya göre hareket eder. Saat ve yazılar dakikada bir yenilenir.")
                s.addView(Ui.check(this, "Animasyon açık (kapalıysa sabit resim, pil dostu)", read { Anim.on(this) }) { putB("animOn", it) })
                row(s, "Hız")
                chips(s, Anim.SPEED_NAMES, read { Anim.speed(this) }) { putI("animSpeed", it) }
                row(s, "Hava durumu", "Diğer havaları denemek için seç; normalde \"Gerçek hava\" kalmalı.")
                chips(s, Anim.FORCE_NAMES, read { Anim.force(this) }) { putI("animForce", it) }
            }
            "card" -> {
                val s = section("İçerik", "Kartın içindeki yerleşim ve görünen bilgiler.")
                row(s, "Hizalama", "Saat ve yazıların karta yaslandığı taraf.")
                chips(s, Cards.ALIGN_NAMES, read { Cards.align(this) }) { putI("cardAlign", it) }
                row(s, "Hava ikonunun yeri")
                chips(s, Cards.ICONPOS_NAMES, read { Cards.iconPos(this) }) { putI("cardIcon", it) }
                row(s, "Gösterilecekler")
                val keys = arrayOf("loc", "date", "desc", "feels")
                val names = arrayOf("Konum", "Tarih", "Hava durumu yazısı", "Hissedilen sıcaklık")
                keys.forEachIndexed { i, k -> s.addView(Ui.check(this, names[i], read { Combo.show(this, k) }) { putB("show_$k", it) }) }
                if (layout() != R.layout.w_card_yigin)
                    s.addView(Ui.check(this, "Yarının havası", read { Cards.tomorrow(this) }) { putB("show_tom", it) })
            }
            "palette" -> if (layout() != R.layout.w_square4) {
                val s = section("İçerik", "Tasarımdaki kutuların biçimi.")
                row(s, "Kutu şekli")
                chips(s, arrayOf("Tasarımdaki gibi", "Yumuşak kare", "Keskin köşe", "Hap"), intOf("boxShape", 0)) { putI("boxShape", it) }
            }
        }
    }

    private fun clockRows(s: LinearLayout) {
        row(s, "Kadran", "Analog saatin yüzündeki işaretler.")
        chips(s, Combo.FACE_NAMES, read { Combo.face(this) }) { putI("face", it) }
        row(s, "Dijital saat", "Analog saatin içindeki rakamla saat.")
        chips(s, arrayOf("Üstte", "Altta", "Yok"), read { Combo.digital(this) }) { putI("digital", it) }
    }

    /* ---------- Konum ve dokunma ---------- */
    private fun locationSection() {
        val s = section("Konum ve dokunma", "Widget'ta yazan konumun ne kadar ayrıntılı olacağı ve hava kısmına dokununca ne olacağı.")
        row(s, "Konum ayrıntısı", "Bilgi bulunamazsa bir üst düzey gösterilir.")
        chips(s, WeatherRepo.DETAIL_NAMES, read { WeatherRepo.detail(this) }) { putI("locDetail", it) }
        row(s, "Hava kısmına dokununca")
        chips(s, arrayOf("Hava uygulamasını aç", "Konumu ve havayı yenile"), intOf("tapWeather", 0)) { putI("tapWeather", it) }
    }

    /* ---------- Boyutlar ---------- */
    private fun sizeSection(cat: String) {
        val s = section("Boyutlar", "Widget içindeki saatin, yazıların (konum dahil) ve hava ikonunun büyüklüğü. %100 tasarımın kendi boyutu.")
        s.addView(Ui.slider(this, { "Saat boyutu: %${60 + it * 90 / 100}" }, (intOf("sClock", 100) - 60) * 100 / 90) { putI("sClock", 60 + it * 90 / 100) })
        s.addView(Ui.slider(this, { "Konum ve yazı boyutu: %${60 + it * 90 / 100}" }, (intOf("sText", 100) - 60) * 100 / 90) { putI("sText", 60 + it * 90 / 100) })
        s.addView(Ui.slider(this, { "İkon boyutu: %${60 + it * 90 / 100}" }, (intOf("sIcon", 100) - 60) * 100 / 90) { putI("sIcon", 60 + it * 90 / 100) })
        if (cat == "card" || cat == "asym" || cat == "origami")
            s.addView(Ui.slider(this, { "Sıcaklık boyutu: %${60 + it * 90 / 100}" }, (intOf("sTemp", 100) - 60) * 100 / 90) { putI("sTemp", 60 + it * 90 / 100) })
        if (cat == "standard" || cat == "palette")
            s.addView(TextView(this).apply { text = "Bu tasarımda ikon boyutu sabittir; saat ve yazı boyutu uygulanır."; setTextColor(Ui.MUTED); textSize = 12f })
    }

    /* ---------- 3. Arka plan ---------- */
    private val BG_KINDS = arrayOf("Tasarımın kendi zemini", "Şeffaf", "Düz renk", "Degrade", "Havaya göre", "Gökyüzü resmi", "Kağıt dokusu")

    private fun bgKind(cat: String): Int {
        val ubg = read { Decor.mode(this) }
        if (ubg > 0) return ubg + 1
        val clear = when (cat) {
            "standard" -> read { Style.bg(this) } == 2
            "origami" -> read { Origami.paper(this) } == 3
            "asym" -> read { Asym.bgOpacity(this) } == 0
            else -> read { Style.opacity(this) } == 0
        }
        return if (clear) 1 else 0
    }

    private fun setBgKind(cat: String, k: Int) {
        val e = p.edit()
        fun i(key: String, v: Int) = e.putInt(Cfg.wkey(wid, key), v)
        when (k) {
            0 -> {
                i("ubg", 0)
                if (cat == "asym" || read { Style.opacity(this) } == 0) i("opacity", if (cat == "asym") 70 else 100)
                if (cat == "standard" && read { Style.bg(this) } == 2) i("bg", 0)
                if (cat == "origami" && read { Origami.paper(this) } == 3) i("paperColor", 0)
            }
            else -> {
                i("ubg", if (k == 1) 0 else k - 1); i("opacity", 0)
                if (cat == "standard") i("bg", 2)
                if (cat == "origami") i("paperColor", 3)
            }
        }
        e.apply(); changed(); build()
    }

    private fun backgroundSection(cat: String) {
        val s = section("Arka plan", "Widget'ın arkasındaki zemin. Önce türünü seç, sonra rengini ve ne kadar dolu (saydam) olacağını ayarla.")
        row(s, "Zemin türü")
        s.addView(Ui.chips(this, BG_KINDS, bgKind(cat)) { setBgKind(cat, it) })
        when (val k = bgKind(cat)) {
            0 -> {
                ownColorRows(s, cat)
                if (cat != "card") s.addView(Ui.slider(this, { "Doluluk: %$it  (0 = tamamen saydam)" }, read { if (cat == "asym") Asym.bgOpacity(this) else Style.opacity(this) }) { putI("opacity", it) })
            }
            1 -> s.addView(TextView(this).apply { text = "Zemin yok; yazılar okunaklı kalsın diye hafif gölge eklenir."; setTextColor(Ui.MUTED); textSize = 12f })
            else -> {
                if (k == 2 || k == 3 || k == 6) { row(s, "Zemin rengi"); chips(s, Decor.SWATCH_NAMES, read { Decor.swatch(this) }) { putI("ubgColor", it) } }
                if (k == 4) s.addView(TextView(this).apply { text = "Güneşte parlama, gece yıldızlar, yağmurda damlalar… zemin hava durumuna göre kendiliğinden değişir."; setTextColor(Ui.MUTED); textSize = 12f })
                s.addView(Ui.slider(this, { "Doluluk: %$it" }, read { Decor.op(this) }) { putI("ubgOp", it) })
            }
        }
    }

    private fun ownColorRows(s: LinearLayout, cat: String) {
        when (cat) {
            "standard" -> {
                row(s, "Tasarımın rengi")
                val cur = when (read { Style.bg(this) }) { 1 -> 1; 3 -> 2; else -> 0 }
                chips(s, arrayOf("Koyu", "Açık", "Renk paletinden"), cur) { putI("bg", intArrayOf(0, 1, 3)[it]) }
                if (cur == 2) paletteRow(s)
            }
            "palette" -> paletteRow(s)
            "clock", "combo", "round", "duo", "mod", "lock" -> {
                row(s, "Tasarımın rengi")
                chips(s, Combo.COLOR_NAMES, read { Combo.color(this) }) { putI("faceColor", it) }
                if (read { Combo.color(this) } == 2) paletteRow(s)
            }
            "card" -> {
                row(s, "Kağıt rengi", "Yazı rengi kağıda göre kendiliğinden açık ya da koyu olur.")
                chips(s, Cards.PAPER_NAMES, read { Cards.paperIdx(this) }) { putI("cardPaper", it) }
                if (read { Cards.paperIdx(this) } == 8) paletteRow(s)
            }
            "gs" -> s.addView(TextView(this).apply {
                text = "Siyah ya da beyaz zemini aşağıdaki doluluk ayarıyla saydamlaştırabilirsin; renk şeritleri ve logo yerinde kalır, yazılara okunaklılık için gölge eklenir."
                setTextColor(Ui.MUTED); textSize = 12f })
            "origami" -> {
                row(s, "Kağıt", "\"Havaya göre\" seçilirse kağıt hava durumuyla renk değiştirir.")
                val map = intArrayOf(0, 1, 2, 4, 5, 6, 7)
                chips(s, arrayOf("Koyu kağıt", "Krem kağıt", "Havaya göre", "Defter kağıdı", "Yırtık karton", "Ataçlı kağıt", "Bantlı not"), map.indexOf(read { Origami.paper(this) }).coerceAtLeast(0)) { putI("paperColor", map[it]) }
            }
        }
    }

    private fun paletteRow(s: LinearLayout) {
        row(s, "Renk paleti", "Otomatik: duvar kağıdından · Havaya göre: hava durumundan")
        chips(s, Palette.NAMES, read { Palette.choice(this) }) { putI("palette", it) }
    }

    /* ---------- 4. Kenarlar ---------- */
    private fun edgesSection() {
        val s = section("Kenarlar", "Widget'ın dış hattı: köşelerin yuvarlaklığı ve çevresine çizilen çerçeve.")
        row(s, "Widget şekli", "Kurabiye, çiçek, kemer gibi silüetler; zemin ve içerik bu şekle kırpılır.")
        chips(s, Decor.SHAPE_NAMES, read { Decor.shape(this) }) { putI("ubgShape", it) }
        row(s, "Çerçeve")
        chips(s, Decor.FRAME_NAMES, read { Decor.frame(this) }) { putI("ubgFrame", it) }
        if (read { Decor.frame(this) } > 0) { row(s, "Çerçeve rengi"); chips(s, Decor.FRAME_COLOR_NAMES, read { Decor.frameColor(this) }) { putI("ubgFrameCol", it) } }
        if (read { Decor.frame(this) } > 0 || read { Decor.mode(this) } > 0) {
            row(s, "Köşeler", "Çerçeve ve eklenen zemin için geçerli.")
            chips(s, Decor.RADIUS_NAMES, read { Decor.radius(this) }) { putI("ubgRad", it) }
        }
    }

    /* ---------- 5. Yazılar ---------- */
    private fun textSection(cat: String) {
        val s = section("Yazılar", "Saatin ve diğer yazıların yazı tipi, saatin efekti ve yazı rengi.")
        if (cat == "theme") {
            s.addView(TextView(this).apply { text = "Bu temanın yazı tipi ve renkleri tasarımın parçası; değiştirilemez."; setTextColor(Ui.MUTED); textSize = 12f })
            return
        }
        row(s, "Rakam yazı tipi", "Saat ve derece rakamları. Uzun bas: gizle.")
        val curF = read { Style.font(this) }
        val r = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        Style.FONT_NAMES.forEachIndexed { i, n ->
            if (i != curF && !visible(Hide.FONT, "$i")) return@forEachIndexed
            r.addView(card(i == curF, { putI("font", i); build() }, { hideToggle(Hide.FONT, "$i", n) }, Hide.has(this, Hide.FONT, "$i")).apply {
                addView(TextView(this@EditActivity).apply { text = "12:45"; textSize = 24f; setTextColor(Ui.FG); typeface = Style.clockTypeface(this@EditActivity, i) })
                addView(TextView(this@EditActivity).apply { text = n; textSize = 11f; setTextColor(Ui.MUTED) })
            })
        }
        s.addView(HorizontalScrollView(this).apply { addView(r) })
        row(s, "Yazı tipi", "Konum, tarih, hava durumu gibi yazılar.")
        val t = read { Style.textFontIdx(this) }
        val r2 = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        (listOf(-1) + Style.FONT_NAMES.indices).forEach { i ->
            if (i >= 0 && i != t && !visible(Hide.FONT, "$i")) return@forEach
            r2.addView(card(i == t, { putI("textFont", i); build() }, if (i >= 0) ({ hideToggle(Hide.FONT, "$i", Style.FONT_NAMES[i]) }) else null, i >= 0 && Hide.has(this, Hide.FONT, "$i")).apply {
                addView(TextView(this@EditActivity).apply {
                    text = "Kadıköy"; textSize = 16f; setTextColor(Ui.FG)
                    typeface = Style.textTypeface(this@EditActivity, if (i < 0) curF else i).also { _ -> }
                })
                addView(TextView(this@EditActivity).apply { text = if (i < 0) "Saatle uyumlu" else Style.FONT_NAMES[i]; textSize = 11f; setTextColor(Ui.MUTED) })
            })
        }
        s.addView(HorizontalScrollView(this).apply { addView(r2) })
        row(s, "Saat efekti", "Saatin rakamlarına uygulanan görünüm.")
        val fx = read { Style.clockFx(this) }
        val r3 = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        Style.FX_NAMES.forEachIndexed { i, n ->
            r3.addView(card(i == fx, { putI("clockFx", i); build() }).apply {
                addView(ImageView(this@EditActivity).apply {
                    val b = android.graphics.Bitmap.createBitmap(dp(84), dp(40), android.graphics.Bitmap.Config.ARGB_8888)
                    val cv = android.graphics.Canvas(b)
                    val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                        typeface = Style.clockTypeface(this@EditActivity, curF); textSize = dp(26).toFloat(); color = Color.WHITE; textAlign = android.graphics.Paint.Align.CENTER
                    }
                    Lock.applyFx(paint, i, paint.textSize, Color.WHITE)
                    cv.drawText("12:45", b.width / 2f, b.height * .74f, paint)
                    setImageBitmap(b)
                })
                addView(TextView(this@EditActivity).apply { text = n; textSize = 11f; setTextColor(Ui.MUTED) })
            })
        }
        s.addView(HorizontalScrollView(this).apply { addView(r3) })
        row(s, "Saat stili", "Dakikayı ayrı renkte, saat ile dakika arasında ince çizgiyle ya da \"dokuz kırk bir\" gibi yazıyla gösterir.")
        chips(s, Ink.STYLE_NAMES, read { Ink.style(this) }) { putI("clockStyle", it) }
        row(s, "Saat kalınlığı")
        chips(s, Ink.WEIGHT_NAMES, read { Ink.weight(this) }) { putI("clockWeight", it) }
        s.addView(Ui.slider(this, { "Harf aralığı: ${it / 2 - 10}" }, (read { Ink.spacing(this) } + 10) * 2) { putI("clockSpacing", it / 2 - 10) })
        hiddenToggleRow(s)
    }

    /* ---------- Renkler ---------- */
    private fun colorSection(cat: String) {
        val s = section("Renkler", "Her öğenin rengini ayrı seç. Daire çizgiliyse tasarımın kendi rengi kullanılır.")
        if (cat == "theme") {
            s.addView(TextView(this).apply { text = "Bu temanın renkleri tasarımın parçası; değiştirilemez."; setTextColor(Ui.MUTED); textSize = 12f })
            return
        }
        row(s, "Hazır paletler", "Tek dokunuşla tüm öğelere uyumlu renkler verir.")
        s.addView(Ui.chips(this, Ink.PRESET_NAMES + "Hepsini sıfırla", -1) { i ->
            val e = p.edit()
            if (i == Ink.PRESET_NAMES.size) Ink.KEYS.forEach { e.remove(Cfg.wkey(wid, it)) }
            else {
                val cols = Ink.presetColors(this, i)
                if (cols == null) { Toast.makeText(this, "Duvar kağıdı renkleri alınamadı", Toast.LENGTH_SHORT).show(); return@chips }
                Ink.KEYS.forEachIndexed { k, key -> e.putInt(Cfg.wkey(wid, key), cols[k]) }
            }
            e.remove(Cfg.wkey(wid, "textColor")).apply(); changed(); build()
        })
        Ink.KEYS.forEachIndexed { i, key -> colorRow(s, Ink.KEY_NAMES[i], key) }
        row(s, "Saat dolgusu")
        chips(s, arrayOf("Düz renk", "İki renk geçiş"), if (read { Ink.grad(this) }) 1 else 0) { putB("clockGrad", it == 1) }
        if (read { Ink.grad(this) }) colorRow(s, "Geçişin alt rengi", Ink.CLOCK2)
        row(s, "Gölge", "Yazıların arkasındaki gölgenin gücü.")
        chips(s, Ink.SHADOW_NAMES, read { Ink.shadow(this) }) { putI("shadowK", it) }
        s.addView(Ui.check(this, "Okunurluğu otomatik düzelt (renk zeminle karışırsa koyulaştır/aç)", read { Ink.autoContrast(this) }) { putB("autoContrast", it) })
        s.addView(Ui.check(this, "Saat rengi gün içinde değişsin (sabah · öğle · akşam · gece)", read { Ink.todOn(this) }) { putB("todColor", it) })
    }

    private fun colorRow(s: LinearLayout, label: String, key: String) {
        val cur = intOf(key, 0).takeIf { it != 0 }
        s.addView(LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(8), 0, dp(4))
            addView(ColorPick.dot(this@EditActivity, cur).apply { layoutParams = LinearLayout.LayoutParams(dp(32), dp(32)) })
            addView(TextView(this@EditActivity).apply {
                text = label; textSize = 14f; setTextColor(Ui.FG); setPadding(dp(12), 0, 0, 0)
                layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
            })
            addView(TextView(this@EditActivity).apply {
                text = if (cur == null) "Otomatik" else String.format("#%06X", cur and 0xFFFFFF); textSize = 12f; setTextColor(Ui.MUTED)
            })
            setOnClickListener {
                ColorPick.show(this@EditActivity, label, cur) { col ->
                    if (col == null) p.edit().remove(Cfg.wkey(wid, key)).apply() else p.edit().putInt(Cfg.wkey(wid, key), col).apply()
                    changed(); build()
                }
            }
        })
    }

    private fun card(selected: Boolean, onClick: () -> Unit, onLong: (() -> Unit)? = null, dim: Boolean = false) = LinearLayout(this).apply {
        if (onLong != null) setOnLongClickListener { onLong(); true }
        if (dim) alpha = .4f
        orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_HORIZONTAL
        setPadding(dp(10), dp(8), dp(10), dp(8))
        background = GradientDrawable().apply { setColor(Color.rgb(46, 51, 62)); cornerRadius = dp(14).toFloat(); if (selected) setStroke(dp(2), Ui.ACCENT) }
        layoutParams = LinearLayout.LayoutParams(-2, -2).apply { rightMargin = dp(8) }
        setOnClickListener { onClick() }
    }

    /* ---------- 6. Hava ikonları ---------- */
    private fun iconSection() {
        val s = section("Hava ikonları", "Önce ikonların şeklini, sonra nasıl boyanacağını (stilini) seç.")
        val shape = read { Style.iconShape(this) }; val style = read { Style.iconStyle(this) }
        fun samples(sh: Int, st: Int) = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            for (k in intArrayOf(0, 2, 7, 9)) addView(ImageView(this@EditActivity).apply {
                setImageResource(Style.row(sh, st)[k]); layoutParams = LinearLayout.LayoutParams(dp(28), dp(28))
            })
        }
        row(s, "Şekil")
        val r = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        Style.SHAPE_NAMES.forEachIndexed { i, n ->
            if (i != shape && !visible(Hide.ICON, "$i")) return@forEachIndexed
            r.addView(card(i == shape, { putI("iconShape", i); putI("iconStyle", style); build() }, { hideToggle(Hide.ICON, "$i", n) }, Hide.has(this, Hide.ICON, "$i")).apply {
                addView(samples(i, style)); addView(TextView(this@EditActivity).apply { text = n; textSize = 11f; setTextColor(Ui.MUTED) })
            })
        }
        s.addView(HorizontalScrollView(this).apply { addView(r) })
        if (!Style.fixedStyle(shape)) {
            row(s, "Stil")
            val r2 = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            Style.ICON_STYLE_NAMES.forEachIndexed { i, n ->
                r2.addView(card(i == style, { putI("iconStyle", i); putI("iconShape", shape); build() }).apply {
                    addView(samples(shape, i)); addView(TextView(this@EditActivity).apply { text = n; textSize = 11f; setTextColor(Ui.MUTED) })
                })
            }
            s.addView(HorizontalScrollView(this).apply { addView(r2) })
        } else s.addView(TextView(this).apply { text = "Bu şeklin kendi stili var; renkleri ve dokusu tasarımın parçası."; setTextColor(Ui.MUTED); textSize = 12f })
        hiddenToggleRow(s)
    }

    /* ---------- 7. İşlemler ---------- */
    private val LOOK_KEYS = listOf("ubgShape", "ubg", "ubgColor", "ubgOp", "ubgRad", "ubgFrame", "ubgFrameCol", "font", "textFont", "textColor", "clockFx",
        "iconShape", "iconStyle", "palette", "sClock", "sText", "sIcon", "locDetail", "tapWeather") + Ink.ALL_KEYS

    private fun actions() {
        root.addView(Ui.card(this).apply {
            addView(TextView(this@EditActivity).apply {
                text = "⧉  Bu görünümü diğer tüm widget'lara uygula"; setTextColor(Ui.ACCENT); textSize = 15f
                setPadding(0, 0, 0, dp(4)); setOnClickListener { copyLook() }
            })
            addView(TextView(this@EditActivity).apply {
                text = "Yazı tipleri, saat efekti ve stili, renkler, ikonlar, ek zemin ve çerçeve kopyalanır."; textSize = 12f; setTextColor(Ui.MUTED)
                setPadding(0, 0, 0, dp(14))
            })
            addView(TextView(this@EditActivity).apply {
                text = "↺  Bu widget'ın ayarlarını sıfırla"; setTextColor(Ui.ACCENT); textSize = 15f
                setOnClickListener {
                    val e = p.edit(); p.all.keys.filter { it.startsWith("w${wid}_") }.forEach { e.remove(it) }
                    e.apply(); sendBroadcast(Widgets.refreshIntent(this@EditActivity)); build()
                }
            })
        })
    }

    private fun copyLook() {
        val mgr = AppWidgetManager.getInstance(this)
        val ed = p.edit(); var n = 0
        for (e in Registry.ALL) for (other in mgr.getAppWidgetIds(android.content.ComponentName(this, e.cls))) {
            if (other == wid) continue
            n++
            for (k in LOOK_KEYS) {
                when (val v = p.all[Cfg.wkey(wid, k)]) {
                    is Int -> ed.putInt(Cfg.wkey(other, k), v)
                    is Boolean -> ed.putBoolean(Cfg.wkey(other, k), v)
                    null -> ed.remove(Cfg.wkey(other, k))
                }
            }
        }
        ed.apply(); sendBroadcast(Widgets.refreshIntent(this))
        Toast.makeText(this, "$n widget'a uygulandı", Toast.LENGTH_SHORT).show()
    }
}
