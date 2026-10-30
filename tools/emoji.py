# Render shared emoji PNGs (Noto Color Emoji) for the band app (Vela fonts may lack color emoji).
import sys, os, json
from PIL import Image, ImageDraw, ImageFont
EMO = json.load(open(os.path.join(os.path.dirname(__file__), 'emoji.json')))
out = sys.argv[1]; size = int(sys.argv[2]) if len(sys.argv) > 2 else 72
os.makedirs(out, exist_ok=True)
font = ImageFont.truetype('/usr/share/fonts/truetype/noto/NotoColorEmoji.ttf', 109)
for name, ch in EMO.items():
    im = Image.new('RGBA', (136, 128), (0, 0, 0, 0))
    ImageDraw.Draw(im).text((0, 0), ch, font=font, embedded_color=True)
    im = im.crop(im.getbbox()); w, h = im.size; m = max(w, h)
    sq = Image.new('RGBA', (m, m), (0, 0, 0, 0)); sq.paste(im, ((m - w) // 2, (m - h) // 2))
    im = sq.resize((size, size), Image.LANCZOS)
    im.save(os.path.join(out, name + '.png'), optimize=True)
print(len(EMO), 'emoji ->', out)
