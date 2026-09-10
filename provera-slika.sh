#!/bin/bash
# Odbij commit u kome je bilo sta nalik slici.
#
# Postoji zato sto je ista vrsta greske vec napravljena u Diktatu, samo sa API
# kljucem: APK sa ugradjenim kljucem stajao je zakacen uz release mesec dana, a
# brisanje iz koda tu ne pomaze jer stari fajl i dalje stoji na GitHub-u. Ovde
# je u pitanju tvoja fotografija dok spavas, pa cena greske nije manja.
#
# Kao git hook:
#   ln -s ../../provera-slika.sh .git/hooks/pre-commit
set -euo pipefail
cd "$(dirname "$0")"

NADJENO=$(git diff --cached --name-only --diff-filter=ACM \
  | grep -iE '\.(jpg|jpeg|png|heic|dng|mp4|mov|gif|webp)$' || true)

if [ -n "$NADJENO" ]; then
  echo "ODBIJENO: u commit-u su fajlovi koji lice na slike."
  echo "$NADJENO" | sed 's/^/  /'
  echo
  echo "Slike noci ne idu u repozitorijum. Ako je fajl stvarno potreban"
  echo "(ikonica, snimak ekrana), dodaj ga u .gitignore kao izuzetak svesno."
  exit 1
fi

echo "Nema slika u commit-u."
