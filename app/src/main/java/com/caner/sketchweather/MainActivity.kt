package com.caner.sketchweather

import android.Manifest
import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {
    private lateinit var root: LinearLayout
    private lateinit var info: TextView
    private lateinit var locStatus: TextView
    private val p get() = WeatherRepo.prefs(this)

    private fun refreshWidget() = sendBroadcast(Widgets.refreshIntent(this))

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        build()
    }

    override fun onResume() {
        super.onResume()
        if (::root.isInitialized) build()
    }

    private fun build() {
        val outer = ScrollView(this).apply { setBackgroundColor(Ui.BG) }
        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(Ui.dp(this@MainActivity, 16), Ui.dp(this@MainActivity, 40), Ui.dp(this@MainActivity, 16), Ui.dp(this@MainActivity, 32))
        }
        outer.addView(root)
        setContentView(outer)
        root.addView(TextView(this).apply { text = "Hava & Saat"; textSize = 30f; setTextColor(Ui.FG); typeface = Style.clockTypeface(this@MainActivity, 1) })
        root.addView(TextView(this).apply { text = "1 · Konumunu seç   2 · Widget ekle   3 · Widget'a dokunup kişiselleştir"; setTextColor(Ui.MUTED); textSize = 13f })
        locationCard()
        myWidgetsCard()
        addCard()
        defaultsCard()
    }

    // ---------- 1. Konum ----------
    private fun locationCard() {
        val s = Ui.section(this, "📍  Konum")
        info = TextView(this).apply { text = WeatherRepo.city(this@MainActivity); textSize = 20f; setTextColor(Ui.FG) }
        s.addView(info)
        locStatus = TextView(this).apply { setTextColor(Ui.MUTED); textSize = 13f }
        s.addView(locStatus)
        updateLocStatus()
        s.addView(Button(this).apply { text = "Anlık konumumu kullan"; setOnClickListener { askLocation() } })
        val input = EditText(this).apply { hint = "ya da şehir yaz (örn. Hamburg)"; setSingleLine(); setTextColor(Ui.FG); setHintTextColor(Ui.MUTED) }
        s.addView(input)
        s.addView(Button(this).apply {
            text = "Şehri kaydet"
            setOnClickListener {
                val q = input.text.toString().trim()
                if (q.isEmpty()) return@setOnClickListener
                locStatus.text = "Aranıyor…"
                Thread {
                    val place = try { WeatherRepo.geocode(q) } catch (e: Exception) { null }
                    runOnUiThread {
                        if (place == null) locStatus.text = "Şehir bulunamadı."
                        else {
                            p.edit().putBoolean("useLoc", false).putString("city", place.name)
                                .putFloat("lat", place.lat.toFloat()).putFloat("lon", place.lon.toFloat()).remove("cache").apply()
                            info.text = place.name; updateLocStatus(); refreshWidget()
                        }
                    }
                }.start()
            }
        })
        root.addView(s)
    }

    // ---------- 2. Ana ekrandaki widget'lar ----------
    private fun myWidgetsCard() {
        val s = Ui.section(this, "Widget'larım", "Düzenlemek için bir widget'a dokun.")
        val mgr = AppWidgetManager.getInstance(this)
        var count = 0
        for (e in Registry.ALL) {
            for (wid in mgr.getAppWidgetIds(ComponentName(this, e.cls))) {
                count++
                val l = Widgets.layoutFor(this, wid, e)
                val cur = Registry.ALL.firstOrNull { it.layout == l && it.size == e.size } ?: e
                s.addView(LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = android.view.Gravity.CENTER_VERTICAL
                    setPadding(0, Ui.dp(this@MainActivity, 8), 0, Ui.dp(this@MainActivity, 8))
                    addView(ImageView(this@MainActivity).apply {
                        setImageResource(cur.preview); adjustViewBounds = true
                        layoutParams = LinearLayout.LayoutParams(Ui.dp(this@MainActivity, if (e.size == "s") 64 else 120), -2)
                    })
                    addView(LinearLayout(this@MainActivity).apply {
                        orientation = LinearLayout.VERTICAL
                        setPadding(Ui.dp(this@MainActivity, 14), 0, 0, 0)
                        layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
                        addView(TextView(this@MainActivity).apply { text = cur.label; textSize = 16f; setTextColor(Ui.FG) })
                        addView(TextView(this@MainActivity).apply { text = Registry.SIZE_TITLES[e.size]; textSize = 12f; setTextColor(Ui.MUTED) })
                    })
                    addView(TextView(this@MainActivity).apply { text = "Düzenle ›"; setTextColor(Ui.ACCENT); textSize = 15f })
                    setOnClickListener {
                        startActivity(Intent(this@MainActivity, EditActivity::class.java).putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, wid))
                    }
                })
            }
        }
        if (count == 0) s.addView(TextView(this).apply { text = "Henüz widget eklemedin. Aşağıdan bir tasarım seç."; setTextColor(Ui.MUTED) })
        root.addView(s)
    }

    // ---------- 3. Yeni widget ekle ----------
    private fun addCard() {
        val s = Ui.section(this, "Yeni widget ekle", "Önizlemeye dokun; ana ekrana eklensin. Eklemeden sonra buradan düzenleyebilirsin.")
        for ((size, title) in Registry.SIZE_TITLES) {
            s.addView(Ui.label(this, title))
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            Registry.ALL.filter { it.size == size }.forEach { e ->
                row.addView(LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(Ui.dp(this@MainActivity, 4), Ui.dp(this@MainActivity, 4), Ui.dp(this@MainActivity, 8), Ui.dp(this@MainActivity, 4))
                    addView(ImageView(this@MainActivity).apply {
                        setImageResource(e.preview); adjustViewBounds = true
                        layoutParams = LinearLayout.LayoutParams(Ui.dp(this@MainActivity, if (size == "s") 110 else 210), -2)
                    })
                    addView(TextView(this@MainActivity).apply { text = e.label; textSize = 12f; setTextColor(Ui.FG) })
                    setOnClickListener { pin(e) }
                })
            }
            s.addView(android.widget.HorizontalScrollView(this).apply { addView(row) })
        }
        root.addView(s)
    }

    private fun pin(e: Entry) {
        val mgr = AppWidgetManager.getInstance(this)
        val ok = Build.VERSION.SDK_INT >= 26 && mgr.isRequestPinAppWidgetSupported &&
            mgr.requestPinAppWidget(ComponentName(this, e.cls), null, null)
        if (!ok) Toast.makeText(this, "Ana ekranda uzun bas → Widget'lar → Hava & Saat → ${e.label}", Toast.LENGTH_LONG).show()
    }

    // ---------- 4. Genel varsayılanlar ----------
    private fun defaultsCard() {
        val s = Ui.section(this, "Genel varsayılanlar", "Kendi ayarı olmayan widget'lar bunları kullanır.")
        s.addView(Ui.label(this, "Yazı tipi"))
        s.addView(Ui.chips(this, Style.FONT_NAMES, p.getInt("font", 1)) { p.edit().putInt("font", it).apply(); refreshWidget(); build() })
        s.addView(Ui.label(this, "Hava ikonları"))
        s.addView(Ui.chips(this, Style.ICON_SET_NAMES, p.getInt("icons", 3)) { p.edit().putInt("icons", it).apply(); refreshWidget(); build() })
        s.addView(Ui.label(this, "Renk paleti"))
        s.addView(Ui.chips(this, Palette.NAMES, p.getInt("palette", 0)) { p.edit().putInt("palette", it).apply(); refreshWidget(); build() })
        s.addView(Ui.label(this, "Çift saat · ev saati"))
        s.addView(Ui.chips(this, Style.HOME_NAMES, p.getInt("home", 0)) { p.edit().putInt("home", it).apply(); refreshWidget(); build() })
        s.addView(TextView(this).apply {
            text = "\nİpucu: Saatin dakika dakika doğru kalması için Ayarlar → Pil → Uygulama başlatma'dan Hava & Saat'e arka plan izni ver."
            setTextColor(Ui.MUTED); textSize = 12f
        })
        root.addView(s)
    }

    // ---------- konum izinleri ----------
    private fun updateLocStatus() {
        locStatus.text = if (WeatherRepo.useLocation(this)) {
            if (Build.VERSION.SDK_INT >= 29 &&
                checkSelfPermission(Manifest.permission.ACCESS_BACKGROUND_LOCATION) != PackageManager.PERMISSION_GRANTED)
                "Anlık konum açık. Arka planda da güncellenmesi için konum iznini \"Her zaman izin ver\" yap."
            else "Anlık konum açık; bulunduğun yere göre güncellenir."
        } else "Seçili şehir kullanılıyor."
    }

    private fun askLocation() {
        requestPermissions(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION), 1)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 1) {
            if (!WeatherRepo.hasLocationPermission(this)) { locStatus.text = "Konum izni verilmedi."; return }
            p.edit().putBoolean("useLoc", true).putFloat("nameLat", 999f).apply()
            locStatus.text = "Konum alınıyor…"
            val lm = getSystemService(LOCATION_SERVICE) as LocationManager
            val done: (Location?) -> Unit = { loc ->
                Thread {
                    val l = loc ?: WeatherRepo.lastKnown(this)
                    if (l != null) WeatherRepo.saveLocation(this, l)
                    runOnUiThread {
                        info.text = WeatherRepo.city(this)
                        updateLocStatus()
                        if (l == null) locStatus.text = "Konum bulunamadı; konum servisinin açık olduğundan emin ol."
                        refreshWidget()
                        if (Build.VERSION.SDK_INT >= 29 &&
                            checkSelfPermission(Manifest.permission.ACCESS_BACKGROUND_LOCATION) != PackageManager.PERMISSION_GRANTED)
                            requestPermissions(arrayOf(Manifest.permission.ACCESS_BACKGROUND_LOCATION), 2)
                    }
                }.start()
            }
            try {
                if (Build.VERSION.SDK_INT >= 30) {
                    val prov = if (lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) LocationManager.NETWORK_PROVIDER else LocationManager.GPS_PROVIDER
                    lm.getCurrentLocation(prov, null, mainExecutor) { done(it) }
                } else done(null)
            } catch (e: SecurityException) { done(null) }
        } else if (requestCode == 2) updateLocStatus()
    }
}
