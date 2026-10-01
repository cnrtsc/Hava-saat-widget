package com.caner.sketchweather

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

/** Tek bir widget'ın düzenleme ekranı: canlı önizleme + sadece o widget'a ait ayarlar. */
class EditActivity : Activity() {
    private var wid = 0
    private lateinit var root: LinearLayout
    private lateinit var preview: FrameLayout
    private var wdp = 320
    private var hdp = 170

    private val p get() = WeatherRepo.prefs(this)
    private fun <T> read(block: () -> T): T = Cfg.with(wid) { block() }
    private fun putI(k: String, v: Int) { p.edit().putInt(Cfg.wkey(wid, k), v).apply(); changed() }
    private fun putB(k: String, v: Boolean) { p.edit().putBoolean(Cfg.wkey(wid, k), v).apply(); changed() }

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
        R.layout.w_roundw0, R.layout.w_roundw1, R.layout.w_roundw2, R.layout.w_square7, R.layout.w_square8,
        R.layout.w_square1, R.layout.w_square2 -> "round"
        R.layout.w_lock, R.layout.w_lockc -> "lock"
        R.layout.w_duo -> "duo"
        R.layout.w_bentox, R.layout.w_bentosq -> "bento"
        R.layout.w_skycard -> "sky"
        R.layout.w_pills -> "pills"
        R.layout.w_ori_card, R.layout.w_ori_strip, R.layout.w_ori_square, R.layout.w_ori_diag -> "origami"
        R.layout.w_th_dot, R.layout.w_th_flip, R.layout.w_th_swiss, R.layout.w_th_prog, R.layout.w_th_term, R.layout.w_th_paper, R.layout.w_th_sector, R.layout.w_th_neu -> "theme"
        R.layout.w_mod_pil, R.layout.w_mod_ay, R.layout.w_mod_takvim -> "mod"
        R.layout.w_strip, R.layout.w_graph -> "round"
        R.layout.w_wide6, R.layout.w_wide7, R.layout.w_wide8, R.layout.w_compact1, R.layout.w_compact2,
        R.layout.w_square3, R.layout.w_square4 -> "palette"
        else -> "standard"
    }

    private fun changed() {
        sendBroadcast(Widgets.refreshIntent(this))
        refreshPreview()
    }

    private fun refreshPreview() {
        preview.removeAllViews()
        try {
            val rv = Widgets.preview(this, wid, wdp, hdp) ?: return
            val v = rv.apply(this, preview)
            preview.addView(v, FrameLayout.LayoutParams(Ui.dp(this, wdp), Ui.dp(this, hdp)).apply { gravity = android.view.Gravity.CENTER })
        } catch (e: Exception) {
            preview.addView(TextView(this).apply { text = "Önizleme gösterilemedi"; setTextColor(Ui.MUTED) })
        }
    }

    private fun build() {
        val outer = ScrollView(this).apply { setBackgroundColor(Ui.BG) }
        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(Ui.dp(this@EditActivity, 16), Ui.dp(this@EditActivity, 36), Ui.dp(this@EditActivity, 16), Ui.dp(this@EditActivity, 32))
        }
        outer.addView(root)
        setContentView(outer)
        val cur = current()
        root.addView(TextView(this).apply { text = cur?.label ?: "Widget"; textSize = 26f; setTextColor(Ui.FG) })
        root.addView(TextView(this).apply {
            text = "Bu ayarlar sadece bu widget için geçerli."; setTextColor(Ui.MUTED); textSize = 13f
        })
        // canlı önizleme
        preview = FrameLayout(this).apply {
            background = GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(Color.rgb(96, 70, 58), Color.rgb(40, 48, 62), Color.rgb(28, 32, 42))).apply {
                cornerRadius = Ui.dp(this@EditActivity, 20).toFloat()
            }
            setPadding(Ui.dp(this@EditActivity, 10), Ui.dp(this@EditActivity, 14), Ui.dp(this@EditActivity, 10), Ui.dp(this@EditActivity, 14))
            layoutParams = LinearLayout.LayoutParams(-1, Ui.dp(this@EditActivity, hdp + 28)).apply { topMargin = Ui.dp(this@EditActivity, 12) }
        }
        root.addView(preview)
        refreshPreview()

        // stil değiştir
        val size = entry()?.size ?: "w"
        val st = Ui.section(this, "Tasarım", "Aynı boydaki başka bir tasarıma geç.")
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        Registry.ALL.forEachIndexed { idx, e ->
            if (e.size != size) return@forEachIndexed
            val w = Ui.dp(this, if (size == "s") 96 else 180)
            val cell = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(Ui.dp(this@EditActivity, 5), Ui.dp(this@EditActivity, 5), Ui.dp(this@EditActivity, 5), Ui.dp(this@EditActivity, 5))
                if (e == cur) background = GradientDrawable().apply { setStroke(Ui.dp(this@EditActivity, 2), Ui.ACCENT); cornerRadius = Ui.dp(this@EditActivity, 12).toFloat() }
                setOnClickListener { p.edit().putInt("ov_$wid", idx).apply(); sendBroadcast(Widgets.refreshIntent(this@EditActivity)); build() }
            }
            cell.addView(ImageView(this).apply { setImageResource(e.preview); adjustViewBounds = true; layoutParams = LinearLayout.LayoutParams(w, -2) })
            cell.addView(TextView(this).apply { text = e.label; setTextColor(if (e == cur) Ui.ACCENT else Ui.FG); textSize = 12f })
            row.addView(cell)
        }
        st.addView(android.widget.HorizontalScrollView(this).apply { addView(row) })
        root.addView(st)

        when (category(layout())) {
            "clock" -> { clockSection(); lookSection(true, false); if (layout() == R.layout.w_square5) iconSection() }
            "combo" -> { clockSection(); panelSection(); lookSection(true, false); iconSection() }
            "round" -> { lookSection(true, false); iconSection() }
            "lock" -> { fontSection(true); lookSection(true, true, false); iconSection() }
            "duo" -> {
                val s = Ui.section(this, "Daireler", "İki dairenin içinde ne görüneceğini seç.")
                s.addView(Ui.label(this, "Sol daire"))
                s.addView(Ui.chips(this, Mod.NAMES, read { Mod.slot(this, "slotL", 0) }) { putI("slotL", it); build() })
                s.addView(Ui.label(this, "Sağ daire"))
                s.addView(Ui.chips(this, Mod.NAMES, read { Mod.slot(this, "slotR", 2) }) { putI("slotR", it); build() })
                root.addView(s)
                if (read { Mod.slot(this, "slotL", 0) == 0 || Mod.slot(this, "slotR", 2) == 0 }) clockSection()
                lookSection(true, false); iconSection()
            }
            "bento" -> {
                val sq = layout() == R.layout.w_bentosq
                val s = Ui.section(this, "Kutular", "Her kutuda ne görüneceğini sen seç.")
                if (!sq) {
                    s.addView(Ui.label(this, "Yerleşim"))
                    s.addView(Ui.chips(this, Bento.ARRANGE, read { Bento.arrange(this, false) }) { putI("arrange", it); build() })
                }
                val names = if (sq || read { Bento.arrange(this, false) } == 2) arrayOf("Sol üst", "Sağ üst", "Sol alt", "Sağ alt")
                    else arrayOf("Büyük kutu", "Üst kutu", "Daire", "Küçük kutu")
                for (i in 0 until 4) {
                    s.addView(Ui.label(this, names[i]))
                    s.addView(Ui.chips(this, Bento.CONTENT, read { Bento.tile(this, i) }) { putI("tile$i", it); build() })
                }
                s.addView(Ui.check(this, "Hava kutusu havaya göre renklensin", read { Bento.weatherColor(this) }) { putB("weatherTint", it) })
                root.addView(s)
                if ((0 until 4).any { i -> read { Bento.tile(this, i) } == 0 }) {
                    val cs = Ui.section(this, "Saat kutusu")
                    cs.addView(Ui.chips(this, Bento.ALIGN, read { Bento.align(this) }) { putI("tileAlign", it); build() })
                    cs.addView(Ui.chips(this, Bento.CLOCK_STYLE, read { Bento.clockStyle(this) }) { putI("tileClock", it); build() })
                    root.addView(cs)
                }
                if ((0 until 4).any { i -> read { Bento.tile(this, i) } == 1 }) clockSection()
                paletteSection(); fontSection(true); iconSection()
            }
            "origami" -> {
                val s = Ui.section(this, "Kağıt", "Zeminin rengini ve doluluğunu seç. \"Havaya göre\" seçersen kağıt hava durumuyla renk değiştirir.")
                s.addView(Ui.chips(this, Origami.PAPER_NAMES, read { Origami.paper(this) }) { putI("paperColor", it); build() })
                if (read { Origami.paper(this) } != 3)
                    s.addView(Ui.slider(this, { "Kağıt doluluğu: %$it" }, read { Style.opacity(this) }) { putI("opacity", it) })
                s.addView(Ui.label(this, "Yazı rengi"))
                s.addView(Ui.chips(this, Style.TEXT_COLOR_NAMES, read { Style.textColorIdx(this) }) { putI("textColor", it); build() })
                root.addView(s)
                fontSection(true); iconSection()
            }
            "pills" -> {
                val s = Ui.section(this, "Kapsüller", "Her kapsülde ne görüneceğini seç. \"Yok\" seçersen o kapsül gizlenir.")
                for (i in 0 until 4) {
                    s.addView(Ui.label(this, "${i + 1}. kapsül"))
                    s.addView(Ui.chips(this, Pills.CONTENT, read { Pills.pill(this, i) }) { putI("pill$i", it); build() })
                }
                s.addView(Ui.check(this, "Hava kapsülü havaya göre renklensin", read { Pills.weatherTint(this) }) { putB("pillTint", it) })
                s.addView(Ui.check(this, "İnce çerçeve", read { Combo.rim(this) }) { putB("rim", it) })
                s.addView(Ui.slider(this, { "Kapsül doluluğu: %$it" + if (it == 0) " (şeffaf)" else "" }, read { Style.opacity(this) }) { putI("opacity", it) })
                s.addView(Ui.label(this, "Yazı rengi"))
                s.addView(Ui.chips(this, Style.TEXT_COLOR_NAMES, read { Style.textColorIdx(this) }) { putI("textColor", it); build() })
                root.addView(s)
                fontSection(true); iconSection()
            }
            "sky" -> {
                val s = Ui.section(this, "Alt şerit", "Alttaki üç kutuda ne görüneceğini seç.")
                for (i in 0 until 3) {
                    s.addView(Ui.label(this, "${i + 1}. kutu"))
                    s.addView(Ui.chips(this, SkyCard.ITEMS, read { SkyCard.item(this, i) }) { putI("sky$i", it); build() })
                }
                s.addView(Ui.check(this, "Hava durumunun yanında ikon", read { SkyCard.showIcon(this) }) { putB("skyIcon", it) })
                s.addView(Ui.slider(this, { "Kart doluluğu: %$it" + if (it == 0) " (şeffaf)" else "" }, read { Style.opacity(this) }) { putI("opacity", it) })
                s.addView(Ui.label(this, "Yazı rengi"))
                s.addView(Ui.chips(this, Style.TEXT_COLOR_NAMES, read { Style.textColorIdx(this) }) { putI("textColor", it); build() })
                root.addView(s)
                fontSection(true); iconSection()
            }
            "theme" -> {
                val s = Ui.section(this, "Tema", "Bu tasarımın renkleri ve yazı tipi temaya özel. Zemini ve ikonları değiştirebilirsin.")
                s.addView(Ui.slider(this, { "Zemin doluluğu: %$it" + if (it == 0) " (şeffaf)" else "" }, read { Style.opacity(this) }) { putI("opacity", it) })
                root.addView(s)
                iconSection()
            }
            "mod" -> {
                val def = when (layout()) { R.layout.w_mod_pil -> 6; R.layout.w_mod_ay -> 7; else -> 8 }
                val s = Ui.section(this, "Daire içeriği")
                s.addView(Ui.chips(this, Mod.NAMES, read { Mod.slot(this, "slot", def) }) { putI("slot", it); build() })
                root.addView(s)
                if (read { Mod.slot(this, "slot", def) } == 0) clockSection()
                lookSection(true, false); iconSection()
            }
            "palette" -> { paletteSection(); fontSection(true); iconSection() }
            else -> { bgSection(); fontSection(true); iconSection() }
        }
        decorSection()
        root.addView(Ui.card(this).apply {
            addView(TextView(this@EditActivity).apply {
                text = "⧉  Bu görünümü diğer tüm widget'lara uygula"; setTextColor(Ui.ACCENT); textSize = 15f
                setPadding(0, 0, 0, Ui.dp(this@EditActivity, 14))
                setOnClickListener { copyLook() }
            })
            addView(TextView(this@EditActivity).apply {
                text = "↺  Bu widget'ın ayarlarını sıfırla"; setTextColor(Ui.ACCENT); textSize = 15f
                setOnClickListener {
                    val e = p.edit()
                    p.all.keys.filter { it.startsWith("w${wid}_") }.forEach { e.remove(it) }
                    e.apply(); sendBroadcast(Widgets.refreshIntent(this@EditActivity)); build()
                }
            })
        })
    }

    private val LOOK_KEYS = listOf("ubg", "ubgColor", "ubgOp", "ubgRad", "ubgFrame", "ubgFrameCol", "font", "textColor", "clockFx", "icons", "palette", "anim", "rim", "boxShape")

    /** Yazı tipi, renkler, ikonlar, zemin ve çerçeveyi diğer widget'lara kopyalar. */
    private fun copyLook() {
        val mgr = AppWidgetManager.getInstance(this)
        val ed = p.edit()
        var n = 0
        for (e in Registry.ALL) for (other in mgr.getAppWidgetIds(android.content.ComponentName(this, e.cls))) {
            if (other == wid) continue
            n++
            for (k in LOOK_KEYS) {
                val src = Cfg.wkey(wid, k); val dst = Cfg.wkey(other, k)
                when (val v = p.all[src]) {
                    is Int -> ed.putInt(dst, v)
                    is Boolean -> ed.putBoolean(dst, v)
                    null -> ed.remove(dst)
                }
            }
        }
        ed.apply()
        sendBroadcast(Widgets.refreshIntent(this))
        android.widget.Toast.makeText(this, "$n widget'a uygulandı", android.widget.Toast.LENGTH_SHORT).show()
    }

    /** Her widget için ortak: ek zemin, renk, doluluk, köşe, çerçeve. */
    private fun decorSection() {
        val s = Ui.section(this, "Zemin ve çerçeve", "Tasarımın üstüne ek bir zemin ya da çerçeve ekle.")
        s.addView(Ui.chips(this, Decor.BG_NAMES, read { Decor.mode(this) }) { putI("ubg", it); build() })
        val m = read { Decor.mode(this) }
        if (m == 1 || m == 2 || m == 5) {
            s.addView(Ui.label(this, "Zemin rengi"))
            s.addView(Ui.chips(this, Decor.SWATCH_NAMES, read { Decor.swatch(this) }) { putI("ubgColor", it); build() })
        }
        if (m > 0) s.addView(Ui.slider(this, { "Zemin doluluğu: %$it" }, read { Decor.op(this) }) { putI("ubgOp", it) })
        if (m > 0 || read { Decor.frame(this) } > 0) {
            s.addView(Ui.label(this, "Köşeler"))
            s.addView(Ui.chips(this, Decor.RADIUS_NAMES, read { Decor.radius(this) }) { putI("ubgRad", it); build() })
        }
        s.addView(Ui.label(this, "Çerçeve"))
        s.addView(Ui.chips(this, Decor.FRAME_NAMES, read { Decor.frame(this) }) { putI("ubgFrame", it); build() })
        if (read { Decor.frame(this) } > 0)
            s.addView(Ui.chips(this, Decor.FRAME_COLOR_NAMES, read { Decor.frameColor(this) }) { putI("ubgFrameCol", it); build() })
        root.addView(s)
    }

    private fun clockSection() {
        val s = Ui.section(this, "Saat")
        s.addView(Ui.label(this, "Kadran"))
        s.addView(Ui.chips(this, Combo.FACE_NAMES, read { Combo.face(this) }) { putI("face", it); build() })
        s.addView(Ui.label(this, "Dijital saat"))
        s.addView(Ui.chips(this, arrayOf("Üstte", "Altta", "Yok"), read { Combo.digital(this) }) { putI("digital", it); build() })
        root.addView(s)
    }

    private fun panelSection() {
        val s = Ui.section(this, "Yan panel", "Saatin yanındaki bilgi alanı.")
        s.addView(Ui.label(this, "Şekil"))
        s.addView(Ui.chips(this, arrayOf("Yuvarlak", "Kare", "Panelsiz"), read { Combo.panel(this) }) { putI("panel", it); build() })
        s.addView(Ui.label(this, "İçerik"))
        s.addView(Ui.chips(this, arrayOf("Sade", "Detaylı", "3 günlük", "Özel"), read { Combo.panelStyle(this) }) { putI("panelStyle", it); build() })
        if (read { Combo.panelStyle(this) } == 3) {
            s.addView(Ui.label(this, "Gösterilecekler"))
            Combo.SHOW_KEYS.forEachIndexed { i, k ->
                s.addView(Ui.check(this, Combo.SHOW_NAMES[i], read { Combo.show(this, k) }) { putB("show_$k", it) })
            }
            s.addView(Ui.label(this, "Sonraki günler"))
            s.addView(Ui.chips(this, Combo.DAY_NAMES, read { Combo.days(this) }) { putI("days", it); build() })
        }
        root.addView(s)
    }

    /** Kadran/panel rengi, saydamlık, çerçeve, yazı rengi */
    private fun lookSection(withRim: Boolean, lock: Boolean, withFont: Boolean = true) {
        val s = Ui.section(this, "Görünüm")
        s.addView(Ui.label(this, if (lock) "Arka plan rengi" else "Zemin rengi"))
        s.addView(Ui.chips(this, Combo.COLOR_NAMES, read { Combo.color(this) }) { putI("faceColor", it); build() })
        if (read { Combo.color(this) } == 2) {
            s.addView(Ui.label(this, "Renk paleti"))
            s.addView(Ui.chips(this, Palette.NAMES, read { Palette.choice(this) }) { putI("palette", it); build() })
        }
        s.addView(Ui.slider(this, { "Zemin doluluğu: %$it" + if (it == 0) " (tamamen şeffaf)" else "" }, read { Cfg.int(this, "opacity", if (lock) 0 else 100) }) { putI("opacity", it) })
        if (withRim) s.addView(Ui.check(this, if (lock) "Saatin etrafında çerçeve" else "İnce çerçeve", read { Combo.rim(this) }) { putB("rim", it) })
        s.addView(Ui.label(this, "Yazı rengi"))
        s.addView(Ui.chips(this, Style.TEXT_COLOR_NAMES, read { Style.textColorIdx(this) }) { putI("textColor", it); build() })
        root.addView(s)
        if (withFont) fontSection(false)
    }

    private fun fontSection(withFx: Boolean) {
        val s = Ui.section(this, "Yazı tipi")
        val g = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val cur = read { Style.font(this) }
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        Style.FONT_NAMES.forEachIndexed { i, n ->
            row.addView(LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(Ui.dp(this@EditActivity, 12), Ui.dp(this@EditActivity, 8), Ui.dp(this@EditActivity, 12), Ui.dp(this@EditActivity, 8))
                background = GradientDrawable().apply {
                    setColor(Color.rgb(46, 51, 62)); cornerRadius = Ui.dp(this@EditActivity, 14).toFloat()
                    if (i == cur) setStroke(Ui.dp(this@EditActivity, 2), Ui.ACCENT)
                }
                layoutParams = LinearLayout.LayoutParams(-2, -2).apply { rightMargin = Ui.dp(this@EditActivity, 8) }
                addView(TextView(this@EditActivity).apply { text = "12:45"; textSize = 26f; setTextColor(Ui.FG); typeface = Style.clockTypeface(this@EditActivity, i) })
                addView(TextView(this@EditActivity).apply { text = n; textSize = 12f; setTextColor(Ui.MUTED) })
                setOnClickListener { putI("font", i); build() }
            })
        }
        g.addView(android.widget.HorizontalScrollView(this).apply { addView(row) })
        s.addView(g)
        if (withFx) {
            s.addView(Ui.label(this, "Saat efekti"))
            s.addView(Ui.chips(this, Style.FX_NAMES, read { Style.clockFx(this) }) { putI("clockFx", it); build() })
        }
        root.addView(s)
    }

    private fun iconSection() {
        val s = Ui.section(this, "Hava ikonları")
        val cur = read { Style.iconSet(this) }
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        Style.ICON_SET_NAMES.forEachIndexed { i, n ->
            row.addView(LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = android.view.Gravity.CENTER_HORIZONTAL
                setPadding(Ui.dp(this@EditActivity, 8), Ui.dp(this@EditActivity, 8), Ui.dp(this@EditActivity, 8), Ui.dp(this@EditActivity, 8))
                background = GradientDrawable().apply {
                    setColor(Color.rgb(46, 51, 62)); cornerRadius = Ui.dp(this@EditActivity, 14).toFloat()
                    if (i == cur) setStroke(Ui.dp(this@EditActivity, 2), Ui.ACCENT)
                }
                layoutParams = LinearLayout.LayoutParams(-2, -2).apply { rightMargin = Ui.dp(this@EditActivity, 8) }
                val icons = LinearLayout(this@EditActivity).apply { orientation = LinearLayout.HORIZONTAL }
                for (k in intArrayOf(0, 2, 7, 9)) icons.addView(ImageView(this@EditActivity).apply {
                    setImageResource(Style.ICONS[i][k]); layoutParams = LinearLayout.LayoutParams(Ui.dp(this@EditActivity, 30), Ui.dp(this@EditActivity, 30))
                })
                addView(icons)
                addView(TextView(this@EditActivity).apply { text = n; textSize = 12f; setTextColor(Ui.MUTED) })
                setOnClickListener { putI("icons", i); build() }
            })
        }
        s.addView(android.widget.HorizontalScrollView(this).apply { addView(row) })
        s.addView(Ui.label(this, "Hareketli ikon"))
        s.addView(Ui.chips(this, arrayOf("Kapalı", "30 sn", "10 sn"), read { Style.anim(this) }) { putI("anim", it); build() })
        root.addView(s)
    }

    private fun bgSection() {
        val s = Ui.section(this, "Arka plan")
        s.addView(Ui.chips(this, arrayOf("Koyu", "Açık", "Şeffaf", "Renkli"), read { Style.bg(this) }) { putI("bg", it); build() })
        if (read { Style.bg(this) } != 2)
            s.addView(Ui.slider(this, { "Arka plan doluluğu: %$it" }, read { Style.opacity(this) }) { putI("opacity", it) })
        if (read { Style.bg(this) } == 3) {
            s.addView(Ui.label(this, "Renk paleti"))
            s.addView(Ui.chips(this, Palette.NAMES, read { Palette.choice(this) }) { putI("palette", it); build() })
        }
        s.addView(Ui.label(this, "Yazı rengi"))
        s.addView(Ui.chips(this, Style.TEXT_COLOR_NAMES, read { Style.textColorIdx(this) }) { putI("textColor", it); build() })
        root.addView(s)
    }

    private fun paletteSection() {
        val s = Ui.section(this, "Renkler ve kutular")
        if (layout() != R.layout.w_square4) {
            s.addView(Ui.label(this, "Kutu şekli"))
            s.addView(Ui.chips(this, arrayOf("Tasarımdaki gibi", "Yumuşak kare", "Keskin köşe", "Hap"), read { Cfg.int(this, "boxShape", 0) }) { putI("boxShape", it); build() })
        }
        s.addView(Ui.label(this, "Renk paleti"))
        s.addView(Ui.chips(this, Palette.NAMES, read { Palette.choice(this) }) { putI("palette", it); build() })
        s.addView(Ui.slider(this, { "Kart doluluğu: %$it" + if (it == 0) " (şeffaf)" else "" }, read { Style.opacity(this) }) { putI("opacity", it) })
        s.addView(Ui.label(this, "Yazı rengi"))
        s.addView(Ui.chips(this, Style.TEXT_COLOR_NAMES, read { Style.textColorIdx(this) }) { putI("textColor", it); build() })
        root.addView(s)
    }
}
