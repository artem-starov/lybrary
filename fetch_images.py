#!/usr/bin/env python3
"""Запускається на GitHub під час збірки (там є інтернет).
Завантажує всі картинки й відповіді Вікіпедії та кладе їх у assets/img,
щоб вони були вшиті в додаток і працювали без інтернету з першого запуску.
Імена файлів = MD5 від URL, так само, як у додатку (MainActivity.cacheName)."""
import hashlib, json, os, sys, time, urllib.error, urllib.parse, urllib.request

OUT = "app/src/main/assets/img"
REPO = os.environ.get("GITHUB_REPOSITORY", "nmt-study-app")
# Вікіпедія вимагає описовий User-Agent з посиланням на проєкт, інакше може відхиляти запити
UA = "NMTStudyApp/1.1 (https://github.com/%s; student study app) python-urllib/3" % REPO
os.makedirs(OUT, exist_ok=True)


def enc(q):
    # те саме, що Uri.encode в Android
    return urllib.parse.quote(q, safe="_-!.~'()*")


def wiki_url(q):
    return ("https://uk.wikipedia.org/w/api.php?action=query&generator=search&gsrsearch=" + enc(q) +
            "&gsrlimit=1&prop=pageimages&piprop=thumbnail&pithumbsize=500&format=json")


def commons_url(q):
    return ("https://commons.wikimedia.org/w/api.php?action=query&generator=search&gsrnamespace=6&gsrlimit=5&gsrsearch=" +
            enc(q) + "&prop=imageinfo&iiprop=url%7Cmime&iiurlwidth=960&format=json")


total = 0
fails = 0
streak = 0


def get(u):
    """Повертає байти (з диска або з мережі) і зберігає їх під ім'ям md5(url)."""
    global total, fails, streak
    path = os.path.join(OUT, hashlib.md5(u.encode("utf-8")).hexdigest())
    if os.path.exists(path) and os.path.getsize(path) > 0:
        return open(path, "rb").read()
    reason = "?"
    for attempt in range(5):
        try:
            req = urllib.request.Request(u, headers={"User-Agent": UA, "Accept": "*/*"})
            with urllib.request.urlopen(req, timeout=30) as r:
                data = r.read()
            if data:
                with open(path, "wb") as f:
                    f.write(data)
                total += len(data)
                streak = 0
                time.sleep(0.5)
                return data
            reason = "порожня відповідь"
        except urllib.error.HTTPError as e:
            reason = "HTTP %d" % e.code
            if e.code in (404, 400):
                break
            wait = 5 * (attempt + 1)
            try:
                wait = max(wait, int(e.headers.get("Retry-After", "0")))
            except Exception:
                pass
            time.sleep(min(wait, 60))
            continue
        except Exception as e:
            reason = type(e).__name__ + ": " + str(e)
        time.sleep(3 * (attempt + 1))
    fails += 1
    streak += 1
    print("FAIL [%s] %s" % (reason, u[:150]), file=sys.stderr)
    if streak >= 8:
        print("Забагато помилок поспіль — зупиняюсь. Додаток докачає картинки сам при першому запуску.", file=sys.stderr)
        print("Готово: %.1f МБ, помилок: %d" % (total / 1048576, fails))
        sys.exit(0)
    return None


def thumbs_wiki(body):
    out = []
    try:
        pages = json.loads(body)["query"]["pages"]
        for p in pages.values():
            src = (p.get("thumbnail") or {}).get("source")
            if src:
                out.append(src)
    except Exception:
        pass
    return out


def thumbs_commons(body):
    out = []
    try:
        pages = list(json.loads(body)["query"]["pages"].values())
        pages.sort(key=lambda p: p.get("index", 99))
        for p in pages:
            ii = (p.get("imageinfo") or [{}])[0]
            if not str(ii.get("mime", "")).startswith("image/"):
                continue
            out.append(ii.get("thumburl") or ii.get("url"))
            if len(out) >= 3:
                break
    except Exception:
        pass
    return [u for u in out if u]


data = json.load(open("data.json", encoding="utf-8"))
wq, mq = [], []
for g in data:
    for s in g["s"]:
        if "Тема в підручнику: " in s:
            tp = s.split("Тема в підручнику: ", 1)[1]
            if tp:
                wq.append(tp)
    for o in g["p"]:
        wq.append(o["name"])
    for m in g.get("maps", []):
        mq.append(m[1])

for q in wq + mq:
    body = get(wiki_url(q))
    if body:
        for u in thumbs_wiki(body):
            get(u)
for q in mq:
    body = get(commons_url(q))
    if body:
        for u in thumbs_commons(body):
            get(u)

print("Готово: %.1f МБ нових даних, помилок: %d, файлів: %d" % (total / 1048576, fails, len(os.listdir(OUT))))
