package studio.room211.san

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Racunanje vremena: kad je sledece okidanje, kojoj noci slika pripada, kako se
 * zove fajl i sta pise na samoj slici.
 *
 * Sve je cista funkcija nad brojevima i datumima, bez ijednog Android poziva,
 * pa se testira na JVM-u. To nije slucajno: sve tri greske koje se ovde mogu
 * napraviti (okidanje koje odlutа sa punog minuta, noc iseckana u ponoc,
 * upisan datum koji ne postoji) tise su od pada aplikacije i vide se tek posle
 * protracene noci.
 */
object Raspored {

    /**
     * Prvo okidanje posle `sada`, poravnato prema ponoci.
     *
     * Zahtev je da se slika u 23:00:00, 23:05:00, 23:10:00, a ne pet minuta od
     * trenutka kad je pritisnut Start. Zato se ne racuna `sada + razmak` nego
     * sledeca tacka na mrezi koja pocinje u ponoc.
     *
     * Kad `sada` padne tacno na mrezu, vraca se SLEDECA tacka, ne ista. Inace
     * bi se isti trenutak okinuo dvaput, jer alarm ume da stigne milisekundu
     * ranije pa da se racunanje ponovi.
     *
     * Razmaci koji dele 60 (1, 2, 3, 5, 10, 15, 30, 60) imaju tacku i u ponoc,
     * pa im mreza prelazi ponoc bez preskoka. Slobodno unet razmak koji ne deli
     * 60 se u ponoc resetuje, i tada je jedan razmak kraci.
     */
    fun sledece(sada: Long, razmakMin: Int, zona: ZoneId = ZoneId.systemDefault()): Long {
        require(razmakMin > 0) { "razmak mora biti veci od nule" }
        val razmak = razmakMin * 60_000L
        val ponoc = ponocPre(sada, zona)
        val proteklo = sada - ponoc
        return ponoc + (proteklo / razmak + 1) * razmak
    }

    fun ponocPre(sada: Long, zona: ZoneId = ZoneId.systemDefault()): Long =
        Instant.ofEpochMilli(sada).atZone(zona).toLocalDate().atStartOfDay(zona)
            .toInstant().toEpochMilli()

    /**
     * Datum VECERI kojoj noc pripada, ne kalendarski datum trenutka.
     *
     * Snimanje pokrenuto u 01:30 pripada sinocnjoj noci, jer bi inace jedna ista
     * noc zavrsila u dva foldera, a covek koji je legao u ponoc i deset ne bi
     * imao gde da je trazi. Granica je podne: sve pre 12:00 pripada prethodnom
     * danu.
     */
    fun nocDatum(pocetak: Long, zona: ZoneId = ZoneId.systemDefault()): LocalDate {
        val vreme = Instant.ofEpochMilli(pocetak).atZone(zona)
        return if (vreme.hour < 12) vreme.toLocalDate().minusDays(1) else vreme.toLocalDate()
    }

    /** `2026/09/2026-09-10`, godina i mesec zbog snalazenja, pun datum zbog izvucenog foldera. */
    fun putanjaNoci(datum: LocalDate): String =
        "%04d/%02d/%s".format(datum.year, datum.monthValue, datum.toString())

    private val IME = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss")
    private val ZIG = DateTimeFormatter.ofPattern("yyyy-MM-dd  HH:mm")

    fun imeFajla(vreme: Long, zona: ZoneId = ZoneId.systemDefault()): String =
        Instant.ofEpochMilli(vreme).atZone(zona).format(IME) + ".jpg"

    /**
     * Tekst koji se upisuje na sliku: `2026-09-11  03:45  #042`.
     *
     * Bez natpisa „datum" i „vreme": zapis je nedvosmislen na svakom jeziku, a
     * svaki znak vise je traka koja pokriva vise kadra. Pise STVARNI datum
     * kadra, ne datum noci, jer bi inace slika u 03:45 nosila vreme koje ne
     * postoji.
     */
    fun zigTekst(vreme: Long, redni: Int, zona: ZoneId = ZoneId.systemDefault()): String =
        "%s  #%03d".format(Instant.ofEpochMilli(vreme).atZone(zona).format(ZIG), redni)

    /**
     * Da li novo pokretanje nastavlja istu noc.
     *
     * Ustao si do kupatila, zaustavio snimanje i vratio se: to je i dalje ista
     * noc, isti folder, isto brojanje i ista zakljucana ekspozicija. Posle
     * granice je to vec sledeci san, pa ide nov folder.
     *
     * Racuna se od POSLEDNJE SLIKE, ne od trenutka zaustavljanja: samo ona
     * kaze dokle je noc stvarno snimana.
     */
    fun nastavakIste(sada: Long, poslednjaSlika: Long, granicaMs: Long): Boolean =
        poslednjaSlika > 0 && sada >= poslednjaSlika && sada - poslednjaSlika < granicaMs

    /** Koliko slika stane u noc od `sati` na zadatom razmaku; za upozorenje u podesavanjima. */
    fun slikaZaNoc(razmakMin: Int, sati: Int = 8): Int = sati * 60 / razmakMin
}
