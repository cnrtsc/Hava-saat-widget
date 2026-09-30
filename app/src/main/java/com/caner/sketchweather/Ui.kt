package com.caner.sketchweather

import android.app.Activity
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.View
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.SeekBar
import android.widget.TextView

/** Ayar ekranlarında ortak, sade bileşenler. */
object Ui {
    val BG = Color.rgb(20, 23, 29)
    val CARD = Color.rgb(32, 36, 45)
    val FG = Color.rgb(236, 240, 245)
    val MUTED = Color.rgb(150, 160, 175)
    val ACCENT = Color.rgb(255, 176, 70)

    fun dp(a: Activity, v: Int) = (v * a.resources.displayMetrics.density).toInt()

    fun card(a: Activity): LinearLayout = LinearLayout(a).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(a, 16), dp(a, 14), dp(a, 16), dp(a, 14))
        background = GradientDrawable().apply { setColor(CARD); cornerRadius = dp(a, 18).toFloat() }
        layoutParams = LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(a, 12) }
    }

    fun section(a: Activity, title: String, hint: String? = null): LinearLayout {
        val c = card(a)
        c.addView(TextView(a).apply { text = title; textSize = 17f; setTextColor(FG) })
        if (hint != null) c.addView(TextView(a).apply { text = hint; textSize = 13f; setTextColor(MUTED); setPadding(0, dp(a, 2), 0, dp(a, 4)) })
        return c
    }

    fun label(a: Activity, t: String) = TextView(a).apply {
        text = t; textSize = 13f; setTextColor(MUTED); setPadding(0, dp(a, 10), 0, dp(a, 2))
    }

    fun radios(a: Activity, names: Array<String>, checked: Int, onPick: (Int) -> Unit): RadioGroup {
        val g = RadioGroup(a)
        names.forEachIndexed { i, n ->
            g.addView(RadioButton(a).apply {
                id = View.generateViewId(); text = n; textSize = 15f; setTextColor(FG)
                isChecked = i == checked
                setOnClickListener { onPick(i) }
            })
        }
        return g
    }

    /** Yatay, kaydırılabilir "çip" seçici (az yer kaplar). */
    fun chips(a: Activity, names: Array<String>, checked: Int, onPick: (Int) -> Unit): View {
        val row = LinearLayout(a).apply { orientation = LinearLayout.HORIZONTAL }
        names.forEachIndexed { i, n ->
            row.addView(TextView(a).apply {
                text = n; textSize = 14f
                setTextColor(if (i == checked) Color.rgb(30, 22, 10) else FG)
                setPadding(dp(a, 14), dp(a, 8), dp(a, 14), dp(a, 8))
                background = GradientDrawable().apply {
                    setColor(if (i == checked) ACCENT else Color.rgb(46, 51, 62)); cornerRadius = dp(a, 20).toFloat()
                }
                layoutParams = LinearLayout.LayoutParams(-2, -2).apply { rightMargin = dp(a, 8) }
                setOnClickListener { onPick(i) }
            })
        }
        return android.widget.HorizontalScrollView(a).apply {
            isHorizontalScrollBarEnabled = false
            addView(row)
            setPadding(0, dp(a, 4), 0, dp(a, 4))
        }
    }

    fun check(a: Activity, t: String, v: Boolean, onChange: (Boolean) -> Unit) = CheckBox(a).apply {
        text = t; setTextColor(FG); isChecked = v
        setOnCheckedChangeListener { _, b -> onChange(b) }
    }

    fun slider(a: Activity, title: (Int) -> String, v: Int, onDone: (Int) -> Unit): LinearLayout {
        val box = LinearLayout(a).apply { orientation = LinearLayout.VERTICAL }
        val lab = label(a, title(v))
        box.addView(lab)
        box.addView(SeekBar(a).apply {
            max = 100; progress = v
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(s: SeekBar?, p: Int, u: Boolean) { lab.text = title(p) }
                override fun onStartTrackingTouch(s: SeekBar?) {}
                override fun onStopTrackingTouch(s: SeekBar?) { onDone(s?.progress ?: v) }
            })
        })
        return box
    }
}
