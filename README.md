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

Najlakše preko GitHub-a, bez kabla. Na telefonu otvori
<https://github.com/milev051/san/releases/latest> i skini `San-<verzija>.apk`.
Repozitorijum je privatan, pa moraš biti prijavljen na GitHub u tom pregledaču.
Android će pitati da dozvoli instalaciju iz tog pregledača; to je jednokratno.

Ako imaš kabl pri ruci:

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
3. Ako je slika crna, redom:
   - **Režim → Noćni**. To je noćni režim samog telefona, isti onaj koji
     Kamera koristi kad u mraku sačekaš par sekundi: senzor snimi više kadrova
     različite dužine i proizvođačev kod ih spoji u jednu svetliju sliku sa
     manje šuma. Radi na Samsung S23 i svemu što ga prijavi kroz Camera2
     Extensions; ako ga telefon nema, ekran to jasno kaže.
   - Ako ni tako ništa, **Ručno**: produži vreme (`1/x`, probaj 8, pa 4, pa 2)
     i podigni ISO. Noćna proba ispisuje i traženo i dobijeno, pa se odmah vidi
     kad telefon odbija traženu vrednost.
   - Ako ni tada ništa, treba ti slabo svetlo u sobi. Kamera bez svetla ne
     vidi, i to nije stvar podešavanja.
4. Podesi **Rastojanje do kreveta**. Fokus se zaključava na tu vrednost, jer u
   mraku autofokus lovi i vraća mutan kadar bez ijedne greške.
5. **Pritisni dugme preko celog ekrana.** Odmah se čuje ton da je pokrenuto,
   pa imaš deset sekundi da legneš. Prva slika ide tek onda, i čuje se, kao i
   druga i treća. Posle toga je tišina do jutra.
6. Ujutru **drži** dugme da zaustaviš.

Ako usred noći zaustaviš pa ponovo pokreneš, **nastavlja se ista noć**: isti
folder, isto brojanje slika i ista zaključana ekspozicija. Tek posle osam sati
od poslednje slike počinje nova noć. Zato snimanje pokrenuto u 22:00 i
zaustavljeno u 10:00 narednog dana ostaje jedan folder, `2026-09-10`.

Ako se usred noći nešto pokvari (nestane mesta, sistem oduzme kameru),
aplikacija se **oglasi**. Tišina znači da sve radi.

---

## Noćni režim

**Režim → Noćni** koristi ono što telefon već ima. Ne pišemo mi slaganje
kadrova nego ga tražimo od proizvođača, kroz standardni Camera2 Extensions API.

Dve posledice koje treba znati:

- **Jedan snimak traje nekoliko sekundi**, pa razmak od jednog minuta nema
  smisla. Uzmi bar dva.
- **Ekspoziciju bira telefon i ne može se zaključati.** Zbog toga se merenje
  pokreta radi tako da promena svetline ne prolazi kao pokret; svakoj sličici
  se pre poređenja oduzme njena srednja svetlina.

Ako noćni snimak ne uspe, snima se obično, ali to piše u `noc.json` i na ekranu.

---

## Prvi ekran

Jedno dugme preko celog ekrana. Telefon visi iznad kreveta, okrenut od tebe, i
uveče se pipa u mraku, pa se sitno dugme među karticama ne nalazi.

**Zaustavljanje traži dugi pritisak.** Da je i ono običan dodir, jedan slučajan
dodir u mraku prekinuo bi noć, a to se ne bi videlo do jutra.

Podešavanja su na **⋮** u gornjem uglu, pregled noći na **noći**. Ispod dugmeta
stoje poslednja snimljena slika, broj slika, vreme sledećeg okidanja, baterija i
slobodno mesto.

## Pregled u aplikaciji

Dugme **noći** otvara spisak svih noći sa brojem slika, trajanjem i brojem
promena položaja; dodir na noć otvara mrežu sličica, a dodir na sličicu
prikazuje ceo kadar.

Dugme **Izvezi za računar** upisuje male kopije i `noc.json` u `Documents/San/`,
odakle se noć prebacuje kablom. Do izvoza te kopije stoje u privatnom folderu
aplikacije i ne vide se u galeriji.

---

## Gde završe slike

```
DCIM/San/2026/09/2026-09-10/          pune slike, vide se u galeriji
  2026-09-10_23-00-00.jpg
  2026-09-11_03-45-00.jpg

Documents/San/2026/09/2026-09-10/     tek posle dugmeta „Izvezi za računar"
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
| Režim | Automatika | **Noćni**, Automatika ili Ručno. Tri odvojena puta |
| Zaključaj ekspoziciju posle prve slike | uključeno | važi za Automatiku: prva slika određuje vrednosti za celu noć |
| Prekini kad baterija padne na | 5% | ugašen telefon ne završi zapisnik |
| Rastojanje do kreveta | 2 m | iz toga se računa fiksni fokus |
| Vreme na arhivskoj slici | uključeno | na kopiji za analizu se upisuje uvek |
| Zvučna potvrda | uključena | ton na pritisak dugmeta, pa prve tri slike, pa tišina |
| Prva slika posle | 10 s | toliko imaš da legneš |
| Čuje se do koje slike | 3 | |
| Sam prekini u sat | isključeno | |

Na jedan minut je 480 slika i oko 1,9 GB po noći. Na pet minuta je 96 slika i
oko 380 MB.

---

## Objava nove verzije

```bash
# podigni versionName i versionCode u android/app/build.gradle.kts
./objavi.sh                    # opis se sklopi sam
./objavi.sh beleske.md         # ili opis iz fajla
```

Skripta odbija da objavi ako radno stablo nije čisto, ako je u repozitorijumu
završilo nešto nalik slici, ako testovi padaju, ako je ta verzija već
objavljena, ako `versionName` u gotovom APK-u nije onaj koji se objavljuje, ako
je R8 pojeo komponentu iz manifesta, ili ako u izvoru ili APK-u nađe nešto
nalik API ključu.

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
