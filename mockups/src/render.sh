#!/usr/bin/env bash
# Re-render all mockups. Needs google-chrome + fonts-noto-cjk + fonts-noto-color-emoji. Run from mockups/src.
set -e; cd "$(dirname "$0")"; OUT=..; C="google-chrome --headless=new --no-sandbox --disable-gpu --hide-scrollbars"
mkdir -p $OUT/@2x
for f in band-*.html; do b=${f%.html}
  $C --default-background-color=00000000 --window-size=336,480 --screenshot=$PWD/$OUT/$b.png file://$PWD/$f 2>/dev/null
  $C --default-background-color=00000000 --force-device-scale-factor=2 --window-size=336,480 --screenshot=$PWD/$OUT/@2x/$b@2x.png file://$PWD/$f 2>/dev/null; done
for f in phone-*.html; do $C --force-device-scale-factor=2 --window-size=540,1200 --screenshot=$PWD/$OUT/${f%.html}.png file://$PWD/$f 2>/dev/null; done
$C --default-background-color=00000000 --window-size=390,450 --screenshot=$PWD/$OUT/watch3-notification.png file://$PWD/watch3-notification.html 2>/dev/null
$C --default-background-color=00000000 --force-device-scale-factor=2 --window-size=390,450 --screenshot=$PWD/$OUT/@2x/watch3-notification@2x.png file://$PWD/watch3-notification.html 2>/dev/null
$C --force-device-scale-factor=2 --window-size=760,2000 --screenshot=/tmp/ob.png file://$PWD/overview-band.html 2>/dev/null
$C --force-device-scale-factor=2 --window-size=940,2400 --screenshot=/tmp/op.png file://$PWD/overview-phone.html 2>/dev/null
python3 - <<P
from PIL import Image, ImageChops
for src,dst in [("/tmp/ob.png","$PWD/$OUT/overview-band.png"),("/tmp/op.png","$PWD/$OUT/overview-phone.png")]:
    im=Image.open(src).convert("RGB"); bg=Image.new("RGB",im.size,im.getpixel((2,im.height-2)))
    box=ImageChops.difference(im,bg).getbbox(); h=min(im.height,box[3]+80)
    im.crop((0,0,im.width,h)).save(dst,optimize=True); print(dst,im.width,h)
P
