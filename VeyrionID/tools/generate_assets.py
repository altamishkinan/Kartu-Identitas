#!/usr/bin/env python3
"""
Generator aset VeyrionID (ItemsAdder).
Pakai: python3 generate_assets.py Kartu_Identitas_Veyrion.png <folder-output-contents>
Hasil: contents/veyrion/resourcepack/veyrion/{font,textures}/...
Ubah konstanta di bawah kalau mau geser posisi / ukuran kartu di layar.
"""
import json, sys, os
from PIL import Image, ImageDraw, ImageFont

SRC = sys.argv[1] if len(sys.argv) > 1 else "Kartu_Identitas_Veyrion.png"
OUT = sys.argv[2] if len(sys.argv) > 2 else "contents"
RP = os.path.join(OUT, "veyrion", "resourcepack", "veyrion")
FONT_DIR = os.path.join(RP, "font")
TEX_FONT = os.path.join(RP, "textures", "font")
TEX_ITEM = os.path.join(RP, "textures", "item")
for d in (FONT_DIR, TEX_FONT, TEX_ITEM):
    os.makedirs(d, exist_ok=True)

# ---- layout HUD (satuan: piksel GUI, gui-scale 2 di 1080p) ----
CARD_W, CARD_H = 224, 142          # ukuran kartu di layar
HI = 4                             # resolusi tekstur = 4x
ASC_TOP = 300                      # jarak atas kartu dari baseline actionbar (makin besar = makin ke atas)
GLYPH_H = 310                      # tinggi glyph bg (harus >= ASC_TOP)
SCALE_SRC = CARD_W / 1520.0
VALUE_X = 57                       # x kolom nilai dari kiri kartu
ROW_YC = [388, 498, 606, 716]      # titik tengah baris pada gambar asli
CHAR_W = 5                         # lebar glyph (advance = 6)

img = Image.open(SRC).convert("RGBA")
BG = (255, 250, 240, 255)

# 1) hapus placeholder [NAMA] dst
d = ImageDraw.Draw(img)
for y0, y1 in [(358, 414), (468, 524), (576, 632), (686, 742)]:
    d.rectangle([742, y0, 1420, y1], fill=BG)
# 2) hapus foto/potret, geser blok data ke kiri
SHIFT = 364
Y0, Y1 = 335, 790
block = img.crop((460, Y0, 1424, Y1))
d.rectangle([90, Y0, 1060, Y1], fill=BG)
img.alpha_composite(block.crop((0, 0, 1060 - 96, Y1 - Y0)), (96, Y0))

# ---- font pixel 5x8 ----
FONT = ImageFont.truetype("/usr/share/fonts/truetype/dejavu/DejaVuSansMono-Bold.ttf", 8)

def glyph_cell(ch, h):
    c = Image.new("RGBA", (CHAR_W, h), (0, 0, 0, 0))
    m = Image.new("L", (12, 12), 0)
    md = ImageDraw.Draw(m); md.fontmode = "1"
    md.text((0, -2), ch, font=FONT, fill=255)
    for yy in range(0, 9):
        for xx in range(CHAR_W):
            if m.getpixel((xx, yy)):
                c.putpixel((xx, yy), (255, 255, 255, 255))
    if c.getpixel((CHAR_W - 1, 9))[3] == 0:
        c.putpixel((CHAR_W - 1, 9), (255, 255, 255, 3))   # penanda lebar tetap
    return c

def pixel_text(txt, scale=4, color=(255, 255, 255, 255)):
    w = max(1, len(txt) * 6)
    t = Image.new("RGBA", (w, 10), (0, 0, 0, 0))
    for i, ch in enumerate(txt):
        cell = glyph_cell(ch, 10)
        cell = Image.composite(Image.new("RGBA", cell.size, color), Image.new("RGBA", cell.size, (0,0,0,0)), cell.split()[3].point(lambda a: 255 if a > 10 else 0))
        t.alpha_composite(cell, (i * 6, 0))
    return t.resize((t.width * scale, t.height * scale), Image.NEAREST)

# ---- kartu latar HUD ----
card = img.resize((CARD_W * HI, CARD_H * HI), Image.LANCZOS)
canvas = Image.new("RGBA", (CARD_W * HI, GLYPH_H * HI), (0, 0, 0, 0))
canvas.alpha_composite(card, (0, 0))
hint = "JONGKOK (SNEAK) UNTUK MENUTUP"
ht = pixel_text(hint, HI, (241, 217, 138, 255))
bw, bh = ht.width + 16 * HI, ht.height + 6 * HI
bx = (CARD_W * HI - bw) // 2
by = CARD_H * HI + 6 * HI
bd = ImageDraw.Draw(canvas)
bd.rounded_rectangle([bx, by, bx + bw, by + bh], radius=4 * HI, fill=(30, 22, 8, 215), outline=(184, 137, 43, 255), width=HI)
canvas.alpha_composite(ht, (bx + 8 * HI, by + 3 * HI))
canvas.save(os.path.join(TEX_FONT, "card_bg.png"))

# ---- sheet teks per baris ----
rows = []
asc_list = []
for yc in ROW_YC:
    yc_gui = yc * SCALE_SRC
    asc = int(round(ASC_TOP - yc_gui + 3))
    asc_list.append(asc)
    chars = []
    sheet = Image.new("RGBA", (CHAR_W * 16, asc * 6), (0, 0, 0, 0))
    for r in range(6):
        line = ""
        for c in range(16):
            code = 32 + r * 16 + c
            if code > 126:
                line += "\u0000"
                continue
            line += chr(code)
            sheet.alpha_composite(glyph_cell(chr(code), asc), (c * CHAR_W, r * asc))
        chars.append(line)
    n = len(asc_list)
    sheet.save(os.path.join(TEX_FONT, f"row{n}.png"))
    with open(os.path.join(FONT_DIR, f"row{n}.json"), "w") as f:
        json.dump({"providers": [{"type": "bitmap", "file": f"veyrion:font/row{n}.png",
                                  "ascent": asc, "height": asc, "chars": chars}]}, f, indent=1)

with open(os.path.join(FONT_DIR, "card_bg.json"), "w") as f:
    json.dump({"providers": [{"type": "bitmap", "file": "veyrion:font/card_bg.png",
                              "ascent": ASC_TOP, "height": GLYPH_H, "chars": ["\ue100"]}]}, f, indent=1)

adv = {}
for b in range(11):
    adv[chr(0xE000 + b)] = -(1 << b)
    adv[chr(0xE010 + b)] = (1 << b)
with open(os.path.join(FONT_DIR, "space.json"), "w") as f:
    json.dump({"providers": [{"type": "space", "advances": adv}]}, f, indent=1)

# ---- kartu fisik (item) 128x128, pixel art 64x40 ----
from PIL import ImageEnhance
small = img.resize((64, 40), Image.BOX).convert("RGB")
small = ImageEnhance.Color(small).enhance(1.6)
small = ImageEnhance.Contrast(small).enhance(1.9)
small = small.quantize(colors=16, method=Image.MEDIANCUT, dither=Image.NONE).convert("RGBA")
phys = small.resize((128, 80), Image.NEAREST)
item = Image.new("RGBA", (128, 128), (0, 0, 0, 0))
item.alpha_composite(phys, (0, 24))
item.save(os.path.join(TEX_ITEM, "kartu_identitas.png"))

# ---- pratinjau (simulasi tata letak HUD) ----
prev = canvas.crop((0, 0, CARD_W * HI, (CARD_H + 24) * HI)).copy()
pd = Image.new("RGBA", prev.size, (60, 90, 70, 255)); pd.alpha_composite(prev); prev = pd
samples = [("KYNARO KARAMAZOV", (58, 42, 16, 255)), ("17", (58, 42, 16, 255)),
           ("11/01/2009", (58, 42, 16, 255)), ("Elf", (176, 122, 26, 255))]
for yc, (t, colr) in zip(ROW_YC, samples):
    tt = pixel_text(t, HI, colr)
    prev.alpha_composite(tt, (VALUE_X * HI, int((yc * SCALE_SRC - 4) * HI)))
prev.save("preview_hud.png")
print("rows ascent:", asc_list, "bg adv:", CARD_W + 1)
