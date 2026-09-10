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

        /**
         * Prekid snimanja se nastavlja u ISTU noc ako je kraci od ovoga.
         *
         * Ustao si do kupatila, zaustavio snimanje i vratio se: to je i dalje
         * ista noc, isti folder i isto brojanje. Osam sati je granica jer je to
         * vec sledeci dan i sledeci san, a ne pauza.
         */
        const val NASTAVAK_MS = 8 * 3600_000L
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
     * Kako se snima: `auto`, `noc` ili `rucno`.
     *
     * Tri iskljuciva puta, ne tri prekidaca koja se sabiraju:
     *
     *  `auto`   automatika telefona, uz zakljucavanje posle prve slike
     *  `noc`    nocni rezim samog telefona preko Camera2 Extensions; telefon
     *           spaja vise kadrova i sam bira ekspoziciju, pa rucnih vrednosti
     *           tu nema i ne mogu se ni traziti
     *  `rucno`  upisano vreme ekspozicije i ISO
     */
    var rezim: String
        get() = prefs.getString("rezim", if (prefs.getBoolean("rucna_eksp", false)) "rucno" else "auto")!!
        set(v) = prefs.edit().putString("rezim", v).apply()

    val rucnaEkspozicija: Boolean
        get() = rezim == "rucno"

    val nocniRezim: Boolean
        get() = rezim == "noc"

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

    /**
     * Zakljucaj automatiku posle prve slike.
     *
     * Prva slika se snimi automatikom, iz nje se procita koliku je ekspoziciju
     * i ISO telefon stvarno upotrebio, i ostatak noci se snima tim vrednostima.
     * Time se dobija ono zbog cega postoji rucni rezim, bez pogadjanja brojeva:
     * sve slike jedne noci su uporedive, sto je uslov da merenje pokreta ima
     * smisla.
     */
    var zakljucajPosleParve: Boolean
        get() = prefs.getBoolean("zakljucaj", true)
        set(v) = prefs.edit().putBoolean("zakljucaj", v).apply()

    /** Vrednosti procitane iz prve slike; 0 znaci da jos nisu zakljucane. */
    var zakljucanaEkspozicijaNs: Long
        get() = prefs.getLong("zakljucana_eksp", 0)
        set(v) = prefs.edit().putLong("zakljucana_eksp", v).apply()

    var zakljucaniIso: Int
        get() = prefs.getInt("zakljucani_iso", 0)
        set(v) = prefs.edit().putInt("zakljucani_iso", v).apply()

    /** Ispod ovoga se snimanje prekida, da se telefon ne ugasi usred noci. */
    var pragBaterije: Int
        get() = prefs.getInt("prag_baterije", 5)
        set(v) = prefs.edit().putInt("prag_baterije", v.coerceIn(0, 50)).apply()

    var zigNaArhivi: Boolean
        get() = prefs.getBoolean("zig_arhiva", true)
        set(v) = prefs.edit().putBoolean("zig_arhiva", v).apply()

    // ------------------------------------------------------------------ zvuk

    /**
     * Koliko sekundi posle Start-a ide prva slika.
     *
     * Dugme se pritisne dok stojis pored telefona, a prva slika treba da uhvati
     * krevet u kome vec lezis. Zvuk pri pritisku kaze da je pokrenuto, pa imas
     * toliko vremena da legnes.
     */
    var odlozenoSek: Int
        get() = prefs.getInt("odlozeno", 10)
        set(v) = prefs.edit().putInt("odlozeno", v.coerceIn(0, 300)).apply()

    /** Do koje slike se cuje potvrda. Posle toga tisina do jutra. */
    var zvukDoSlike: Int
        get() = prefs.getInt("zvuk_do", 3)
        set(v) = prefs.edit().putInt("zvuk_do", v.coerceIn(0, 20)).apply()

    /** Javi se na prve tri slike pa cuti do jutra. */
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

    /** Noc u koju se upisuje, `2026-09-10`. Ostaje ista kroz kratke prekide. */
    var tekucaNoc: String
        get() = prefs.getString("tekuca_noc", "")!!
        set(v) = prefs.edit().putString("tekuca_noc", v).apply()

    /** Vreme poslednje uspesne slike; iz njega se odlucuje da li je prekid nastavak. */
    var poslednjaSlika: Long
        get() = prefs.getLong("poslednja_slika", 0)
        set(v) = prefs.edit().putLong("poslednja_slika", v).apply()

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
