package studio.room211.san

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.time.Instant
import java.time.ZoneId

/**
 * Zapisnik jedne noci.
 *
 * Merodavan primerak stoji u privatnom folderu aplikacije, jer se dopunjuje
 * posle svake slike i mora da prezivi gasenje procesa. Kopija u `Documents`
 * se pise redje i sluzi za prenos na racunar.
 *
 * U njemu je i izmereni pokret po kadru, iz koga posle izlaze grafikon noci i
 * izbor kadrova koji se salju modelu.
 */
class Noc(private val ctx: Context, val datum: String) {

    private val fajl = File(File(ctx.filesDir, "noci").apply { mkdirs() }, "$datum.json")

    fun dodaj(
        vreme: Long,
        ime: String,
        pokret: Double,
        razmakMin: Int,
        greska: String = "",
        uri: String = "",
        ekspozicijaNs: Long = 0,
        iso: Int = 0,
        nocni: Boolean = false,
        slozeno: Int = 0,
    ) {
        val koren = ucitaj()
        koren.put("noc", datum)
        koren.put("razmak_min", razmakMin)
        koren.put("prag_pokreta", Pokret.PRAG)
        if (!koren.has("pocetak")) koren.put("pocetak", vremeTekst(vreme))
        koren.put("kraj", vremeTekst(vreme))

        val redovi = koren.optJSONArray("slike") ?: JSONArray()
        redovi.put(JSONObject().apply {
            put("vreme", vremeTekst(vreme))
            put("fajl", ime)
            put("pokret", String.format(java.util.Locale.US, "%.4f", pokret).toDouble())
            put("promena", pokret >= Pokret.PRAG)
            if (uri.isNotBlank()) put("uri", uri)
            if (ekspozicijaNs > 0) put("ekspozicija_ns", ekspozicijaNs)
            if (iso > 0) put("iso", iso)
            if (nocni) put("nocni_rezim", true)
            if (slozeno > 0) put("slozeno_kadrova", slozeno)
            if (greska.isNotBlank()) put("greska", greska)
        })
        koren.put("slike", redovi)
        koren.put("ukupno", redovi.length())
        fajl.writeText(koren.toString(2))
    }

    /** Rupa u noci: sistem oduzeo kameru, nema mesta, snimak nije uspeo. */
    fun rupa(vreme: Long, razlog: String, razmakMin: Int) =
        dodaj(vreme, "", 0.0, razmakMin, razlog)

    fun ucitaj(): JSONObject =
        if (fajl.exists()) JSONObject(fajl.readText()) else JSONObject()

    fun tekst(): String = if (fajl.exists()) fajl.readText() else "{}"

    fun postoji(): Boolean = fajl.exists()

    private fun vremeTekst(vreme: Long): String =
        Instant.ofEpochMilli(vreme).atZone(ZoneId.systemDefault())
            .toLocalDateTime().withNano(0).toString()

    companion object {
        fun spisak(ctx: Context): List<String> =
            File(ctx.filesDir, "noci").listFiles()
                ?.filter { it.name.endsWith(".json") }
                ?.map { it.name.removeSuffix(".json") }
                ?.sortedDescending()
                ?: emptyList()
    }
}
