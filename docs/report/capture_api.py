"""Calls the two external APIs exactly like the app does and saves the real responses as images
for the project report (docs/report/screenshots/api_*.png).  Run:  python -I capture_api.py"""
import datetime
import json
import os
import urllib.request

from PIL import Image, ImageDraw, ImageFont

HERE = os.path.dirname(os.path.abspath(__file__))
OUT = os.path.join(HERE, "screenshots")
UA = "ShareBite-Android/1.0.0 (com.sharebite.app)"  # same User-Agent as the app

REQUESTS = {
    "api_nominatim": ("Nominatim (OpenStreetMap) geocoding: address -> coordinates",
                      "https://nominatim.openstreetmap.org/search?format=jsonv2&limit=1&q=Banani%2C%20Dhaka"),
    "api_openmeteo": ("Open-Meteo weather at the pickup point (Banani, Dhaka)",
                      "https://api.open-meteo.com/v1/forecast?latitude=23.7937&longitude=90.4066"
                      "&current=temperature_2m,relative_humidity_2m,weather_code&timezone=auto"),
}


def font(size, bold=False):
    names = ["consolab.ttf" if bold else "consola.ttf", "DejaVuSansMono-Bold.ttf" if bold else "DejaVuSansMono.ttf"]
    for n in names:
        for base in (r"C:\Windows\Fonts", "/usr/share/fonts/truetype/dejavu"):
            p = os.path.join(base, n)
            if os.path.exists(p):
                return ImageFont.truetype(p, size)
    return ImageFont.load_default()


def render(key, title, url, status, headers, body):
    pretty = json.dumps(body, indent=2, ensure_ascii=False).splitlines()
    shown = pretty[:44] + (["  ..."] if len(pretty) > 44 else [])
    f, fb, fs = font(17), font(18, True), font(15)
    width, line = 1150, 23
    wrapped_url = [url[i:i + 108] for i in range(0, len(url), 108)]
    height = 120 + line * (len(wrapped_url) + len(shown) + 3)
    im = Image.new("RGB", (width, height), "#0F172A")
    d = ImageDraw.Draw(im)
    d.rectangle((0, 0, width, 44), fill="#1E293B")
    for i, c in enumerate(("#F87171", "#FBBF24", "#34D399")):
        d.ellipse((16 + i * 22, 15, 30 + i * 22, 29), fill=c)
    d.text((95, 12), title, font=fb, fill="#E2E8F0")
    y = 60
    d.text((20, y), "GET", font=fb, fill="#34D399")
    for i, part in enumerate(wrapped_url):
        d.text((70, y + i * line), part, font=f, fill="#93C5FD")
    y += line * len(wrapped_url) + 6
    stamp = datetime.datetime.now().strftime("%Y-%m-%d %H:%M")
    d.text((20, y), f"HTTP {status}  |  {headers.get('Content-Type', '')}  |  captured {stamp}", font=fs, fill="#94A3B8")
    y += line + 10
    for row in shown:
        color = "#E2E8F0"
        stripped = row.strip()
        if stripped.startswith('"') and '":' in stripped:
            k, _, v = row.partition('":')
            d.text((20, y), k + '":', font=f, fill="#F9A8D4")
            kw = d.textlength(k + '":', font=f)
            d.text((20 + kw, y), v, font=f, fill="#FDE68A" if v.strip().startswith('"') else "#A7F3D0")
        else:
            d.text((20, y), row, font=f, fill=color)
        y += line
    im.save(os.path.join(OUT, key + ".png"))


def main():
    os.makedirs(OUT, exist_ok=True)
    for key, (title, url) in REQUESTS.items():
        req = urllib.request.Request(url, headers={"User-Agent": UA, "Accept": "application/json"})
        with urllib.request.urlopen(req, timeout=30) as r:
            body = json.load(r)
            render(key, title, url, r.status, dict(r.headers), body)
            print("saved", key, r.status)


if __name__ == "__main__":
    main()
