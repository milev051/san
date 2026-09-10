package studio.room211.san

import kotlin.math.abs

/**
 * Merenje koliko se promenilo izmedju dva kadra.
 *
 * Nije prepoznavanje polozaja nego jedan broj po slici, iz koga posle izlazi i
 * grafikon noci i izbor kadrova koji se salju modelu. Racuna se nad sivom
 * slicicom 64x64, sto traje par milisekundi i ne trosi nista.
 */
object Pokret {

    const val STRANA = 64

    /** Podrazumevani prag; iznad njega se kadar smatra promenom polozaja. */
    const val PRAG = 0.045

    /**
     * Srednja apsolutna razlika dve slicice, 0.0 do 1.0.
     *
     * Merenje se radi PRE nego sto se na sliku upise vreme. Traka sa vremenom
     * se menja na svakom kadru, pa bi inace svaka slika izgledala kao promena i
     * ceo grafikon bi bio smece.
     */
    fun razlika(a: IntArray, b: IntArray): Double {
        require(a.size == b.size) { "slicice moraju biti iste velicine" }
        if (a.isEmpty()) return 0.0
        var zbir = 0L
        for (i in a.indices) zbir += abs(a[i] - b[i])
        return zbir.toDouble() / (a.size * 255.0)
    }

    /**
     * Koji kadrovi idu modelu: svaki preko praga, plus po jedan na `sidroNa`
     * kadrova da se i mirni delovi noci vide.
     *
     * Prvi kadar uvek ulazi, jer je pocetni polozaj podatak koji se ne moze
     * izvesti ni iz cega.
     */
    fun izbor(razlike: List<Double>, prag: Double = PRAG, sidroNa: Int = 12): List<Int> {
        val izabrani = ArrayList<Int>()
        for (i in razlike.indices) {
            if (i == 0 || razlike[i] >= prag || i % sidroNa == 0) izabrani.add(i)
        }
        return izabrani
    }
}
