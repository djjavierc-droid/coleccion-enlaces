#!/bin/bash
# HILO G — ola 2: servicios de terceros NUEVOS para /share/r/
# Entrada: https://www.facebook.com/share/r/1Bo58NQ (token real del repo, tests de la app)
# Ninguno de estos estaba en la lista de muertos de la ola 1.
OUT="$HOME/workspace/coleccion-enlaces/investigacion-ola2/G"
mkdir -p "$OUT"
SHARE="https://www.facebook.com/share/r/1Bo58NQ"
ENC="https%3A%2F%2Fwww.facebook.com%2Fshare%2Fr%2F1Bo58NQ"
UA="Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0 Safari/537.36"
LOG="$OUT/00-resumen-http.log"
: > "$LOG"
echo "Entrada: $SHARE" | tee -a "$LOG"
echo "Fecha: $(date -u '+%Y-%m-%d %H:%M UTC')" | tee -a "$LOG"
echo | tee -a "$LOG"

run() { # $1=nombre $2=url $3=archivo_salida $4=extra curl args
  local name="$1" url="$2" out="$3"; shift 3
  code=$(curl -s -L --max-time 40 -A "$UA" -D "$OUT/$name.headers.txt" -o "$OUT/$out" -w "%{http_code}" "$@" "$url" 2>/dev/null)
  size=$(stat -c%s "$OUT/$out" 2>/dev/null || echo 0)
  echo "$name | HTTP $code | $size bytes | $url" | tee -a "$LOG"
}

# 1. unshorten.it (API de expansion; distinto de unshorten.me, muerto en ola 1)
run "01-unshorten-it" "https://unshorten.it/api.php?url=$ENC" "01-unshorten-it.txt"

# 2. LongURL API v2 (expand + title)
run "02-longurl" "https://api.longurl.org/v2/expand?url=$ENC&format=json&title=1" "02-longurl.json"

# 3. Redirect Detective: primero la home para descubrir el form
curl -s --max-time 30 -A "$UA" -o "$OUT/03-redirectdetective-home.html" -w "home HTTP %{http_code}\n" "https://www.redirectdetective.com/" | tee -a "$LOG"
grep -oiE '<form[^>]*>' "$OUT/03-redirectdetective-home.html" | head -3 | tee -a "$LOG"
# intento POST con los nombres de campo mas probables
code=$(curl -s -L --max-time 40 -A "$UA" -D "$OUT/03-redirectdetective.headers.txt" -o "$OUT/03-redirectdetective.html" -w "%{http_code}" -d "url=$SHARE" -d "submit=Trace" "https://www.redirectdetective.com/" 2>/dev/null)
echo "03-redirectdetective POST | HTTP $code | $(stat -c%s "$OUT/03-redirectdetective.html") bytes" | tee -a "$LOG"

# 4. LinkUnshorten: home + intento de expansion
curl -s --max-time 30 -A "$UA" -o "$OUT/04-linkunshorten-home.html" -w "home HTTP %{http_code}\n" "https://www.linkunshorten.com/" | tee -a "$LOG"
grep -oiE '<form[^>]*action="[^"]*"[^>]*>' "$OUT/04-linkunshorten-home.html" | head -3 | tee -a "$LOG"
code=$(curl -s -L --max-time 40 -A "$UA" -D "$OUT/04-linkunshorten.headers.txt" -o "$OUT/04-linkunshorten.html" -w "%{http_code}" -d "url=$SHARE" "https://www.linkunshorten.com/" 2>/dev/null)
echo "04-linkunshorten POST | HTTP $code | $(stat -c%s "$OUT/04-linkunshorten.html") bytes" | tee -a "$LOG"

# 5. allorigins (proxy que descarga la pagina en su servidor)
run "05-allorigins" "https://api.allorigins.win/get?url=$ENC" "05-allorigins.json"

# 6. codetabs proxy (idem)
run "06-codetabs" "https://api.codetabs.com/v1/proxy?quest=$ENC" "06-codetabs.html"

# 7. PageSpeed Insights (renderiza con Chrome: finalUrl + screenshot)
run "07-pagespeed" "https://www.googleapis.com/pagespeedonline/v5/runPagespeed?url=$ENC&screenshot=true" "07-pagespeed.json"

# 8. s-shot.ru (screenshot gratis sin clave)
run "08-sshot" "https://mini.s-shot.ru/1024x768/JPEG/1024/Z100/?$SHARE" "08-sshot.jpg"

# 9. urlmeta.org (API de metadatos; probablemente exige clave, se registra el veredicto)
run "09-urlmeta" "https://api.urlmeta.org/?url=$ENC" "09-urlmeta.json"

echo | tee -a "$LOG"
echo "FIN" | tee -a "$LOG"
