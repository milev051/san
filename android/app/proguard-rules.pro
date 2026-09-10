# Komponente koje sistem pravi po imenu iz manifesta ne smeju da se preimenuju.
# Ista greska je vec napravljena u Mostu: R8 preimenuje servis, sistem ga ne
# nadje, i nista se ne desi, bez ijedne greske u dnevniku.
-keep class studio.room211.san.MainActivity { *; }
-keep class studio.room211.san.Podesavanja { *; }
-keep class studio.room211.san.Noci { *; }
-keep class studio.room211.san.Snimanje { *; }
-keep class studio.room211.san.Budilnik { *; }
