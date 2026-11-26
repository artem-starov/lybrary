package com.nmt.navchannia

import android.app.Activity
import android.app.AlertDialog
import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.net.Uri
import android.view.MotionEvent
import android.view.View
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : Activity() {
    private val BG = 0xFF0F1218.toInt()
    private val CARD = 0xFF181D27.toInt()
    private val TX = 0xFFEEF1F7.toInt()
    private val MU = 0xFF98A2B3.toInt()
    private val AC = 0xFF4F7CF5.toInt()
    private val OK = 0xFF16A34A.toInt()
    private val BAD = 0xFFDC2626.toInt()
    private val WARN = 0xFFF59E0B.toInt()
    private val BD = 0xFF2A3140.toInt()

    private lateinit var root: ScrollView
    private lateinit var box: LinearLayout
    private lateinit var data: JSONArray
    private var back: (() -> Unit)? = null

    @Volatile
    private var prefetching = false

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
        checkUpdate()
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

    // ───────────── Перевірка оновлень ─────────────

    // Дивиться у GitHub Releases файл version.json і порівнює з поточною версією.
    private fun checkUpdate() {
        Thread {
            try {
                val repo = assets.open("repo.txt").bufferedReader().use { it.readText() }.trim()
                if (repo.contains("/")) {
                    val c = URL("https://github.com/" + repo + "/releases/download/latest/version.json").openConnection() as HttpURLConnection
                    c.connectTimeout = 8000
                    c.readTimeout = 8000
                    c.setRequestProperty("Cache-Control", "no-cache")
                    if (c.responseCode == 200) {
                        val j = JSONObject(c.inputStream.bufferedReader().use { it.readText() })
                        val remote = j.getInt("versionCode")
                        val name = j.optString("versionName", "")
                        @Suppress("DEPRECATION")
                        val cur = packageManager.getPackageInfo(packageName, 0).versionCode
                        if (remote > cur) {
                            runOnUiThread {
                                if (!isFinishing) {
                                    AlertDialog.Builder(this@MainActivity)
                                        .setTitle("Доступна нова версія")
                                        .setMessage("Вийшло оновлення додатку " + name + ". Рекомендуємо оновити, щоб мати нові питання й виправлення. Прогрес і збережені дані залишаться.")
                                        .setPositiveButton("Оновити") { _, _ ->
                                            installUpdate("https://github.com/" + repo + "/releases/download/latest/NMT.apk")
                                        }
                                        .setNegativeButton("Пізніше", null)
                                        .show()
                                }
                            }
                        }
                    }
                    c.disconnect()
                }
            } catch (e: Exception) {
            }
        }.start()
    }

    // Перевіряє дозвіл на встановлення, далі завантажує APK з прогресом і відкриває екран встановлення.
    private fun installUpdate(url: String) {
        if (Build.VERSION.SDK_INT >= 26 && !packageManager.canRequestPackageInstalls()) {
            AlertDialog.Builder(this)
                .setTitle("Потрібен дозвіл")
                .setMessage("Щоб оновити додаток, дозволь йому встановлювати програми. Відкриється налаштування: увімкни перемикач, поверніться назад і ще раз натисни «Оновити».")
                .setPositiveButton("Відкрити налаштування") { _, _ ->
                    try {
                        startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:" + packageName)))
                    } catch (e: Exception) {
                        open(url)
                    }
                }
                .setNegativeButton("Скасувати", null)
                .show()
            return
        }
        downloadApk(url)
    }

    private fun startInstall(uri: Uri) {
        try {
            val i = Intent(Intent.ACTION_VIEW)
            i.setDataAndType(uri, "application/vnd.android.package-archive")
            i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(i)
        } catch (e: Exception) {
            Toast.makeText(this, "Не вдалося відкрити встановлення. Відкрий завантажений файл зі сповіщень.", Toast.LENGTH_LONG).show()
        }
    }

    private fun downloadApk(url: String) {
        try {
            val dm = getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            val old = File(getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), "NMT.apk")
            if (old.exists()) old.delete()
            val req = DownloadManager.Request(Uri.parse(url))
            req.setTitle("НМТ Навчання — оновлення")
            req.setMimeType("application/vnd.android.package-archive")
            req.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            req.setDestinationInExternalFilesDir(this, Environment.DIRECTORY_DOWNLOADS, "NMT.apk")
            val id = dm.enqueue(req)
            val stop = BooleanArray(1)
            val dlg = AlertDialog.Builder(this)
                .setTitle("Завантаження оновлення")
                .setMessage("Починаємо…")
                .setCancelable(false)
                .setNegativeButton("Скасувати") { _, _ ->
                    stop[0] = true
                    dm.remove(id)
                }
                .show()
            Thread {
                while (!stop[0]) {
                    try {
                        Thread.sleep(500)
                    } catch (e: Exception) {
                    }
                    if (stop[0]) break
                    var status = -1
                    var down = 0L
                    var tot = -1L
                    val c = dm.query(DownloadManager.Query().setFilterById(id))
                    try {
                        if (c != null && c.moveToFirst()) {
                            status = c.getInt(c.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
                            down = c.getLong(c.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR))
                            tot = c.getLong(c.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES))
                        }
                    } finally {
                        c?.close()
                    }
                    if (status == DownloadManager.STATUS_SUCCESSFUL) {
                        val uri = dm.getUriForDownloadedFile(id)
                        runOnUiThread {
                            dlg.dismiss()
                            if (!isFinishing && uri != null) startInstall(uri)
                        }
                        break
                    } else if (status == DownloadManager.STATUS_FAILED || status == -1) {
                        runOnUiThread {
                            dlg.dismiss()
                            if (!isFinishing) {
                                AlertDialog.Builder(this@MainActivity)
                                    .setMessage("Не вдалося завантажити оновлення. Перевір інтернет або спробуй завантажити через браузер.")
                                    .setPositiveButton("Через браузер") { _, _ -> open(url) }
                                    .setNegativeButton("Закрити", null)
                                    .show()
                            }
                        }
                        break
                    } else {
                        val pc = if (tot > 0) (down * 100 / tot).toInt() else 0
                        runOnUiThread { dlg.setMessage("Завантажено $pc%") }
                    }
                }
            }.start()
        } catch (e: Exception) {
            open(url)
        }
    }

    // ───────────── Статистика й повторення ─────────────

    private fun qid(q: JSONObject): String = q.getString("q").hashCode().toString()

    private fun stats(): JSONObject {
        return try {
            JSONObject(prefs().getString("st", "{}") ?: "{}")
        } catch (e: Exception) {
            JSONObject()
        }
    }

    // [переглянуто разів, правильно разів, правильних поспіль]
    private fun st(s: JSONObject, q: JSONObject): IntArray {
        val a = s.optJSONArray(qid(q)) ?: return intArrayOf(0, 0, 0)
        return intArrayOf(a.optInt(0, 0), a.optInt(1, 0), a.optInt(2, 0))
    }

    private fun record(q: JSONObject, good: Boolean) {
        val s = stats()
        val x = st(s, q)
        val seen = x[0] + 1
        val right = x[1] + (if (good) 1 else 0)
        val streak = if (good) x[2] + 1 else 0
        val arr = JSONArray()
        arr.put(seen)
        arr.put(right)
        arr.put(streak)
        arr.put(System.currentTimeMillis())
        s.put(qid(q), arr)
        prefs().edit().putString("st", s.toString()).apply()
    }

    private fun addHistory(title: String, ok: Int, total: Int) {
        val old = try {
            JSONArray(prefs().getString("hs", "[]") ?: "[]")
        } catch (e: Exception) {
            JSONArray()
        }
        val h = JSONArray()
        val from = maxOf(0, old.length() - 29)
        for (i in from until old.length()) h.put(old.get(i))
        val r = JSONArray()
        r.put(System.currentTimeMillis())
        r.put(title)
        r.put(ok)
        r.put(total)
        h.put(r)
        prefs().edit().putString("hs", h.toString()).apply()
    }

    private fun allQ(): List<Pair<Int, JSONObject>> {
        val l = ArrayList<Pair<Int, JSONObject>>()
        for (g in toList(data)) {
            for (q in toList(g.getJSONArray("q"))) l.add(Pair(g.getInt("n"), q))
        }
        return l
    }

    private fun dueCount(): Int {
        val s = stats()
        var n = 0
        for ((_, q) in allQ()) {
            val x = st(s, q)
            if (x[0] > 0 && x[2] < 3) n++
        }
        return n
    }

    private fun review() {
        val s = stats()
        val all = allQ()
        var due = 0
        val cand = ArrayList<Pair<JSONObject, Double>>()
        for ((_, q) in all) {
            val x = st(s, q)
            if (x[0] > 0 && x[2] < 3) due++
            val w = when {
                x[0] == 0 -> 1.5
                x[2] == 0 -> 10.0
                else -> maxOf(0.5, 6.0 - x[2] * 2.0)
            }
            cand.add(Pair(q, w))
        }
        if (due == 0) {
            show({ hist() }) {
                addView(tv("🔁 Повторення", 24f, true))
                val c = card()
                c.addView(tv("Поки що нічого повторювати. Пройди діагностичний тест або тест по класу — питання, на яких помилишся, потрапляють сюди й повертаються частіше.", 16f))
                addView(c)
                addView(btn("Назад") { hist() })
            }
            return
        }
        val picked = ArrayList<JSONObject>()
        val n = minOf(15, cand.size)
        for (i in 0 until n) {
            var total = 0.0
            for (c in cand) total += c.second
            var r = Math.random() * total
            var idx = cand.size - 1
            for (k in cand.indices) {
                r -= cand[k].second
                if (r <= 0) {
                    idx = k
                    break
                }
            }
            picked.add(cand.removeAt(idx).first)
        }
        quiz(picked, "r", "Повторення") { hist() }
    }

    private fun bar(p: Float, color: Int): LinearLayout {
        val r = LinearLayout(this)
        r.orientation = LinearLayout.HORIZONTAL
        val rp = LinearLayout.LayoutParams(-1, dp(10))
        rp.topMargin = dp(4)
        rp.bottomMargin = dp(6)
        r.layoutParams = rp
        r.background = bg(BD)
        val f = View(this)
        f.background = bg(color)
        f.layoutParams = LinearLayout.LayoutParams(0, -1, p.coerceIn(0f, 1f))
        val e = View(this)
        e.layoutParams = LinearLayout.LayoutParams(0, -1, 1f - p.coerceIn(0f, 1f))
        r.addView(f)
        r.addView(e)
        return r
    }

    private fun statsScreen() {
        show({ hist() }) {
            addView(tv("📊 Статистика", 24f, true))
            val s = stats()
            val all = allQ()
            var answers = 0
            var right = 0
            var seenQ = 0
            var mastered = 0
            for ((_, q) in all) {
                val x = st(s, q)
                answers += x[0]
                right += x[1]
                if (x[0] > 0) seenQ++
                if (x[2] >= 3) mastered++
            }
            val top = card()
            if (answers == 0) {
                top.addView(tv("Даних ще немає. Пройди будь-який тест — і тут з’явиться твій прогрес.", 16f))
            } else {
                top.addView(tv("${right * 100 / answers}% правильних відповідей", 22f, true))
                top.addView(tv("Усього відповідей: $answers (правильно: $right)", 14f, false, MU))
                top.addView(tv("Питань пройдено: $seenQ з ${all.size}", 14f, false, MU))
                top.addView(tv("Засвоєно (3 правильні поспіль): $mastered", 14f, false, MU))
            }
            addView(top)

            val h2 = tv("За класами", 20f, true)
            h2.setPadding(0, dp(12), 0, 0)
            addView(h2)
            val gc = card()
            for (g in toList(data)) {
                var a = 0
                var rr = 0
                for (q in toList(g.getJSONArray("q"))) {
                    val x = st(s, q)
                    a += x[0]
                    rr += x[1]
                }
                val p = if (a == 0) 0f else rr.toFloat() / a
                val label = if (a == 0) "—" else "${(p * 100).toInt()}%"
                gc.addView(tv("${g.getInt("n")} клас · ${g.getString("t")} — $label", 14f))
                val col = if (p < 0.5f) BAD else if (p < 0.7f) WARN else OK
                gc.addView(bar(p, col))
            }
            addView(gc)

            val hard = ArrayList<Pair<JSONObject, IntArray>>()
            for ((_, q) in all) {
                val x = st(s, q)
                if (x[0] - x[1] > 0) hard.add(Pair(q, x))
            }
            hard.sortByDescending { it.second[0] - it.second[1] }
            if (hard.isNotEmpty()) {
                val h3 = tv("Найскладніші питання", 20f, true)
                h3.setPadding(0, dp(12), 0, 0)
                addView(h3)
                val hc = card()
                for ((q, x) in hard.take(5)) {
                    val t = tv("• ${q.getString("q")}  (помилок: ${x[0] - x[1]} з ${x[0]})", 15f)
                    t.setPadding(0, dp(4), 0, dp(4))
                    hc.addView(t)
                }
                addView(hc)
            }

            val hs = try {
                JSONArray(prefs().getString("hs", "[]") ?: "[]")
            } catch (e: Exception) {
                JSONArray()
            }
            if (hs.length() > 0) {
                val h4 = tv("Останні тести", 20f, true)
                h4.setPadding(0, dp(12), 0, 0)
                addView(h4)
                val rc = card()
                val fmt = SimpleDateFormat("dd.MM HH:mm", Locale.getDefault())
                var shown = 0
                var i = hs.length() - 1
                while (i >= 0 && shown < 8) {
                    val r = hs.getJSONArray(i)
                    val line = "${fmt.format(Date(r.getLong(0)))} · ${r.getString(1)} — ${r.getInt(2)}/${r.getInt(3)}"
                    val t = tv(line, 15f)
                    t.setPadding(0, dp(3), 0, dp(3))
                    rc.addView(t)
                    shown++
                    i--
                }
                addView(rc)
            }

            addView(btn("Скинути статистику", false) {
                AlertDialog.Builder(this@MainActivity)
                    .setMessage("Скинути всю статистику й історію тестів?")
                    .setPositiveButton("Скинути") { _, _ ->
                        prefs().edit().clear().apply()
                        statsScreen()
                    }
                    .setNegativeButton("Скасувати", null)
                    .show()
            })
            addView(btn("Назад") { hist() })
        }
    }

    // ───────────── Екрани ─────────────

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
            val due = dueCount()
            val rsub = if (due == 0) "питання, на яких помилився, повертаються частіше" else "до повторення: $due"
            addView(menu("🔁 Повторення", rsub) { review() })
            addView(menu("📊 Статистика", "прогрес за класами й складні питання") { statsScreen() })
            addView(menu("📌 Усі пам’ятки", "ключові дати всіх класів") { memos() })
            for (i in 0 until data.length()) {
                val g = data.getJSONObject(i)
                addView(menu("${g.getInt("n")} клас — ${pct("g" + g.getInt("n"))}", g.getString("t")) { grade(i) })
            }
            addView(menu("⬇️ Офлайн-режим", "завантажити картинки, щоб працювало без інтернету") { offline() })
        }
    }

    private fun grade(i: Int) {
        val g = data.getJSONObject(i)
        show({ hist() }) {
            addView(tv("${g.getInt("n")} клас: ${g.getString("t")}", 22f, true))
            addView(memoCard(g))
            val c = card()
            val s = g.getJSONArray("s")
            val used = HashSet<String>()
            for (j in 0 until s.length()) {
                val full = s.getString(j)
                val row = LinearLayout(this@MainActivity)
                row.orientation = LinearLayout.HORIZONTAL
                row.setPadding(0, dp(6), 0, dp(6))
                val t = tv("• " + full, 16f)
                t.layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
                row.addView(t)
                val iv = ImageView(this@MainActivity)
                iv.scaleType = ImageView.ScaleType.FIT_CENTER
                val lp2 = LinearLayout.LayoutParams(dp(104), dp(104))
                lp2.leftMargin = dp(10)
                iv.layoutParams = lp2
                iv.visibility = View.GONE
                row.addView(iv)
                val tp = full.substringAfter("Тема в підручнику: ", "")
                if (tp.isNotEmpty()) wikiImg(iv, tp, used)
                c.addView(row)
                val mps = g.optJSONArray("maps")
                if (mps != null) {
                    for (qi in 0 until mps.length()) {
                        val mj = mps.getJSONArray(qi)
                        if (mj.length() > 2 && mj.getInt(2) == j) {
                            val cap = tv("🗺 " + mj.getString(0), 13f, false, MU)
                            cap.visibility = View.GONE
                            c.addView(cap)
                            val mv = imgView()
                            c.addView(mv)
                            mapImg(mv, cap, mj.getString(1), used)
                        }
                    }
                }
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
            val p = g.getJSONArray("p")
            if (p.length() > 0) {
                val h = tv("Діячі епохи", 20f, true)
                h.setPadding(0, dp(12), 0, 0)
                addView(h)
            }
            for (o in toList(p)) {
                val pc = card()
                val piv = imgView()
                pc.addView(piv)
                portrait(piv, o.getString("name"))
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

    // ───────────── Мережа + кеш на диску ─────────────

    private val pool = java.util.concurrent.Executors.newFixedThreadPool(4)

    private fun cacheDirectory(): File {
        val d = File(filesDir, "cache")
        if (!d.exists()) d.mkdirs()
        return d
    }

    private fun cacheFile(u: String): File {
        val h = MessageDigest.getInstance("MD5").digest(u.toByteArray(Charsets.UTF_8))
        val sb = StringBuilder()
        for (x in h) sb.append("%02x".format(x.toInt() and 0xff))
        return File(cacheDirectory(), sb.toString())
    }

    private fun cacheSize(): Long {
        var n = 0L
        val fs = cacheDirectory().listFiles() ?: return 0L
        for (f in fs) n += f.length()
        return n
    }

    // Спочатку шукає на диску, інакше завантажує й зберігає. Далі все працює без інтернету.
    private fun http(u: String): ByteArray? {
        val cf = cacheFile(u)
        try {
            if (cf.exists() && cf.length() > 0) return cf.readBytes()
        } catch (e: Exception) {
        }
        for (t in 0..1) {
            try {
                val c = URL(u).openConnection() as HttpURLConnection
                c.connectTimeout = 10000
                c.readTimeout = 15000
                c.setRequestProperty("User-Agent", "NMTStudyApp/1.0 (student study app; contact: github.com)")
                c.setRequestProperty("Accept", "*/*")
                val code = c.responseCode
                if (code == 200) {
                    val r = c.inputStream.use { it.readBytes() }
                    c.disconnect()
                    try {
                        val tmp = File(cf.path + ".tmp")
                        tmp.writeBytes(r)
                        tmp.renameTo(cf)
                    } catch (e: Exception) {
                    }
                    return r
                }
                c.disconnect()
                if (code != 429 && code < 500) return null
            } catch (e: Exception) {
            }
            try {
                Thread.sleep(1500)
            } catch (e: Exception) {
            }
        }
        return null
    }

    private fun wikiUrl(q: String): String =
        "https://uk.wikipedia.org/w/api.php?action=query&generator=search&gsrsearch=" + Uri.encode(q) +
            "&gsrlimit=1&prop=pageimages&piprop=thumbnail&pithumbsize=500&format=json"

    private fun commonsUrl(q: String): String =
        "https://commons.wikimedia.org/w/api.php?action=query&generator=search&gsrnamespace=6&gsrlimit=5&gsrsearch=" +
            Uri.encode(q) + "&prop=imageinfo&iiprop=url%7Cmime&iiurlwidth=960&format=json"

    private fun imgView(): ImageView {
        val v = ImageView(this)
        v.adjustViewBounds = true
        v.scaleType = ImageView.ScaleType.FIT_CENTER
        v.maxHeight = dp(320)
        v.visibility = View.GONE
        val p = LinearLayout.LayoutParams(-1, -2)
        p.topMargin = dp(8)
        v.layoutParams = p
        return v
    }

    private fun setImg(iv: ImageView, url: String, link: String?, cap: View? = null) {
        pool.execute {
            val b = http(url)
            val bmp = if (b != null) BitmapFactory.decodeByteArray(b, 0, b.size) else null
            if (bmp != null) runOnUiThread {
                iv.setImageBitmap(bmp)
                iv.visibility = View.VISIBLE
                cap?.visibility = View.VISIBLE
                iv.alpha = 0f
                iv.animate().alpha(1f).setDuration(300).start()
                if (link != null && link.isNotEmpty()) iv.setOnClickListener { open(link) }
            }
        }
    }

    private fun portrait(iv: ImageView, name: String) {
        pool.execute {
            val b = http(wikiUrl(name)) ?: return@execute
            try {
                val pages = JSONObject(String(b, Charsets.UTF_8)).getJSONObject("query").getJSONObject("pages")
                val keys = pages.keys()
                while (keys.hasNext()) {
                    val p = pages.getJSONObject(keys.next())
                    val th = p.optJSONObject("thumbnail")
                    if (th != null) {
                        val page = "https://uk.wikipedia.org/wiki/" + Uri.encode(p.getString("title").replace(' ', '_'))
                        runOnUiThread { setImg(iv, th.getString("source"), page) }
                        break
                    }
                }
            } catch (e: Exception) {
            }
        }
    }

    private fun wikiImg(iv: ImageView, query: String, used: MutableSet<String>, cap: View? = null) {
        pool.execute {
            val b = http(wikiUrl(query)) ?: return@execute
            try {
                val pages = JSONObject(String(b, Charsets.UTF_8)).getJSONObject("query").getJSONObject("pages")
                val p = pages.getJSONObject(pages.keys().next())
                val th = p.optJSONObject("thumbnail") ?: return@execute
                val src = th.getString("source")
                val added = synchronized(used) { used.add(src) }
                if (!added) return@execute
                val page = "https://uk.wikipedia.org/wiki/" + Uri.encode(p.getString("title").replace(' ', '_'))
                runOnUiThread { setImg(iv, src, page, cap) }
            } catch (e: Exception) {
            }
        }
    }

    private fun mapImg(iv: ImageView, cap: View, query: String, used: MutableSet<String>) {
        pool.execute {
            var done = false
            try {
                val b = http(commonsUrl(query))
                if (b != null) {
                    val pages = JSONObject(String(b, Charsets.UTF_8)).getJSONObject("query").getJSONObject("pages")
                    val list = ArrayList<JSONObject>()
                    val keys = pages.keys()
                    while (keys.hasNext()) list.add(pages.getJSONObject(keys.next()))
                    list.sortBy { it.optInt("index", 99) }
                    for (p in list) {
                        val ii = p.optJSONArray("imageinfo")?.optJSONObject(0) ?: continue
                        if (!ii.optString("mime").startsWith("image/")) continue
                        val th = ii.optString("thumburl", ii.optString("url"))
                        if (!synchronized(used) { used.add(th) }) continue
                        val page = ii.optString("descriptionurl")
                        runOnUiThread { setImg(iv, th, page, cap) }
                        done = true
                        break
                    }
                }
            } catch (e: Exception) {
            }
            if (!done) wikiImg(iv, query, used, cap)
        }
    }

    private fun open(u: String) {
        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(u)))
    }

    // ───────────── Офлайн-режим ─────────────

    // Завантажує всі картинки й відповіді Вікіпедії в кеш. Виконується у фоновому потоці.
    private fun prefetch(progress: (Int, Int) -> Unit) {
        val wq = ArrayList<String>()
        val mq = ArrayList<String>()
        for (g in toList(data)) {
            val s = g.getJSONArray("s")
            for (j in 0 until s.length()) {
                val tp = s.getString(j).substringAfter("Тема в підручнику: ", "")
                if (tp.isNotEmpty()) wq.add(tp)
            }
            for (o in toList(g.getJSONArray("p"))) wq.add(o.getString("name"))
            val m = g.optJSONArray("maps")
            if (m != null) for (i in 0 until m.length()) mq.add(m.getJSONArray(i).getString(1))
        }
        val total = wq.size + mq.size
        var done = 0
        for (q in wq) {
            try {
                val b = http(wikiUrl(q))
                if (b != null) {
                    val pages = JSONObject(String(b, Charsets.UTF_8)).optJSONObject("query")?.optJSONObject("pages")
                    if (pages != null) {
                        val keys = pages.keys()
                        while (keys.hasNext()) {
                            val src = pages.getJSONObject(keys.next()).optJSONObject("thumbnail")?.optString("source")
                            if (!src.isNullOrEmpty()) http(src)
                        }
                    }
                }
            } catch (e: Exception) {
            }
            done++
            progress(done, total)
        }
        for (q in mq) {
            try {
                val b = http(commonsUrl(q))
                if (b != null) {
                    val pages = JSONObject(String(b, Charsets.UTF_8)).optJSONObject("query")?.optJSONObject("pages")
                    if (pages != null) {
                        val list = ArrayList<JSONObject>()
                        val keys = pages.keys()
                        while (keys.hasNext()) list.add(pages.getJSONObject(keys.next()))
                        list.sortBy { it.optInt("index", 99) }
                        var n = 0
                        for (p in list) {
                            val ii = p.optJSONArray("imageinfo")?.optJSONObject(0) ?: continue
                            if (!ii.optString("mime").startsWith("image/")) continue
                            if (n >= 3) break
                            n++
                            http(ii.optString("thumburl", ii.optString("url")))
                        }
                    }
                }
            } catch (e: Exception) {
            }
            done++
            progress(done, total)
        }
    }

    private fun mb(n: Long): String = String.format(Locale.US, "%.1f МБ", n / 1048576.0)

    private fun offline() {
        show({ hist() }) {
            addView(tv("⬇️ Офлайн-режим", 24f, true))
            val c = card()
            c.addView(tv("Картинки, карти й портрети один раз завантажуються й зберігаються в пам’яті телефона. Далі вони відкриваються без інтернету. Перше завантаження краще робити через Wi-Fi (кілька десятків МБ).", 15f))
            val info = tv("У пам’яті зараз: " + mb(cacheSize()), 14f, false, MU)
            info.setPadding(0, dp(8), 0, 0)
            c.addView(info)
            addView(c)
            val startBtn = btn("Завантажити все") {}
            startBtn.setOnClickListener {
                if (prefetching) return@setOnClickListener
                prefetching = true
                startBtn.isEnabled = false
                info.text = "Завантаження…"
                Thread {
                    prefetch { d, t ->
                        runOnUiThread { info.text = "Завантажено $d з $t · " + mb(cacheSize()) }
                    }
                    prefetching = false
                    runOnUiThread {
                        info.text = "Готово ✅ У пам’яті: " + mb(cacheSize())
                        startBtn.isEnabled = true
                    }
                }.start()
            }
            addView(startBtn)
            addView(btn("Очистити кеш", false) {
                val fs = cacheDirectory().listFiles()
                if (fs != null) for (f in fs) f.delete()
                info.text = "У пам’яті зараз: " + mb(cacheSize())
            })
            addView(btn("Назад", false) { hist() })
        }
    }

    // ───────────── Контент ─────────────

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

    private val lockRx = Regex("(?iu)усі|всі|жодн|обидв|вище|правильн")

    private fun quiz(qs: List<JSONObject>, key: String, title: String, ret: () -> Unit) {
        var k = 0
        var ok = 0
        fun end() {
            val p = ok.toFloat() / qs.size
            if (key != "r") prefs().edit().putFloat(key, p).apply()
            addHistory(title, ok, qs.size)
            show(ret) {
                addView(tv(title, 22f, true))
                val c = card()
                val sc = tv("0 / ${qs.size}", 44f, true)
                c.addView(sc)
                val va = ValueAnimator.ofInt(0, ok)
                va.duration = 700
                va.addUpdateListener { sc.text = "${it.animatedValue} / ${qs.size}" }
                va.start()
                val pts = if (key == "r") "" else " Орієнтовно ≈ ${(100 + p * 100).toInt()} балів зі 200."
                c.addView(tv("Правильно: ${(p * 100).toInt()}%.$pts", 15f, false, MU))
                addView(c)
                addView(tv(if (p < 0.7f) "Варто повторити конспект і спробувати ще раз." else "Гарний результат!", 16f))
                addView(site())
                addView(btn("Назад") { ret() })
            }
        }
        fun ask() {
            val q = qs[k]
            val o = q.getJSONArray("o")
            val a0 = q.getInt("a")
            val order = ArrayList<Int>()
            for (j in 0 until o.length()) order.add(j)
            var locked = false
            for (j in 0 until o.length()) if (lockRx.containsMatchIn(o.getString(j))) locked = true
            if (!locked) order.shuffle()
            val a = order.indexOf(a0)
            show(ret) {
                addView(tv("$title · ${k + 1}/${qs.size}", 14f, false, MU))
                addView(tv(q.getString("q"), 18f, true))
                val bs = ArrayList<Button>()
                for (j in 0 until o.length()) {
                    val b = btn(o.getString(order[j]), false) {}
                    b.setOnClickListener {
                        if (j == a) ok++
                        record(q, j == a)
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
