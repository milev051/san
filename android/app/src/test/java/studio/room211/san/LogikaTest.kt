package studio.room211.san

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

class LogikaTest {

    private val zona = ZoneId.of("Europe/Belgrade")

    private fun ms(tekst: String): Long =
        LocalDateTime.parse(tekst).atZone(zona).toInstant().toEpochMilli()

    private fun sat(vreme: Long): String =
        java.time.Instant.ofEpochMilli(vreme).atZone(zona).toLocalDateTime().toString()

    // ------------------------------------------------------------- okidanje

    @Test
    fun `okida se na pun minut, ne pet minuta od starta`() {
        assertEquals("2026-09-10T23:05", sat(Raspored.sledece(ms("2026-09-10T23:02:37"), 5, zona)))
        assertEquals("2026-09-10T23:10", sat(Raspored.sledece(ms("2026-09-10T23:07:01"), 5, zona)))
        assertEquals("2026-09-10T23:03", sat(Raspored.sledece(ms("2026-09-10T23:02:37"), 3, zona)))
        assertEquals("2026-09-10T23:03", sat(Raspored.sledece(ms("2026-09-10T23:02:37"), 1, zona)))
    }

    @Test
    fun `tacno na mrezi vraca sledecu tacku, ne istu`() {
        // Alarm ume da stigne milisekundu ranije ili tacno na vreme. Da se ovde
        // vrati isti trenutak, isti kadar bi se okinuo dvaput.
        assertEquals("2026-09-10T23:10", sat(Raspored.sledece(ms("2026-09-10T23:05:00"), 5, zona)))
    }

    @Test
    fun `razmaci koji dele sat prelaze ponoc bez preskoka`() {
        for (razmak in listOf(1, 2, 3, 5, 10, 15, 30, 60)) {
            val pre = ms("2026-09-10T23:59:30")
            val prvo = Raspored.sledece(pre, razmak, zona)
            val drugo = Raspored.sledece(prvo, razmak, zona)
            assertEquals(
                "razmak $razmak preskace ponoc",
                razmak * 60_000L, drugo - prvo
            )
        }
    }

    @Test
    fun `sledece okidanje je uvek u buducnosti`() {
        var t = ms("2026-09-10T22:00:00")
        repeat(200) {
            val sledece = Raspored.sledece(t, 5, zona)
            assertTrue("okidanje u proslosti na ${sat(t)}", sledece > t)
            t = sledece
        }
    }

    // ------------------------------------------------------------------ noc

    @Test
    fun `noc nosi datum veceri, ne kalendarski datum kadra`() {
        assertEquals(LocalDate.parse("2026-09-10"), Raspored.nocDatum(ms("2026-09-10T23:40:00"), zona))
        assertEquals(LocalDate.parse("2026-09-10"), Raspored.nocDatum(ms("2026-09-11T03:45:00"), zona))
        assertEquals(LocalDate.parse("2026-09-10"), Raspored.nocDatum(ms("2026-09-11T07:25:00"), zona))
        // Legao posle podne, to je vec sledeca noc.
        assertEquals(LocalDate.parse("2026-09-11"), Raspored.nocDatum(ms("2026-09-11T13:00:00"), zona))
    }

    @Test
    fun `putanja noci ima godinu, mesec i pun datum`() {
        assertEquals("2026/09/2026-09-10", Raspored.putanjaNoci(LocalDate.parse("2026-09-10")))
        assertEquals("2026/01/2026-01-05", Raspored.putanjaNoci(LocalDate.parse("2026-01-05")))
    }

    @Test
    fun `ime fajla nosi pun datum i vreme kadra`() {
        assertEquals("2026-09-11_03-45-00.jpg", Raspored.imeFajla(ms("2026-09-11T03:45:00"), zona))
    }

    @Test
    fun `zig nosi stvarni datum kadra, ne datum noci`() {
        // Slika u 03:45 stoji u folderu 2026-09-10, ali nosi 2026-09-11.
        assertEquals("2026-09-11  03:45  #042", Raspored.zigTekst(ms("2026-09-11T03:45:00"), 42, zona))
        assertEquals("2026-09-10  23:00  #001", Raspored.zigTekst(ms("2026-09-10T23:00:00"), 1, zona))
    }

    @Test
    fun `broj slika po noci`() {
        assertEquals(96, Raspored.slikaZaNoc(5))
        assertEquals(480, Raspored.slikaZaNoc(1))
        assertEquals(160, Raspored.slikaZaNoc(3))
    }

    // -------------------------------------------------------------- nastavak

    private val osamSati = 8 * 3600_000L

    @Test
    fun `kratak prekid nastavlja istu noc`() {
        val poslednja = ms("2026-09-11T02:00:00")
        // Ustao do kupatila, zaustavio, vratio se posle deset minuta.
        assertTrue(Raspored.nastavakIste(ms("2026-09-11T02:10:00"), poslednja, osamSati))
        assertTrue(Raspored.nastavakIste(ms("2026-09-11T09:59:00"), poslednja, osamSati))
    }

    @Test
    fun `posle osam sati pocinje nova noc`() {
        val poslednja = ms("2026-09-11T02:00:00")
        assertTrue(!Raspored.nastavakIste(ms("2026-09-11T10:00:00"), poslednja, osamSati))
        assertTrue(!Raspored.nastavakIste(ms("2026-09-11T22:00:00"), poslednja, osamSati))
    }

    @Test
    fun `bez ijedne prethodne slike nema sta da se nastavi`() {
        assertTrue(!Raspored.nastavakIste(ms("2026-09-11T02:10:00"), 0, osamSati))
    }

    @Test
    fun `sat pomeren unazad ne pravi nastavak iz buducnosti`() {
        // Telefon uskladi sat i vrati ga minut unazad; bez ovog uslova bi
        // razlika bila negativna, dakle „manja od granice", pa bi se nastavila
        // noc koja se jos nije desila.
        assertTrue(!Raspored.nastavakIste(ms("2026-09-11T01:59:00"), ms("2026-09-11T02:00:00"), osamSati))
    }

    @Test
    fun `noc pokrenuta u deset uvece drzi isti folder do deset ujutru`() {
        val pocetak = ms("2026-09-10T22:00:00")
        val noc = Raspored.nocDatum(pocetak, zona)
        // Folder se odredjuje jednom, na pocetku, pa kadar u 10:00 narednog
        // dana i dalje pripada istoj noci.
        assertEquals(LocalDate.parse("2026-09-10"), noc)
        assertTrue(Raspored.nastavakIste(ms("2026-09-11T10:00:00"), ms("2026-09-11T09:55:00"), osamSati))
    }

    // ---------------------------------------------------------------- pokret

    @Test
    fun `iste slicice nemaju pokret, suprotne imaju pun`() {
        val crna = IntArray(16) { 0 }
        val bela = IntArray(16) { 255 }
        assertEquals(0.0, Pokret.razlika(crna, crna), 1e-9)
        assertEquals(1.0, Pokret.razlika(crna, bela), 1e-9)
    }

    @Test
    fun `razlika je srednja vrednost, ne najveca`() {
        val a = IntArray(4) { 0 }
        val b = intArrayOf(255, 0, 0, 0)
        assertEquals(0.25, Pokret.razlika(a, b), 1e-9)
    }

    @Test
    fun `promena svetline nije pokret`() {
        // Nocni rezim bira ekspoziciju po snimku, pa ista mirna soba ume da
        // bude svetlija na sledecem kadru. To ne sme da se broji kao pokret.
        val tamna = intArrayOf(40, 80, 120, 160)
        val svetla = intArrayOf(80, 120, 160, 200)
        assertEquals(0.0, Pokret.razlikaBezSjaja(tamna, svetla), 1e-9)
        // Sirova razlika bi tu prijavila pun pomeraj svakog piksela.
        assertEquals(40 / 255.0, Pokret.razlika(tamna, svetla), 1e-9)
    }

    @Test
    fun `stvaran pomeraj se i dalje vidi bez obzira na svetlinu`() {
        val pre = intArrayOf(0, 0, 255, 255)
        val posle = intArrayOf(255, 255, 0, 0)
        assertTrue(Pokret.razlikaBezSjaja(pre, posle) > 0.5)
        // Ista scena posvetljena za 30 daje isti nalaz.
        val posleSvetlije = IntArray(4) { (posle[it] + 30).coerceAtMost(255) }
        assertTrue(Pokret.razlikaBezSjaja(pre, posleSvetlije) > 0.4)
    }

    @Test
    fun `izbor uzima prvi kadar, promene i sidra`() {
        val razlike = listOf(0.0, 0.001, 0.002, 0.20, 0.001, 0.001)
        val izabrani = Pokret.izbor(razlike, prag = 0.045, sidroNa = 12)
        assertEquals(listOf(0, 3), izabrani)
    }

    @Test
    fun `mirna noc i dalje daje sidro na svakih dvanaest kadrova`() {
        val razlike = List(40) { 0.0 }
        assertEquals(listOf(0, 12, 24, 36), Pokret.izbor(razlike, sidroNa = 12))
    }
}
