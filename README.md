# San

Telefon stoji iznad kreveta i slika u zadatim razmacima cele noći. Ujutru
dobiješ folder sa slikama te noći, izmeren pokret po kadru, i pripremljen
materijal za analizu položaja.

Puna zamisao, sa razlozima i odbačenim rešenjima, stoji u
**[FUNKCIJE.md](FUNKCIJE.md)**. Odluke i već napravljene greške su u
**[CLAUDE.md](CLAUDE.md)**.

| | stanje |
|---|---|
| **[android/](android/)** | prva verzija: snimanje, tačno okidanje, zvučna potvrda, noćna proba |
| **[analiza/](analiza/)** | priprema noći i poređenje modela, bez API ključa |

---

## Instalacija

```bash
cd android
./build.sh install     # traži uključen USB debugging
./build.sh             # samo napravi APK (~1.6 MB, skupljen)
```

Pa u aplikaciji, ekran **Provera** kaže šta još fali. Tri stvari se ne mogu
dati iz aplikacije nego samo u sistemskim podešavanjima, i bez njih snimanje
tiho stane usred noći:

1. **Kamera** i **obaveštenja**, dozvole
2. **Tačni alarmi**, inače okidanje luta po desetak minuta
3. **Štednja baterije** isključena za San. Na Samsungu se to zove „Aplikacije
   koje se nikad ne uspavljuju“.

---

## Prva noć

Uveče, u sobi u kojoj ćeš i spavati:

1. Namesti telefon iznad kreveta, uspravno, i **priključi punjač**.
2. **Noćna proba** → „Snimi probnu sliku". Pogledaj šta se vidi u mraku.
   Ekran ispisuje i koliki raspon ekspozicije tvoj telefon uopšte dozvoljava,
   jer se to razlikuje od modela do modela.
3. Ako je slika crna: uključi **Ručnu ekspoziciju**, produži vreme (`1/x`,
   probaj 8, pa 4, pa 2), podigni ISO, pa ponovo probaj. Ako ni tada ništa,
   treba ti slabo svetlo u sobi; kamera bez svetla ne vidi, i to nije stvar
   podešavanja.
4. Podesi **Rastojanje do kreveta**. Fokus se zaključava na tu vrednost, jer u
   mraku autofokus lovi i vraća mutan kadar bez ijedne greške.
5. **Počni**. Prva slika ide odmah i **čuje se**. Druga se čuje na sledećem
   punom minutu po mreži, i to je potvrda da se ponavljanje stvarno vrti.
   Posle toga je tišina do jutra.
6. Ujutru **Zaustavi**.

Ako se usred noći nešto pokvari (nestane mesta, sistem oduzme kameru),
aplikacija se **oglasi**. Tišina znači da sve radi.

---

## Gde završe slike

```
DCIM/San/2026/09/2026-09-10/          pune slike, vide se u galeriji
  2026-09-10_23-00-00.jpg
  2026-09-11_03-45-00.jpg

Documents/San/2026/09/2026-09-10/     ovo se prebacuje na računar
  iste slike smanjene na 768px
  noc.json                            zapisnik: vremena, pokret po kadru, rupe
```

Folder nosi **datum večeri kad je noć počela**, a fajl **stvarni datum i vreme
kadra**. Zato slika iz 03:45 stoji u folderu `2026-09-10` a zove se
`2026-09-11_03-45-00.jpg`. Isto piše i upisano u gornjem levom uglu slike, i
u EXIF-u.

---

## Podešavanja

| | podrazumevano | |
|---|---|---|
| Razmak | 5 min | 1, 2, 3, 5, 10, 15, 30 ili 60. Slika se na pun minut po satu, ne od trenutka pokretanja |
| Rezolucija | puna | ekran odmah kaže koliko je to slika i gigabajta po noći |
| Kamera | zadnja | veći senzor, a ekran gleda od kreveta |
| Ručna ekspozicija | isključena | uključi je čim proba pokaže da automatika luta |
| Rastojanje do kreveta | 2 m | iz toga se računa fiksni fokus |
| Vreme na arhivskoj slici | uključeno | na kopiji za analizu se upisuje uvek |
| Zvučna potvrda | uključena | prva i druga slika, pa tišina |
| Sam prekini u sat | isključeno | |

Na jedan minut je 480 slika i oko 1,9 GB po noći. Na pet minuta je 96 slika i
oko 380 MB.

---

## Analiza

Slike se prebace na računar, u `analiza/slike/2026/09/2026-09-10/`, i dalje ide
bez ikakvog API ključa, agentima koje već imaš:

```bash
cd analiza
python3 spremi.py 2026-09-10 --model opus5   # izabere kadrove, napravi zadatak
python3 uporedi.py 2026-09-10                # oceni izlaze, uporedi modele
```

`spremi.py` šalje **samo kadrove na kojima se nešto promenilo**, po izmerenom
pokretu, plus sidro na svakih dvanaest. Od 96 slika obično ostane tridesetak, a
nijedna promena položaja se ne propušta.

Detalji, uključujući zašto je poređenje bez etalona bezvredno, u
[analiza/CLAUDE.md](analiza/CLAUDE.md).

---

## Šta namerno ne radi

**Ne snima video i ne snima zvuk.** Osam sati videa je desetine gigabajta i sat
pregledanja, a za položaj tela nosi istu informaciju kao 96 slika.

**Ne meri faze sna.** Kamera vidi položaj i pokret, ne moždane talase.

**Ne prepoznaje položaj na telefonu.** Telefon meri pokret i ćuti; položaj
prepoznaje model sa računara ili ti sam, sa desetak dodira ujutru.

**Nema žive slike sa kamere u aplikaciji.** U mračnoj sobi ne pokazuje ništa
korisno; probna slika sa zaključanom ekspozicijom pokazuje tačno ono što će noć
dati.
