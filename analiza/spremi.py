#!/usr/bin/env python3
"""Spremi jednu noc za slanje modelu.

Cita `noc.json` i slike prebacene sa telefona (folder `Documents/San/...`),
bira kadrove po izmerenom pokretu, i pravi folder zadatka sa gotovim promptom
i spiskom slika koje se prevlace u razgovor.

    python3 spremi.py 2026-09-10 --model opus5
    python3 spremi.py 2026-09-10 --prag 0.02      # vise kadrova
    python3 spremi.py 2026-09-10 --sve            # bez filtera

Ne trazi ni mrezu ni kljuc.
"""
import argparse
import json
import shutil
import sys
from datetime import datetime
from pathlib import Path

KOREN = Path(__file__).resolve().parent
PRAG = 0.045
SIDRO = 12


def putanja_noci(datum: str) -> Path:
    god, mes = datum[:4], datum[5:7]
    return KOREN / "slike" / god / mes / datum


def ucitaj(datum: str) -> dict:
    folder = putanja_noci(datum)
    zapisnik = folder / "noc.json"
    if not zapisnik.exists():
        sys.exit(
            f"Nema {zapisnik}.\n"
            f"Prebaci sa telefona folder Documents/San/{datum[:4]}/{datum[5:7]}/{datum}\n"
            f"u {folder.parent}"
        )
    return json.loads(zapisnik.read_text())


def izbor(slike, prag, sidro, sve):
    """Prvi kadar, svaka promena preko praga, plus sidro na svakih `sidro` kadrova.

    Isto pravilo kao `Pokret.izbor` u aplikaciji. Ako se ovo razidje sa
    telefonom, ista noc daje razlicit izbor u zavisnosti od toga ko bira, a to
    se primeti tek kad se dva izlaza ne poklope.
    """
    if sve:
        return list(range(len(slike)))
    izabrani = []
    for i, s in enumerate(slike):
        if not s.get("fajl"):
            continue                       # rupa u noci, nema sta da se salje
        if i == 0 or s.get("pokret", 0) >= prag or i % sidro == 0:
            izabrani.append(i)
    return izabrani


def main():
    a = argparse.ArgumentParser()
    a.add_argument("datum")
    a.add_argument("--model", default="model")
    a.add_argument("--prompt", default="01-polozaj")
    a.add_argument("--prag", type=float, default=PRAG)
    a.add_argument("--sidro", type=int, default=SIDRO)
    a.add_argument("--sve", action="store_true")
    arg = a.parse_args()

    zapisnik = ucitaj(arg.datum)
    slike = zapisnik.get("slike", [])
    if not slike:
        sys.exit("Zapisnik je prazan.")

    izabrani = izbor(slike, arg.prag, arg.sidro, arg.sve)
    izvor = putanja_noci(arg.datum)

    verzija = 1
    while True:
        zadatak = KOREN / "zadaci" / f"{arg.datum}_{arg.model}_p{verzija:02d}"
        if not zadatak.exists():
            break
        verzija += 1

    poslati = zadatak / "poslati"
    poslati.mkdir(parents=True)

    nedostaje = []
    for i in izabrani:
        fajl = izvor / slike[i]["fajl"]
        if fajl.exists():
            shutil.copy2(fajl, poslati / fajl.name)
        else:
            nedostaje.append(slike[i]["fajl"])

    predlozak = (KOREN / "promptovi" / f"{arg.prompt}.md").read_text()
    telo = predlozak.split("---", 1)[1].strip() if "---" in predlozak else predlozak
    telo = telo.replace("{BROJ}", str(len(izabrani) - len(nedostaje)))
    telo = telo.replace("{NOC}", arg.datum)
    (zadatak / "prompt.txt").write_text(telo + "\n")

    (zadatak / "zadatak.md").write_text(
        f"""# Zadatak {zadatak.name}

| | |
|---|---|
| noć | {arg.datum} |
| prompt | {arg.prompt}, verzija iz {datetime.now():%Y-%m-%d} |
| model | {arg.model} |
| kadrova u noći | {len(slike)} |
| poslato | {len(izabrani) - len(nedostaje)} |
| prag pokreta | {arg.prag}{" (bez filtera)" if arg.sve else ""} |
| napravljeno | {datetime.now():%Y-%m-%d %H:%M} |

## Kako se pokreće

Agent u terminalu: otvori ovaj folder i reci mu da pročita `prompt.txt`, uzme
slike iz `poslati/` u grupama od 12 po redosledu imena, i upiše rezultat u
`izlaz.csv`.

Razgovor u pregledaču: nalepi `prompt.txt`, prevuci slike iz `poslati/` u
grupama od najviše 12, pa ono što model ispiše sačuvaj u `sirovo.txt`.

Zatim:

    python3 ../../uporedi.py {arg.datum}
""")
    (zadatak / "sirovo.txt").write_text("")

    print(f"Zadatak: {zadatak}")
    print(f"Kadrova u noći: {len(slike)}, poslato: {len(izabrani) - len(nedostaje)}")
    if nedostaje:
        print(f"UPOZORENJE: {len(nedostaje)} slika nema na disku, npr. {nedostaje[0]}")
    print(f"Prompt: {zadatak / 'prompt.txt'}")
    print(f"Slike:  {poslati}")


if __name__ == "__main__":
    main()
