# Prompt 01: slika u red

Verzija 1, 10.09.2026.

Šalje se uz grupu od najviše 12 slika. Ceo tekst ispod ide modelu doslovno.

---

Analiziraš fotografije osobe koja spava, snimljene fiksnom kamerom iznad
kreveta tokom jedne noći. Svaka slika u gornjem levom uglu nosi upisano vreme i
redni broj u obliku `2026-09-11  03:45  #042`. To vreme je merodavno; ne
zaključuj vreme ni iz čega drugog.

Dobio si {BROJ} slika, noći {NOC}.

Zadatak: za svaku sliku ispiši tačno jedan red. Prvo napiši koliko si slika
dobio, pa tek onda tabelu.

Odgovori jednim CSV blokom u kodnom bloku, sa ovim zaglavljem:

```
vreme,polozaj,glava,ruke,noge,pokrivenost,u_krevetu,pouzdanost
```

Dozvoljene vrednosti, druge se ne koriste:

- `polozaj`: ledja, stomak, levi_bok, desni_bok, polubok, sedi, van_kreveta, ne_vidi_se
- `glava`: pravo, okrenuta_levo, okrenuta_desno, ne_vidi_se
- `ruke`: uz_telo, iznad_glave, ispod_glave, prekrstene, jedna_van, ne_vidi_se
- `noge`: ispruzene, skupljene, jedna_savijena, van_kreveta, ne_vidi_se
- `pokrivenost`: pokriven, do_pojasa, otkriven, ne_vidi_se
- `u_krevetu`: da, ne
- `pouzdanost`: broj od 0.0 do 1.0

Pravila:

1. `vreme` prepiši sa slike, u obliku `2026-09-11T03:45:00`.
2. Ispiši red za **svaku** sliku. Nikada ne sažimaj, ne piši „isto kao gore",
   ne preskači i ne staj na pola.
3. Kada je kadar mračan ili nejasan, koristi `ne_vidi_se` i nisku pouzdanost.
   Nagađanje sa visokom pouzdanošću je gore od priznatog `ne_vidi_se`.
4. `levi_bok` znači da osoba leži na svom levom boku, ne na levoj strani kadra.
5. Slike su uzastopne. Kada se u odnosu na prethodnu ništa nije promenilo,
   ponovi isti položaj; ne traži razliku koje nema.
6. Nemoj ništa da objašnjavaš pre ili posle tabele, osim tražene brojke.
