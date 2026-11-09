"""Prepares demo pictures for seed.mjs.

* Food and cover photos: searched on Wikimedia Commons, keeping only openly licensed files
  (CC0 / public domain / CC BY / CC BY-SA), resized to 1024x768 JPEGs. Author and licence are
  written to credits.json and shown in the app as the photo credit.
* Logos: generated here (initials on a coloured badge) for the fictional demo businesses, so no
  real brand is copied.

Run:  python -I prepare_images.py      (needs Pillow; writes photos/, logos/, credits.json)
"""
import html
import io
import json
import os
import re
import sys
import time
import urllib.parse
import urllib.error
import urllib.request

from PIL import Image, ImageDraw, ImageFont

HERE = os.path.dirname(os.path.abspath(__file__))
PHOTOS = os.path.join(HERE, "photos")
LOGOS = os.path.join(HERE, "logos")
UA = "ShareBite-demo-seed/1.0 (student project; image preparation script)"
OK_LICENSES = ("cc0", "public domain", "cc by", "cc-by", "pd")

# key -> Commons search terms (first openly licensed landscape-ish match wins)
PHOTO_QUERIES = {
    "croissants": "croissants plate",
    "bread": "bread loaves bakery",
    "cinnamon": "cinnamon rolls",
    "chocolate_cake": "chocolate cake slice",
    "biryani": "chicken biryani",
    "veg_curry": "vegetable korma",
    "dal": "dal tadka",
    "naan": "naan bread",
    "sandwich": "sandwiches platter",
    "salad": "salad bowl vegetables",
    "pasta": "pasta dish tomato",
    "muffins": "blueberry muffins",
    "fried_rice": "vegetable fried rice",
    "samosa": "samosa",
    "khichuri": "khichuri",
    "paratha": "paratha",
    "bananas": "bunch of bananas",
    "mangoes": "mangoes fruit",
    "vegetables": "fresh vegetables assortment",
    "yogurt": "yogurt cups",
    "kacchi": "kacchi biryani",
    "chicken_roast": "chicken roast bengali",
    "phirni": "phirni dessert",
    "cupcakes": "cupcakes",
    "doughnuts": "glazed donuts",
    "cookies": "chocolate chip cookies",
    "pitha": "pitha bangladesh",
    "chicken_curry": "chicken curry",
    "fish_curry": "fish curry rice",
}

# Fictional demo businesses: key -> (initials, colour)
LOGO_SPECS = {
    "golden_crust": ("GC", "#B54708"),
    "spice_route": ("SR", "#C11574"),
    "green_leaf": ("GL", "#0B6B4F"),
    "campus_bites": ("CB", "#175CD3"),
    "fresh_basket": ("FB", "#4E9F3D"),
    "royal_biryani": ("RB", "#6941C6"),
    "sweet_moments": ("SM", "#E04F8F"),
    "nadias_kitchen": ("NK", "#0E7090"),
}


def api(params):
    url = "https://commons.wikimedia.org/w/api.php?" + urllib.parse.urlencode(params)
    req = urllib.request.Request(url, headers={"User-Agent": UA})
    with urllib.request.urlopen(req, timeout=30) as r:
        return json.load(r)


def strip_html(s):
    return html.unescape(re.sub(r"<[^>]+>", "", s or "")).strip()


def find_photo(query):
    data = with_backoff(lambda: api({
        "action": "query", "format": "json", "generator": "search",
        "gsrsearch": query + " filetype:bitmap", "gsrnamespace": 6, "gsrlimit": 15,
        "prop": "imageinfo", "iiprop": "url|extmetadata|size|mime", "iiurlwidth": 1280,
    }))
    pages = sorted(data.get("query", {}).get("pages", {}).values(), key=lambda p: p.get("index", 99))
    for p in pages:
        info = (p.get("imageinfo") or [{}])[0]
        meta = info.get("extmetadata", {})
        lic = strip_html(meta.get("LicenseShortName", {}).get("value", "")).lower()
        if info.get("mime") != "image/jpeg" or not any(l in lic for l in OK_LICENSES):
            continue
        w, h = info.get("width", 0), info.get("height", 0)
        if w < 900 or h < 600 or w < h:  # landscape only, decent size
            continue
        artist = strip_html(meta.get("Artist", {}).get("value", "")) or "Unknown author"
        artist = re.sub(r"\s+", " ", artist)[:60]
        license_name = strip_html(meta.get("LicenseShortName", {}).get("value", ""))
        return {
            "url": info.get("thumburl") or info["url"],
            "credit": f"{artist}, {license_name}, via Wikimedia Commons",
            "source": info.get("descriptionurl"),
        }
    return None


def download(url):
    return with_backoff(lambda: _fetch(url))


def _fetch(url):
    req = urllib.request.Request(url, headers={"User-Agent": UA})
    with urllib.request.urlopen(req, timeout=60) as r:
        return r.read()


def with_backoff(fn, attempts=6):
    """Wikimedia rate-limits bursts (HTTP 429); wait and retry politely."""
    delay = 30
    for i in range(attempts):
        try:
            return fn()
        except urllib.error.HTTPError as e:
            if e.code != 429 or i == attempts - 1:
                raise
            print("  rate limited, waiting", delay, "s", flush=True)
            time.sleep(delay)
            delay = min(delay * 2, 240)


def to_jpeg(raw, out_path, size=(1024, 768)):
    im = Image.open(io.BytesIO(raw)).convert("RGB")
    # centre-crop to 4:3, then resize
    tw, th = size
    w, h = im.size
    target = tw / th
    if w / h > target:
        nw = int(h * target)
        im = im.crop(((w - nw) // 2, 0, (w - nw) // 2 + nw, h))
    else:
        nh = int(w / target)
        im = im.crop((0, (h - nh) // 2, w, (h - nh) // 2 + nh))
    im = im.resize(size, Image.LANCZOS)
    im.save(out_path, "JPEG", quality=78, optimize=True, progressive=True)


def font(size):
    for name in ("segoeuib.ttf", "arialbd.ttf", "DejaVuSans-Bold.ttf"):
        for base in (r"C:\Windows\Fonts", "/usr/share/fonts/truetype/dejavu"):
            path = os.path.join(base, name)
            if os.path.exists(path):
                return ImageFont.truetype(path, size)
    return ImageFont.load_default()


def make_logo(initials, color, out_path, size=512):
    im = Image.new("RGB", (size, size), "white")
    d = ImageDraw.Draw(im)
    pad = 10
    d.ellipse((pad, pad, size - pad, size - pad), fill=color)
    inner = 46
    d.ellipse((inner, inner, size - inner, size - inner), outline="white", width=10)
    f = font(int(size * 0.34))
    box = d.textbbox((0, 0), initials, font=f)
    tw, th = box[2] - box[0], box[3] - box[1]
    d.text(((size - tw) / 2 - box[0], (size - th) / 2 - box[1]), initials, font=f, fill="white")
    im.save(out_path, "JPEG", quality=88)


def main():
    os.makedirs(PHOTOS, exist_ok=True)
    os.makedirs(LOGOS, exist_ok=True)
    credits_path = os.path.join(HERE, "credits.json")
    credits = json.load(open(credits_path, encoding="utf-8")) if os.path.exists(credits_path) else {}
    only = set(sys.argv[1:])
    for key, query in PHOTO_QUERIES.items():
        if only and key not in only:
            continue
        out = os.path.join(PHOTOS, key + ".jpg")
        if os.path.exists(out) and key in credits and not only:
            continue
        try:
            hit = find_photo(query)
            if not hit:
                print("no openly licensed photo for", key)
                continue
            to_jpeg(download(hit["url"]), out)
            credits[key] = {"credit": hit["credit"], "source": hit["source"]}
            print("ok", key, "|", hit["credit"])
            time.sleep(4)
        except Exception as e:  # keep going; a missing photo just falls back to the placeholder
            print("failed", key, e)
    for key, (initials, color) in LOGO_SPECS.items():
        make_logo(initials, color, os.path.join(LOGOS, key + ".jpg"))
    json.dump(credits, open(credits_path, "w", encoding="utf-8"), indent=2, ensure_ascii=False)
    print("photos:", len(os.listdir(PHOTOS)), "logos:", len(os.listdir(LOGOS)))


if __name__ == "__main__":
    main()
