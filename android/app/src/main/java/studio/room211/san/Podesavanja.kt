package studio.room211.san

import android.Manifest
import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.BatteryManager
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.material.color.DynamicColors
import studio.room211.san.Ui.body
import studio.room211.san.Ui.button
import studio.room211.san.Ui.card
import studio.room211.san.Ui.choice
import studio.room211.san.Ui.dp
import studio.room211.san.Ui.field
import studio.room211.san.Ui.switch
import java.time.Instant
import java.time.ZoneId
import java.util.concurrent.Executors

/**
 * Podesavanja, noćna proba i provera.
 *
 * Odvojeno od prvog ekrana namerno. Telefon visi iznad kreveta, okrenut od
 * tebe, i uveče se traži jedno jedino dugme; sve ostalo se podešava jednom, u
 * ruci, i posle se ne dira.
 *
 * Zive slike sa kamere nema. U mračnoj sobi ne pokazuje ništa korisno, a
 * probna slika sa zaključanom ekspozicijom pokazuje tačno ono što će noć dati.
 */
class Podesavanja : AppCompatActivity() {

    private lateinit var cfg: Config
    private lateinit var provera: TextView
    private lateinit var probaOpis: TextView
    private lateinit var probaSlika: ImageView
    private lateinit var brojSlikaOpis: TextView
    private lateinit var rezimOpis: TextView
    private lateinit var kameraOpis: TextView
    private var ekspozicijaPolje: com.google.android.material.textfield.TextInputEditText? = null
    private var isoPolje: com.google.android.material.textfield.TextInputEditText? = null
    private val posao = Executors.newSingleThreadExecutor()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        DynamicColors.applyToActivityIfAvailable(this)
        cfg = Config(this)
        trraziDozvole()

        val koren = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val p = dp(14)
            setPadding(p, dp(18), p, dp(28))
        }
        koren.addView(naslov())
        koren.addView(karticaProba())
        koren.addView(karticaKamera())
        koren.addView(karticaRazmak())
        koren.addView(karticaSlika())
        koren.addView(karticaZvuk())
        koren.addView(karticaProvera())

        setContentView(ScrollView(this).apply {
            addView(koren, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        })
    }

    override fun onResume() {
        super.onResume()
        provera.text = tekstProvere()
        brojSlikaOpis.text = opisRazmaka()
        osveziRezim()
    }

    private fun naslov() = TextView(this).apply {
        text = "Podešavanja  ·  San v${packageManager.getPackageInfo(packageName, 0).versionName}"
        setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_HeadlineSmall)
        setPadding(dp(6), 0, 0, dp(4))
    }

    // ------------------------------------------------------------ noćna proba

    private fun karticaProba(): ViewGroup {
        val (kartica, sadrzaj) = card(this, "Noćna proba")
        sadrzaj.addView(body(this, "Snimi jednu sliku sa trenutnim podešavanjima i pogledaj je " +
                "odmah. Ovo se radi uveče, u mraku u kom će se i snimati, jer granicu " +
                "ekspozicije ne određujemo mi nego telefon."))
        probaSlika = ImageView(this).apply {
            adjustViewBounds = true
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(8); bottomMargin = dp(8) }
        }
        probaOpis = body(this, "")
        sadrzaj.addView(button(this, "Snimi probnu sliku") { probaj() })
        sadrzaj.addView(probaSlika)
        sadrzaj.addView(probaOpis)
        return kartica
    }

    private fun probaj() {
        if (!imaKameru()) { trraziDozvole(); return }
        if (cfg.aktivno) {
            probaOpis.text = "Snimanje je u toku; zaustavi ga pa probaj."
            return
        }
        probaOpis.text = "Snimam…"
        posao.execute {
            val kamera = Kamera(this, cfg)
            val sposobnosti = kamera.sposobnosti()
            val pocelo = System.currentTimeMillis()
            val ishod = kamera.snimi()
            val trajalo = System.currentTimeMillis() - pocelo
            runOnUiThread {
                val opis = StringBuilder()
                opis.append(sposobnosti?.opis() ?: "Kamera nije dostupna")
                val prosirenja = kamera.nocniRezimi()
                opis.append("\nRežimi telefona: " +
                        if (prosirenja.isEmpty()) "nijedan" else prosirenja.joinToString(", "))
                opis.append("\nSnimak je trajao ${trajalo / 1000.0}s")
                if (ishod.nocni) opis.append("\nSnimljeno NOĆNIM režimom telefona")
                if (ishod.slozeno > 0) {
                    opis.append("\nSloženo ${ishod.slozeno} kadrova u jednu sliku")
                }
                if (ishod.bajtovi != null) {
                    // Uspravno, isto kao što će biti sačuvano. `BitmapFactory`
                    // ne gleda EXIF, pa je slika ovde ranije stajala bočno.
                    probaSlika.setImageBitmap(Slika.uspravno(ishod.bajtovi, 1600))
                    opis.append("\nVeličina: ${ishod.bajtovi.size / 1024} KB")
                    opis.append("\n\n").append(uporediTrazeno(ishod))
                } else {
                    probaSlika.setImageDrawable(null)
                    opis.append("\nSnimak nije uspeo: ${ishod.greska}")
                }
                probaOpis.text = opis.toString()
            }
        }
    }

    /**
     * Šta je traženo, a šta je senzor stvarno upotrebio.
     *
     * Ovo je jedini način da se vidi da telefon odbija traženu ekspoziciju.
     * Granica se razlikuje od modela do modela, pa se svaka vrednost iznad nje
     * tiho stegne na istu, i izgleda kao da podešavanje ne radi.
     */
    private fun uporediTrazeno(ishod: Kamera.Rezultat): String {
        val redovi = ArrayList<String>()
        if (ishod.slozeno > 0) {
            redovi.add("Po kadru: ${Kamera.Sposobnosti.vremeTekst(cfg.ekspozicijaNs)}, ISO ${cfg.iso}")
            redovi.add("Ukupno: ${Kamera.Sposobnosti.vremeTekst(ishod.ekspozicijaNs)} " +
                    "kroz ${ishod.slozeno} kadrova")
            return redovi.joinToString("\n")
        }
        val trazeno = when {
            cfg.rucnaEkspozicija -> cfg.ekspozicijaNs
            cfg.zakljucanaEkspozicijaNs > 0 -> cfg.zakljucanaEkspozicijaNs
            else -> 0L
        }
        if (ishod.ekspozicijaNs > 0) {
            redovi.add("Dobijeno: ${Kamera.Sposobnosti.vremeTekst(ishod.ekspozicijaNs)}, ISO ${ishod.iso}")
        }
        if (trazeno > 0) {
            redovi.add("Traženo: ${Kamera.Sposobnosti.vremeTekst(trazeno)}, ISO ${cfg.iso}")
            if (!ishod.rucnoPrimenjeno) {
                redovi.add("UPOZORENJE: telefon je snimao automatikom, ručne vrednosti nisu primenjene.")
            } else if (ishod.ekspozicijaNs > 0 &&
                kotlin.math.abs(ishod.ekspozicijaNs - trazeno) > trazeno / 10
            ) {
                redovi.add("UPOZORENJE: telefon je traženu ekspoziciju stegao na svoju granicu. " +
                        "Pritisni „Postavi na granicu telefona\", pa ako je i tada mračno, " +
                        "izaberi režim Slaganje ili drugu kameru.")
            }
        } else {
            redovi.add("Režim: automatika")
        }
        return redovi.joinToString("\n")
    }

    // ---------------------------------------------------------------- kamera

    /**
     * Izbor tačne kamere, ne samo prednja ili zadnja.
     *
     * Telefon ima više kamera i one se razlikuju baš u onome što je ovde jedino
     * važno: dokle ide ekspozicija i da li nude noćni režim. Na S23 kamera 0
     * daje najviše 1/6 s i nema nijedan noćni režim, pa se druge moraju
     * isprobati.
     */
    private fun karticaKamera(): ViewGroup {
        val (kartica, sadrzaj) = card(this, "Kamera")
        val sve = try { Kamera(this, cfg).sveKamere() } catch (e: Exception) { emptyList() }

        if (sve.isEmpty()) {
            sadrzaj.addView(body(this, "Kamere se ne mogu pročitati."))
            kameraOpis = body(this, "")
            sadrzaj.addView(kameraOpis)
            return kartica
        }

        sadrzaj.addView(choice(
            this,
            listOf("" to "auto") + sve.map { it.id to "${it.id} ${it.strana.take(4)}" },
            cfg.kameraId,
        ) { izbor ->
            cfg.kameraId = izbor
            kameraOpis.text = opisKamera(sve)
            osveziRezim()
        })

        kameraOpis = body(this, opisKamera(sve))
        sadrzaj.addView(kameraOpis)

        // Dva puta do istog proizvodjacevog koda daju razlicite odgovore, pa
        // se drugi mora proveriti posebno. Traje par sekundi, zato dugme.
        sadrzaj.addView(button(this, "Proveri noćni režim preko CameraX") {
            kameraOpis.text = "Proveravam…"
            posao.execute {
                val id = cfg.kameraId.ifBlank { Kamera(this, cfg).idKamere() }
                val ima = try { NocniX.dostupan(this, id) } catch (e: Exception) { false }
                runOnUiThread {
                    kameraOpis.text = opisKamera(sve) + "\n\nCameraX na kameri ${id ?: "?"}: " +
                            if (ima) "NUDI noćni režim. Izaberi režim Noćni."
                            else "ne nudi noćni režim. Ostaje Slaganje."
                }
            }
        })
        return kartica
    }

    private fun opisKamera(sve: List<Kamera.Sposobnosti>): String {
        val izabrana = cfg.kameraId
        return sve.joinToString("\n\n") { k ->
            val oznaka = if (k.id == izabrana) "► " else "  "
            val nocni = if (k.rezimi.contains("noćni")) "NOĆNI REŽIM" else "bez noćnog"
            val eksp = k.ekspozicija
            val raspon = if (eksp == null) "?" else
                "do ${Kamera.Sposobnosti.vremeTekst(eksp.upper)}"
            val najveca = k.velicine.firstOrNull()
            "$oznaka${k.id} ${k.strana}, ${k.nivo}\n" +
                    "   ekspozicija $raspon, ISO do ${k.iso?.upper ?: "?"}\n" +
                    "   ${najveca?.width ?: 0}x${najveca?.height ?: 0}, $nocni"
        } + "\n\nTraži kameru koja ima noćni režim ili dužu ekspoziciju. Ako je nijedna nema, " +
                "koristi Slaganje."
    }

    // ---------------------------------------------------------------- razmak

    private fun karticaRazmak(): ViewGroup {
        val (kartica, sadrzaj) = card(this, "Razmak")
        sadrzaj.addView(body(this, "Slika se na pun minut po satu, a ne od trenutka pokretanja: " +
                "na pet minuta to je 23:00, 23:05, 23:10."))
        sadrzaj.addView(choice(
            this,
            Config.RAZMACI.map { it.toString() to "$it min" },
            cfg.razmakMin.toString(),
        ) { izbor ->
            cfg.razmakMin = izbor.toInt()
            brojSlikaOpis.text = opisRazmaka()
        })
        brojSlikaOpis = body(this, opisRazmaka())
        sadrzaj.addView(brojSlikaOpis)
        return kartica
    }

    private fun opisRazmaka(): String {
        val broj = Raspored.slikaZaNoc(cfg.razmakMin)
        val poSlici = when (cfg.rezolucija) {
            "2mp" -> 0.4
            "8mp" -> 2.0
            else -> 4.0
        }
        val gb = broj * poSlici / 1024.0
        val osnovno = "Osam sati na ${cfg.razmakMin} min je oko $broj slika, " +
                "približno %.1f GB po noći.".format(gb)
        // Nocni snimak traje sekundama, jer telefon slaze vise kadrova.
        return if (cfg.rezim == "noc" && cfg.razmakMin < 2) {
            "$osnovno\nUPOZORENJE: noćni snimak traje nekoliko sekundi, pa razmak od jednog " +
                    "minuta ostavlja malo vremena između kadrova. Uzmi bar 2 minuta."
        } else osnovno
    }

    // ----------------------------------------------------------------- slika

    private fun karticaSlika(): ViewGroup {
        val (kartica, sadrzaj) = card(this, "Slika")
        sadrzaj.addView(choice(
            this,
            listOf("puna" to "Puna", "8mp" to "8 MP", "2mp" to "2 MP"),
            cfg.rezolucija,
        ) { cfg.rezolucija = it; brojSlikaOpis.text = opisRazmaka() })

        sadrzaj.addView(switch(this, "Prednja kamera", cfg.prednjaKamera) {
            cfg.prednjaKamera = it
        })
        sadrzaj.addView(switch(this, "Upiši vreme i na arhivsku sliku", cfg.zigNaArhivi) {
            cfg.zigNaArhivi = it
        })
        sadrzaj.addView(body(this, "Na kopiji koja ide modelu vreme se upisuje uvek, jer je " +
                "tamo jedino sidro za redosled."))

        sadrzaj.addView(body(this, "Kako se snima. Tri odvojena puta, ne prekidači koji se sabiraju."))
        sadrzaj.addView(choice(
            this,
            listOf("noc" to "Noćni", "auto" to "Auto", "rucno" to "Ručno", "slaganje" to "Slaganje"),
            cfg.rezim,
        ) { cfg.rezim = it; osveziRezim() })

        rezimOpis = body(this, "")
        sadrzaj.addView(rezimOpis)

        sadrzaj.addView(switch(this, "Zaključaj ekspoziciju posle prve slike",
            cfg.zakljucajPosleParve) { cfg.zakljucajPosleParve = it })
        sadrzaj.addView(body(this, "Važi za Automatiku. Prva slika se snimi automatikom, iz nje se " +
                "pročita koju je ekspoziciju i ISO telefon stvarno upotrebio, i ostatak noći ide " +
                "tim vrednostima, pa su sve slike uporedive."))

        val eksp = field(this, "Ekspozicija, 1/x sekunde", ekspozicijaTekst()) { uneto ->
            uneto.toIntOrNull()?.let { if (it > 0) cfg.ekspozicijaNs = 1_000_000_000L / it }
        }
        ekspozicijaPolje = eksp.second
        sadrzaj.addView(eksp.first)
        val isoP = field(this, "ISO", cfg.iso.toString()) { uneto ->
            uneto.toIntOrNull()?.let { cfg.iso = it }
        }
        isoPolje = isoP.second
        sadrzaj.addView(isoP.first)

        // Bez ovoga se granica telefona traži pogađanjem: sve preko nje daje
        // istu sliku, jer je telefon tiho stegne i ne kaže ništa.
        sadrzaj.addView(button(this, "Postavi na granicu telefona") {
            val kamera = Kamera(this, cfg)
            val najduza = kamera.najduzaEkspozicija()
            val najveci = kamera.najveciIso()
            if (najduza > 0) cfg.ekspozicijaNs = najduza
            if (najveci > 0) cfg.iso = najveci
            ekspozicijaPolje?.setText(ekspozicijaTekst())
            isoPolje?.setText(cfg.iso.toString())
        })

        sadrzaj.addView(switch(this, "Traži i preko granice telefona", cfg.prekoGranice) {
            cfg.prekoGranice = it
        })
        sadrzaj.addView(body(this, "Camera2 dozvoljava da se traži ekspozicija izvan prijavljenog " +
                "raspona: radi na nekim uređajima, ali se ne garantuje. Vredi probati baš na " +
                "Samsungu, jer njihova Kamera ide do 30 sekundi dok Camera2 prijavljuje 1/6, što " +
                "znači da je granica njihova odluka a ne granica senzora. Noćna proba pod " +
                "„Dobijeno“ kaže da li je prošlo."))

        sadrzaj.addView(field(this, "Kadrova za slaganje", cfg.kadrovaZaSlaganje.toString()) { uneto ->
            uneto.toIntOrNull()?.let { cfg.kadrovaZaSlaganje = it }
        }.first)
        sadrzaj.addView(switch(this, "Pojačaj sliku posle slaganja", cfg.autoPojacanje) {
            cfg.autoPojacanje = it
        })
        sadrzaj.addView(body(this, "Osam kadrova mraka i dalje ume da bude mrak. Zato se posle " +
                "sabiranja gleda gde je najsvetliji deo slike i podiže se do vrha. Najsvetlijih " +
                "pola procenta se zanemari, da jedan indikator na punjaču ne odredi osvetljenost " +
                "cele sobe."))
        sadrzaj.addView(field(this, "Rastojanje do kreveta, u metrima", cfg.rastojanjeM.toString()) { uneto ->
            uneto.toFloatOrNull()?.let { cfg.rastojanjeM = it }
        }.first)
        sadrzaj.addView(body(this, "Fokus se zaključava na to rastojanje. U mraku autofokus lovi " +
                "i vraća mutan kadar, bez ijedne greške."))
        return kartica
    }

    /**
     * Opis izabranog režima, sa odgovorom na jedino pitanje koje je važno:
     * da li ga ovaj telefon uopšte ima.
     */
    private fun osveziRezim() {
        val kamera = Kamera(this, cfg)
        val ima = try { kamera.nocniDostupan() } catch (e: Exception) { false }
        rezimOpis.text = when (cfg.rezim) {
            "noc" -> if (ima) {
                "Noćni režim samog telefona: senzor snimi više kadrova različite dužine i " +
                        "proizvođačev kod ih spoji u jednu svetliju sliku sa manje šuma. Isto što " +
                        "radi Kamera kad u mraku sačekaš par sekundi.\n" +
                        "Jedan snimak traje nekoliko sekundi, pa razmak ispod jednog minuta ovde " +
                        "nema smisla. Ekspoziciju bira telefon; ručne vrednosti se ne mogu tražiti."
            } else {
                "UPOZORENJE: ovaj telefon ne prijavljuje noćni režim na izabranoj kameri. " +
                        "Snimaće se obično. Probaj drugu kameru ili izaberi Automatiku."
            }
            "slaganje" -> {
                val kamera = Kamera(this, cfg)
                val najduza = kamera.najduzaEkspozicija()
                "Duga ekspozicija sabiranjem više kadrova, kad telefon dužu ne dozvoljava. " +
                        "Snimi se ${cfg.kadrovaZaSlaganje} kadrova zaredom i saberu se u jedan, pa je " +
                        "slika ${Slaganje.dobitak(cfg.kadrovaZaSlaganje)} i sa manje šuma.\n" +
                        (if (najduza > 0) "Ova kamera daje najviše " +
                                "${Kamera.Sposobnosti.vremeTekst(najduza)} po kadru, što uz " +
                                "${cfg.kadrovaZaSlaganje} kadrova daje oko " +
                                "${Kamera.Sposobnosti.vremeTekst(najduza * cfg.kadrovaZaSlaganje)} " +
                                "ukupne ekspozicije.\n" else "") +
                        "Traje nekoliko sekundi po slici, izlaz je oko 2 MP, a pokret tokom " +
                        "snimanja se razmaže. Za položaj tela to ne smeta."
            }
            "rucno" -> "Upisane vrednosti ispod. Ako ih telefon ne prihvati, Noćna proba to kaže: " +
                    "ispisuje i traženo i dobijeno."
            else -> "Automatika telefona. Uz zaključavanje ispod, prva slika određuje vrednosti za " +
                    "celu noć." + if (ima) "\nOvaj telefon ima i noćni režim: " +
                    kamera.nocniRezimi().joinToString(", ") + "." else ""
        }
    }

    private fun ekspozicijaTekst(): String =
        (1_000_000_000L / cfg.ekspozicijaNs.coerceAtLeast(1)).toString()

    // ------------------------------------------------------------------ zvuk

    private fun karticaZvuk(): ViewGroup {
        val (kartica, sadrzaj) = card(this, "Zvučna potvrda")
        sadrzaj.addView(body(this, "Zvuk na pritisak dugmeta, pa na svaku od prvih nekoliko " +
                "slika, pa tišina do jutra. Ton na pritisak je drugačiji od tona na slici, jer se " +
                "u mraku ne gleda ekran."))
        sadrzaj.addView(switch(this, "Uključeno", cfg.zvuk) { cfg.zvuk = it })
        sadrzaj.addView(field(this, "Čuje se do koje slike", cfg.zvukDoSlike.toString()) { uneto ->
            uneto.toIntOrNull()?.let { cfg.zvukDoSlike = it }
        }.first)
        sadrzaj.addView(field(this, "Prva slika posle, u sekundama", cfg.odlozenoSek.toString()) { uneto ->
            uneto.toIntOrNull()?.let { cfg.odlozenoSek = it }
        }.first)
        sadrzaj.addView(body(this, "Toliko imaš da legneš pošto pritisneš dugme. Prva slika inače " +
                "uhvati praznu sobu i tebe pored telefona."))
        sadrzaj.addView(field(this, "Jačina, 0 do 100", cfg.jacina.toString()) { uneto ->
            uneto.toIntOrNull()?.let { cfg.jacina = it }
        }.first)
        sadrzaj.addView(switch(this, "Vibracija", cfg.vibracija) { cfg.vibracija = it })
        sadrzaj.addView(switch(this, "Javi se na svakoj slici (samo za probu)", cfg.zvukSvaka) {
            cfg.zvukSvaka = it
        })
        sadrzaj.addView(button(this, "Čuj pokretanje") { Zvuk.pocetak(this, cfg) })
        sadrzaj.addView(button(this, "Čuj potvrdu slike") { Zvuk.potvrda(this, cfg) })
        sadrzaj.addView(field(this, "Prekini kad baterija padne na, u odsto",
            cfg.pragBaterije.toString()) { uneto ->
            uneto.toIntOrNull()?.let { cfg.pragBaterije = it }
        }.first)
        sadrzaj.addView(body(this, "Ugašen telefon ne završi zapisnik i ne izveze noć, pa se staje " +
                "dok još ima struje. Snimanje bez punjača troši oko 5 do 10 odsto po satu."))
        sadrzaj.addView(field(this, "Sam prekini u sat (prazno = nikad)",
            if (cfg.autoStopSat in 0..23) cfg.autoStopSat.toString() else "") { uneto ->
            cfg.autoStopSat = uneto.toIntOrNull()?.takeIf { it in 0..23 } ?: -1
        }.first)
        return kartica
    }

    // --------------------------------------------------------------- provera

    private fun karticaProvera(): ViewGroup {
        val (kartica, sadrzaj) = card(this, "Provera")
        provera = body(this, "")
        sadrzaj.addView(provera)
        sadrzaj.addView(button(this, "Dozvole aplikacije") {
            startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                .setData(Uri.parse("package:$packageName")))
        })
        sadrzaj.addView(button(this, "Tačni alarmi") {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM))
            }
        })
        sadrzaj.addView(button(this, "Očisti stare kopije iz Documents") {
            val obrisano = Zapis.obrisiIzvoz(this)
            android.widget.Toast.makeText(
                this, "Obrisano $obrisano fajlova iz Documents/San",
                android.widget.Toast.LENGTH_LONG,
            ).show()
        })
        sadrzaj.addView(body(this, "Verzija 0.1 je male kopije upisivala u Documents posle svake " +
                "slike, pa je galerija prikazivala dva albuma istog imena sa istim slikama. " +
                "Ovim se ti stari fajlovi brišu; pune slike u galeriji ostaju netaknute."))
        sadrzaj.addView(button(this, "Štednja baterije") {
            startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
        })
        sadrzaj.addView(body(this, "Ako sistem uspava aplikaciju, snimanje tiho prestane usred " +
                "noći. Na Samsungu se to zove „Aplikacije koje se nikad ne uspavljuju“."))
        return kartica
    }

    private fun tekstProvere(): String {
        val redovi = ArrayList<String>()
        redovi.add(if (imaKameru()) "Kamera: odobrena" else "Kamera: NIJE odobrena")
        redovi.add(
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
                (getSystemService(Context.ALARM_SERVICE) as AlarmManager).canScheduleExactAlarms()
            ) "Tačni alarmi: dozvoljeni" else "Tačni alarmi: NISU dozvoljeni, okidanje će lutati"
        )
        val struja = getSystemService(Context.POWER_SERVICE) as PowerManager
        redovi.add(
            if (struja.isIgnoringBatteryOptimizations(packageName))
                "Štednja baterije: isključena za San"
            else "Štednja baterije: uključena, servis može biti ubijen"
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            redovi.add(
                if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                    == PackageManager.PERMISSION_GRANTED
                ) "Obaveštenja: odobrena" else "Obaveštenja: nisu odobrena"
            )
        }
        redovi.add(if (naPunjacu()) "Punjač: priključen" else "Punjač: NIJE priključen")
        return redovi.joinToString("\n")
    }

    // -------------------------------------------------------------- pomoćno

    private fun imaKameru() = ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED

    private fun trraziDozvole() {
        val trazene = ArrayList<String>()
        if (!imaKameru()) trazene.add(Manifest.permission.CAMERA)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) trazene.add(Manifest.permission.POST_NOTIFICATIONS)
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
            != PackageManager.PERMISSION_GRANTED
        ) trazene.add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        if (trazene.isNotEmpty()) ActivityCompat.requestPermissions(this, trazene.toTypedArray(), 1)
    }

    private fun naPunjacu(): Boolean {
        val bm = getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        return bm.isCharging
    }

    private fun baterija(): Int = try {
        (getSystemService(Context.BATTERY_SERVICE) as BatteryManager)
            .getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
    } catch (e: Exception) {
        -1
    }

    private fun vremeTekst(vreme: Long): String =
        Instant.ofEpochMilli(vreme).atZone(ZoneId.systemDefault()).toLocalTime()
            .withSecond(0).withNano(0).toString()

    private fun gigabajti(bajtova: Long): String = "%.1f GB".format(bajtova / 1024.0 / 1024.0 / 1024.0)

    override fun onDestroy() {
        posao.shutdown()
        super.onDestroy()
    }
}
