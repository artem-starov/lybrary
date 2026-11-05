package com.nmt.navchannia

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.net.Uri
import android.view.MotionEvent
import android.view.View
import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import org.json.JSONArray
import org.json.JSONObject

class MainActivity : Activity() {
    private val BG = 0xFF0F1218.toInt()
    private val CARD = 0xFF181D27.toInt()
    private val TX = 0xFFEEF1F7.toInt()
    private val MU = 0xFF98A2B3.toInt()
    private val AC = 0xFF4F7CF5.toInt()
    private val OK = 0xFF16A34A.toInt()
    private val BAD = 0xFFDC2626.toInt()
    private val BD = 0xFF2A3140.toInt()

    private lateinit var root: ScrollView
    private lateinit var box: LinearLayout
    private lateinit var data: JSONArray
    private var back: (() -> Unit)? = null

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    override fun onCreate(s: Bundle?) {
        super.onCreate(s)
        data = JSONArray(assets.open("data.json").bufferedReader().use { it.readText() })
        root = ScrollView(this)
        root.setBackgroundColor(BG)
        root.fitsSystemWindows = true
        box = LinearLayout(this)
        box.orientation = LinearLayout.VERTICAL
        box.setPadding(dp(16), dp(16), dp(16), dp(24))
        root.addView(box)
        setContentView(root)
        home()
    }

    override fun onBackPressed() {
        val b = back
        if (b != null) b() else super.onBackPressed()
    }

    private fun lp(): LinearLayout.LayoutParams {
        val p = LinearLayout.LayoutParams(-1, -2)
        p.topMargin = dp(8)
        return p
    }

    private fun tv(text: String, size: Float = 16f, bold: Boolean = false, color: Int = TX): TextView {
        val t = TextView(this)
        t.text = text
        t.textSize = size
        t.setTextColor(color)
        if (bold) t.setTypeface(null, Typeface.BOLD)
        return t
    }

    private fun bg(fill: Int, stroke: Int = Color.TRANSPARENT): GradientDrawable {
        val g = GradientDrawable()
        g.setColor(fill)
        g.cornerRadius = dp(12).toFloat()
        g.setStroke(dp(1), stroke)
        return g
    }

    private fun btn(text: String, primary: Boolean = true, onClick: () -> Unit): Button {
        val b = Button(this)
        b.text = text
        b.isAllCaps = false
        b.textSize = 16f
        b.setTextColor(if (primary) Color.WHITE else TX)
        b.background = bg(if (primary) AC else CARD, BD)
        b.layoutParams = lp()
        b.setOnClickListener { onClick() }
        pressFx(b)
        return b
    }

    private fun card(): LinearLayout {
        val c = LinearLayout(this)
        c.orientation = LinearLayout.VERTICAL
        c.setPadding(dp(14), dp(12), dp(14), dp(12))
        c.background = bg(CARD, BD)
        c.layoutParams = lp()
        return c
    }

    private fun menu(title: String, sub: String, onClick: (() -> Unit)?): LinearLayout {
        val c = card()
        c.addView(tv(title, 17f, true))
        c.addView(tv(sub, 14f, false, MU))
        if (onClick != null) {
            c.setOnClickListener { onClick() }
            pressFx(c)
        } else {
            c.alpha = 0.5f
            c.tag = "dim"
        }
        return c
    }

    private fun pressFx(v: View) {
        v.setOnTouchListener { x, e ->
            when (e.action) {
                MotionEvent.ACTION_DOWN -> x.animate().scaleX(0.96f).scaleY(0.96f).setDuration(90).start()
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> x.animate().scaleX(1f).scaleY(1f).setDuration(120).start()
            }
            false
        }
    }

    private fun show(b: (() -> Unit)?, build: LinearLayout.() -> Unit) {
        back = b
        box.removeAllViews()
        box.build()
        root.scrollTo(0, 0)
        for (i in 0 until box.childCount) {
            val v = box.getChildAt(i)
            val target = if (v.tag == "dim") 0.5f else 1f
            v.alpha = 0f
            v.translationY = dp(14).toFloat()
            v.animate().alpha(target).translationY(0f).setDuration(260).setStartDelay(minOf(i, 10) * 40L).start()
        }
    }

    private fun prefs() = getSharedPreferences("nmt", 0)

    private fun pct(k: String): String {
        val v = prefs().getFloat(k, -1f)
        return if (v < 0) "—" else "${(v * 100).toInt()}%"
    }

    private fun toList(a: JSONArray): List<JSONObject> {
        val l = ArrayList<JSONObject>()
        for (i in 0 until a.length()) l.add(a.getJSONObject(i))
        return l
    }

    private fun home() {
        show(null) {
            addView(tv("Навчання до НМТ", 24f, true))
            addView(tv("Обери, що вчити", 14f, false, MU))
            addView(menu("📜 Історія України", "5–11 класи: конспект, діячі, тести") { hist() })
            for (s in listOf("🇺🇦 Українська мова", "➗ Математика", "🇬🇧 Англійська")) addView(menu(s, "скоро", null))
        }
    }

    private fun hist() {
        show({ home() }) {
            addView(tv("Історія України", 24f, true))
            addView(site())
            val d = prefs().getFloat("d", -1f)
            val sub = if (d < 0) "30 питань з усіх класів" else "Останній результат ≈ ${(100 + d * 100).toInt()} балів"
            addView(menu("Діагностичний тест", sub) { diag() })
            addView(menu("📌 Усі пам’ятки", "ключові дати всіх класів") { memos() })
            for (i in 0 until data.length()) {
                val g = data.getJSONObject(i)
                addView(menu("${g.getInt("n")} клас — ${pct("g" + g.getInt("n"))}", g.getString("t")) { grade(i) })
            }
        }
    }

    private fun grade(i: Int) {
        val g = data.getJSONObject(i)
        show({ hist() }) {
            addView(tv("${g.getInt("n")} клас: ${g.getString("t")}", 22f, true))
            addView(memoCard(g))
            val c = card()
            val s = g.getJSONArray("s")
            for (j in 0 until s.length()) {
                val t = tv("• " + s.getString(j), 16f)
                t.setPadding(0, dp(4), 0, dp(4))
                c.addView(t)
            }
            addView(c)
            val tm = g.optJSONArray("terms")
            if (tm != null && tm.length() > 0) {
                val h2 = tv("Поняття і терміни", 20f, true)
                h2.setPadding(0, dp(12), 0, 0)
                addView(h2)
                val tc = card()
                for (j in 0 until tm.length()) {
                    val tj = tm.getJSONArray(j)
                    val x = tv(tj.getString(0) + " — " + tj.getString(1), 16f)
                    x.setPadding(0, dp(4), 0, dp(4))
                    tc.addView(x)
                }
                addView(tc)
            }
            val mp = g.optJSONArray("maps")
            if (mp != null && mp.length() > 0) {
                val h3 = tv("Карти й зображення", 20f, true)
                h3.setPadding(0, dp(12), 0, 0)
                addView(h3)
                for (j in 0 until mp.length()) {
                    val mj = mp.getJSONArray(j)
                    val u = "https://www.google.com/search?tbm=isch&q=" + Uri.encode(mj.getString(1))
                    addView(btn("🗺 " + mj.getString(0), false) {
                        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(u)))
                    })
                }
            }
            val p = g.getJSONArray("p")
            if (p.length() > 0) {
                val h = tv("Діячі епохи", 20f, true)
                h.setPadding(0, dp(12), 0, 0)
                addView(h)
            }
            for (o in toList(p)) {
                val pc = card()
                pc.addView(tv(o.getString("name"), 17f, true))
                pc.addView(tv(o.getString("years") + " · " + o.getString("role"), 14f, false, MU))
                val d = tv(o.getString("desc"), 16f)
                d.setPadding(0, dp(6), 0, 0)
                pc.addView(d)
                val url = "https://uk.wikipedia.org/w/index.php?search=" + Uri.encode(o.getString("name"))
                pc.addView(btn("Портрет і біографія (Вікіпедія)", false) {
                    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                })
                addView(pc)
            }
            addView(btn("Здати тест") {
                quiz(toList(g.getJSONArray("q")), "g" + g.getInt("n"), "${g.getInt("n")} клас") { grade(i) }
            })
        }
    }

    private fun open(u: String) {
        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(u)))
    }

    private fun site(): LinearLayout {
        val c = card()
        c.addView(tv("ℹ️ Тести в цьому додатку — лише для закріплення матеріалу. Для пробного проходження НМТ користуйся сайтом zno.osvita.ua:", 14f, false, MU))
        c.addView(btn("Тренувальні варіанти НМТ") { open("https://zno.osvita.ua/ukraine-history/nmt.html") })
        c.addView(btn("Завдання за темами", false) { open("https://zno.osvita.ua/ukraine-history/tema.html") })
        return c
    }

    private fun memoCard(g: JSONObject): LinearLayout {
        val c = card()
        c.background = bg(0xFF3A3220.toInt(), 0xFF6B5B2A.toInt())
        c.addView(tv("📌 Пам’ятка", 17f, true))
        val m = g.getJSONArray("memo")
        for (j in 0 until m.length()) {
            val x = tv("• " + m.getString(j), 15f)
            x.setPadding(0, dp(3), 0, dp(3))
            c.addView(x)
        }
        return c
    }

    private fun memos() {
        show({ hist() }) {
            addView(tv("📌 Усі пам’ятки", 24f, true))
            for (g in toList(data)) {
                val c = memoCard(g)
                c.addView(tv("${g.getInt("n")} клас: ${g.getString("t")}", 13f, false, MU), 0)
                addView(c)
            }
        }
    }

    private fun diag() {
        val l = ArrayList<JSONObject>()
        val rest = ArrayList<JSONObject>()
        for (g in toList(data)) {
            val s = toList(g.getJSONArray("q")).shuffled()
            l.addAll(s.take(4))
            rest.addAll(s.drop(4))
        }
        l.addAll(rest.shuffled().take(2))
        quiz(l.shuffled(), "d", "Діагностика") { hist() }
    }

    private fun quiz(qs: List<JSONObject>, key: String, title: String, ret: () -> Unit) {
        var k = 0
        var ok = 0
        fun end() {
            val p = ok.toFloat() / qs.size
            prefs().edit().putFloat(key, p).apply()
            show(ret) {
                addView(tv(title, 22f, true))
                val c = card()
                val sc = tv("0 / ${qs.size}", 44f, true)
                c.addView(sc)
                val va = ValueAnimator.ofInt(0, ok)
                va.duration = 700
                va.addUpdateListener { sc.text = "${it.animatedValue} / ${qs.size}" }
                va.start()
                c.addView(tv("Правильно: ${(p * 100).toInt()}%. Орієнтовно ≈ ${(100 + p * 100).toInt()} балів зі 200.", 15f, false, MU))
                addView(c)
                addView(tv(if (p < 0.7f) "Варто повторити конспект і спробувати ще раз." else "Гарний результат!", 16f))
                addView(site())
                addView(btn("Назад") { ret() })
            }
        }
        fun ask() {
            val q = qs[k]
            val o = q.getJSONArray("o")
            val a = q.getInt("a")
            show(ret) {
                addView(tv("$title · ${k + 1}/${qs.size}", 14f, false, MU))
                addView(tv(q.getString("q"), 18f, true))
                val bs = ArrayList<Button>()
                for (j in 0 until o.length()) {
                    val b = btn(o.getString(j), false) {}
                    b.setOnClickListener {
                        if (j == a) ok++
                        for ((m, x) in bs.withIndex()) {
                            x.isEnabled = false
                            if (m == a) {
                                x.background = bg(OK, OK)
                                x.setTextColor(Color.WHITE)
                                x.animate().scaleX(1.05f).scaleY(1.05f).setDuration(150).withEndAction {
                                    x.animate().scaleX(1f).scaleY(1f).setDuration(150).start()
                                }.start()
                            } else if (m == j) {
                                x.background = bg(BAD, BAD)
                                x.setTextColor(Color.WHITE)
                                val s = dp(10).toFloat()
                                ObjectAnimator.ofFloat(x, "translationX", 0f, -s, s, -s / 2, s / 2, 0f).setDuration(350).start()
                            }
                        }
                        val ex = q.optString("e", "")
                        if (ex.isNotEmpty()) {
                            val ec = card()
                            ec.addView(tv("💡 " + ex, 15f, false, MU))
                            val eb = btn("💡 Пояснення", false) {}
                            eb.setOnClickListener {
                                eb.visibility = View.GONE
                                addView(ec, indexOfChild(eb) + 1)
                                ec.alpha = 0f
                                ec.animate().alpha(1f).setDuration(250).start()
                            }
                            addView(eb)
                        }
                        val nb = btn(if (k + 1 < qs.size) "Далі" else "Результат") {
                            k++
                            if (k < qs.size) ask() else end()
                        }
                        nb.alpha = 0f
                        addView(nb)
                        nb.animate().alpha(1f).setDuration(250).start()
                    }
                    bs.add(b)
                    addView(b)
                }
            }
        }
        ask()
    }
}
