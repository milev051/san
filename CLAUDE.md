# Napomene za održavanje

Ovaj projekat održavaju modeli, ne ljudi. Ovde su odluke i **razlozi** za njih,
plus greške koje su već napravljene, da se ne ponavljaju.

Dva dela: `android/` (Kotlin) snima noć; `analiza/` (Python, macOS) priprema
noć za model i ocenjuje izlaze. Isti obrasci kao u Diktatu i Mostu: bez mrežnih
biblioteka, ekran se gradi u kodu, JVM testovi nad čistom logikom.

---

## Šta ovo rešava

Kako se čovek okreće tokom noći i u kom položaju provede koliko vremena.
Telefon samo snima i meri pokret; zaključivanje je posao računara.

---

## Pravila koja se ne smeju prekršiti

**Kamera ne okreće piksele, nego upisuje ugao u EXIF.** Galerija to poštuje,
`BitmapFactory` ne. Zato slika u aplikaciji izgleda položeno. Važnije: kad se
na sliku upisuje vreme, slika se iznova kodira i EXIF nestane, pa bi traka bila
nacrtana bočno i takva ostala zauvek. Okretanje se primenjuje na piksele u
`Slika.uspravno`, PRE crtanja trake, a gotova slika dobija uspravnu oznaku.

**Noćni režim i ručne vrednosti se međusobno isključuju.** U proširenoj sesiji
(`CameraExtensionSession`) dozvoljeni su samo ključevi koje uređaj prijavi kroz
`getAvailableCaptureRequestKeys`, a `SENSOR_EXPOSURE_TIME` nije među njima:
ekspoziciju bira proizvođačev kod. Zato je režim jedan izbor od tri, a ne tri
prekidača koja se sabiraju. Ključ koji uređaj nije prijavio ruši ceo snimak
izuzetkom, pa se postavlja samo ono što je dozvoljeno.

**Proširenja imaju svoj spisak veličina.** Uzimaju se iz
`getExtensionSupportedSizes`, ne iz `SCALER_STREAM_CONFIGURATION_MAP`; veličina
van tog spiska obara konfiguraciju sesije.

**Proširena sesija traži živi pregled pre snimka.** Bez ponavljajućeg zahteva
proizvođačev kod nema iz čega da slaže kadrove.

**Noćni snimak traje sekundama, ne milisekundama.** Rok čekanja je 45 sekundi.
Sa istim rokom kao za običan snimak, noćni bi se prekidao taman kad radi.

**Kad noćni režim ne uspe, snima se obično, ali se to KAŽE.** Slika bez noćnog
režima je bolja od nijedne slike, a prećutana zamena znači da bi se cela noć
snimila loše a da niko ne zna zašto.

**Merenje pokreta ne sme da broji promenu svetline.** Noćni režim bira
ekspoziciju za svaki snimak, pa dva kadra iste mirne sobe umeju da budu
različito svetla. `Pokret.razlikaBezSjaja` svakoj sličici oduzme njenu srednju
svetlinu pre poređenja; bez toga mirna noć izgleda nemirno.

**Kamere se razlikuju, i izbor mora da postoji.** Izmereno na Samsung S23:
kamera 0 daje najviše 1/6 s i ne prijavljuje nijedan noćni režim, nego samo
automatski, lice i bokeh. Zbog toga postoji izbor tačne kamere po oznaci, a ne
samo prednja/zadnja, i kartica koja za svaku ispisuje raspon ekspozicije i
proširenja.

**Noćni režim se traži na dva načina, jer to nisu isti spiskovi.** Platformski
`CameraExtensionCharacteristics` na S23 ne prijavljuje `EXTENSION_NIGHT` ni na
jednoj kameri; CameraX proširenja razgovaraju sa OEM bibliotekom direktno i umeju
sa starijim izdanjem te biblioteke. Prvo se proba platformski put jer je lakši,
pa CameraX, pa tek onda obično snimanje, i uvek se kaže šta je stvarno urađeno.

**CameraX se sme dirati samo sa glavne niti.** `LifecycleRegistry` i
`bindToLifecycle` padaju sa bilo koje druge, a poziv dolazi sa radne niti
servisa, pa ceo posao ide kroz `Handler(Looper.getMainLooper())` a pozivalac
čeka na bravi. Isto pravilo kao AppKit u Mostu.

**Pregled se vezuje i kad se ne prikazuje.** Deo proizvođačeve obrade radi nad
tokom pregleda, pa bez njega neki uređaji odbiju snimak.

**Kad telefon ne da dugu ekspoziciju, slaže se više kadrova.** `Slaganje.spoji`
sabira, ne uprosečava: prosek osam mračnih kadrova je i dalje mračan, samo mu je
šum manji. Sabiranje je ono što sliku posvetli, pa se vrednosti ne dele nego
mekano primiču belom: tvrdo sečenje na 255 spljoštilo bi svaku lampicu u ravnu
belu mrlju. Ispod kolena je zbir netaknut, jer je tamo skoro cela noćna slika.
Zaokružuje se, ne odseca, inače ni potpuno prezaren piksel ne bi bio čisto beo.
Slaže se na 1600px duže strane, jer sabirač za 12 MP ne bi stao u memoriju.

**Tražena ekspozicija nije dobijena ekspozicija.** Granica se razlikuje od
modela do modela; sve iznad nje telefon tiho stegne na svoju vrednost, bez
greške, pa izgleda kao da podešavanje ne radi. Zato se iz `CaptureResult` čita
šta je senzor STVARNO upotrebio, i „Noćna proba" ispisuje i traženo i dobijeno.

**Male kopije ne smeju u `Documents` automatski.** Galerija tada prikazuje dva
albuma istog imena sa istim slikama, jedan iz DCIM-a i jedan iz Documents-a.
Male kopije žive u privatnom folderu aplikacije, a u `Documents` ih prebacuje
dugme „Izvezi za računar", uz `.nomedia`.

**Pokret se meri PRE nego što se na sliku upiše vreme.** Traka sa vremenom se
menja na svakoj slici. Ko izmeri razliku posle upisa, dobije da se svaki kadar
promenio, pa je ceo grafikon noći smeće, izbor kadrova za model besmislen, a
ništa ne izgleda pokvareno. Vidi redosled u `Obrada.jednaSlika`.

**Okidanje se računa prema ponoći, ne od trenutka pokretanja.** `sada + razmak`
odluta sa punog minuta već posle nekoliko sati, jer svaki snimak traje malo
drugačije. `Raspored.sledece` zato računa sledeću tačku na mreži.

**Kad `sada` padne tačno na mrežu, vraća se SLEDEĆA tačka.** Alarm ume da stigne
milisekundu ranije ili tačno na vreme; vraćanje istog trenutka okinulo bi isti
kadar dvaput.

**Folder nosi datum večeri, ne kalendarski datum kadra.** Granica je podne.
Bez toga se svaka noć iseče na dva foldera u ponoć.

**Na slici piše STVARNI datum kadra, ne datum noći.** Slika u 03:45 stoji u
folderu `2026-09-10` a nosi `2026-09-11`. Da piše datum noći, model bi u redove
upisao vreme koje ne postoji, i to bi izgledalo uredno.

**Kratak prekid je nastavak iste noći, ne nova noć.** Granica je osam sati od
POSLEDNJE SLIKE, ne od zaustavljanja. Ustao do kupatila i vratio se: isti
folder, isto brojanje, ista zaključana ekspozicija. `Raspored.nastavakIste`
odbija i negativnu razliku, jer telefon ume da pomeri sat unazad, a razlika
manja od granice bi tada nastavila noć koja se još nije desila.

**Kamera se otvara samo za snimak pa zatvara.** Otvorena cele noći greje telefon
i troši bateriju osam sati zbog posla koji traje sekundu.

**Fokus se zaključava uvek, i u automatskom režimu.** U mraku autofokus lovi i
vraća mutan kadar bez ijedne greške u dnevniku.

**Ekspozicija se zaključava kad telefon to dozvoljava.** Automatika u mraku
luta, pa dve susedne slike nisu uporedive, a merenje pokreta stoji tačno na
tome da jesu.

**Zagrevanje pre snimka nije višak.** Bez lažne površine i kratkog ponavljajućeg
zahteva, jedini snimak stiže pre nego što se senzor smiri, pa je kadar crn ili
prosvetljen.

**Servis mora biti tipa `camera` i mora se pokrenuti dok je aplikacija na
ekranu.** Android 14 odbija oboje drugačije. Zato snimanje počinje dugmetom, a
ne rasporedom u pozadini.

**Alarm gađa servis preko `getForegroundService`, ne preko prijemnika.** Od
Androida 12 prijemnik ne sme da upali servis u prvom planu iz pozadine; ovako
se samo budi servis koji već radi, a tačan alarm je izuzetak koji to dozvoljava.

**`startForeground` se poziva pre bilo kakvog posla i njegov neuspeh se ne sme
srušiti.** Kad sistem ubije servis pa ga alarm probudi, pokretanje ume da bude
odbijeno. Pad bi u tri ujutru izgledao isto kao da kamera ne radi, pa se umesto
toga upisuje `razlogPrekida` i vidi se ujutru.

**Zaustavljanje traži dugi pritisak, pokretanje ne.** Ceo ekran je dugme jer se
telefon pipa u mraku, ali bi tada jedan slučajan dodir prekinuo noć, a to se ne
bi videlo do jutra. Zato je pokretanje jedan dodir, a zaustavljanje dugi
pritisak.

**Zvuk pri pokretanju ide PRE bilo kakvog posla, i drugim tonom.** To je jedina
potvrda koju imaš dok stojiš pored telefona okrenutog od tebe, a po zvuku se
mora razlikovati „primljeno je" od „slika je napravljena".

**Prva slika ide posle odlaganja, ne odmah.** Odmah bi uhvatila praznu sobu i
tebe pored telefona.

**Greška usred noći se OGLAŠAVA.** Tišina inače znači „sve je u redu" i onda
kada nije, a to se otkriva tek ujutru, kada je noć već propala.

**Rupa se upisuje u zapisnik, ne prećutkuje.** Kadar koji nije uspeo ide u
`noc.json` sa razlogom, da se na grafikonu vidi rupa umesto da noć izgleda cela.

**Isti fajl se briše pa upisuje.** MediaStore inače pravi `noc (1).json`,
`noc (2).json` u nedogled, pa na računar stigne dvadeset zapisnika iste noći.

**`noc.json` ne može u DCIM.** MediaStore tamo prima samo slike i video. Zato
zapisnik i smanjene kopije idu u `Documents/San/`, što je ionako folder koji se
prebacuje na računar.

**Smanjene kopije ne idu u galeriju.** Napravile bi drugi album sa istim
slikama, pa bi svako listanje telefona pokazalo svaku noć dvaput.

**Slike ne idu u git, i to se proverava po nastavku.** `.gitignore` po folderu
štiti samo dok neko ne ostavi sliku van njega. `provera-slika.sh` odbija commit.
Ista vrsta greške je već napravljena u Diktatu sa API ključem u APK-u.

**Izbor kadrova za model mora biti isti na telefonu i u `spremi.py`.**
`Pokret.izbor` i funkcija `izbor` u skripti su namerno istog oblika. Da se
raziđu, ista noć bi davala različit izbor u zavisnosti od toga ko bira, a to se
primeti tek kad se dva izlaza ne poklope.

---

## Greške koje su već napravljene

| greška | posledica | ispravno |
|---|---|---|
| Merenje pokreta posle upisa vremena na sliku | svaki kadar izgleda kao promena | slicica se pravi iz sirovog kadra, tekst se crta posle |
| `sada + razmak` umesto mreže od ponoći | okidanje odluta sa punog minuta | `Raspored.sledece` |
| Datum noći upisan na sliku | model vrati vremena koja ne postoje | `Raspored.zigTekst` piše stvarni datum kadra |
| Snimak bez zagrevanja | prvi kadar crn ili prosvetljen | lažna površina i kratak ponavljajući zahtev |
| `noc.json` u DCIM preko MediaStore-a | upis odbijen bez jasnog razloga | `Documents/San/` |
| Isti fajl upisan bez brisanja | `noc (1).json`, `noc (2).json`, … | brisanje pa upis |
| Pad kad `startForeground` ne prođe | noć stane u tri ujutru bez traga | uhvaćen izuzetak i upisan `razlogPrekida` |
| Puna slika dekodirana u ARGB_8888 bez rezerve | 48 MB po kadru, `OutOfMemory` | pokušaj ARGB, pa RGB_565, pa original bez trake |
| `BitmapFactory` bez EXIF ugla | probna slika položena, a traka sa vremenom nacrtana bočno i zapečena u sliku | `Slika.uspravno` pre crtanja, uspravna oznaka pri upisu |
| Male kopije upisivane u `Documents` posle svake slike | dva albuma istog imena sa istim slikama u galeriji | privatni folder, izvoz na dugme; `Zapis.obrisiIzvoz` čisti staro |
| `SENSOR_EXPOSURE_TIME` bez čitanja `CaptureResult` | tražena vrednost stegnuta na granicu uređaja, izgleda kao da podešavanje ne radi | pročitaj i prikaži dobijeno pored traženog |
| `SENSOR_FRAME_DURATION` kraće od ekspozicije | senzor preseca ekspoziciju | veće od dva, uz `SENSOR_INFO_MAX_FRAME_DURATION` |
| Traženo 1/2 s na kameri koja daje 1/6 s | ista mračna slika za svaku traženu vrednost, bez greške | dugme „Postavi na granicu telefona", pa režim Slaganje |
| Pretpostavka da noćni režim postoji na glavnoj kameri | na S23 ga kamera 0 nema | izbor kamere po oznaci, uz ispis proširenja za svaku |
| `strings \| grep -q` uz `pipefail` u `objavi.sh` | `grep` izađe na prvom pogotku, `strings` dobije SIGPIPE, skripta odbije ispravan APK | stringovi u fajl pa `grep -F` nad fajlom |

---

## Zašto je nešto tako a ne drugačije

**Zašto nema žive slike sa kamere.** U mračnoj sobi ne pokazuje ništa, a tražila
bi drugu površinu u sesiji i ceo `TextureView`. Probna slika sa zaključanom
ekspozicijom pokazuje tačno ono što će noć dati, što je jedino što se zaista
želi videti.

**Zašto prva slika ide odmah, van mreže.** Potvrda ima smisla samo dok stojiš
pored telefona. Sa razmakom od sat vremena bi se na prvi zvuk čekalo sat.

**Zašto zvuk ide na `STREAM_ALARM`.** Telefon koji noću stoji u tihom režimu ne
bi pustio ništa na kanalu obaveštenja, pa bi čovek legao misleći da nešto ne radi.

**Zašto se šalju samo kadrovi sa promenom.** Promena položaja po definiciji
pravi pokret, pa se ništa ne gubi, a od 96 kadrova ostane tridesetak. Sidro na
svakih dvanaest postoji da se i mirni delovi noći vide.

**Zašto zatvorene liste vrednosti u promptu.** Slobodan opis znači da se dva
modela ne mogu uporediti uopšte, a svaki izlaz se posle krpi rukom.

**Zašto poređenje bez etalona ne vredi.** Tri modela vrate tri uredne tabele i
sve tri izgledaju ubedljivo. Ručne oznake su jedini sudija. Isti postupak je u
Diktatu dao izmerenih 0.93 umesto utiska.

**Zašto bez API ključa, za sada.** Pretplate su plaćene, a odluka o ključu ima
smisla tek kad se zna koji model i koliko dobro radi. Prelazak ne menja foldere:
isti `promptovi/`, isti `zadaci/`, isti `uporedi.py`.

**Zašto se ne prepoznaje položaj na telefonu.** Bio bi treći način da se isto
uradi lošije od modela sa računara i od desetak dodira ujutru.

---

## Kako se proverava izmena

**Android**

```bash
cd android && ./gradlew test      # 26 JVM testova nad računanjem vremena i pokreta
./build.sh                        # release APK
./build.sh install                # i pošalji na telefon
```

Build koji „prođe" ne znači da je izmena unutra. Release je skupljen R8-om, pa
komponente iz manifesta moraju ostati u `proguard-rules.pro`:

```bash
unzip -o app/build/outputs/apk/release/app-release.apk 'classes*.dex' -d /tmp/dx
strings -a /tmp/dx/classes*.dex | grep 'studio/room211/san/Snimanje'
```

Dijakritički stringovi se ne vide (MUTF-8), traži ASCII delove. Podigni
`versionName` da se na telefonu vidi koja je verzija.

**Ono što se ne vidi iz testova** mora se probati na telefonu, jednom, i to
preko noći: da li servis preživi do jutra, da li se okidanje drži punog minuta
posle šest sati, i da li okidač uopšte može da ćuti na tom modelu.

**Analiza**

```bash
cd analiza
python3 spremi.py <datum> --model <ime>
python3 uporedi.py <datum>
```

Ceo put se proverava bez telefona: napravi izmišljen `noc.json` sa 96 kadrova i
par promena, pusti `spremi.py`, upiši lažan izlaz u `sirovo.txt` i vidi da li
`uporedi.py` uhvati skraćen izlaz i nedozvoljene vrednosti. To je ovde urađeno
pre prve prave noći i uhvatilo je oboje.

---

## Šta još nije napravljeno

Redosled i razlozi su u `FUNKCIJE.md`, tačka 13.

| | |
|---|---|
| Filmska traka i grafikon pokreta | mreža sličica postoji od 0.2, ostalo ne |
| Označavanje položaja prstom | ujutru, samo kadrovi sa promenom; iz toga izlazi etalon |
| Automatsko brisanje starih noći | i sažimanje u ubrzani snimak posle sedam dana |
| Sinhronizacija dva telefona | Bonjour i token, prepisati iz `most/` |
| Izvoz noći kao video | |
