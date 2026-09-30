package com.caner.sketchweather

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.ScrollView
import android.widget.TextView

/** Widget eklenirken stil seçtirir; her widget kendi stilini saklar. */
class ConfigActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val wid = intent?.extras?.getInt(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
            ?: AppWidgetManager.INVALID_APPWIDGET_ID
        val result = Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, wid)
        setResult(RESULT_CANCELED, result)
        if (wid == AppWidgetManager.INVALID_APPWIDGET_ID) { finish(); return }

        val info = AppWidgetManager.getInstance(this).getAppWidgetInfo(wid)
        val kind = Kind.ofClass(info?.provider?.className) ?: Kind.WIDE
        val p = WeatherRepo.prefs(this)
        val pad = (20 * resources.displayMetrics.density).toInt()
        val fg = Color.rgb(236, 240, 245)
        var picked = kind.style(this, wid)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad * 2, pad, pad)
        }
        root.addView(TextView(this).apply {
            text = "${kind.title} · stil seç"
            textSize = 24f
            setTextColor(fg)
        })
        val g = RadioGroup(this)
        kind.names.forEachIndexed { i, name ->
            g.addView(RadioButton(this).apply {
                id = View.generateViewId()
                text = name
                textSize = 17f
                setTextColor(fg)
                isChecked = i == picked
                setPadding(pad / 2, pad / 3, 0, pad / 3)
                setOnClickListener { picked = i }
            })
        }
        root.addView(g)
        root.addView(TextView(this).apply {
            text = "Yazı tipi, ikon seti, renk ve konumu uygulamanın ayarlarından değiştirebilirsin."
            setTextColor(Color.rgb(150, 160, 175))
            setPadding(0, pad / 2, 0, pad / 2)
        })
        root.addView(Button(this).apply {
            text = "Widget'ı ekle"
            setOnClickListener {
                p.edit().putInt("style_$wid", picked).apply()
                Widgets.renderAll(applicationContext, WeatherRepo.cached(applicationContext))
                sendBroadcast(Widgets.refreshIntent(this@ConfigActivity))
                setResult(RESULT_OK, result)
                finish()
            }
        })
        setContentView(ScrollView(this).apply {
            setBackgroundColor(Color.rgb(22, 25, 31))
            addView(root)
        })
    }
}
