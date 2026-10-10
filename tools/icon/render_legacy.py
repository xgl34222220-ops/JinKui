#!/usr/bin/env python3
"""把 jinkui-icon-b.svg 渲染成旧式（非自适应）图标：mipmap-*/ic_launcher.png（圆角方形）与 ic_launcher_round.png（圆形）。

自适应图标的 108dp 画布里只有中间 72dp 可见，所以先裁出中间 2/3，再按密度缩放。
需要 Python 的 CairoSVG 与 Pillow。用法：python3 tools/icon/render_legacy.py
"""
import io
import os
import cairosvg
from PIL import Image, ImageDraw

HERE = os.path.dirname(os.path.abspath(__file__))
RES = os.path.join(HERE, "..", "..", "app", "src", "main", "res")
SIZES = {"mdpi": 48, "hdpi": 72, "xhdpi": 96, "xxhdpi": 144, "xxxhdpi": 192}


def render(px):
    full = int(round(px * 108 / 72))
    png = cairosvg.svg2png(url=os.path.join(HERE, "jinkui-icon-b.svg"), output_width=full, output_height=full)
    im = Image.open(io.BytesIO(png)).convert("RGBA")
    off = (full - px) // 2
    return im.crop((off, off, off + px, off + px))


def masked(im, round_):
    s = 4
    big = Image.new("L", (im.width * s, im.height * s), 0)
    d = ImageDraw.Draw(big)
    if round_:
        d.ellipse((0, 0, big.width - 1, big.height - 1), fill=255)
    else:
        d.rounded_rectangle((0, 0, big.width - 1, big.height - 1), radius=int(big.width * 0.22), fill=255)
    mask = big.resize(im.size, Image.LANCZOS)
    out = Image.new("RGBA", im.size, (0, 0, 0, 0))
    out.paste(im, (0, 0), mask)
    return out


for name, px in SIZES.items():
    folder = os.path.join(RES, "mipmap-" + name)
    os.makedirs(folder, exist_ok=True)
    im = render(px)
    masked(im, False).save(os.path.join(folder, "ic_launcher.png"), optimize=True)
    masked(im, True).save(os.path.join(folder, "ic_launcher_round.png"), optimize=True)
    print(name, px)
