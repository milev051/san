package studio.room211.san

import android.graphics.Bitmap
import java.io.ByteArrayOutputStream

/**
 * Duga ekspozicija sabiranjem vise kadrova.
 *
 * Postoji zato sto se na Samsung S23 pokazalo da kamera 0 preko Camera2 daje
 * najvise 1/6 sekunde, a nocnog rezima na njoj nema. Sve preko te granice
 * telefon tiho stegne na 1/6, pa svaka duza trazena vrednost daje isti mrak.
 *
 * Osam kadrova od 1/6 s sabranih zajedno je isto svetlo kao jedan od 1.3 s.
 * To je i ono sto nocni rezim radi u proizvodjacevom kodu, samo bez njihovog
 * poravnavanja kadrova.
 *
 * **Sabira se, ne uprosecava.** Prosek osam mracnih kadrova je i dalje mracan;
 * samo mu je sum manji. Sabiranje je ono sto sliku posvetli, i zato se
 * vrednosti moraju seci na 255 umesto da se dele.
 *
 * Sum pritom opada, jer je slucajan u svakom kadru, a slika ispod njega je u
 * svih osam ista.
 */
object Slaganje {

    /** Duza strana na kojoj se slaze. Sabirac za 12 MP ne bi stao u memoriju. */
    const val STRANA = 1600

    fun spoji(kadrovi: List<ByteArray>, kvalitet: Int): ByteArray? {
        if (kadrovi.isEmpty()) return null
        if (kadrovi.size == 1) return kadrovi[0]

        var sirina = 0
        var visina = 0
        var zbirR: IntArray? = null
        var zbirG: IntArray? = null
        var zbirB: IntArray? = null
        var spojenih = 0
        var ugao = 0

        for (kadar in kadrovi) {
            val slika = Slika.uspravno(kadar, STRANA) ?: continue
            if (zbirR == null) {
                sirina = slika.width
                visina = slika.height
                ugao = Slika.ugao(kadar)
                zbirR = IntArray(sirina * visina)
                zbirG = IntArray(sirina * visina)
                zbirB = IntArray(sirina * visina)
            } else if (slika.width != sirina || slika.height != visina) {
                // Kadar druge velicine se preskace umesto da se rasteze:
                // rastegnut bi uneo pomeraj u sliku koja treba da bude ista.
                slika.recycle()
                continue
            }

            val piksele = IntArray(sirina * visina)
            slika.getPixels(piksele, 0, sirina, 0, 0, sirina, visina)
            slika.recycle()
            for (i in piksele.indices) {
                val p = piksele[i]
                zbirR!![i] += (p shr 16) and 0xFF
                zbirG!![i] += (p shr 8) and 0xFF
                zbirB!![i] += p and 0xFF
            }
            spojenih++
        }

        if (zbirR == null || spojenih == 0) return null

        val izlaz = IntArray(sirina * visina)
        for (i in izlaz.indices) {
            val r = stegni(zbirR!![i])
            val g = stegni(zbirG!![i])
            val b = stegni(zbirB!![i])
            izlaz[i] = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
        }

        val spojena = Bitmap.createBitmap(izlaz, sirina, visina, Bitmap.Config.ARGB_8888)
        val bafer = ByteArrayOutputStream()
        spojena.compress(Bitmap.CompressFormat.JPEG, kvalitet, bafer)
        spojena.recycle()
        return bafer.toByteArray()
    }

    /** Odakle pocinje meko stezanje. Ispod toga je zbir netaknut. */
    private const val KOLENO = 200

    /**
     * Zbir u opseg 0 do 255, mekano.
     *
     * Tvrdo secenje na 255 spljosti sve sto je svetlo u ravnu belu mrlju: gde
     * god u sobi ima lampica ili prozora, tu se izgubi i oblik. Zato je do
     * kolena netaknuto, a iznad njega se glatko primice belom i nikad je ne
     * dostigne naglo.
     *
     * Tamni deo slike, a to je ovde skoro cela slika, ostaje savrseno linearan,
     * jer se tek tamo vidi razlika koju je slaganje i donelo.
     */
    fun stegni(zbir: Int): Int {
        if (zbir <= KOLENO) return if (zbir < 0) 0 else zbir
        val preko = (zbir - KOLENO).toDouble()
        val ostatak = 255.0 - KOLENO
        // Zaokruzuje se, ne seče: odsecanje bi i potpuno prezaren piksel
        // ostavilo na 254, pa bela nikad ne bi bila bela.
        return kotlin.math.round(255.0 - ostatak * kotlin.math.exp(-preko / ostatak))
            .toInt().coerceIn(0, 255)
    }

    /** Koliko puta je slika svetlija nego jedan kadar, priblizno. */
    fun dobitak(kadrova: Int): String = "oko ${kadrova}x svetlije"
}
