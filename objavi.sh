#!/bin/bash
# Objavi novu verziju kao GitHub release, da se APK skine sa telefona.
#
#   ./objavi.sh                 opis se sklopi sam
#   ./objavi.sh beleske.md      opis iz fajla
#
# Radi sve redom: proveri da je radno stablo cisto, da u repozitorijumu nema
# slika, pokrene testove, sklopi release APK, proveri verziju U SAMOM APK-u,
# proveri da nema kljuceva, pa napravi tag i release i okaci APK.
set -euo pipefail
cd "$(dirname "$0")"

BELESKE="${1:-}"
GRADLE=android/app/build.gradle.kts
APK=android/app/build/outputs/apk/release/app-release.apk

crveno() { printf '\033[31m%s\033[0m\n' "$1"; }
zeleno() { printf '\033[32m%s\033[0m\n' "$1"; }

# ---------------------------------------------------------------- 1. stanje

if [ -n "$(git status --porcelain)" ]; then
  crveno "Radno stablo nije cisto. Commituj pa objavi:"
  git status --short
  exit 1
fi

VERZIJA=$(grep -oE 'versionName = "[^"]+"' "$GRADLE" | head -1 | cut -d'"' -f2)
KOD=$(grep -oE 'versionCode = [0-9]+' "$GRADLE" | head -1 | grep -oE '[0-9]+')
[ -n "$VERZIJA" ] || { crveno "Ne mogu da procitam versionName iz $GRADLE"; exit 1; }
TAG="v$VERZIJA"
IME_APK="San-$VERZIJA.apk"

if git rev-parse "$TAG" >/dev/null 2>&1 || gh release view "$TAG" >/dev/null 2>&1; then
  crveno "Verzija $VERZIJA je vec objavljena."
  echo "Podigni versionName i versionCode u $GRADLE pa probaj ponovo."
  exit 1
fi

echo "Objavljujem $TAG (versionCode $KOD)"

# ------------------------------------------------------- 2. nema slika unutra
#
# Ovde je cena greske tvoja fotografija dok spavas, u repozitorijumu, zauvek,
# i posle brisanja iz istorije. Zato se gleda sta je git STVARNO zapamtio, a ne
# sta pise u .gitignore.

SLIKE=$(git ls-files | grep -iE '\.(jpg|jpeg|png|heic|dng|mp4|mov|gif|webp)$' || true)
if [ -n "$SLIKE" ]; then
  crveno "U repozitorijumu su fajlovi koji lice na slike:"
  echo "$SLIKE" | sed 's/^/  /'
  exit 1
fi
zeleno "  nema slika u repozitorijumu"

# --------------------------------------------------------------- 3. testovi

echo
echo "Testovi..."
(cd android && ./gradlew test -q >/dev/null 2>&1) || { crveno "Android testovi padaju."; exit 1; }
for s in analiza/*.py; do
  python3 -c "import ast,sys;ast.parse(open(sys.argv[1]).read())" "$s" \
    || { crveno "Pokvarena skripta: $s"; exit 1; }
done
zeleno "  prolaze"

# ----------------------------------------------------------------- 4. build

echo
echo "Sklapam APK..."
(cd android && ./build.sh >/dev/null)
[ -f "$APK" ] || { crveno "Nema APK-a na $APK"; exit 1; }

# Build koji „prodje" ne znaci da je izmena unutra: proveri verziju u samom
# APK-u, ne u gradle fajlu.
AAPT=$(ls "$HOME"/Library/Android/sdk/build-tools/*/aapt2 2>/dev/null | tail -1)
if [ -n "$AAPT" ]; then
  U_APK=$("$AAPT" dump badging "$APK" 2>/dev/null | grep -oE "versionName='[^']+'" | cut -d"'" -f2)
  if [ "$U_APK" != "$VERZIJA" ]; then
    crveno "APK je verzija $U_APK, a objavljujem $VERZIJA. Build nije prosao."
    exit 1
  fi
fi

# Komponente iz manifesta mora da prezive R8. Bez toga ih sistem ne nadje i
# nista se ne desi, bez ijedne greske u dnevniku.
DEX=$(mktemp -d)
trap 'rm -rf "$DEX"' EXIT
unzip -qo "$APK" 'classes*.dex' -d "$DEX"
# Stringovi se prvo ispisu u fajl, pa se pretrazuje fajl. `strings | grep -q`
# ovde ne valja: grep izadje na prvom pogotku, strings dobije SIGPIPE, a
# `pipefail` to prijavi kao pad, pa skripta odbije ispravan APK.
strings -a "$DEX"/classes*.dex > "$DEX/stringovi.txt" 2>/dev/null || true
for K in MainActivity Snimanje; do
  grep -qF "studio/room211/san/$K" "$DEX/stringovi.txt" \
    || { crveno "R8 je pojeo $K. Proveri proguard-rules.pro"; exit 1; }
done
zeleno "  $APK ($(du -h "$APK" | cut -f1)), verzija i komponente potvrdjene u APK-u"

# ------------------------------------------------------ 5. nema kljuceva unutra
#
# Provereno na Diktatu: APK sa ugradjenim Gemini i Groq kljucem stajao je
# okacen uz release mesec dana. Gledaju se i izvor i APK, i to nije visak: R8
# izbacuje konstantu koja se nigde ne koristi, pa kljuc ostavljen „za kasnije"
# NE postoji u APK-u a postoji u repozitorijumu, i tamo ostaje zauvek.

echo
echo "Trazim kljuceve..."
IZVOR=$(git ls-files | grep -vE '\.(jar|png|apk)$' | while read -r f; do
  LC_ALL=C grep -ohE \
    'AIza[0-9A-Za-z_-]{30,40}|gsk_[A-Za-z0-9]{40,}|sk-ant-[A-Za-z0-9_-]{30,}|sk-[A-Za-z0-9_-]{30,}|gh[pousr]_[A-Za-z0-9]{30,}|AKIA[0-9A-Z]{16}|BEGIN [A-Z ]*PRIVATE KEY' \
    "$f" 2>/dev/null
done | sort -u || true)
if [ -n "$IZVOR" ]; then
  crveno "Izvorni kod sadrzi nesto sto lici na API kljuc:"
  echo "$IZVOR" | sed 's/^/  /'
  exit 1
fi
NALAZI=$(grep -oE \
  'AIza[0-9A-Za-z_-]{30,40}|gsk_[A-Za-z0-9]{40,}|sk-ant-[A-Za-z0-9_-]{30,}|sk-[A-Za-z0-9_-]{30,}|gh[pousr]_[A-Za-z0-9]{30,}|AKIA[0-9A-Z]{16}' \
  "$DEX/stringovi.txt" | sort -u || true)
if [ -n "$NALAZI" ]; then
  crveno "APK sadrzi nesto sto lici na API kljuc:"
  echo "$NALAZI" | sed 's/^/  /'
  exit 1
fi
zeleno "  izvor i APK cisti"

# --------------------------------------------------------------- 6. objava

if [ -n "$BELESKE" ] && [ -f "$BELESKE" ]; then
  OPIS=$(cat "$BELESKE")
else
  OPIS="Instalacioni APK, potpisan debug kljucem. Instalira se preko postojece
verzije; podesavanja ostaju.

Verzija $VERZIJA ($KOD), $(du -h "$APK" | cut -f1).

Posle instalacije, uvece i u mraku u kom ces i snimati:

1. Namesti telefon iznad kreveta i prikljuci punjac.
2. **Provera** -> odobri kameru i obavestenja, dozvoli tacne alarme, iskljuci
   stednju baterije za San. Bez toga snimanje tiho stane usred noci.
3. **Nocna proba** -> \"Snimi probnu sliku\" i pogledaj sta se vidi u mraku.
   Ako je crna, ukljuci Rucnu ekspoziciju i produzi vreme.
4. **Pocni**. Prva slika ide odmah i cuje se; druga se cuje na sledecem punom
   minutu. Posle toga je tisina do jutra.

Uputstvo korak po korak je u README.md."
fi

cp "$APK" "/tmp/$IME_APK"
gh release create "$TAG" "/tmp/$IME_APK" \
  --title "San $VERZIJA (Android)" \
  --notes "$OPIS"
rm -f "/tmp/$IME_APK"

echo
zeleno "Objavljeno."
echo "Na telefonu otvori:  $(gh repo view --json url --jq .url)/releases/latest"
