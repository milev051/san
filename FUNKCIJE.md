# San: kamera za analizu spavanja

Predlog funkcionalnosti pre pisanja koda. Napisano 10.09.2026.

Telefon stoji iznad kreveta, uspravno, i slika u zadatim razmacima cele noći.
Ujutru dobiješ folder sa slikama te noći i pregled kako si se okretao.

---

## 0. Odluke donete 10.09.2026.

| pitanje | odluka | posledica |
|---|---|---|
| Svetlo | **slaba noćna lampa**, gori celu noć | najpouzdanije; ekspozicija se zaključava na izmerenu vrednost i sve slike jedne noći postaju uporedive |
| Drugi telefon | **jedan po jedan**, arhive se spajaju | sinhronizacija je prosto prenošenje foldera, bez poravnanja vremena između uređaja |
| Arhiva | **ostaje na telefonu**, u galeriji | mesto na telefonu postaje glavno ograničenje, vidi tačku 6 |
| AI analiza | **bez API ključa**, agentima koje već plaćaš, nad folderom na računaru | promptovi, izlazi i ocene žive u istom repozitorijumu, slike ne, vidi tačku 9 |
| Vreme na slici | **upisuje se u kadar**, bez natpisa, stvarni datum kadra | model tada ne može da pomeša redosled |

Ostaje da se zna **koji je telefon**. Od modela zavisi granica ekspozicije,
da li okidač može da ćuti i koliko agresivno sistem ubija servise. Aplikacija
sve troje meri sama u ekranu „Provera", ali dok se ne zna model, plan računa sa
najgorim slučajem.

## 1. Šta se preuzima iz postojeća dva projekta

Oba projekta (`Diktat` i `Most`) stoje na istim obrascima, pa ovaj ide istim putem:

| obrazac | odakle | zašto se zadržava |
|---|---|---|
| Kotlin bez XML layouta, ekran se gradi u kodu (`Ui.kt`, Material 3 kartice) | Most | jedan fajl manje po ekranu, isti izgled u oba projekta |
| `namespace studio.room211.<ime>`, minSdk 26, targetSdk 34 | oba | isti prag, iste dozvole |
| Release skupljen R8-om, potpisan debug ključem, `build.sh` i `build.sh install` | oba | APK ostaje ispod 2 MB, instalira se preko postojećeg |
| Komponente iz manifesta upisane u `proguard-rules.pro` | Most | bez toga R8 preimenuje servis i sistem ga ne nađe, bez ijedne greške u dnevniku |
| JVM testovi nad čistom logikom (`./gradlew test`) | oba | računanje sledećeg okidanja, imena foldera i praga pokreta se testira bez telefona |
| `CLAUDE.md` sa odlukama, razlozima i već napravljenim greškama | oba | projekat održavaju modeli |
| Podešavanja u `SharedPreferences`, `Config.kt` | oba | preživi instalaciju preko postojeće |
| Prenos preko lokalne mreže: HTTP + token + Bonjour, bez tuđeg servera | Most | isti kod za sinhronizaciju dva telefona, ništa ne izlazi na internet |

Ime aplikacije: **San**. Paket `studio.room211.san`.

---

## 2. Najveći rizik, i treba ga rešiti prvi: mrak

Noću u sobi nema svetla, a telefonska kamera bez svetla ne vidi ništa. To nije
stvar podešavanja u aplikaciji nego fizike, pa se rešava pre svega ostalog.

| rešenje | kako izgleda | mana |
|---|---|---|
| **Slaba noćna lampa** (izabrano) | topla lampica, 1 do 5 luksa, iza glave ili u uglu | mora da gori cele noći |
| **IC lampa (940nm)** | oku nevidljiva, kamera je vidi kao belo svetlo | većina zadnjih kamera ima IC filter i ne vidi je; prednja često vidi, mora proba |
| **Duga ekspozicija** | 1/4s do 2s, ISO do kraja | pomera se čovek, pa je slika mutna; granicu određuje sam telefon |
| **Blic ili baterijska** | radi uvek | budi te, otpada |

**Aplikacija zato dobija ekran „Noćna proba".** Pročita od telefona koliki
raspon ekspozicije i ISO uopšte dozvoljava, slika probnu sliku sa trenutnim
podešavanjima i odmah je pokaže na ekranu. Podesiš jednom, uveče, i znaš šta
ćeš dobiti ujutru. Bez toga se greška vidi tek posle prve protraćene noći.

Granica ekspozicije se razlikuje od telefona do telefona: negde 2s, negde 1/5s.
Zato se čita iz `SENSOR_INFO_EXPOSURE_TIME_RANGE`, a ne pretpostavlja.

**Zadnja kamera, ne prednja.** Veći senzor i svetliji objektiv, a ekran tada
gleda od kreveta pa te ne obasjava.

---

## 3. Kako se slika

**Ekspozicija se zaključava, ne prepušta automatici.** Automatika u mraku luta:
jedna slika crna, sledeća prosvetljena, treća mutna. Vrednosti se izmere jednom
u „Noćnoj probi" i posle toga se svaka slika pravi identično. Time i sve slike
jedne noći postaju uporedive, što je ceo smisao merenja pokreta iz tačke 7.

**Kamera se otvara samo za snimak pa zatvara.** Otvorena cela noć greje telefon
i troši bateriju bez razloga. Sa zaključanom ekspozicijom nema čekanja da se
automatika smiri, pa ceo posao traje oko sekundu.

Dva puta do slike, oba se ugrađuju:

| | rezolucija | zvuk okidača |
|---|---|---|
| **A: pravi snimak** | puna, 12 MP i više | neki telefoni prisilno pište i to se iz aplikacije ne može ugasiti |
| **B: kadar iz toka** | do 1080p ili 4K, zavisno od telefona | uvek tih |

Podrazumevano ide A. Ako se na tvom telefonu pokaže da okidač ne može da ućuti,
aplikacija prelazi na B automatski i to kaže na ekranu. B ima i sporednu korist:
može da složi nekoliko uzastopnih kadrova u jedan i tako smanji šum u mraku.

---

## 4. Okidanje tačno na sat

Traženo: ako je razmak 5 minuta, slika se u 23:00:00, 23:05:00, 23:10:00, a ne
pet minuta od trenutka kad si pritisnuo start.

Računa se `sledeće = ceil(sada / razmak) * razmak` nad ponoći kao nulom. Za
razmak koji ne deli sat na celo (recimo 7 minuta) poravnanje se vrši prema
ponoći, pa je red ravnomeran i preko punog sata.

Okidanje ide preko `AlarmManager.setExactAndAllowWhileIdle`, ne preko običnog
tajmera. Android posle sat vremena mirovanja uspava aplikaciju i običan tajmer
tada kasni po deset i više minuta, tiho.

Uz to ide servis u prvom planu tipa `camera` i `PARTIAL_WAKE_LOCK`. Od Androida
14 servis mora da ima taj tip i dozvolu `FOREGROUND_SERVICE_CAMERA`, i mora da
bude **pokrenut dok je aplikacija na ekranu**, inače sistem odbije da ga upali.
To znači: snimanje se pokreće dugmetom u aplikaciji, ne rasporedom u pozadini.

Zaključan ekran ne prekida servis, pa slikanje radi i kad je ekran ugašen.

Ponuđeni razmaci: 1, 2, 5, 10, 15, 30 i 60 minuta, plus slobodan unos.

---

## 5. Zvučna potvrda na početku

Traženo: da čuješ da radi dok ležeš, pa posle tišina do jutra.

Aplikacija se javi zvukom na **prvoj i drugoj slici**, pa ćuti do kraja noći.
Druga slika je važnija od prve: prva samo kaže da je start prošao, druga da je
ceo ciklus ponavljanja živ.

Uz to:

- jačina zvuka se podešava, i ima dugme „Čuj kako zvuči"
- vibracija umesto zvuka, za slučaj da neko spava pored
- „javi se na svakoj slici", samo za probu, sam se gasi posle deset minuta
- ako slikanje **padne** usred noći (nestalo mesta, sistem oduzeo kameru),
  aplikacija se oglasi. Tišina tada znači da nešto ne valja, a ne da je sve u redu.

---

## 6. Gde i kako se čuvaju slike

```
DCIM/San/2026-09-10/
  2026-09-10_23-00-00.jpg
  2026-09-10_23-05-00.jpg
  ...
  2026-09-11_07-25-00.jpg
  noc.json
```

**Folder nosi datum večeri kad je noć počela, ne datum svake slike.** Slika iz
03:45 pripada noći koja je počela sinoć; da folder pratio kalendarski datum,
svaka noć bi bila iseckana na dva foldera po ponoći.

**Ime fajla nosi pun datum i vreme**, pa je slika prepoznatljiva i kad je
izvučeš iz foldera. Isto vreme se upisuje i u EXIF (`DateTimeOriginal`), da ga
galerija i macOS pravilno sortiraju.

`noc.json` je zapisnik noći: vreme starta i kraja, podešavanja, i za svaku
sliku vreme i izmerena vrednost pokreta. Iz njega se crta grafikon, i po njemu
se sinhronizuju dva telefona.

**Vidljivost u galeriji se bira.** Upis ide preko `MediaStore` u `DCIM/San/`,
pa se folder pojavi u galeriji kao svaki drugi album i slike prežive brisanje
aplikacije. Ako ti je draže da ih niko ne vidi listajući telefon, prekidač
„Sakrij iz galerije" prebacuje čuvanje u privatan folder aplikacije.

### Upisano vreme na slici

Vreme se upisuje **na samu sliku**, u gornji levi ugao, sitno, belo na
providnoj tamnoj traci:

```
2026-09-11  03:45  #042
```

**Bez natpisa `datum:` i `vreme:`, i bez prevoda.** Zapis `2026-09-11` je
nedvosmislen na svakom jeziku i nijedan model ga ne meša sa danom i mesecom, a
`#042` se čita sam. Natpisi bi bili tridesetak znakova više na traci koja
pokriva deo kadra, a ne bi rešili ništa: oblik se modelu objasni jednom u
promptu i posle ga čita pouzdano.

**Piše stvarni datum kadra, ne datum noći.** Slika u 03:45 nosi `2026-09-11`
iako je u folderu `2026-09-10`. Da piše datum noći, model bi u redove upisao
vreme koje ne postoji. Kojoj noći slika pripada kaže folder, i to prompt navede
jednom na početku.

EXIF i ime fajla ovo već nose, ali upisano vreme rešava tri stvari koje oni ne
rešavaju:

- kad slike prevučeš u razgovor sa modelom, **svaka slika sama kaže kad je
  nastala**. Bez toga model pomeša redosled i tiho vrati redove sa pogrešnim
  vremenima, što je najgora vrsta greške jer izgleda uredno.
- redni broj `#042` odmah pokazuje da li nešto fali usred noći. Ukupan broj se
  ne upisuje, jer se u trenutku snimanja još ne zna koliko će noć trajati;
  koliko je slika poslato kaže prompt
- slika izvučena iz foldera i dalje zna odakle je

**Redosled je bitan: pokret se meri PRE upisivanja vremena.** Traka sa vremenom
se menja na svakoj slici, pa bi merenje razlike inače na svakom kadru videlo
promenu i ceo grafikon pokreta bi bio smeće. Sličica 64x64 se pravi iz sirovog
kadra, tekst se crta posle.

Na arhivskoj slici se upisivanje može isključiti; na smanjenoj kopiji koja ide
modelu je uvek uključeno, jer je tamo jedino sidro za vreme.

### Koliko to zauzme

Osam sati na pet minuta je 96 slika po noći.

| kvalitet | po slici | po noći | mesec dana |
|---|---|---|---|
| puna rezolucija, 12 MP, q95 | ~4 MB | ~380 MB | ~11 GB |
| 8 MP, q90 | ~2 MB | ~190 MB | ~5,7 GB |
| 1080x1920, q85 | ~0,4 MB | ~38 MB | ~1,2 GB |

Za prepoznavanje položaja tela dovoljno je i najmanje. Pošto si tražio najjači
kvalitet, podrazumevano ostaje puna rezolucija, ali uz dve kočnice:

- **sažimanje noći u video posle 7 dana i brisanje posle 30**, oba se podešavaju, vidi tačku 8
- **prekid ako na telefonu ostane manje od 1 GB**, uz zvuk iz tačke 5

Uz svaku sliku se čuva i sličica 320px. Pregled noći se onda lista trenutno,
umesto da telefon otvara stotinu slika od po četiri megabajta.

---

## 7. Šta aplikacija radi sa slikama ujutru

Ovo je razlika između foldera slika i alatke koja odgovara na pitanje kako
spavaš.

**Merenje pokreta.** Svaka slika se odmah po snimanju smanji na sivu sličicu
64x64 i uporedi sa prethodnom. Razlika je jedan broj, upisan u `noc.json`.
Traje par milisekundi i ne troši ništa.

Iz tog jednog broja izlazi sve ostalo:

- **grafikon noći**: gde si mirovao, gde si se prevrtao, koliko puta
- **spisak promena položaja**: samo slike gde je razlika prešla prag, obično
  10 do 20 od 96
- **filmska traka**: prevlačiš prstom kroz celu noć, kao ubrzani snimak

**Označavanje položaja.** Dodirneš sliku i izabereš: leđa, levi bok, desni bok,
stomak, van kreveta. Položaj važi do sledeće označene slike, pa aplikacija
izračuna koliko si sati proveo u kom položaju. Pošto se označavaju samo slike
sa promenom, to je desetak dodira ujutru, ne 96.

**Jutarnji izveštaj**: trajanje noći, broj slika, broj promena položaja, sati po
položaju, najmirniji i najnemirniji deo noći. Sedmični pregled sabira noći.

**Izvoz noći kao video.** 96 slika u ubrzani snimak od desetak sekundi. Korisno
za gledanje, i sažme noć sa 380 MB na par megabajta ako hoćeš da čuvaš arhivu a
oslobodiš mesto.

Sve ovo radi lokalno, bez modela i bez mreže. Automatsko prepoznavanje položaja
iz slike se namerno ne pravi sada: to je model koji greši, a ručno označavanje
desetak slika ujutru daje tačan podatak za pola minuta.

---

## 8. Dva telefona i mesto arhive

Arhiva ostaje **na telefonu**, u `DCIM/San/`, vidljiva u galeriji. Mac se ne
uvodi u priču, pa u prvoj verziji nema ni Python dela ni prijemnika.

Pošto telefoni rade jedan po jedan, sinhronizacija je prosto **prenošenje
foldera koje drugi telefon nema**. Isti obrazac kao u Mostu: Bonjour nađe drugi
telefon na kućnoj mreži, token sprečava da ti bilo ko sa iste mreže podmetne
slike, a prenosi se po `noc.json` zapisniku, pa prekinut prenos nastavlja gde je
stao a ne ispočetka. Ide samo kad su oba telefona na punjaču i na kućnom
Wi-Fi-ju. To je tačka 4 u redosledu izrade, ne prva verzija.

Do tada folderi ostaju obični folderi, pa se prebacuju kablom kad zatreba.

**Mesto na telefonu je zato glavno ograničenje**, i rešava se u tri koraka:

| starost noći | šta se čuva | zauzeće |
|---|---|---|
| do 7 dana | sve slike u punoj rezoluciji | ~380 MB po noći |
| 7 do 30 dana | ubrzani snimak noći, sličice i `noc.json` | ~5 MB po noći |
| preko 30 dana | briše se, ili ostaje ako isključiš brisanje | 0 |

Sažimanje u video se pokreće samo dok je telefon na punjaču i zaključan, jer
traje par minuta i greje. Grafikon pokreta i izveštaj o položajima ostaju i
posle brisanja slika, pošto stoje u `noc.json`, a on je par kilobajta.

Oba praga se podešavaju, i oba mogu da se isključe. Prekid pri manje od 1 GB
slobodnog mesta se ne isključuje.

## 9. AI analiza slika

Slike se šalju modelu na analizu. Ključna odluka: **ne šalju se sve slike
jednom pozivu, i ne šalju se dva puta.** Radi se u dva koraka, jer se time
razdvaja skup posao (gledanje slike) od jeftinog (zaključivanje nad podacima).

### Zašto dva koraka

Jedan poziv sa 300 slika iz pet noći je i skup i slab: model dobije ogromnu
količinu piksela i pokušava iz nje da izvuče i položaje i satnice i zaključke
odjednom, pa gubi tačnost baš na onome što je merljivo. Kad se slika jednom
pretvori u red podataka, taj red se posle koristi neograničeno puta, besplatno,
za bilo koje pitanje.

**Korak 1 pretvara sliku u jedan red.** Radi se jednom po slici, odmah ili
ujutru, i rezultat se čuva zauvek.

**Korak 2 čita samo redove, bez ijedne slike.** Tu se postavljaju prava pitanja:
kako se menjaju položaji kroz noć, ima li obrasca po satnicama, razlikuje li se
noć posle kasne večere od ostalih. Cela nedelja je tu par hiljada reči teksta,
pa se pitanje može ponoviti deset puta bez ikakvog troška.

### Korak 1: slika u red

Izlaz je strogo određen, sa unapred zadatim vrednostima. Slobodan opis se ne
prima, jer se nad njim posle ne može ništa sabrati.

```json
{"vreme":"2026-09-11T03:45:00",
 "polozaj":"desni_bok",
 "glava":"okrenuta_levo",
 "ruke":"iznad_glave",
 "noge":"skupljene",
 "pokrivenost":"do_pojasa",
 "u_krevetu":true,
 "pouzdanost":0.82,
 "napomena":""}
```

Vrednosti su zatvorene liste (`polozaj`: leđa, stomak, levi_bok, desni_bok,
polubok, sedi, van_kreveta, ne_vidi_se). Slobodan opis bi značio da se posle
svaki izlaz ručno krpi, i da se dva modela ne mogu uporediti uopšte.

U radu bez ključa oblik ne garantuje niko, pa ga **proverava `uporedi.py`**:
broj redova, dozvoljene vrednosti, vremena koja postoje u folderu. Šta ne
prođe, vraća se na ponovo, ne popravlja se rukom. Prompt zato traži CSV u
kodnom bloku, jedan red po slici, izričito zabranjuje sažimanje, i traži da
model **prvo ispiše koliko je slika dobio** pa tek onda tabelu. Kad se taj broj
ne poklopi, izlaz je neispravan i pre nego što se pogleda.

Četiri stvari čine razliku u tačnosti:

**Slike idu u grupama od 12, ne pojedinačno.** Dvanaest slika na razmaku od pet
minuta je tačno jedan sat. Model tada vidi susedne kadrove i može da kaže „isti
položaj kao prethodni", što je i tačnije i doslednije od dvanaest nezavisnih
procena. Uputstvo se pritom šalje jednom umesto dvanaest puta.

**Ispred svake slike ide tekstualni blok sa vremenom.** Bez toga model pomeša
redosled i redovi dobiju pogrešna vremena, tiho.

**Šalju se samo slike na kojima se nešto promenilo.** Merenje pokreta iz tačke 7
već zna gde je promena. Šalje se svaki kadar preko praga, plus po jedan na sat
kao sidro. Od 96 slika obično ostane 25 do 35, a nijedna promena položaja se ne
propušta, jer promena po definiciji pravi pokret. Ostali redovi se popune
prethodnim položajem, sa oznakom da su izvedeni a ne viđeni.

**Slika se pre slanja smanji na oko 432x768.** Za razlikovanje leđa od boka
rezolucija telefona ne donosi ništa, a puna slika troši kontekst modela
nekoliko puta više, pa agent u terminalu na pola noći ostane bez mesta. Puna
slika ostaje na disku, agent dobija smanjenu.

### Sve slike ostaju, modelu ide izbor

Da, tako je. **Nijedna slika se ne briše i ne menja.** Filter po pokretu bira
koje se šalju na analizu, a ostale se popune položajem prethodnog kadra i
označe kao izvedene, ne viđene. Prag filtera stoji u `noc.json`, pa se ista noć
može ponovo propustiti sa nižim pragom i dobiti više kadrova, kad god poželiš.

### Offline režim: agenti koje već plaćaš, bez ključa

Ovo je izabrani put. Slike se prebace na računar, ti pokreneš agenta nad
folderom, i ništa se ne plaća po slici.

Dva načina, oba kroz isti folder:

**Agent u terminalu** (Claude Code, Codex i slični). Otvoriš `analiza/` i kažeš
šta da radi. U folderu stoji `CLAUDE.md` koji agentu daje ceo postupak: koji
prompt da pročita, da slike uzima u grupama od 12, kako da imenuje izlaz i da
ne sme da sažima. Ti kažeš jednu rečenicu, ostalo je zapisano.

**Razgovor u pregledaču** (ChatGPT, Claude). `spremi.py` napravi gotov tekst za
lepljenje i folder sa smanjenim slikama koje prevučeš u poruku. Ono što model
ispiše nalepiš u `sirovo.txt`, a `uporedi.py` iz toga izvuče tabelu.

**Jedan pokušaj je jedan folder.** To je ono što posle omogućava da se gleda
šta je koji agent uradio i koliko je bio tačan:

```
analiza/zadaci/2026-09-10_opus5_p03/
  zadatak.md      koji prompt i koja verzija, koji model, koji folder, kad
  sirovo.txt      ono što je model doslovno ispisao
  izlaz.csv       izvučena tabela
  ocena.txt       rezultat poređenja sa etalonom
```

Bez `zadatak.md` se posle mesec dana ne zna koji je prompt dao koji izlaz, pa
poređenje prompta v2 i v3 postaje nagađanje.

### Raspored celog projekta i GitHub

Sve na jednom mestu, kako si tražio, a slike ispadaju iz repozitorijuma:

```
20260910 Sleep camera/
  android/                    aplikacija
  analiza/
    promptovi/
      01-polozaj.md           slika u red
      02-izvestaj.md          nad redovima, bez slika
      arhiva/                 starije verzije, da se poređenje ima sa čim
    zadaci/                   jedan pokušaj je jedan folder, ide u git
    etalon/                   tvoje ručne oznake, idu u git
    slike/                    smanjene kopije za analizu, NE idu u git
      2026/09/2026-09-10/
    spremi.py  uporedi.py  CLAUDE.md
  README.md  CLAUDE.md  .gitignore
```

**U git ide sve osim slika**: promptovi, sve njihove verzije, svaki izlaz svakog
modela, etalon, ocene. To i jeste poenta, jer se tek iz istorije vidi da li je
prompt v3 bio bolji od v2, ili se samo tako činilo.

**Puna arhiva noći ne stoji u repozitorijumu uopšte**, nego napolju, recimo u
`~/Slike/San/`. U `analiza/slike/` idu samo smanjene kopije, oko 40 KB po slici,
4 MB po noći, i tu su samo zato da agent ima šta da otvori bez traženja po disku.

**`.gitignore` se piše po nastavku, ne po folderu.** `*.jpg`, `*.png`, `*.mp4`,
gde god se nalazili. Ignorisan folder štiti samo dok neko ne ostavi sliku van
njega, a ovde je cena greške tvoja fotografija u javnom repozitorijumu, zauvek,
i posle brisanja iz istorije. Uz to ide provera pre objave koja **odbija commit
ako je bilo šta nalik slici u pripremi**, po ugledu na skener ključeva iz
`objavi.sh` u Mostu, koji je tamo napravljen posle tačno takve greške.
Repozitorijum je svejedno privatan.

### Kako se dele folderi sa noćima

`slike/2026/09/2026-09-10/`, dakle godina, mesec, pa noć.

Ravno, sve u jednom folderu, znači 365 stavki godišnje. To radi, ali se u njemu
ne vidi ništa: ne znaš u kom si mesecu, ne možeš da premestiš zimu na spoljni
disk jednim potezom, a spisak se učitava sekundu. Sa godinom i mesecom ima 12
foldera po godini i tridesetak noći u svakom.

**Ime noći ostaje pun datum, `2026-09-10`, a ne `10`.** Folder izvučen iz
konteksta i dalje zna šta je. Isto pravilo kao kod imena fajla.

Ovo se odlučuje sada, a ne kasnije preko agenta. Kasnija preraspodela znači
menjanje putanja u `noc.json`, u svakom `zadatak.md` i u svakoj oceni, dakle
posao koji sada košta nula a za pola godine pola dana, uz šansu da nešto ostane
polomljeno.

### Koji model, i kako to izmeriti bez ključa

Merenje ne traži ključ. Pustiš istu noć kroz svaki agent koji već imaš, pa
`uporedi.py` uporedi svaki izlaz sa tvojih dvadesetak ručno označenih kadrova i
ispiše četiri stvari:

| | odgovara na |
|---|---|
| pogodak po modelu naspram etalona | koji model stvarno vidi položaj |
| slaganje modela međusobno | da li uopšte gledaju istu stvar |
| redovi koji fale ili su pomereni | koji model odustane na tridesetoj slici |
| kadrovi gde se svi razilaze | te pogledaj sam, obično su stvarno dvosmisleni |

**Bez etalona poređenje ne vredi ništa.** Tri modela vrate tri uredne tabele i
sve tri izgledaju ubedljivo, a nemaš čime da presudiš. Ručne oznake su sudija;
aplikacija ih ionako skuplja.

Isti `uporedi.py` posle služi i za praćenje da li je nova verzija prompta bolja
od stare, nad istom noći i istim etalonom.

### Ako se ikad pređe na ključ

Ostavljam zapisano, jer se odluka može promeniti kad ručno prebacivanje dosadi.
Cene po milionu tokena, stanje 09/2026:

| model | ulaz | izlaz | jedna noć | uz Batch |
|---|---|---|---|---|
| Claude Haiku 4.5 | $1 | $5 | ~$0,08 | ~$0,04 |
| Claude Sonnet 5 | $2 | $10 | ~$0,17 | ~$0,09 |
| Claude Opus 5 | $5 | $25 | ~$0,42 | ~$0,21 |

Message Batches API šalje više noći odjednom, asinhrono i upola jeftinije, što
je tačno oblik „skupim par dana pa pošaljem sve". Prelazak ne menja ništa u
folderima: isti `promptovi/`, isti `zadaci/`, isti `uporedi.py`, samo se izlaz
puni skriptom umesto rukom.

### Privatnost, i šta „offline" ovde stvarno znači

Jedna razlika koju treba jasno reći: **bez API ključa ne znači da slike ne
odlaze nikuda.** Claude Code i ChatGPT su i dalje tuđi serveri; menja se ko
plaća i po čemu, ne kuda slike idu. Anthropic ne koristi API podatke za obuku
modela, a za pretplate u razgovoru pravila su drugačija i menjaju se, pa ih
vredi pročitati jednom pre prve noći.

Ako je cilj da slika ne izađe iz kuće uopšte, postoji treći put: model koji radi
lokalno na Mac-u, preko Ollama-e. Sporije je, slabije vidi i traži podešavanje,
ali ne šalje ništa. Vredi ga probati baš zato što `uporedi.py` može da izmeri
koliko je slabiji, umesto da se to pretpostavlja.

Tri stepena, biraš:

| stepen | šta izlazi |
|---|---|
| ceo kadar | najtačnije, model vidi sve |
| samo krevet, isečeno | isečak se podesi jednom, lice i soba ostaju napolju |
| lokalni model ili ništa | bez modela ostaje merenje pokreta: kada si se okretao, ne i u kom položaju |

## 10. Ekrani

Četiri kartice, isti Material 3 izgled kao Most i Diktat, tamna tema
podrazumevano jer se aplikacija otvara uveče u mračnoj sobi.

**Snimanje.** Veliko dugme Start i Stop. Iznad njega živa slika kamere, da
nameštaš kadar. Ispod: sledeće okidanje u koliko sati, koliko je slika do sada
napravljeno, baterija, slobodno mesto. Kad pritisneš Start, kamera se gasi sa
ekrana i ostaje samo crn ekran sa satom sledećeg okidanja, prigušen na najmanju
osvetljenost, da te telefon ne obasjava dok tražiš san.

**Noći.** Spisak noći, svaka sa datumom, trajanjem, brojem slika i malim
grafikonom pokreta. Dodir otvara noć: mreža sličica, filmska traka, izveštaj,
dugmad za izvoz videa i brisanje.

**Podešavanja.** Razmak, kvalitet i rezolucija, kamera (zadnja ili prednja),
ekspozicija i ISO sa dugmetom „Noćna proba", zvučna potvrda, automatsko
brisanje, vidljivost u galeriji, sinhronizacija, automatski prekid u određeni sat.

**Provera.** Jedan ekran koji redom kaže: dozvola za kameru, dozvola za tačne
alarme, isključenje iz štednje baterije, slobodno mesto, da li okidač može da
ćuti, kad je poslednja sinhronizacija prošla. Isti obrazac kao `doctor.py` na
Mac-u i ekran sa dozvolama u Diktatu, jer se u oba projekta pokazalo da se
polovina problema svede na jednu neuključenu dozvolu.

---

## 11. Šta namerno ne radi

**Sama aplikacija ne šalje ništa na internet.** Nema naloga, nema oblaka, nema
analitike. Jedini put napolje je AI analiza iz tačke 9, koju sam pokrećeš sa
Mac-a nad noćima koje si izabrao, i koja se može isključiti ili ograničiti na
isečak kadra.

**Ne snima video celu noć.** Osam sati videa je desetine gigabajta i sat
pregledanja, a za položaj tela nosi istu informaciju kao 96 slika.

**Ne snima zvuk.** Hrkanje i disanje su drugi zadatak i druga dozvola; ako
zatreba, dodaje se kasnije i odvojeno se uključuje.

**Ne meri faze sna.** Kamera vidi položaj i pokret, ne moždane talase. Ono što
se iz ovoga pošteno vidi jeste koliko se okrećeš, kada, i u kom položaju si
proveo koliko vremena.

**Ne prepoznaje položaj sam, na telefonu.** Telefon meri pokret i ćuti;
prepoznavanje položaja je posao modela sa Mac-a, tačka 9, ili tvojih desetak
dodira ujutru, tačka 7. Model na telefonu bi bio treći način da se isto uradi
lošije.

---

## 12. Šta može da pokvari noć, i šta se radi povodom toga

| opasnost | šta se dešava | mera u aplikaciji |
|---|---|---|
| Proizvođač ubije servis (Samsung, Xiaomi štednja) | slikanje tiho prestane u dva ujutru | ekran „Provera" vodi pravo u isključenje štednje baterije; alarm se ponovo zakazuje i posle ubijanja servisa |
| Prisilan zvuk okidača | budi te svakih pet minuta | prelazak na kadar iz toka, tačka 3 |
| Nestane mesta | slike se tiho ne upisuju | prag od 1 GB, zvučno upozorenje, automatski prekid |
| Telefon nije na punjaču | ujutru ugašen telefon | upozorenje pre starta ako punjač nije priključen |
| Grejanje telefona | kamera se sama gasi | kamera se otvara samo za snimak, ne stoji otvorena |
| Sistem oduzme kameru (poziv, druga aplikacija) | rupa u noći | rupa se upiše u `noc.json` i vidi se na grafikonu, umesto da se pretvara da je noć cela |

---

## 13. Redosled izrade

1. **Prva noć što pre.** Servis, tačno okidanje, upis u folder, zvuk na prve dve
   slike, „Noćna proba" za mrak. **Napravljeno 10.09.2026, verzija 0.1.**
   Merenje pokreta i zapisnik `noc.json` su ušli već ovde, jer su uslov za
   sve ostalo.
2. **Priprema za analizu bez ključa.** `spremi.py`, `uporedi.py`, prompt 01 i
   02, raspored zadataka. **Napravljeno 10.09.2026**, provereno na izmišljenoj
   noći; ostaje da se pusti na pravoj.
3. Pregled noći u aplikaciji: sličice, filmska traka, grafikon pokreta.
4. Označavanje položaja prstom, ujutru, samo kadrovi sa promenom. Odatle izlazi
   etalon, bez koga poređenje modela ne vredi ništa.
5. Automatsko brisanje starih noći i sažimanje u ubrzani snimak.
6. Sinhronizacija dva telefona i izvoz noći kao video.

---

## 14. Otvoreno

**Koji telefon.** Jedini podatak koji još nedostaje. Utiče na granicu
ekspozicije, na to da li okidač može da ćuti i na štednju baterije koja ubija
servise. Ako je Samsung, ekran „Provera" dobija i prečicu do „Aplikacije koje
nikad ne uspavljuj", jer se to na Samsungu pokazalo kao najčešći uzrok da
pozadinski posao tiho stane.
