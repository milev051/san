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

## Kako dobiti svetlu sliku u mraku

Četiri režima, i biraju se tim redom.

**Kamera pre režima.** Telefon ima više kamera i one se razlikuju baš u onome
što je ovde jedino važno: dokle ide ekspozicija i da li nude noćni režim.
Kartica **Kamera** ispisuje sve, sa rasponom ekspozicije i oznakom
`NOĆNI REŽIM` kod onih koje ga imaju. Izmereno na Samsung S23: kamera 0 daje
najviše **1/6 s** i nema nijedan noćni režim, pa vredi probati ostale.

| režim | šta radi | kada |
|---|---|---|
| **Noćni** | noćni režim samog telefona | kad ga ta kamera nudi; ekran to kaže |
| **Auto** | automatika, uz zaključavanje posle prve slike | kad u sobi ima nekog svetla |
| **Ručno** | upisano vreme i ISO | kad kamera daje dovoljno dugu ekspoziciju |
| **Slaganje** | snimi više kadrova zaredom i sabere ih u jedan | kad ništa od gornjeg ne daje svetlu sliku |

**Noćni režim na Samsungu, izmereno na S23:** nijedna od četiri kamere ga ne
nudi, ni platformskim putem ni preko CameraX-a. Sve četiri prijavljuju
ekspoziciju do 1/6 s, dok Samsungova Kamera ide do 30 sekundi. Granica je,
dakle, njihova odluka za tuđe aplikacije, ne granica senzora. Open Camera je na
Galaxy uređajima došao do istog: dobija bokeh, ne i noćni režim.

Zato postoje dva zaobilazna puta, oba u aplikaciji: **Traži i preko granice
telefona** i režim **Slaganje**.

**Noćni režim se traži na dva načina**, jer to nije isti spisak. Prvo preko
platformskog Camera2 Extensions API-ja, pa preko CameraX proširenja, koja
razgovaraju sa proizvođačevom bibliotekom direktno i ponekad vide ono što
platforma ne prijavljuje. Dugme **Proveri noćni režim preko CameraX** u kartici
Kamera odgovara na to pitanje za izabranu kameru. Ako ga ni tim putem nema,
proizvođač ga drži u svojoj aplikaciji i nijednim API-jem ga ne daje.

**Slaganje** je odgovor na telefon koji dužu ekspoziciju prosto ne dozvoljava.
Osam kadrova od 1/6 s sabranih zajedno je isto svetlo kao jedan od 1,3 s, uz
manje šuma, jer je šum slučajan u svakom kadru a slika ispod njega ista. To je
i ono što noćni režim radi u proizvođačevom kodu.

Svetla se ne seku tvrdo nego se glatko primiču belom, pa lampica ili prozor u
sobi zadrže oblik umesto da postanu ravna bela mrlja. Tamni deo slike, a to je
ovde skoro cela slika, ostaje netaknut.

Cena: jedna slika traje nekoliko sekundi, izlaz je oko 2 MP, a pokret tokom tih
par sekundi se razmaže. Za položaj tela ništa od toga ne smeta.

**Slaganje neće izgledati kao Samsungov noćni režim, i to je očekivano.** Oni
slažu sirove kadrove sa senzora, u više od osam bita, uz poravnavanje i
uklanjanje šuma pisano za taj senzor. Ovde se slažu već sažete JPEG slike, u
osam bita. Razlika u finoći je odatle, ne iz broja kadrova.

Uz ručne vrednosti postoji dugme **Postavi na granicu telefona**, koje upiše
najdužu ekspoziciju i najveći ISO koje ta kamera stvarno daje. Bez njega se
granica traži pogađanjem, jer telefon sve preko nje tiho stegne i ne kaže ništa.

**Traži i preko granice telefona** je prekidač vredan probe. Camera2 dozvoljava
da se traži vrednost izvan prijavljenog raspona: radi na nekim uređajima, a ne
garantuje se. Noćna proba pod „Dobijeno" kaže da li je prošlo. Ako prođe, dobio
si pravu dugu ekspoziciju i slaganje ti više ne treba.

**Pojačaj sliku posle slaganja** rešava ono što sabiranje samo ne može: osam
kadrova mraka i dalje ume da bude mrak. Posle sabiranja se gleda gde je
najsvetliji deo slike i podiže se do vrha, uz zanemarivanje najsvetlijih pola
procenta, da jedan indikator na punjaču ne odredi osvetljenost cele sobe.

**Zbog svega ovoga merenje pokreta ne broji promenu svetline:** svakoj sličici
se pre poređenja oduzme njena srednja svetlina, pa noć u kojoj telefon menja
ekspoziciju ne izgleda nemirno.

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
| Kamera | auto | tačna kamera po oznaci; razlikuju se u ekspoziciji i noćnom režimu |
| Režim | Automatika | Noćni, Auto, Ručno ili **Slaganje**. Ispod izbora se vidi samo ono što taj režim koristi |
| Kadrova za slaganje | 8 | osam kadrova od 1/6 s je kao jedan od 1,3 s |
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
