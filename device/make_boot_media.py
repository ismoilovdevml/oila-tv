#!/usr/bin/env python3
"""Make the boot logo (JPEG) and boot video (MP4) with your own text.

    python3 device/make_boot_media.py --logo "Android TV" --video "Shaftoli qoqi"

Needs Pillow (pip install pillow) and ffmpeg. Output formats match what this TV's boot
loader and boot-video player accept: a 1366x768 baseline 4:2:0 JPEG, and a 10 s
H.264 High@3.1 640x368 24 fps MP4 with a silent AAC 48 kHz stereo track.
"""
import argparse, os, subprocess, tempfile
from PIL import Image, ImageDraw, ImageFont

FONTS = ["/System/Library/Fonts/Supplemental/Arial Bold.ttf",
         "/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf",
         "/usr/share/fonts/TTF/DejaVuSans-Bold.ttf"]

def card(text, size, px, color):
    font = next((ImageFont.truetype(f, px) for f in FONTS if os.path.exists(f)), None)
    if font is None:
        raise SystemExit("no bold TTF font found; edit FONTS")
    im = Image.new("RGB", size, (0, 0, 0))
    d = ImageDraw.Draw(im)
    b = d.textbbox((0, 0), text, font=font)
    d.text(((size[0] - (b[2] - b[0])) / 2 - b[0], (size[1] - (b[3] - b[1])) / 2 - b[1]),
           text, font=font, fill=color)
    return im

def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--logo", default="Android TV", help="text on the boot-loader logo")
    ap.add_argument("--video", default="Oila TV", help="text in the boot video")
    ap.add_argument("--out", default=os.path.join(os.path.dirname(__file__), "boot"))
    a = ap.parse_args()
    os.makedirs(a.out, exist_ok=True)

    card(a.logo, (1366, 768), 96, (255, 255, 255)).save(
        os.path.join(a.out, "bootlogo.jpg"), "JPEG", quality=90, subsampling=2, progressive=False)

    with tempfile.TemporaryDirectory() as tmp:
        frame = os.path.join(tmp, "frame.png")
        card(a.video, (1280, 736), 128, (255, 176, 122)).resize((640, 368), Image.LANCZOS).save(frame)
        subprocess.run(["ffmpeg", "-v", "error", "-y", "-loop", "1", "-framerate", "24", "-i", frame,
                        "-f", "lavfi", "-i", "anullsrc=r=48000:cl=stereo", "-t", "10",
                        "-vf", "fade=t=in:st=0:d=1,format=yuv420p", "-c:v", "libx264",
                        "-profile:v", "high", "-level", "3.1", "-pix_fmt", "yuv420p", "-r", "24",
                        "-b:v", "200k", "-c:a", "aac", "-b:a", "32k", "-ar", "48000", "-ac", "2",
                        "-movflags", "+faststart", "-shortest", os.path.join(a.out, "bootvideo.mp4")],
                       check=True)
    print("wrote", os.path.join(a.out, "bootlogo.jpg"), "and", os.path.join(a.out, "bootvideo.mp4"))

if __name__ == "__main__":
    main()
