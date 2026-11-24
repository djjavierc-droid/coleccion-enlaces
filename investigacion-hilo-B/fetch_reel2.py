#!/usr/bin/env python3
"""Recupera el pipeline Hilo B tras el borrado de /tmp: para cada reel
(id, nombre): pagina Googlebot -> og:* -> mp4 (extractor corregido
con escapes unicode) -> descarga video -> ffprobe -> 5 cuadros.
Uso: python3 fetch_reel2.py <id> <nombre>
"""
import base64
import html as htmlmod
import json
import re
import subprocess
import sys
from pathlib import Path

UA_APP = "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0 Mobile Safari/537.36"
UA_BOT = "Googlebot/2.1 (+http://www.google.com/bot.html)"


def curl(url, out, ua):
    r = subprocess.run(["curl", "-s", "-L", "-A", ua, "-o", str(out), "-w",
                        "%{http_code} %{size_download} %{content_type}", url],
                       capture_output=True, text=True)
    return r.stdout.strip()


def decode_efg(u):
    m = re.search(r"efg=([A-Za-z0-9\-_]+={0,2})", u)
    if not m:
        return {}
    try:
        return json.loads(base64.urlsafe_b64decode(m.group(1) + "=="))
    except Exception:
        return {}


def extract_mp4s(raw):
    raw = raw.replace("\\/", "/")
    raw = re.sub(r"\\u([0-9a-fA-F]{4})", lambda m: chr(int(m.group(1), 16)), raw)
    raw = htmlmod.unescape(raw)
    urls = []
    for m in re.finditer(r"\.mp4", raw):
        j = m.start()
        while j > 0 and raw[j - 1] not in "\"'<> \t\n":
            j -= 1
        k = m.end()
        while k < len(raw) and raw[k] not in "\"'<> \t\n":
            k += 1
        tok = raw[j:k]
        if tok.startswith("https://video-") and tok not in urls:
            urls.append(tok)
    return urls


def main():
    rid, name = sys.argv[1], sys.argv[2]
    page = Path(f"{name}-googlebot.html")
    print("PAGE:", curl(f"https://www.facebook.com/reel/{rid}/", page, UA_BOT), flush=True)
    raw = page.read_text(encoding="utf-8", errors="ignore")

    def meta(prop):
        m = re.search(r'<meta property="%s" content="([^"]*)"' % re.escape(prop), raw)
        return htmlmod.unescape(m.group(1)) if m else None

    title, desc, image = meta("og:title"), meta("og:description"), meta("og:image")
    print("OG:TITLE:", title)
    print("OG:IMAGE:", (image or "")[:130])
    Path(f"{name}-og.txt").write_text(
        f"reel_id: {rid}\nurl: https://www.facebook.com/reel/{rid}/\nog:title: {title}\n"
        f"og:description: {desc}\nog:image: {image}\n", encoding="utf-8")
    if image:
        print("IMG:", curl(image, f"{name}-ogimage.jpg", UA_APP), flush=True)

    urls = extract_mp4s(raw)
    cands = []
    for i, u in enumerate(urls):
        efg = decode_efg(u)
        tag = str(efg.get("vencodetag", ""))
        br = int(efg.get("bitrate", 0) or 0)
        print(f"MP4[{i}] {'AUDIO' if 'audio' in tag else 'VIDEO?'} bitrate={br} dur={efg.get('duration_s')} len={len(u)} oh={'oh=' in u}")
        Path(f"{name}-mp4-url-{i}.txt").write_text(u, encoding="utf-8")
        cands.append((br, i, u, "audio" in tag))
    videos = sorted([c for c in cands if not c[3]], reverse=True)
    video_file = None
    for br, i, u, _ in videos:
        out = Path(f"{name}-video.mp4")
        print(f"TRY mp4[{i}] bitrate={br}:", curl(u, out, UA_APP), flush=True)
        probe = subprocess.run(["ffprobe", "-v", "error", "-show_entries",
                                "stream=codec_type,codec_name,width,height",
                                "-show_entries", "format=duration,size",
                                "-of", "csv=p=0", str(out)], capture_output=True, text=True)
        print("  ffprobe:", probe.stdout.strip().replace("\n", " | ") or probe.stderr.strip()[:200], flush=True)
        if "video" in probe.stdout and "h264" in probe.stdout:
            video_file = out
            print("  OK video h264", flush=True)
            break
    if video_file:
        dur = float(subprocess.run(["ffprobe", "-v", "error", "-show_entries", "format=duration",
                                    "-of", "csv=p=0", str(video_file)], capture_output=True, text=True).stdout.strip())
        times = [0.1, dur * 0.25, dur * 0.5, dur * 0.75, dur - 0.15]
        for t in times:
            out = f"{name}-frame-t{t:.2f}.jpg"
            subprocess.run(["ffmpeg", "-v", "error", "-y", "-ss", str(t), "-i", str(video_file),
                            "-frames:v", "1", "-q:v", "2", out], check=True)
            print("  frame:", out, Path(out).stat().st_size, "bytes", flush=True)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
