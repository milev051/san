package studio.room211.san

import android.content.Context

/** Podesavanja i stanje noci; sve stane u `SharedPreferences`, kao u Diktatu i Mostu. */
class Config(context: Context) {

    private val prefs = context.getSharedPreferences("san", Context.MODE_PRIVATE)

    companion object {
        /** Razmaci koji se nude dugmadima. Svi dele 60, pa im mreza prelazi ponoc bez preskoka. */
        val RAZMACI = listOf(1, 2, 3, 5, 10, 15, 30, 60)

        /** Ispod ovoga se snimanje prekida. Tiho nepisanje slika je gore od prekida. */
        const val NAJMANJE_MESTA = 1_000L * 1024 * 1024

        /** Duza strana kopije koja ide modelu. Za razliku ledja i boka vise ne treba. */
        const val ANALIZA_STRANA = 768
    }

    // ------------------------------------------------------------ podesavanja

    /** Razmak izmedju dve slike, u minutima. */
    var razmakMin: Int
        get() = prefs.getInt("razmak", 5)
        set(v) = prefs.edit().putInt("razmak", v.coerceIn(1, 180)).apply()

    /** Zadnja kamera je podrazumevana: veci senzor, svetliji objektiv, a ekran gleda od kreveta. */
    var prednjaKamera: Boolean
        get() = prefs.getBoolean("prednja", false)
        set(v) = prefs.edit().putBoolean("prednja", v).apply()

    /** `puna`, `8mp` ili `2mp`. */
    var rezolucija: String
        get() = prefs.getString("rezolucija", "puna")!!
        set(v) = prefs.edit().putString("rezolucija", v).apply()

    var kvalitet: Int
        get() = prefs.getInt("kvalitet", 95)
        set(v) = prefs.edit().putInt("kvalitet", v.coerceIn(60, 100)).apply()

    /**
     * Rucna ekspozicija.
     *
     * Automatika u mraku luta: jedna slika crna, sledeca prosvetljena. Kad se
     * vrednosti zakljucaju, sve slike jedne noci postaju uporedive, sto je
     * uslov da merenje pokreta uopste ima smisla.
     */
    var rucnaEkspozicija: Boolean
        get() = prefs.getBoolean("rucna_eksp", false)
        set(v) = prefs.edit().putBoolean("rucna_eksp", v).apply()

    /** Vreme ekspozicije u nanosekundama. Granicu odredjuje telefon, ne mi. */
    var ekspozicijaNs: Long
        get() = prefs.getLong("eksp_ns", 125_000_000L)   // 1/8 s
        set(v) = prefs.edit().putLong("eksp_ns", v).apply()

    var iso: Int
        get() = prefs.getInt("iso", 1600)
        set(v) = prefs.edit().putInt("iso", v).apply()

    /**
     * Rastojanje do kreveta u metrima; iz njega se racuna fiksni fokus.
     *
     * U mraku autofokus lovi i najcesce ne nadje nista, pa slika ispadne mutna
     * bez ikakve greske u dnevniku. Telefon stoji na istom mestu cele noci, pa
     * fokus nema sta da trazi.
     */
    var rastojanjeM: Float
        get() = prefs.getFloat("rastojanje", 2.0f)
        set(v) = prefs.edit().putFloat("rastojanje", v.coerceIn(0.2f, 20f)).apply()

    var zigNaArhivi: Boolean
        get() = prefs.getBoolean("zig_arhiva", true)
        set(v) = prefs.edit().putBoolean("zig_arhiva", v).apply()

    // ------------------------------------------------------------------ zvuk

    /** Javi se na prve dve slike pa cuti do jutra. */
    var zvuk: Boolean
        get() = prefs.getBoolean("zvuk", true)
        set(v) = prefs.edit().putBoolean("zvuk", v).apply()

    var jacina: Int
        get() = prefs.getInt("jacina", 40)
        set(v) = prefs.edit().putInt("jacina", v.coerceIn(0, 100)).apply()

    var vibracija: Boolean
        get() = prefs.getBoolean("vibracija", false)
        set(v) = prefs.edit().putBoolean("vibracija", v).apply()

    /** Samo za probu: javi se na svakoj slici. Sam se gasi posle deset minuta. */
    var zvukSvaka: Boolean
        get() = prefs.getBoolean("zvuk_svaka", false)
        set(v) = prefs.edit().putBoolean("zvuk_svaka", v).apply()

    /** Sat u koji se snimanje samo prekida; -1 znaci nikad. */
    var autoStopSat: Int
        get() = prefs.getInt("auto_stop", -1)
        set(v) = prefs.edit().putInt("auto_stop", v).apply()

    // ----------------------------------------------------------- stanje noci

    var aktivno: Boolean
        get() = prefs.getBoolean("aktivno", false)
        set(v) = prefs.edit().putBoolean("aktivno", v).apply()

    var pocetak: Long
        get() = prefs.getLong("pocetak", 0)
        set(v) = prefs.edit().putLong("pocetak", v).apply()

    var brojSlika: Int
        get() = prefs.getInt("broj", 0)
        set(v) = prefs.edit().putInt("broj", v).apply()

    var sledece: Long
        get() = prefs.getLong("sledece", 0)
        set(v) = prefs.edit().putLong("sledece", v).apply()

    /** Prethodna slicica 64x64, da se izmeri pokret i posle gasenja procesa. */
    var prethodnaSlicica: IntArray?
        get() = prefs.getString("slicica", "")!!
            .takeIf { it.isNotBlank() }
            ?.split(",")?.map { it.toInt() }?.toIntArray()
        set(v) = prefs.edit()
            .putString("slicica", v?.joinToString(",") ?: "")
            .apply()

    /** Zasto je poslednje snimanje stalo. Vidi se ujutru, kad je vec kasno da se pita. */
    var razlogPrekida: String
        get() = prefs.getString("razlog_prekida", "")!!
        set(v) = prefs.edit().putString("razlog_prekida", v).apply()

    var poslednjaGreska: String
        get() = prefs.getString("greska", "")!!
        set(v) = prefs.edit().putString("greska", v).apply()
}
