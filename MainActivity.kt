package com.nmt.navchannia

import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.app.Activity
import android.app.AlertDialog
import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.text.InputType
import android.text.SpannableString
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.TextPaint
import android.text.method.LinkMovementMethod
import android.text.style.ClickableSpan
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.Button
import android.widget.FrameLayout
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
    // Палітра: глибокий синій вечір і золото пшениці. Кожна епоха має свій колір.
    private val BG = 0xFF07122B.toInt()
    private val BG2 = 0xFF0D2150.toInt()
    private val CARD = 0xFF10224A.toInt()
    private val CARD2 = 0xFF18306A.toInt()
    private val TX = 0xFFF2F5FF.toInt()
    private val MU = 0xFFA3B3D6.toInt()
    private val AC = 0xFFFFC94D.toInt()
    private val ON_AC = 0xFF2A1D00.toInt()
    private val OK = 0xFF2DD4A0.toInt()
    private val BAD = 0xFFFF6B6B.toInt()
    private val WARN = 0xFFFFA94D.toInt()
    private val BD = 0x33FFFFFF
    private val ERA = intArrayOf(
        0xFF2DD4BF.toInt(), 0xFFFFC94D.toInt(), 0xFFFB923C.toInt(), 0xFFF472B6.toInt(),
        0xFFA78BFA.toInt(), 0xFF60A5FA.toInt(), 0xFF34D399.toInt()
    )

    private lateinit var root: ScrollView
    private lateinit var box: LinearLayout
    private lateinit var data: JSONArray
    private var back: (() -> Unit)? = null

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    override fun onCreate(s: Bundle?) {
        super.onCreate(s)
        data = JSONArray(assets.open("data.json").bufferedReader().use { it.readText() })
        try {
            window.statusBarColor = BG2
            window.navigationBarColor = BG
        } catch (e: Exception) {
        }
        root = ScrollView(this)
        root.background = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(BG2, BG))
        root.fitsSystemWindows = true
        root.isVerticalScrollBarEnabled = false
        box = LinearLayout(this)
        box.orientation = LinearLayout.VERTICAL
        box.setPadding(dp(16), dp(16), dp(16), dp(28))
        root.addView(box)
        setContentView(root)
        home()
        checkUpdate()
        startPrefetch()
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

    // Заголовки (від 20sp) — із засічками, як у літописі; решта — звичайний шрифт
    private fun tv(text: String, size: Float = 16f, bold: Boolean = false, color: Int = TX): TextView {
        val t = TextView(this)
        t.text = text
        t.textSize = size
        t.setTextColor(color)
        t.setLineSpacing(0f, 1.12f)
        if (bold) {
            t.typeface = if (size >= 20f) Typeface.create("serif", Typeface.BOLD) else Typeface.create("sans-serif-medium", Typeface.NORMAL)
        }
        return t
    }

    private fun h1(text: String, size: Float = 28f): TextView {
        val t = tv(text, size, true)
        t.setPadding(0, dp(4), 0, dp(4))
        return t
    }

    private fun bg(fill: Int, stroke: Int = Color.TRANSPARENT, r: Int = 16): GradientDrawable {
        val g = GradientDrawable()
        g.setColor(fill)
        g.cornerRadius = dp(r).toFloat()
        g.setStroke(dp(1), stroke)
        return g
    }

    private fun circle(c: Int): GradientDrawable {
        val g = GradientDrawable()
        g.shape = GradientDrawable.OVAL
        g.setColor(c)
        return g
    }

    private fun ripple(d: Drawable, r: Int = 16): Drawable =
        RippleDrawable(ColorStateList.valueOf(0x33FFFFFF), d, bg(Color.WHITE, Color.TRANSPARENT, r))

    private fun tint(c: Int, a: Int): Int = (a shl 24) or (c and 0x00FFFFFF)

    private fun badge(text: String, color: Int, size: Int = 44, textSize: Float = 20f): TextView {
        val t = TextView(this)
        t.text = text
        t.textSize = textSize
        t.gravity = Gravity.CENTER
        t.setTextColor(TX)
        t.background = circle(color)
        t.layoutParams = LinearLayout.LayoutParams(dp(size), dp(size))
        return t
    }

    private fun btn(text: String, primary: Boolean = true, onClick: () -> Unit): Button {
        val b = Button(this)
        b.text = text
        b.isAllCaps = false
        b.textSize = 16f
        b.typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        b.stateListAnimator = null
        b.minHeight = dp(52)
        b.setTextColor(if (primary) ON_AC else TX)
        b.background = ripple(if (primary) bg(AC, AC, 16) else bg(CARD2, BD, 16))
        b.layoutParams = lp()
        b.setOnClickListener { onClick() }
        pressFx(b)
        return b
    }

    private fun card(): LinearLayout {
        val c = LinearLayout(this)
        c.orientation = LinearLayout.VERTICAL
        c.setPadding(dp(16), dp(14), dp(16), dp(14))
        c.background = bg(CARD, BD, 18)
        c.layoutParams = lp()
        return c
    }

    private fun clickable(c: View, d: Drawable, onClick: () -> Unit) {
        c.background = ripple(d, 18)
        c.setOnClickListener { onClick() }
        pressFx(c)
    }

    private fun navRow(icon: String, color: Int, ttl: String, sub: String, onClick: (() -> Unit)?): LinearLayout {
        val c = card()
        c.orientation = LinearLayout.HORIZONTAL
        c.gravity = Gravity.CENTER_VERTICAL
        c.addView(badge(icon, tint(color, 0x33), 44))
        val col = LinearLayout(this)
        col.orientation = LinearLayout.VERTICAL
        val cp = LinearLayout.LayoutParams(0, -2, 1f)
        cp.leftMargin = dp(14)
        col.layoutParams = cp
        col.addView(tv(ttl, 17f, true))
        col.addView(tv(sub, 13f, false, MU))
        c.addView(col)
        if (onClick != null) {
            c.addView(tv("›", 26f, false, MU))
            clickable(c, bg(CARD, BD, 18)) { onClick() }
        } else {
            c.alpha = 0.5f
        }
        return c
    }

    private fun pressFx(v: View) {
        v.setOnTouchListener { x, e ->
            when (e.action) {
                MotionEvent.ACTION_DOWN -> x.animate().scaleX(0.97f).scaleY(0.97f).setDuration(90).start()
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> x.animate().scaleX(1f).scaleY(1f).setDuration(120).start()
            }
            false
        }
    }

    // Один плавний перехід на весь екран
    private fun show(b: (() -> Unit)?, build: LinearLayout.() -> Unit) {
        back = b
        box.removeAllViews()
        box.build()
        root.scrollTo(0, 0)
        box.animate().cancel()
        box.alpha = 0f
        box.translationY = dp(14).toFloat()
        box.animate().alpha(1f).translationY(0f).setDuration(240).setInterpolator(DecelerateInterpolator()).start()
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

    private fun review() = reviewOf(allQ().map { it.second }) { hist() }

    private fun reviewOf(pool: List<JSONObject>, ret: () -> Unit) {
        val s = stats()
        var due = 0
        val cand = ArrayList<Pair<JSONObject, Double>>()
        for (q in pool) {
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
            show({ ret() }) {
                addView(h1("🔁 Повторення"))
                val c = card()
                c.addView(tv("Поки що нічого повторювати. Пройди тест — питання, на яких помилишся, потрапляють сюди й повертаються частіше.", 16f))
                addView(c)
                addView(btn("Назад") { ret() })
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
        quiz(picked, "r", "Повторення", ret)
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
            addView(h1("📊 Статистика"))
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

    // [пройдено питань, засвоєно, усього, відповідей, правильних]
    private fun overall(): IntArray {
        val s = stats()
        var seen = 0
        var mastered = 0
        var total = 0
        var answers = 0
        var right = 0
        for ((_, q) in allQ()) {
            val x = st(s, q)
            total++
            answers += x[0]
            right += x[1]
            if (x[0] > 0) seen++
            if (x[2] >= 3) mastered++
        }
        return intArrayOf(seen, mastered, total, answers, right)
    }

    private fun home() {
        show(null) {
            addView(h1("Навчання до НМТ", 30f))
            addView(tv("Конспект, повторення й тести — все в одному місці", 14f, false, MU))
            val o = overall()
            val due = dueCount()
            val hero = LinearLayout(this@MainActivity)
            hero.orientation = LinearLayout.VERTICAL
            hero.setPadding(dp(18), dp(18), dp(18), dp(18))
            val hg = GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(0xFF2557D6.toInt(), 0xFF14307A.toInt()))
            hg.cornerRadius = dp(24).toFloat()
            hg.setStroke(dp(1), BD)
            hero.background = hg
            val hp = lp()
            hp.topMargin = dp(18)
            hero.layoutParams = hp
            val top = LinearLayout(this@MainActivity)
            top.orientation = LinearLayout.HORIZONTAL
            top.gravity = Gravity.CENTER_VERTICAL
            val fl = FrameLayout(this@MainActivity)
            fl.layoutParams = LinearLayout.LayoutParams(dp(96), dp(96))
            val ring = RingView(this@MainActivity)
            ring.color = AC
            ring.track = 0x33FFFFFF
            ring.stroke = dp(9).toFloat()
            fl.addView(ring, FrameLayout.LayoutParams(-1, -1))
            val frac = if (o[2] == 0) 0f else o[1].toFloat() / o[2]
            val ct = tv("${(frac * 100).toInt()}%", 22f, true)
            ct.gravity = Gravity.CENTER
            fl.addView(ct, FrameLayout.LayoutParams(-2, -2, Gravity.CENTER))
            ring.animateTo(frac)
            top.addView(fl)
            val col = LinearLayout(this@MainActivity)
            col.orientation = LinearLayout.VERTICAL
            val cp = LinearLayout.LayoutParams(0, -2, 1f)
            cp.leftMargin = dp(16)
            col.layoutParams = cp
            col.addView(tv("Засвоєно", 13f, false, 0xCCFFFFFF.toInt()))
            col.addView(tv("${o[1]} з ${o[2]} питань", 20f, true))
            val note = if (o[0] == 0) "Почни з діагностики — вона покаже, що ти вже знаєш." else "Питання вважається засвоєним після трьох правильних відповідей поспіль."
            col.addView(tv(note, 12f, false, 0xCCFFFFFF.toInt()))
            top.addView(col)
            hero.addView(top)
            val action = if (due > 0) "Повторити ($due)" else "Пройти діагностику"
            hero.addView(btn(action) { if (due > 0) review() else diag() })
            addView(hero)

            val h = tv("Предмети", 20f, true)
            h.setPadding(0, dp(20), 0, dp(2))
            addView(h)
            addView(navRow("📜", ERA[1], "Історія України", "5–11 класи: конспект, діячі, тести") { hist() })
            addView(navRow("🇺🇦", ERA[0], "Українська мова", "правила, тести, наголоси, фразеологізми") { ukrHome() })
            for ((e, n) in listOf("➗" to "Математика", "🇬🇧" to "Англійська")) {
                addView(navRow(e, MU, n, "скоро", null))
            }
        }
    }

    private fun tile(icon: String, color: Int, ttl: String, sub: String, onClick: () -> Unit): LinearLayout {
        val c = LinearLayout(this)
        c.orientation = LinearLayout.VERTICAL
        c.setPadding(dp(14), dp(14), dp(14), dp(14))
        val p = LinearLayout.LayoutParams(0, -1, 1f)
        p.setMargins(dp(4), dp(4), dp(4), dp(4))
        c.layoutParams = p
        c.addView(badge(icon, tint(color, 0x33), 40, 18f))
        val t = tv(ttl, 16f, true)
        t.setPadding(0, dp(10), 0, 0)
        c.addView(t)
        c.addView(tv(sub, 12f, false, MU))
        clickable(c, bg(CARD, BD, 18)) { onClick() }
        return c
    }

    private fun tiles(a: LinearLayout, b: LinearLayout): LinearLayout {
        val r = LinearLayout(this)
        r.orientation = LinearLayout.HORIZONTAL
        val p = LinearLayout.LayoutParams(-1, -2)
        p.topMargin = dp(4)
        r.layoutParams = p
        r.addView(a)
        r.addView(b)
        return r
    }

    // Епоха — вузол на лінії часу з кольором і шкалою засвоєння
    private fun eraRow(i: Int, first: Boolean, last: Boolean, s: JSONObject): LinearLayout {
        val g = data.getJSONObject(i)
        val ec = ERA[i % ERA.size]
        val qs = toList(g.getJSONArray("q"))
        var mastered = 0
        for (q in qs) if (st(s, q)[2] >= 3) mastered++
        val r = LinearLayout(this)
        r.orientation = LinearLayout.HORIZONTAL
        r.layoutParams = LinearLayout.LayoutParams(-1, -2)
        val left = FrameLayout(this)
        left.layoutParams = LinearLayout.LayoutParams(dp(48), -1)
        val line = View(this)
        line.setBackgroundColor(0x33FFFFFF)
        val lpL = FrameLayout.LayoutParams(dp(2), if (last) dp(32) else -1, Gravity.CENTER_HORIZONTAL)
        if (first) lpL.topMargin = dp(32)
        left.addView(line, lpL)
        val node = badge("${g.getInt("n")}", ec, 36, 15f)
        node.setTextColor(ON_AC)
        node.typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        val np = FrameLayout.LayoutParams(dp(36), dp(36), Gravity.CENTER_HORIZONTAL or Gravity.TOP)
        np.topMargin = dp(14)
        left.addView(node, np)
        r.addView(left)
        val c = card()
        val cp = LinearLayout.LayoutParams(0, -2, 1f)
        cp.topMargin = dp(6)
        cp.bottomMargin = dp(2)
        c.layoutParams = cp
        c.addView(tv(g.getString("t"), 17f, true))
        c.addView(tv("${g.getInt("n")} клас · засвоєно $mastered з ${qs.size}", 13f, false, MU))
        c.addView(bar(if (qs.isEmpty()) 0f else mastered.toFloat() / qs.size, ec))
        clickable(c, bg(CARD, BD, 18)) { grade(i) }
        r.addView(c)
        return r
    }

    private fun hist() {
        show({ home() }) {
            addView(h1("Історія України"))
            addView(tv("Обери, з чого почати", 14f, false, MU))
            val s = stats()
            val d = prefs().getFloat("d", -1f)
            val dsub = if (d < 0) "30 питань з усіх епох" else "≈ ${(100 + d * 100).toInt()} балів"
            val due = dueCount()
            val rsub = if (due == 0) "помилки повертаються частіше" else "до повторення: $due"
            val r1 = tiles(
                tile("🎯", AC, "Діагностика", dsub) { diag() },
                tile("🔁", OK, "Повторення", rsub) { review() }
            )
            val r2 = tiles(
                tile("📊", ERA[5], "Статистика", "прогрес і слабкі місця") { statsScreen() },
                tile("📌", ERA[3], "Пам’ятки", "ключові дати епох") { memos() }
            )
            (r1.layoutParams as LinearLayout.LayoutParams).topMargin = dp(14)
            addView(r1)
            addView(r2)
            val h = tv("Епохи", 20f, true)
            h.setPadding(0, dp(20), 0, dp(6))
            addView(h)
            val n = data.length()
            for (i in 0 until n) addView(eraRow(i, i == 0, i == n - 1, s))
            addView(site())
        }
    }

    private fun grade(i: Int) {
        val g = data.getJSONObject(i)
        show({ hist() }) {
            val era = ERA[i % ERA.size]
            val chip = tv("${g.getInt("n")} клас", 13f, true, ON_AC)
            chip.setPadding(dp(12), dp(4), dp(12), dp(4))
            chip.background = bg(era, era, 20)
            chip.layoutParams = LinearLayout.LayoutParams(-2, -2)
            addView(chip)
            addView(h1(g.getString("t")))
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
                val pcap = tv("Фото: Вікіпедія", 11f, false, MU)
                pcap.visibility = View.GONE
                pc.addView(pcap)
                portrait(piv, o.getString("name"), pcap)
                pc.addView(tv(o.getString("name"), 17f, true))
                pc.addView(tv(o.getString("years") + " · " + o.getString("role"), 14f, false, MU))
                val d = tv(o.getString("desc"), 16f)
                d.setPadding(0, dp(6), 0, 0)
                pc.addView(d)
                addView(pc)
            }
            addView(btn("Здати тест") {
                quiz(toList(g.getJSONArray("q")), "g" + g.getInt("n"), "${g.getInt("n")} клас") { grade(i) }
            })
        }
    }

    // ───────────── Мережа + кеш на диску ─────────────

    private val pool = java.util.concurrent.Executors.newFixedThreadPool(4)

    // Вікіпедія просить описовий User-Agent з посиланням на проєкт
    private val ua: String by lazy {
        val r = try {
            assets.open("repo.txt").bufferedReader().use { it.readText() }.trim()
        } catch (e: Exception) {
            "nmt-study-app"
        }
        "NMTStudyApp/1.1 (https://github.com/" + r + "; student study app)"
    }

    private fun cacheDirectory(): File {
        val d = File(filesDir, "cache")
        if (!d.exists()) d.mkdirs()
        return d
    }

    private fun cacheName(u: String): String {
        val h = MessageDigest.getInstance("MD5").digest(u.toByteArray(Charsets.UTF_8))
        val sb = StringBuilder()
        for (x in h) sb.append("%02x".format(x.toInt() and 0xff))
        return sb.toString()
    }

    private fun cacheFile(u: String): File = File(cacheDirectory(), cacheName(u))

    // Спочатку шукає на диску, інакше завантажує й зберігає. Далі все працює без інтернету.
    private fun http(u: String): ByteArray? {
        val cf = cacheFile(u)
        try {
            if (cf.exists() && cf.length() > 0) return cf.readBytes()
        } catch (e: Exception) {
        }
        // Картинки й відповіді Вікіпедії, вшиті в додаток під час збірки (assets/img)
        try {
            val r = assets.open("img/" + cacheName(u)).use { it.readBytes() }
            if (r.isNotEmpty()) return r
        } catch (e: Exception) {
        }
        for (t in 0..1) {
            try {
                val c = URL(u).openConnection() as HttpURLConnection
                c.connectTimeout = 10000
                c.readTimeout = 15000
                c.setRequestProperty("User-Agent", ua)
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

    private fun portrait(iv: ImageView, name: String, cap: View? = null) {
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
                        runOnUiThread { setImg(iv, th.getString("source"), page, cap) }
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

    // ───────────── Тихе завантаження картинок ─────────────

    private fun curVersion(): Int {
        @Suppress("DEPRECATION")
        return packageManager.getPackageInfo(packageName, 0).versionCode
    }

    // При першому запуску кожної версії у фоні докачує все, чого ще немає на телефоні. Користувач нічого не натискає.
    private fun startPrefetch() {
        val sys = getSharedPreferences("sys", 0)
        val cur = try {
            curVersion()
        } catch (e: Exception) {
            return
        }
        if (sys.getInt("pf", 0) >= cur) return
        Thread {
            val fails = prefetch()
            if (fails == 0) sys.edit().putInt("pf", cur).apply()
        }.start()
    }

    // Повертає кількість невдалих завантажень (0 = усе є на телефоні).
    private fun prefetch(): Int {
        var fails = 0
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
        for (q in wq + mq) {
            try {
                val b = http(wikiUrl(q))
                if (b == null) {
                    fails++
                } else {
                    val pages = JSONObject(String(b, Charsets.UTF_8)).optJSONObject("query")?.optJSONObject("pages")
                    if (pages != null) {
                        val keys = pages.keys()
                        while (keys.hasNext()) {
                            val src = pages.getJSONObject(keys.next()).optJSONObject("thumbnail")?.optString("source")
                            if (!src.isNullOrEmpty() && http(src) == null) fails++
                        }
                    }
                }
            } catch (e: Exception) {
                fails++
            }
        }
        for (q in mq) {
            try {
                val b = http(commonsUrl(q))
                if (b == null) {
                    fails++
                } else {
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
                            if (http(ii.optString("thumburl", ii.optString("url"))) == null) fails++
                        }
                    }
                }
            } catch (e: Exception) {
                fails++
            }
        }
        return fails
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
        c.background = bg(0xFF2D2B3A.toInt(), 0x66FFC94D, 18)
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
            addView(h1("📌 Усі пам’ятки"))
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

    // ───────────── Українська мова ─────────────

    private val ukr: JSONObject by lazy {
        JSONObject(assets.open("ukr.json").bufferedReader().use { it.readText() })
    }

    private val VOW = "аеєиіїоуюя"

    private fun typed(a: JSONArray?, t: String): List<JSONObject> {
        val l = ArrayList<JSONObject>()
        if (a != null) {
            for (i in 0 until a.length()) {
                val o = JSONObject(a.getJSONObject(i).toString())
                if (!o.has("t")) o.put("t", t)
                l.add(o)
            }
        }
        return l
    }

    private fun secQ(i: Int): List<JSONObject> {
        val s = ukr.getJSONArray("sections").getJSONObject(i)
        return typed(s.optJSONArray("q"), "c") + typed(s.optJSONArray("m"), "m") + typed(s.optJSONArray("w"), "w")
    }

    private fun stressWords(): List<Pair<String, Int>> {
        val a = ukr.getJSONArray("stress")
        val l = ArrayList<Pair<String, Int>>()
        for (i in 0 until a.length()) {
            val w = a.getString(i)
            l.add(Pair(w.lowercase(), w.indexOfFirst { it.isUpperCase() }))
        }
        return l
    }

    private fun stressQ(w: String, i: Int): JSONObject {
        val o = JSONObject()
        o.put("t", "s")
        o.put("q", "Постав наголос: " + w)
        o.put("w", w)
        o.put("i", i)
        o.put("e", "Правильно: " + w.substring(0, i) + w[i].uppercaseChar() + w.substring(i + 1))
        return o
    }

    private fun phrases(): List<Pair<String, String>> {
        val a = ukr.getJSONArray("phr")
        val l = ArrayList<Pair<String, String>>()
        for (i in 0 until a.length()) {
            val p = a.getJSONArray(i)
            l.add(Pair(p.getString(0), p.getString(1)))
        }
        return l
    }

    private fun phrQ(i: Int, all: List<Pair<String, String>>): JSONObject {
        val p = all[i]
        val others = ArrayList<String>()
        for ((k, x) in all.withIndex()) if (k != i) others.add(x.second)
        others.shuffle()
        val opts = ArrayList<String>(others.take(3))
        opts.add(p.second)
        opts.shuffle()
        val o = JSONObject()
        o.put("q", "Що означає фразеологізм «" + p.first + "»?")
        o.put("o", JSONArray(opts))
        o.put("a", opts.indexOf(p.second))
        o.put("e", "«" + p.first + "» — " + p.second + ".")
        return o
    }

    private fun phrMatch(all: List<Pair<String, String>>): JSONObject {
        val sh = ArrayList<Pair<String, String>>(all)
        sh.shuffle()
        val pick = sh.take(4)
        val extra = sh[4].second
        val opts = ArrayList<String>()
        for (p in pick) opts.add(p.second)
        opts.add(extra)
        opts.shuffle()
        val l = JSONArray()
        val a = JSONArray()
        for (p in pick) {
            l.put(p.first)
            a.put(opts.indexOf(p.second))
        }
        val o = JSONObject()
        o.put("t", "m")
        o.put("nr", true)
        o.put("q", "Установіть відповідність між фразеологізмом (1–4) та його значенням (А–Д).")
        o.put("l", l)
        o.put("r", JSONArray(opts))
        o.put("a", a)
        o.put("e", "")
        return o
    }

    private fun practiceQs(ci: Int): List<JSONObject> {
        val c = ukr.getJSONArray("practice").getJSONObject(ci)
        val items = c.getJSONArray("items")
        val l = ArrayList<JSONObject>()
        for (i in 0 until items.length()) {
            val it = items.getJSONArray(i)
            val o = JSONObject()
            o.put("t", "w")
            o.put("pr", true)
            o.put("q", it.getString(0))
            o.put("ans", it.getJSONArray(1))
            o.put("e", it.optString(2, ""))
            o.put("h", c.optString("hint", ""))
            l.add(o)
        }
        return l
    }

    // Усі завдання з української мови (стабільні за текстом, тому статистика зберігається)
    private fun ukrPool(): List<JSONObject> {
        val l = ArrayList<JSONObject>()
        val n = ukr.getJSONArray("sections").length()
        for (i in 0 until n) l.addAll(secQ(i))
        val pn = ukr.getJSONArray("practice").length()
        for (i in 0 until pn) l.addAll(practiceQs(i))
        for ((w, i) in stressWords()) l.add(stressQ(w, i))
        val ph = phrases()
        for (i in ph.indices) l.add(phrQ(i, ph))
        return l
    }

    private fun ukrDue(): Int {
        val s = stats()
        var n = 0
        for (q in ukrPool()) {
            val x = st(s, q)
            if (x[0] > 0 && x[2] < 3) n++
        }
        return n
    }

    private fun sectionTitle(text: String): TextView {
        val h = tv(text, 20f, true)
        h.setPadding(0, dp(20), 0, dp(6))
        return h
    }

    private fun siteU(): LinearLayout {
        val c = card()
        c.addView(tv("ℹ️ Для пробного проходження НМТ з української мови користуйся сайтом zno.osvita.ua:", 14f, false, MU))
        c.addView(btn("Тренувальні варіанти НМТ") { open("https://zno.osvita.ua/ukrmova/nmt.html") })
        c.addView(btn("Завдання за темами", false) { open("https://zno.osvita.ua/ukrainian/tema.html") })
        return c
    }

    private fun ukrHome() {
        show({ home() }) {
            addView(h1("Українська мова"))
            addView(tv("Правила, тести як на НМТ, наголоси й фразеологізми", 14f, false, MU))
            val due = ukrDue()
            val rsub = if (due == 0) "помилки повертаються частіше" else "до повторення: $due"
            val r1 = tiles(
                tile("🎯", AC, "Змішаний тест", "20 завдань, як на НМТ") { ukrMixed() },
                tile("🔁", OK, "Повторення", rsub) { reviewOf(ukrPool()) { ukrHome() } }
            )
            val r2 = tiles(
                tile("🔊", ERA[5], "Наголоси", "словник і тренажер") { ukrStress() },
                tile("💬", ERA[3], "Фразеологізми", "значення й тренажер") { ukrPhr() }
            )
            (r1.layoutParams as LinearLayout.LayoutParams).topMargin = dp(14)
            addView(r1)
            addView(r2)
            addView(sectionTitle("Розділи програми"))
            addView(navRow("✍️", AC, "Практика письма", "е/и, апостроф, відмінки, ступені — пиши й закріплюй") { ukrPractice() })
            val secs = ukr.getJSONArray("sections")
            for (i in 0 until secs.length()) {
                val sec = secs.getJSONObject(i)
                val v = prefs().getFloat("u_" + sec.getString("id"), -1f)
                val sub = if (v < 0) "правила, тест, вправи" else "останній тест: ${(v * 100).toInt()}%"
                addView(navRow(sec.getString("ic"), ERA[i % ERA.size], sec.getString("t"), sub) { ukrSection(i) })
            }
            addView(siteU())
        }
    }

    // Окреме меню: тут учимося писати, а не здаємо тест
    private fun ukrPractice() {
        show({ ukrHome() }) {
            addView(h1("✍️ Практика письма"))
            val info = card()
            info.addView(tv("Тут не тест, а навчання. Пиши відповідь сам: є підказка з правилом, а після помилки можна спробувати ще раз.", 15f))
            addView(info)
            val pr = ukr.getJSONArray("practice")
            addView(btn("Змішана практика (15 завдань)") {
                val all = ArrayList<JSONObject>()
                for (i in 0 until pr.length()) all.addAll(practiceQs(i))
                quiz(all.shuffled().take(15), "p", "Практика письма") { ukrPractice() }
            })
            addView(sectionTitle("Теми"))
            for (i in 0 until pr.length()) {
                val c = pr.getJSONObject(i)
                val n = c.getJSONArray("items").length()
                val v = prefs().getFloat("p_" + c.getString("id"), -1f)
                val sub = if (v < 0) "$n завдань" else "$n завдань · останній результат ${(v * 100).toInt()}%"
                addView(navRow(c.getString("ic"), ERA[i % ERA.size], c.getString("t"), sub) {
                    quiz(practiceQs(i).shuffled().take(10), "p_" + c.getString("id"), c.getString("t")) { ukrPractice() }
                })
            }
            addView(btn("Назад", false) { ukrHome() })
        }
    }

    private fun ukrMixed() {
        val pool = ukrPool()
        val c = pool.filter { it.optString("t", "c") == "c" && !it.has("nr") }.shuffled().take(12)
        val sx = pool.filter { it.optString("t") == "s" }.shuffled().take(3)
        val w = pool.filter { it.optString("t") == "w" }.shuffled().take(2)
        val m = pool.filter { it.optString("t") == "m" }.shuffled().take(3)
        quiz(c + sx + w + m, "u", "Змішаний тест") { ukrHome() }
    }

    private fun ukrSection(i: Int) {
        val sec = ukr.getJSONArray("sections").getJSONObject(i)
        val era = ERA[i % ERA.size]
        show({ ukrHome() }) {
            val chip = tv("Розділ ${i + 1}", 13f, true, ON_AC)
            chip.setPadding(dp(12), dp(4), dp(12), dp(4))
            chip.background = bg(era, era, 20)
            chip.layoutParams = LinearLayout.LayoutParams(-2, -2)
            addView(chip)
            addView(h1(sec.getString("t")))
            addView(memoCard(sec))
            addView(sectionTitle("Правила"))
            val rc = card()
            val rules = sec.getJSONArray("rules")
            for (j in 0 until rules.length()) {
                val t = tv("• " + rules.getString(j), 15f)
                t.setPadding(0, dp(5), 0, dp(5))
                rc.addView(t)
            }
            addView(rc)
            val qs = secQ(i)
            val tests = qs.filter { it.optString("t") != "w" }
            val writes = qs.filter { it.optString("t") == "w" }
            addView(btn("Тест розділу (${tests.size} завдань)") {
                quiz(tests.shuffled(), "u_" + sec.getString("id"), sec.getString("t")) { ukrSection(i) }
            })
            addView(btn("Вправи: впиши відповідь (${writes.size})", false) {
                quiz(writes.shuffled(), "uw", "Вправи: " + sec.getString("t")) { ukrSection(i) }
            })
            addView(btn("Назад", false) { ukrHome() })
        }
    }

    private fun ukrStress() {
        show({ ukrHome() }) {
            addView(h1("Наголоси"))
            val words = stressWords()
            val info = card()
            info.addView(tv("${words.size} слів, у яких найчастіше помиляються. У тренажері натисни наголошену голосну.", 15f))
            addView(info)
            addView(btn("Тренувати наголоси (15)") {
                quiz(words.shuffled().take(15).map { stressQ(it.first, it.second) }, "us", "Наголоси") { ukrStress() }
            })
            addView(sectionTitle("Словник"))
            val sb = SpannableStringBuilder()
            val raw = ukr.getJSONArray("stress")
            for (j in 0 until raw.length()) {
                val w = raw.getString(j)
                val start = sb.length
                sb.append(w)
                val ui = w.indexOfFirst { it.isUpperCase() }
                if (ui >= 0) {
                    sb.setSpan(ForegroundColorSpan(AC), start + ui, start + ui + 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                    sb.setSpan(StyleSpan(Typeface.BOLD), start + ui, start + ui + 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                }
                if (j < raw.length() - 1) sb.append("   ·   ")
            }
            val dc = card()
            val dt = tv("", 18f)
            dt.setLineSpacing(0f, 1.35f)
            dt.text = sb
            dc.addView(dt)
            addView(dc)
            addView(tv("Золота велика літера — наголошена голосна.", 12f, false, MU))
            addView(btn("Назад", false) { ukrHome() })
        }
    }

    private fun ukrPhr() {
        show({ ukrHome() }) {
            addView(h1("Фразеологізми"))
            val ph = phrases()
            addView(btn("Тренувати (10 завдань)") {
                val idx = ph.indices.shuffled().take(8)
                val l = ArrayList<JSONObject>()
                for (i in idx) l.add(phrQ(i, ph))
                l.add(phrMatch(ph))
                l.add(phrMatch(ph))
                quiz(l, "up", "Фразеологізми") { ukrPhr() }
            })
            addView(sectionTitle("Словник"))
            for ((p, m) in ph) {
                val c = card()
                c.addView(tv(p, 17f, true))
                c.addView(tv(m, 14f, false, MU))
                addView(c)
            }
            addView(btn("Назад", false) { ukrHome() })
        }
    }

    // ───────────── Тест ─────────────

    private val lockRx = Regex("(?iu)усі|всі|жодн|обидв|вище|правильн")

    private class Opt(val row: LinearLayout, val badge: TextView, val label: TextView)

    private fun norm(s: String): String =
        s.trim().lowercase().replace("’", "'").replace("ʼ", "'").replace("`", "'").replace(Regex("\\s+"), " ")

    private fun hideKb(v: View) {
        try {
            val im = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            im.hideSoftInputFromWindow(v.windowToken, 0)
        } catch (e: Exception) {
        }
    }

    private fun renderMcq(q: JSONObject, after: (Boolean, String) -> Unit) {
        val o = q.getJSONArray("o")
        val a0 = q.getInt("a")
        val order = ArrayList<Int>()
        for (j in 0 until o.length()) order.add(j)
        var locked = false
        for (j in 0 until o.length()) if (lockRx.containsMatchIn(o.getString(j))) locked = true
        if (!locked) order.shuffle()
        val a = order.indexOf(a0)
        val opts = ArrayList<Opt>()
        var answered = false
        for (j in 0 until o.length()) {
            val r = LinearLayout(this)
            r.orientation = LinearLayout.HORIZONTAL
            r.gravity = Gravity.CENTER_VERTICAL
            r.setPadding(dp(12), dp(12), dp(14), dp(12))
            r.layoutParams = lp()
            r.background = ripple(bg(CARD2, BD, 16))
            val bd = badge("АБВГДЕ"[j].toString(), tint(Color.WHITE, 0x22), 32, 14f)
            r.addView(bd)
            val lb = tv(o.getString(order[j]), 16f)
            val lpp = LinearLayout.LayoutParams(0, -2, 1f)
            lpp.leftMargin = dp(12)
            lb.layoutParams = lpp
            r.addView(lb)
            pressFx(r)
            opts.add(Opt(r, bd, lb))
            r.setOnClickListener {
                if (answered) return@setOnClickListener
                answered = true
                val good = j == a
                for ((m, x) in opts.withIndex()) {
                    if (m == a) {
                        x.row.background = ripple(bg(tint(OK, 0x33), OK, 16))
                        x.badge.background = circle(OK)
                        x.badge.text = "✓"
                        x.badge.setTextColor(ON_AC)
                        x.row.animate().scaleX(1.03f).scaleY(1.03f).setDuration(150).withEndAction {
                            x.row.animate().scaleX(1f).scaleY(1f).setDuration(150).start()
                        }.start()
                    } else if (m == j) {
                        x.row.background = ripple(bg(tint(BAD, 0x33), BAD, 16))
                        x.badge.background = circle(BAD)
                        x.badge.text = "✕"
                        x.badge.setTextColor(ON_AC)
                        val sh = dp(10).toFloat()
                        ObjectAnimator.ofFloat(x.row, "translationX", 0f, -sh, sh, -sh / 2, sh / 2, 0f).setDuration(350).start()
                    } else {
                        x.row.animate().alpha(0.45f).setDuration(200).start()
                    }
                }
                after(good, "")
            }
            box.addView(r)
        }
    }

    // Відповідність: 4 рядки, 5 варіантів (А–Д), як на НМТ
    private fun renderMatch(q: JSONObject, after: (Boolean, String) -> Unit) {
        val l = q.getJSONArray("l")
        val r = q.getJSONArray("r")
        val a = q.getJSONArray("a")
        val letters = "АБВГД"
        val oc = card()
        for (j in 0 until r.length()) {
            val t = tv("${letters[j]}   ${r.getString(j)}", 15f)
            t.setPadding(0, dp(3), 0, dp(3))
            oc.addView(t)
        }
        box.addView(oc)
        val sel = IntArray(l.length()) { -1 }
        val chips = ArrayList<ArrayList<TextView>>()
        var done = false
        for (i in 0 until l.length()) {
            val rc = card()
            rc.addView(tv("${i + 1}. ${l.getString(i)}", 16f))
            val rowc = LinearLayout(this)
            rowc.orientation = LinearLayout.HORIZONTAL
            val rp = LinearLayout.LayoutParams(-2, -2)
            rp.topMargin = dp(10)
            rowc.layoutParams = rp
            val mine = ArrayList<TextView>()
            for (j in 0 until r.length()) {
                val ch = TextView(this)
                ch.text = letters[j].toString()
                ch.gravity = Gravity.CENTER
                ch.textSize = 16f
                ch.setTextColor(TX)
                ch.background = circle(tint(Color.WHITE, 0x22))
                val cp = LinearLayout.LayoutParams(dp(44), dp(44))
                cp.rightMargin = dp(8)
                ch.layoutParams = cp
                ch.setOnClickListener {
                    if (done) return@setOnClickListener
                    sel[i] = j
                    for ((m, c2) in mine.withIndex()) {
                        c2.background = circle(if (m == j) AC else tint(Color.WHITE, 0x22))
                        c2.setTextColor(if (m == j) ON_AC else TX)
                    }
                }
                mine.add(ch)
                rowc.addView(ch)
            }
            chips.add(mine)
            rc.addView(rowc)
            box.addView(rc)
        }
        val check = btn("Перевірити") {}
        check.setOnClickListener {
            if (done) return@setOnClickListener
            if (sel.any { it < 0 }) {
                Toast.makeText(this@MainActivity, "Обери відповідь у кожному рядку", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            done = true
            var good = true
            val keyTxt = StringBuilder()
            for (i in sel.indices) {
                val c = a.getInt(i)
                if (sel[i] == c) {
                    chips[i][c].background = circle(OK)
                    chips[i][c].setTextColor(ON_AC)
                } else {
                    good = false
                    chips[i][sel[i]].background = circle(BAD)
                    chips[i][sel[i]].setTextColor(ON_AC)
                    chips[i][c].background = circle(OK)
                    chips[i][c].setTextColor(ON_AC)
                }
                keyTxt.append("${i + 1}–${letters[c]}   ")
            }
            check.visibility = View.GONE
            after(good, if (good) "" else "Правильно: " + keyTxt.toString().trim())
        }
        box.addView(check)
    }

    // Вписування відповіді (для запам'ятовування; на справжньому НМТ таких завдань немає)
    private fun renderWrite(q: JSONObject, after: (Boolean, String) -> Unit) {
        val et = EditText(this)
        et.hint = "Твоя відповідь"
        et.setHintTextColor(MU)
        et.setTextColor(TX)
        et.textSize = 18f
        et.isSingleLine = true
        et.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
        et.setPadding(dp(16), dp(14), dp(16), dp(14))
        et.background = bg(CARD2, BD, 16)
        et.layoutParams = lp()
        box.addView(et)
        val retry = tv("", 14f, false, WARN)
        retry.setPadding(dp(4), dp(8), dp(4), 0)
        retry.visibility = View.GONE
        box.addView(retry)
        var done = false
        var tries = 0
        val check = btn("Перевірити") {}
        check.setOnClickListener {
            if (done) return@setOnClickListener
            val u = norm(et.text.toString())
            if (u.isEmpty()) {
                Toast.makeText(this@MainActivity, "Впиши відповідь", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            tries++
            val ans = q.getJSONArray("ans")
            var good = false
            for (i in 0 until ans.length()) if (norm(ans.getString(i)) == u) good = true
            // У практиці після першої помилки даємо ще одну спробу
            if (!good && q.optBoolean("pr", false) && tries < 2) {
                et.background = bg(tint(BAD, 0x33), BAD, 16)
                retry.text = "Майже! Спробуй ще раз — подивись на підказку."
                retry.visibility = View.VISIBLE
                ObjectAnimator.ofFloat(et, "translationX", 0f, -dp(10).toFloat(), dp(10).toFloat(), 0f).setDuration(280).start()
                return@setOnClickListener
            }
            done = true
            et.isEnabled = false
            val col = if (good) OK else BAD
            et.background = bg(tint(col, 0x33), col, 16)
            retry.visibility = View.GONE
            hideKb(et)
            check.visibility = View.GONE
            after(good && tries == 1, if (good) "" else "Правильна відповідь: " + ans.getString(0))
        }
        box.addView(check)
    }

    // Наголос: натисни наголошену голосну
    private fun renderStress(q: JSONObject, after: (Boolean, String) -> Unit) {
        val w = q.getString("w")
        val idx = q.getInt("i")
        val c = card()
        c.gravity = Gravity.CENTER_HORIZONTAL
        val hint = tv("Натисни наголошену голосну", 13f, false, MU)
        hint.gravity = Gravity.CENTER
        c.addView(hint)
        val wt = TextView(this)
        wt.textSize = 38f
        wt.typeface = Typeface.create("serif", Typeface.BOLD)
        wt.gravity = Gravity.CENTER
        wt.setTextColor(TX)
        wt.setPadding(0, dp(14), 0, dp(14))
        val sp = SpannableString(w)
        var done = false
        for (i in w.indices) {
            if (VOW.indexOf(w[i]) >= 0) {
                sp.setSpan(object : ClickableSpan() {
                    override fun onClick(widget: View) {
                        if (done) return
                        done = true
                        val good = i == idx
                        wt.text = w.substring(0, idx + 1) + "\u0301" + w.substring(idx + 1)
                        wt.setTextColor(if (good) OK else BAD)
                        after(good, "")
                    }

                    override fun updateDrawState(ds: TextPaint) {
                        ds.color = AC
                        ds.isUnderlineText = true
                    }
                }, i, i + 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
        }
        wt.text = sp
        wt.movementMethod = LinkMovementMethod.getInstance()
        wt.highlightColor = Color.TRANSPARENT
        c.addView(wt)
        box.addView(c)
    }

    private fun quiz(qs: List<JSONObject>, key: String, title: String, ret: () -> Unit) {
        if (qs.isEmpty()) {
            Toast.makeText(this, "Немає завдань", Toast.LENGTH_SHORT).show()
            return
        }
        var k = 0
        var ok = 0
        val wrong = ArrayList<JSONObject>()
        fun end() {
            val p = ok.toFloat() / qs.size
            if (key != "r" && key != "w") prefs().edit().putFloat(key, p).apply()
            addHistory(title, ok, qs.size)
            val ringColor = if (p >= 0.7f) OK else if (p >= 0.5f) WARN else BAD
            val isUkr = key.startsWith("u") || key.startsWith("p")
            show(ret) {
                addView(tv(title, 14f, false, MU))
                addView(h1("Результат"))
                val c = card()
                c.gravity = Gravity.CENTER_HORIZONTAL
                val fl = FrameLayout(this@MainActivity)
                val flp = LinearLayout.LayoutParams(dp(180), dp(180))
                flp.topMargin = dp(8)
                flp.bottomMargin = dp(8)
                fl.layoutParams = flp
                val ring = RingView(this@MainActivity)
                ring.color = ringColor
                ring.track = 0x33FFFFFF
                ring.stroke = dp(14).toFloat()
                fl.addView(ring, FrameLayout.LayoutParams(-1, -1))
                val sc = tv("0 / ${qs.size}", 34f, true)
                sc.gravity = Gravity.CENTER
                fl.addView(sc, FrameLayout.LayoutParams(-2, -2, Gravity.CENTER))
                c.addView(fl)
                ring.animateTo(p)
                val va = ValueAnimator.ofInt(0, ok)
                va.duration = 900
                va.addUpdateListener { sc.text = "${it.animatedValue} / ${qs.size}" }
                va.start()
                val msg = if (p >= 0.9f) "Блискуче!" else if (p >= 0.7f) "Гарний результат" else if (p >= 0.5f) "Непогано, є що підтягнути" else "Варто повторити правила"
                val mt = tv(msg, 20f, true)
                mt.gravity = Gravity.CENTER
                c.addView(mt)
                val noPts = key == "r" || key == "w" || isUkr
                val pts = if (noPts) "" else " Орієнтовно ≈ ${(100 + p * 100).toInt()} балів зі 200."
                val dt = tv("Правильно: ${(p * 100).toInt()}%.$pts", 14f, false, MU)
                dt.gravity = Gravity.CENTER
                dt.setPadding(0, dp(4), 0, 0)
                c.addView(dt)
                addView(c)
                if (wrong.isNotEmpty()) {
                    addView(btn("Розібрати помилки (${wrong.size})") { quiz(ArrayList(wrong), "w", "Помилки", ret) })
                }
                addView(btn("Готово", wrong.isEmpty()) { ret() })
                if (isUkr) addView(siteU()) else if (key != "r" && key != "w") addView(site())
            }
        }
        fun ask() {
            val q = qs[k]
            val t = q.optString("t", "c")
            val after: (Boolean, String) -> Unit = { good, extra ->
                if (good) ok++ else wrong.add(q)
                if (!q.optBoolean("nr", false)) record(q, good)
                box.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                val ex = (q.optString("e", "") + (if (extra.isNotEmpty()) "\n" + extra else "")).trim()
                if (ex.isNotEmpty()) {
                    val ec = card()
                    ec.addView(tv("💡 " + ex, 15f, false, 0xFFD6E0F7.toInt()))
                    if (!good) {
                        box.addView(ec)
                        ec.alpha = 0f
                        ec.animate().alpha(1f).setDuration(250).start()
                    } else {
                        val eb = btn("Чому так?", false) {}
                        eb.setOnClickListener {
                            eb.visibility = View.GONE
                            box.addView(ec, box.indexOfChild(eb) + 1)
                            ec.alpha = 0f
                            ec.animate().alpha(1f).setDuration(250).start()
                        }
                        box.addView(eb)
                    }
                }
                val nb = btn(if (k + 1 < qs.size) "Далі" else "Результат") {
                    k++
                    if (k < qs.size) ask() else end()
                }
                nb.alpha = 0f
                box.addView(nb)
                nb.animate().alpha(1f).setDuration(250).start()
                root.postDelayed({ root.smoothScrollTo(0, box.height) }, 300)
            }
            show(ret) {
                val head = LinearLayout(this@MainActivity)
                head.orientation = LinearLayout.HORIZONTAL
                val l1 = tv(title, 14f, false, MU)
                l1.layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
                head.addView(l1)
                head.addView(tv("${k + 1} / ${qs.size}", 14f, true))
                addView(head)
                addView(bar((k + 1).toFloat() / qs.size, AC))
                val qc = card()
                qc.addView(tv(q.getString("q"), 20f, true))
                addView(qc)
                val hintTxt = q.optString("h", "")
                if (hintTxt.isNotEmpty()) {
                    val ht = tv("💡 Підказка: " + hintTxt, 13f, false, MU)
                    ht.setPadding(dp(4), dp(8), dp(4), 0)
                    addView(ht)
                }
                when (t) {
                    "m" -> renderMatch(q, after)
                    "w" -> renderWrite(q, after)
                    "s" -> renderStress(q, after)
                    else -> renderMcq(q, after)
                }
            }
        }
        ask()
    }
}

// Кільце прогресу, що плавно заповнюється
class RingView(ctx: Context) : View(ctx) {
    var color: Int = 0xFFFFC94D.toInt()
    var track: Int = 0x33FFFFFF
    var stroke: Float = 24f
    private var prog = 0f
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rect = RectF()

    fun animateTo(target: Float) {
        val va = ValueAnimator.ofFloat(0f, target.coerceIn(0f, 1f))
        va.duration = 900
        va.interpolator = DecelerateInterpolator()
        va.addUpdateListener {
            prog = it.animatedValue as Float
            invalidate()
        }
        va.start()
    }

    override fun onDraw(c: Canvas) {
        super.onDraw(c)
        val pad = stroke / 2f
        rect.set(pad, pad, width - pad, height - pad)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = stroke
        paint.strokeCap = Paint.Cap.ROUND
        paint.color = track
        c.drawArc(rect, 0f, 360f, false, paint)
        if (prog > 0f) {
            paint.color = color
            c.drawArc(rect, -90f, 360f * prog, false, paint)
        }
    }
}
