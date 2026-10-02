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

    private fun build() {
        val outer = ScrollView(this).apply { setBackgroundColor(Ui.BG) }
        root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(16), dp(36), dp(16), dp(32)) }
        outer.addView(root); setContentView(outer)
        val cur = current()
        root.addView(TextView(this).apply { text = cur?.label ?: "Widget"; textSize = 26f; setTextColor(Ui.FG) })
        root.addView(TextView(this).apply { text = "Buradaki ayarlar yalnızca bu widget'ı değiştirir. Değişiklikler önizlemede hemen görünür."; setTextColor(Ui.MUTED); textSize = 13f })
        preview = FrameLayout(this).apply {
            background = GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(Color.rgb(96, 70, 58), Color.rgb(40, 48, 62), Color.rgb(28, 32, 42))).apply { cornerRadius = dp(20).toFloat() }
            setPadding(dp(10), dp(14), dp(10), dp(14))
            layoutParams = LinearLayout.LayoutParams(-1, dp(hdp + 28)).apply { topMargin = dp(12) }
        }
        root.addView(preview); refreshPreview()

        val cat = category(layout())
        designSection(cur)
        contentSection(cat)
        backgroundSection(cat)
        edgesSection()
        textSection(cat)
        iconSection()
        actions()
    }

    /* ---------- 1. Tasarım ---------- */
    private fun designSection(cur: Entry?) {
        val s = section("Tasarım", "Widget'ın genel düzeni. Aynı boydaki başka bir tasarıma geçebilirsin; ayarların korunur.")
        val size = entry()?.size ?: "w"
        val r = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        Registry.ALL.forEachIndexed { idx, e ->
            if (e.size != size) return@forEachIndexed
            r.addView(LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL; setPadding(dp(5), dp(5), dp(5), dp(5))
                if (e == cur) background = GradientDrawable().apply { setStroke(dp(2), Ui.ACCENT); cornerRadius = dp(12).toFloat() }
                setOnClickListener { p.edit().putInt("ov_$wid", idx).apply(); sendBroadcast(Widgets.refreshIntent(this@EditActivity)); build() }
                addView(ImageView(this@EditActivity).apply { setImageResource(e.preview); adjustViewBounds = true; layoutParams = LinearLayout.LayoutParams(dp(if (size == "s") 96 else 180), -2) })
                addView(TextView(this@EditActivity).apply { text = e.label; setTextColor(if (e == cur) Ui.ACCENT else Ui.FG); textSize = 12f })
            })
        }
        s.addView(HorizontalScrollView(this).apply { addView(r) })
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

    /* ---------- 3. Arka plan ---------- */
    private val BG_KINDS = arrayOf("Tasarımın kendi zemini", "Şeffaf", "Düz renk", "Degrade", "Havaya göre", "Gökyüzü resmi", "Kağıt dokusu")

    private fun bgKind(cat: String): Int {
        val ubg = read { Decor.mode(this) }
        if (ubg > 0) return ubg + 1
        val clear = when (cat) {
            "standard" -> read { Style.bg(this) } == 2
            "origami" -> read { Origami.paper(this) } == 3
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
                if (read { Style.opacity(this) } == 0) i("opacity", 100)
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
                s.addView(Ui.slider(this, { "Doluluk: %$it  (0 = tamamen saydam)" }, read { Style.opacity(this) }) { putI("opacity", it) })
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
            "origami" -> {
                row(s, "Kağıt rengi", "\"Havaya göre\" seçilirse kağıt hava durumuyla renk değiştirir.")
                chips(s, arrayOf("Koyu kağıt", "Krem kağıt", "Havaya göre"), read { Origami.paper(this) }.coerceAtMost(2)) { putI("paperColor", it) }
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
        row(s, "Saat yazı tipi")
        val curF = read { Style.font(this) }
        val r = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        Style.FONT_NAMES.forEachIndexed { i, n ->
            r.addView(card(i == curF, { putI("font", i); build() }).apply {
                addView(TextView(this@EditActivity).apply { text = "12:45"; textSize = 24f; setTextColor(Ui.FG); typeface = Style.clockTypeface(this@EditActivity, i) })
                addView(TextView(this@EditActivity).apply { text = n; textSize = 11f; setTextColor(Ui.MUTED) })
            })
        }
        s.addView(HorizontalScrollView(this).apply { addView(r) })
        row(s, "Diğer yazılar", "Konum, tarih, hava durumu gibi yazıların yazı tipi.")
        val t = read { Style.textFontIdx(this) }
        val r2 = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        (listOf(-1) + Style.FONT_NAMES.indices).forEach { i ->
            r2.addView(card(i == t, { putI("textFont", i); build() }).apply {
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
        row(s, "Yazı rengi")
        chips(s, Style.TEXT_COLOR_NAMES, read { Style.textColorIdx(this) }) { putI("textColor", it) }
    }

    private fun card(selected: Boolean, onClick: () -> Unit) = LinearLayout(this).apply {
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
            r.addView(card(i == shape, { putI("iconShape", i); putI("iconStyle", style); build() }).apply {
                addView(samples(i, style)); addView(TextView(this@EditActivity).apply { text = n; textSize = 11f; setTextColor(Ui.MUTED) })
            })
        }
        s.addView(HorizontalScrollView(this).apply { addView(r) })
        if (shape < 5) {
            row(s, "Stil")
            val r2 = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            Style.ICON_STYLE_NAMES.forEachIndexed { i, n ->
                r2.addView(card(i == style, { putI("iconStyle", i); putI("iconShape", shape); build() }).apply {
                    addView(samples(shape, i)); addView(TextView(this@EditActivity).apply { text = n; textSize = 11f; setTextColor(Ui.MUTED) })
                })
            }
            s.addView(HorizontalScrollView(this).apply { addView(r2) })
        } else s.addView(TextView(this).apply { text = "Gerçekçi ikonların tek bir stili var."; setTextColor(Ui.MUTED); textSize = 12f })
    }

    /* ---------- 7. İşlemler ---------- */
    private val LOOK_KEYS = listOf("ubg", "ubgColor", "ubgOp", "ubgRad", "ubgFrame", "ubgFrameCol", "font", "textFont", "textColor", "clockFx",
        "iconShape", "iconStyle", "palette")

    private fun actions() {
        root.addView(Ui.card(this).apply {
            addView(TextView(this@EditActivity).apply {
                text = "⧉  Bu görünümü diğer tüm widget'lara uygula"; setTextColor(Ui.ACCENT); textSize = 15f
                setPadding(0, 0, 0, dp(4)); setOnClickListener { copyLook() }
            })
            addView(TextView(this@EditActivity).apply {
                text = "Yazı tipleri, saat efekti, yazı rengi, ikonlar, ek zemin ve çerçeve kopyalanır."; textSize = 12f; setTextColor(Ui.MUTED)
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
