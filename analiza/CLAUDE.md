# analiza/

Priprema noći za model i ocena onoga što model vrati. Bez API ključa, bez mreže,
samo standardna biblioteka.

## Postupak, za agenta u terminalu

Kada dobiješ zadatak da analiziraš noć:

1. Otvori `zadaci/<datum>_<model>_pNN/` i pročitaj `prompt.txt`. To je ceo
   zadatak, doslovno; ne prepravljaj ga i ne skraćuj.
2. Slike su u `poslati/`. Uzimaj ih **po redosledu imena, u grupama od najviše
   12**. Dvanaest slika na razmaku od pet minuta je jedan sat, i model tada vidi
   susedne kadrove pa može reći „isti položaj kao prethodni".
3. Za svaku sliku ispiši tačno jedan red. **Nikada ne sažimaj i ne piši „isto
   kao gore".** Ako ostaneš bez konteksta, upiši ono što imaš u `izlaz.csv` pa
   nastavi od prve neobrađene slike; nemoj da nagađaš ostatak.
4. Rezultat upiši u `izlaz.csv`, sa zaglavljem iz prompta i jednim redom po
   slici. Ono što si doslovno ispisao ide u `sirovo.txt`.
5. Pokreni `python3 ../../uporedi.py <datum>` i pogledaj primedbe. Skraćen
   izlaz i nedozvoljene vrednosti se **ne krpe rukom**, nego se zadatak radi
   ponovo.

## Pravila

**Jedan pokušaj je jedan folder.** `zadatak.md` pamti koji prompt, koja
verzija, koji model i koja noć. Bez toga se posle mesec dana ne zna šta je dalo
koji izlaz, pa poređenje prompta v2 i v3 postaje nagađanje.

**Prompt se menja samo u `promptovi/`, i stara verzija se prvo kopira u
`promptovi/arhiva/`.** Inače se ne može reći da li je nova bolja.

**Etalon je jedini sudija.** `etalon/<datum>.csv` su ručno označeni kadrovi.
Bez njega `uporedi.py` može da kaže samo da li se modeli međusobno slažu, a tri
modela umeju da se slože i kad su sva tri u krivu.

**Izbor kadrova mora ostati isti kao u aplikaciji.** Funkcija `izbor` u
`spremi.py` i `Pokret.izbor` u Kotlinu su namerno istog oblika. Da se raziđu,
ista noć bi davala različit izbor u zavisnosti od toga ko bira.

**Slike ne idu u git.** `.gitignore` ih hvata po nastavku, a `provera-slika.sh`
odbija commit. Promptovi, izlazi, ocene i etalon **idu**, jer su oni ceo smisao
poređenja.
