package com.caner.sketchweather

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.ScrollView
import android.widget.TextView

class MainActivity : Activity() {

    private lateinit var info: TextView
    private lateinit var locStatus: TextView
    private lateinit var myWidgets: LinearLayout
    private var padPx = 0

    private val fg = Color.rgb(236, 240, 245)
    private val muted = Color.rgb(150, 160, 175)

    private fun refreshWidget() {
        sendBroadcast(Widgets.refreshIntent(this))
    }

    private fun header(text: String, pad: Int): TextView = TextView(this).apply {
        this.text = text
        textSize = 13f
        setTextColor(muted)
        letterSpacing = 0.08f
        setPadding(0, pad, 0, pad / 3)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val p = WeatherRepo.prefs(this)
        val dp = resources.displayMetrics.density
        val pad = (20 * dp).toInt()

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad * 3, pad, pad * 2)
        }
        val scroll = ScrollView(this).apply {
            setBackgroundColor(Color.rgb(22, 25, 31))
            addView(root)
        }

        root.addView(TextView(this).apply {
            text = "Hava & Saat"
            textSize = 30f
            setTextColor(fg)
            typeface = Style.clockTypeface(this@MainActivity, 1)
        })

        // Galeri: tüm stiller önizlemeleriyle, dokununca ana ekrana eklenir
        root.addView(header("WİDGET GALERİSİ · ekle", pad))
        root.addView(TextView(this).apply {
            text = "Önizlemeye dokun; widget doğrudan ana ekrana eklensin."
            setTextColor(muted)
        })
        for ((size, title) in Registry.SIZE_TITLES) {
            root.addView(TextView(this).apply {
                text = title
                textSize = 15f
                setTextColor(fg)
                setPadding(0, pad / 2, 0, pad / 4)
            })
            root.addView(gallery(size, null, pad))
        }

        // Ekli widget'lar: stil değiştirme
        myWidgets = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(header("WİDGET'LARIM · stil değiştir", pad))
        root.addView(myWidgets)
        fillMyWidgets(pad)

        // Analog saat + bilgi ayarları
        root.addView(header("ANALOG SAAT + BİLGİ", pad))
        root.addView(sub("Kadran"))
        root.addView(radioList(Combo.FACE_NAMES, Combo.face(this), 15f) { i -> p.edit().putInt("face", i).apply() })
        root.addView(sub("Dijital saat"))
        root.addView(radioList(Combo.DIGITAL_NAMES, Combo.digital(this), 15f) { i -> p.edit().putInt("digital", i).apply() })
        root.addView(sub("Kadran ve panel rengi"))
        root.addView(radioList(Combo.COLOR_NAMES, Combo.color(this), 15f) { i -> p.edit().putInt("faceColor", i).apply() })
        root.addView(sub("Yan panel"))
        root.addView(radioList(Combo.PANEL_NAMES, Combo.panel(this), 15f) { i -> p.edit().putInt("panel", i).apply() })
        val opLabel = sub("Arka plan saydamlığı: %${Combo.opacity(this)} dolu")
        root.addView(opLabel)
        root.addView(android.widget.SeekBar(this).apply {
            max = 100
            progress = Combo.opacity(this@MainActivity)
            setOnSeekBarChangeListener(object : android.widget.SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(sb: android.widget.SeekBar?, v: Int, fromUser: Boolean) {
                    opLabel.text = "Arka plan saydamlığı: %$v dolu" + if (v == 0) " (tamamen şeffaf)" else ""
                }
                override fun onStartTrackingTouch(sb: android.widget.SeekBar?) {}
                override fun onStopTrackingTouch(sb: android.widget.SeekBar?) {
                    p.edit().putInt("opacity", sb?.progress ?: 100).apply()
                    refreshWidget()
                }
            })
        })
        root.addView(android.widget.CheckBox(this).apply {
            text = "İnce çerçeve"
            setTextColor(fg)
            isChecked = Combo.rim(this@MainActivity)
            setOnCheckedChangeListener { _, v -> p.edit().putBoolean("rim", v).apply(); refreshWidget() }
        })
        root.addView(sub("Panelde gösterilecekler"))
        Combo.SHOW_KEYS.forEachIndexed { i, key ->
            root.addView(android.widget.CheckBox(this).apply {
                text = Combo.SHOW_NAMES[i]
                setTextColor(fg)
                isChecked = Combo.show(this@MainActivity, key)
                setOnCheckedChangeListener { _, v -> p.edit().putBoolean("show_$key", v).apply(); refreshWidget() }
            })
        }
        root.addView(sub("Sonraki günler"))
        root.addView(radioList(Combo.DAY_NAMES, Combo.days(this), 15f) { i -> p.edit().putInt("days", i).apply() })

        // Konum
        root.addView(header("KONUM", pad))
        info = TextView(this).apply {
            text = WeatherRepo.city(this@MainActivity)
            textSize = 18f
            setTextColor(fg)
        }
        root.addView(info)
        val locBtn = Button(this).apply { text = "📍  Anlık konumumu kullan" }
        locStatus = TextView(this).apply { setTextColor(muted) }
        root.addView(locBtn)
        root.addView(locStatus)
        updateLocStatus()
        locBtn.setOnClickListener { askLocation() }
        root.addView(TextView(this).apply {
            text = "ya da şehir seç:"
            setTextColor(muted)
            setPadding(0, pad / 2, 0, 0)
        })
        val input = EditText(this).apply {
            hint = "Şehir ara (örn. Hamburg)"
            setSingleLine()
            setTextColor(fg)
            setHintTextColor(muted)
        }
        val save = Button(this).apply { text = "Şehri kaydet" }
        val status = TextView(this).apply { setTextColor(muted) }
        root.addView(input)
        root.addView(save)
        root.addView(status)
        save.setOnClickListener {
            val q = input.text.toString().trim()
            if (q.isEmpty()) return@setOnClickListener
            status.text = "Aranıyor…"
            Thread {
                val place = try { WeatherRepo.geocode(q) } catch (e: Exception) { null }
                runOnUiThread {
                    if (place == null) {
                        status.text = "Şehir bulunamadı. Yazımı ve internet bağlantısını kontrol et."
                    } else {
                        p.edit()
                            .putBoolean("useLoc", false)
                            .putString("city", place.name)
                            .putFloat("lat", place.lat.toFloat())
                            .putFloat("lon", place.lon.toFloat())
                            .remove("cache")
                            .apply()
                        info.text = place.name
                        status.text = "Kaydedildi."
                        updateLocStatus()
                        refreshWidget()
                    }
                }
            }.start()
        }

        // Yazı tipi
        root.addView(header("SAAT YAZI TİPİ", pad))
        val fonts = RadioGroup(this)
        Style.FONT_NAMES.forEachIndexed { i, name ->
            fonts.addView(RadioButton(this).apply {
                id = View.generateViewId()
                text = "12:45   $name"
                textSize = 22f
                setTextColor(fg)
                typeface = Style.clockTypeface(this@MainActivity, i)
                isChecked = i == Style.font(this@MainActivity)
                setPadding(pad / 2, pad / 4, 0, pad / 4)
                setOnClickListener {
                    p.edit().putInt("font", i).apply()
                    refreshWidget()
                }
            })
        }
        root.addView(fonts)

        // Çift saat için ev saati
        root.addView(header("ÇİFT SAAT · EV SAATİ", pad))
        root.addView(radioList(Style.HOME_NAMES, Style.home(this), 16f) { i -> p.edit().putInt("home", i).apply() })

        // Renk paleti
        root.addView(header("RENK PALETİ", pad))
        root.addView(radioList(Palette.NAMES, Palette.choice(this), 16f) { i -> p.edit().putInt("palette", i).apply() })

        // Hareketli ikon
        root.addView(header("HAREKETLİ İKON", pad))
        root.addView(radioList(Style.ANIM_NAMES, Style.anim(this), 16f) { i -> p.edit().putInt("anim", i).apply() })

        // İkon seti
        root.addView(header("HAVA İKONLARI", pad))
        val sets = RadioGroup(this)
        val iconPx = (40 * dp).toInt()
        Style.ICON_SET_NAMES.forEachIndexed { i, name ->
            sets.addView(RadioButton(this).apply {
                id = View.generateViewId()
                text = name
                textSize = 17f
                setTextColor(fg)
                isChecked = i == Style.iconSet(this@MainActivity)
                setPadding(pad / 2, pad / 4, 0, pad / 4)
                val sample = intArrayOf(0, 2, 7, 9).map { k ->
                    resources.getDrawable(Style.ICONS[i][k], theme).apply { setBounds(0, 0, iconPx, iconPx) }
                }
                val strip = android.graphics.drawable.LayerDrawable(sample.toTypedArray())
                sample.forEachIndexed { k, _ -> strip.setLayerInset(k, k * iconPx, 0, (3 - k) * iconPx, 0) }
                strip.setBounds(0, 0, iconPx * 4, iconPx)
                setCompoundDrawables(null, null, strip, null)
                compoundDrawablePadding = pad
                setOnClickListener {
                    p.edit().putInt("icons", i).apply()
                    refreshWidget()
                }
            })
        }
        root.addView(sets)

        // Arka plan
        root.addView(header("ARKA PLAN", pad))
        val bgs = RadioGroup(this)
        Style.BG_NAMES.forEachIndexed { i, name ->
            bgs.addView(RadioButton(this).apply {
                id = View.generateViewId()
                text = name
                textSize = 17f
                setTextColor(fg)
                isChecked = i == Style.bg(this@MainActivity)
                setOnClickListener {
                    p.edit().putInt("bg", i).apply()
                    refreshWidget()
                }
            })
        }
        root.addView(bgs)

        root.addView(TextView(this).apply {
            text = "\n21 farklı widget var; her biri listede kendi önizlemesiyle çıkar. Eklemek için ana ekranda boş bir alana uzun bas → Widget'lar → Hava & Saat.\n\n" +
                "Saate dokun: alarmlar açılır.\nHava durumuna dokun: yenilenir.\nAlt satıra dokun: bu ayarlar açılır."
            setTextColor(muted)
            typeface = Typeface.DEFAULT
        })

        setContentView(scroll)
    }

    private fun updateLocStatus() {
        locStatus.text = if (WeatherRepo.useLocation(this)) {
            if (Build.VERSION.SDK_INT >= 29 &&
                checkSelfPermission(Manifest.permission.ACCESS_BACKGROUND_LOCATION) != PackageManager.PERMISSION_GRANTED)
                "Anlık konum açık. Widget arka planda da güncellensin diye konum iznini \"Her zaman izin ver\" yapman önerilir."
            else "Anlık konum açık; widget bulunduğun yere göre güncellenir."
        } else "Şu an seçili şehir kullanılıyor."
    }

    private fun askLocation() {
        requestPermissions(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION), 1)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 1) {
            if (!WeatherRepo.hasLocationPermission(this)) {
                locStatus.text = "Konum izni verilmedi."
                return
            }
            WeatherRepo.prefs(this).edit().putBoolean("useLoc", true).putFloat("nameLat", 999f).apply()
            locStatus.text = "Konum alınıyor…"
            val lm = getSystemService(LOCATION_SERVICE) as LocationManager
            val done: (Location?) -> Unit = { loc ->
                Thread {
                    val l = loc ?: WeatherRepo.lastKnown(this)
                    if (l != null) WeatherRepo.saveLocation(this, l)
                    runOnUiThread {
                        info.text = WeatherRepo.city(this)
                        updateLocStatus()
                        if (l == null) locStatus.text = "Konum bulunamadı; telefonun konum servisinin açık olduğundan emin ol."
                        refreshWidget()
                        if (Build.VERSION.SDK_INT >= 29 &&
                            checkSelfPermission(Manifest.permission.ACCESS_BACKGROUND_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                            requestPermissions(arrayOf(Manifest.permission.ACCESS_BACKGROUND_LOCATION), 2)
                        }
                    }
                }.start()
            }
            try {
                if (Build.VERSION.SDK_INT >= 30) {
                    val prov = if (lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) LocationManager.NETWORK_PROVIDER else LocationManager.GPS_PROVIDER
                    lm.getCurrentLocation(prov, null, mainExecutor) { done(it) }
                } else done(null)
            } catch (e: SecurityException) {
                done(null)
            }
        } else if (requestCode == 2) {
            updateLocStatus()
        }
    }

    private fun radioList(names: Array<String>, checked: Int, size: Float, onPick: (Int) -> Unit): RadioGroup {
        val g = RadioGroup(this)
        names.forEachIndexed { i, name ->
            g.addView(RadioButton(this).apply {
                id = View.generateViewId()
                text = name
                textSize = size
                setTextColor(fg)
                isChecked = i == checked
                setOnClickListener {
                    onPick(i)
                    refreshWidget()
                }
            })
        }
        return g
    }


    override fun onResume() {
        super.onResume()
        if (::myWidgets.isInitialized) fillMyWidgets(padPx)
    }

    /** Aynı boydaki stillerin yatay önizleme şeridi. wid null ise ekler, değilse o widget'ın stilini değiştirir. */
    private fun gallery(size: String, wid: Int?, pad: Int): android.widget.HorizontalScrollView {
        val dp = resources.displayMetrics.density
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val p = WeatherRepo.prefs(this)
        val current = if (wid != null) {
            val own = Registry.ofClass(android.appwidget.AppWidgetManager.getInstance(this).getAppWidgetInfo(wid)?.provider?.className)
            Registry.ALL.getOrNull(p.getInt("ov_$wid", -1))?.takeIf { it.size == size } ?: own
        } else null
        Registry.ALL.forEachIndexed { idx, e ->
            if (e.size != size) return@forEachIndexed
            val w = ((if (size == "s") 120 else 230) * dp).toInt()
            val cell = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding((6 * dp).toInt(), (6 * dp).toInt(), (6 * dp).toInt(), (6 * dp).toInt())
                if (e == current) setBackgroundColor(Color.rgb(52, 60, 78))
            }
            cell.addView(android.widget.ImageView(this).apply {
                setImageResource(e.preview)
                adjustViewBounds = true
                layoutParams = LinearLayout.LayoutParams(w, LinearLayout.LayoutParams.WRAP_CONTENT)
            })
            cell.addView(TextView(this).apply {
                text = if (e == current) "✓ ${e.label}" else e.label
                setTextColor(fg)
                textSize = 13f
                setPadding(0, (4 * dp).toInt(), 0, 0)
            })
            cell.setOnClickListener {
                if (wid == null) pin(e) else {
                    p.edit().putInt("ov_$wid", idx).apply()
                    refreshWidget()
                    fillMyWidgets(pad)
                }
            }
            row.addView(cell)
        }
        return android.widget.HorizontalScrollView(this).apply { addView(row) }
    }

    private fun pin(e: Entry) {
        val mgr = android.appwidget.AppWidgetManager.getInstance(this)
        val ok = Build.VERSION.SDK_INT >= 26 && mgr.isRequestPinAppWidgetSupported &&
            mgr.requestPinAppWidget(android.content.ComponentName(this, e.cls), null, null)
        if (!ok) android.widget.Toast.makeText(
            this, "Telefon doğrudan eklemeyi desteklemiyor. Ana ekranda uzun bas → Widget'lar → Hava & Saat → ${e.label}",
            android.widget.Toast.LENGTH_LONG
        ).show()
    }

    private fun fillMyWidgets(pad: Int) {
        padPx = pad
        myWidgets.removeAllViews()
        val mgr = android.appwidget.AppWidgetManager.getInstance(this)
        var count = 0
        for (e in Registry.ALL) {
            for (wid in mgr.getAppWidgetIds(android.content.ComponentName(this, e.cls))) {
                count++
                myWidgets.addView(TextView(this).apply {
                    text = "${Registry.SIZE_TITLES[e.size]} widget #$count"
                    textSize = 15f
                    setTextColor(fg)
                    setPadding(0, pad / 2, 0, pad / 4)
                })
                myWidgets.addView(gallery(e.size, wid, pad))
            }
        }
        if (count == 0) myWidgets.addView(TextView(this).apply {
            text = "Henüz ana ekranda widget yok. Yukarıdaki galeriden ekleyebilirsin."
            setTextColor(muted)
        })
    }

    private fun sub(t: String): TextView = TextView(this).apply {
        text = t
        textSize = 14f
        setTextColor(muted)
        setPadding(0, (12 * resources.displayMetrics.density).toInt(), 0, 0)
    }
}
