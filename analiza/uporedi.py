#!/usr/bin/env python3
"""Uporedi izlaze modela medjusobno i sa etalonom.

    python3 uporedi.py 2026-09-10

Cita svaki `zadaci/<datum>_*/` folder, izvlaci tabelu iz `izlaz.csv` ili iz
`sirovo.txt` (ono sto je model doslovno ispisao), proverava oblik, pa poredi.

Bez etalona poredjenje ne vredi nista: tri modela vrate tri uredne tabele i sve
tri izgledaju ubedljivo. Etalon je `etalon/<datum>.csv`, tvojih dvadesetak
rucno oznacenih kadrova.

Ne trazi ni mrezu ni kljuc.
"""
import csv
import io
import json
import re
import sys
from collections import Counter, defaultdict
from pathlib import Path

KOREN = Path(__file__).resolve().parent

POLJA = ["vreme", "polozaj", "glava", "ruke", "noge", "pokrivenost", "u_krevetu", "pouzdanost"]

DOZVOLJENO = {
    "polozaj": {"ledja", "stomak", "levi_bok", "desni_bok", "polubok", "sedi",
                "van_kreveta", "ne_vidi_se"},
    "glava": {"pravo", "okrenuta_levo", "okrenuta_desno", "ne_vidi_se"},
    "ruke": {"uz_telo", "iznad_glave", "ispod_glave", "prekrstene", "jedna_van", "ne_vidi_se"},
    "noge": {"ispruzene", "skupljene", "jedna_savijena", "van_kreveta", "ne_vidi_se"},
    "pokrivenost": {"pokriven", "do_pojasa", "otkriven", "ne_vidi_se"},
    "u_krevetu": {"da", "ne"},
}


def izvuci_tabelu(tekst):
    """Uzmi CSV iz onoga sto je model ispisao.

    Modeli tabelu umeju da uokvire kodnim blokom, da je uvedu recenicom, ili da
    posle nje dopisu zakljucak. Trazi se blok koji pocinje zaglavljem.
    """
    blokovi = re.findall(r"```(?:csv)?\s*\n(.*?)```", tekst, re.S)
    kandidati = blokovi + [tekst]
    for blok in kandidati:
        if "vreme,polozaj" in blok:
            pocetak = blok.index("vreme,polozaj")
            return blok[pocetak:]
    return ""


def procitaj_zadatak(folder):
    izlaz = folder / "izlaz.csv"
    tekst = izlaz.read_text() if izlaz.exists() else ""
    if not tekst.strip():
        sirovo = folder / "sirovo.txt"
        tekst = izvuci_tabelu(sirovo.read_text()) if sirovo.exists() else ""
    if not tekst.strip():
        return None, ["nema ni izlaz.csv ni upotrebljiv sirovo.txt"]

    redovi, primedbe = {}, []
    citac = csv.DictReader(io.StringIO(tekst))
    if not citac.fieldnames or citac.fieldnames[0].strip() != "vreme":
        return None, ["zaglavlje nije ono trazeno"]

    for r in citac:
        vreme = (r.get("vreme") or "").strip()
        if not vreme:
            continue
        red = {k: (r.get(k) or "").strip() for k in POLJA}
        for polje, dozvoljene in DOZVOLJENO.items():
            if red[polje] and red[polje] not in dozvoljene:
                primedbe.append(f"{vreme}: {polje}={red[polje]} nije dozvoljena vrednost")
        if vreme in redovi:
            primedbe.append(f"{vreme}: red se ponavlja")
        redovi[vreme] = red
    return redovi, primedbe


def main():
    if len(sys.argv) < 2:
        sys.exit("Upotreba: python3 uporedi.py <datum>")
    datum = sys.argv[1]

    zadaci = sorted((KOREN / "zadaci").glob(f"{datum}_*"))
    if not zadaci:
        sys.exit(f"Nema nijednog zadatka za {datum}. Prvo: python3 spremi.py {datum}")

    etalon_fajl = KOREN / "etalon" / f"{datum}.csv"
    etalon = {}
    if etalon_fajl.exists():
        for r in csv.DictReader(io.StringIO(etalon_fajl.read_text())):
            if (r.get("vreme") or "").strip():
                etalon[r["vreme"].strip()] = {k: (r.get(k) or "").strip() for k in POLJA}

    izlazi, sve_primedbe = {}, {}
    for folder in zadaci:
        poslato = len(list((folder / "poslati").glob("*.jpg"))) if (folder / "poslati").exists() else 0
        redovi, primedbe = procitaj_zadatak(folder)
        if redovi is None:
            sve_primedbe[folder.name] = primedbe
            continue
        if poslato and len(redovi) != poslato:
            primedbe.insert(0, f"poslato {poslato} slika, vraceno {len(redovi)} redova")
        izlazi[folder.name] = redovi
        sve_primedbe[folder.name] = primedbe

    print(f"=== Noć {datum} ===\n")

    print("-- oblik izlaza --")
    for ime in sorted(sve_primedbe):
        primedbe = sve_primedbe[ime]
        broj = len(izlazi.get(ime, {}))
        stanje = "uredno" if not primedbe else f"{len(primedbe)} primedbi"
        print(f"{ime:44s} {broj:4d} redova  {stanje}")
        for p in primedbe[:5]:
            print(f"    {p}")
        if len(primedbe) > 5:
            print(f"    ... i još {len(primedbe) - 5}")
    print()

    if etalon:
        print(f"-- pogodak naspram etalona ({len(etalon)} kadrova) --")
        for ime, redovi in sorted(izlazi.items()):
            zajednicki = [v for v in etalon if v in redovi]
            if not zajednicki:
                print(f"{ime:44s} nema zajedničkih vremena sa etalonom")
                continue
            pogodaka = sum(1 for v in zajednicki if redovi[v]["polozaj"] == etalon[v]["polozaj"])
            odnos = pogodaka / len(zajednicki)
            print(f"{ime:44s} {pogodaka:3d}/{len(zajednicki):3d}   {odnos:.2f}")
        print()
    else:
        print(f"-- etalona nema --\nNapravi {etalon_fajl} sa dvadesetak ručno označenih kadrova.")
        print("Bez njega se model bira po utisku, a utisak na mutnoj noćnoj slici greši.\n")

    if len(izlazi) > 1:
        print("-- slaganje modela međusobno --")
        imena = sorted(izlazi)
        for i in range(len(imena)):
            for j in range(i + 1, len(imena)):
                a, b = izlazi[imena[i]], izlazi[imena[j]]
                zajednicki = [v for v in a if v in b]
                if not zajednicki:
                    continue
                isti = sum(1 for v in zajednicki if a[v]["polozaj"] == b[v]["polozaj"])
                print(f"{imena[i]}  vs  {imena[j]}: {isti}/{len(zajednicki)} ({isti/len(zajednicki):.2f})")
        print()

        print("-- kadrovi gde se svi razilaze --")
        sva_vremena = set()
        for redovi in izlazi.values():
            sva_vremena |= set(redovi)
        neslaganja = []
        for v in sorted(sva_vremena):
            odgovori = [r[v]["polozaj"] for r in izlazi.values() if v in r]
            if len(odgovori) > 1 and len(set(odgovori)) == len(odgovori):
                neslaganja.append((v, odgovori))
        if not neslaganja:
            print("nema takvih kadrova")
        for v, odgovori in neslaganja[:20]:
            print(f"{v}   {', '.join(odgovori)}")
        if len(neslaganja) > 20:
            print(f"... i još {len(neslaganja) - 20}")
        print("\nOve kadrove pogledaj sam; obično su stvarno dvosmisleni.")


if __name__ == "__main__":
    main()
