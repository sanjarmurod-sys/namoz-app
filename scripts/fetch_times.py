#!/usr/bin/env python3
"""Telegram kanalidagi haftalik namoz vaqtlari rasmini o'qib, times.json ga yozadi."""
import datetime, io, json, os, re, subprocess, sys, tempfile, urllib.request
from PIL import Image, ImageOps

CHANNEL = "namozvaqti365"
OUT = "times.json"
KEYS = ["fajr", "dhuhr", "asr", "maghrib", "isha"]
# Har bir namoz uchun mantiqiy soat oralig'i (daqiqalarda)
LIMITS = [(180, 450), (660, 870), (810, 1080), (900, 1290), (1020, 1350)]
MAX_JUMP = 30  # o'tgan haftadan eng ko'pi bilan shuncha daqiqa farq


def http(url):
    req = urllib.request.Request(url, headers={"User-Agent": "Mozilla/5.0"})
    return urllib.request.urlopen(req, timeout=40).read()


def tess(img, psm, whitelist="0123456789-."):
    with tempfile.NamedTemporaryFile(suffix=".png", delete=False) as f:
        img.save(f.name)
        r = subprocess.run(["tesseract", f.name, "-", "-l", "eng", "--psm", str(psm),
                            "-c", "tessedit_char_whitelist=" + whitelist],
                           capture_output=True, text=True)
    os.unlink(f.name)
    return r.stdout


def prep(im, thr, target_h=900):
    scale = max(1.0, min(8.0, target_h / max(im.height, 1)))
    im = im.resize((int(im.width * scale), int(im.height * scale)), Image.LANCZOS)
    im = ImageOps.autocontrast(im, cutoff=2).point(lambda v: 0 if v < thr else 255)
    return ImageOps.expand(im, border=40, fill=255)


def read_image(data):
    """Rasmdan (5 ta vaqt, (kun1, oy1), (kun2, oy2)) ni qaytaradi yoki None."""
    im = Image.open(io.BytesIO(data)).convert("L")
    w, h = im.size
    right = im.crop((int(w * 0.5), 0, w, h))
    head = im.crop((0, 0, w, max(int(h * 0.22), 20)))
    times = None
    for thr in (120, 110, 130, 100, 140):
        for psm in (6, 4):
            found = re.findall(r"(?<!\d)(\d{1,2})-(\d{2})(?!\d)", tess(prep(right, thr), psm))
            found = [(int(a), int(b)) for a, b in found if int(a) < 24 and int(b) < 60]
            if len(found) == 5:
                times = found
                break
        if times:
            break
    dates = None
    for thr in (120, 110, 130, 100):
        for crop, psm in ((head, 7), (head, 6), (im, 6)):
            found = re.findall(r"(\d{1,2})\.(\d{1,2})", tess(prep(crop, thr), psm))
            found = [(int(d), int(m)) for d, m in found if 1 <= int(d) <= 31 and 1 <= int(m) <= 12]
            if len(found) >= 2:
                dates = (found[0], found[1])
                break
        if dates:
            break
    if not times or not dates:
        return None
    return times, dates


def validate(times):
    mins = [h * 60 + m for h, m in times]
    for v, (lo, hi) in zip(mins, LIMITS):
        if not lo <= v <= hi:
            return "vaqt mantiqiy oraliqdan tashqarida: %02d:%02d" % (v // 60, v % 60)
    if any(b <= a for a, b in zip(mins, mins[1:])):
        return "vaqtlar ketma-ket emas"
    return None


def build_range(dates, today):
    (d1, m1), (d2, m2) = dates
    y1 = today.year
    if m1 > today.month + 6:
        y1 -= 1
    y2 = y1 + 1 if (m2, d2) < (m1, d1) else y1
    start, end = datetime.date(y1, m1, d1), datetime.date(y2, m2, d2)
    if not 0 <= (end - start).days <= 14:
        return None
    return [start + datetime.timedelta(n) for n in range((end - start).days + 1)]


def latest_photos():
    html = http("https://t.me/s/" + CHANNEL).decode("utf8", "ignore")
    out = []
    for block in html.split("tgme_widget_message_wrap")[1:]:
        post = re.search(r'data-post="([^"]+)"', block)
        img = re.search(r"tgme_widget_message_photo_wrap[^>]*background-image:url\('([^']+)'\)", block)
        if post and img:
            out.append((post.group(1), img.group(1)))
    return list(reversed(out))[:10]  # eng yangisi birinchi


def main():
    today = datetime.date.today()
    prev = {}
    if os.path.exists(OUT):
        try:
            prev = json.load(open(OUT, encoding="utf8"))
        except Exception:
            prev = {}
    photos = latest_photos()
    if not photos:
        print("XATO: kanalda rasm topilmadi (kanal ochiq emasmi yoki sahifa tuzilishi o'zgardi)")
        return 1
    for post, url in photos:
        try:
            r = read_image(http(url))
        except Exception as e:
            print(post, "o'qib bo'lmadi:", e)
            continue
        if not r:
            print(post, "jadval emas yoki o'qib bo'lmadi")
            continue
        times, dates = r
        err = validate(times)
        days = build_range(dates, today)
        if err or not days:
            print(post, "o'tkazib yuborildi:", err or "sana oralig'i noto'g'ri", times, dates)
            continue
        # o'tgan hafta bilan solishtirish
        old = (prev.get("days") or {})
        if old:
            last = old[sorted(old)[-1]]
            for k, (h, m) in zip(KEYS, times):
                ph, pm = map(int, last[k].split(":"))
                if abs((h * 60 + m) - (ph * 60 + pm)) > MAX_JUMP:
                    print("XATO: %s o'tgan haftadan %d daqiqadan ortiq farq qiladi. Jadval o'zgartirilmadi." % (k, MAX_JUMP))
                    return 1
        day = {k: "%02d:%02d" % t for k, t in zip(KEYS, times)}
        merged = {k: v for k, v in old.items() if k >= str(today - datetime.timedelta(days=60))}
        for d in days:
            merged[d.isoformat()] = day
        res = {"city": "Kanal jadvali", "source": "https://t.me/" + CHANNEL, "post": post,
               "updated": today.isoformat(), "days": dict(sorted(merged.items()))}
        if json.dumps(res.get("days")) == json.dumps(old):
            print("Yangi jadval yo'q (o'zgarish topilmadi):", post)
            return 0
        json.dump(res, open(OUT, "w", encoding="utf8"), ensure_ascii=False, indent=1)
        print("OK:", post, days[0], "->", days[-1], day)
        return 0
    print("XATO: oxirgi rasmlardan birortasi ham jadval sifatida o'qilmadi")
    return 1


if __name__ == "__main__":
    sys.exit(main())
