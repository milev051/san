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
 * Jedini ekran.
 *
 * Redosled kartica prati redosled kojim se stvari rade uvece: pokreni, proveri
 * da se u mraku uopste nesto vidi, podesi, pa proveri dozvole. Stanje je na
 * vrhu jer je to jedino zbog cega se ekran otvara posle prve noci.
 *
 * Zive slike sa kamere namerno nema. U mracnoj sobi ona ne pokazuje nista
 * korisno, a probna slika sa zakljucanom ekspozicijom pokazuje tacno ono sto
 * ce noc dati.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var cfg: Config
    private lateinit var stanje: TextView
    private lateinit var provera: TextView
    private lateinit var probaOpis: TextView
    private lateinit var probaSlika: ImageView
    private lateinit var brojSlikaOpis: TextView
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
        koren.addView(karticaStanje())
        koren.addView(karticaProba())
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
        osvezi()
    }

    private fun naslov() = TextView(this).apply {
        text = "San v${packageManager.getPackageInfo(packageName, 0).versionName}"
        setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_HeadlineSmall)
        setPadding(dp(6), 0, 0, dp(4))
    }

    // ----------------------------------------------------------------- stanje

    private fun karticaStanje(): ViewGroup {
        val (kartica, sadrzaj) = card(this, "Snimanje")
        stanje = body(this, "")
        sadrzaj.addView(stanje)
        sadrzaj.addView(button(this, "Počni") {
            if (!imaKameru()) { trraziDozvole(); return@button }
            Snimanje.pokreni(this)
            stanje.postDelayed({ osvezi() }, 2500)
        })
        sadrzaj.addView(button(this, "Zaustavi") {
            Snimanje.zaustavi(this)
            stanje.postDelayed({ osvezi() }, 600)
        })
        return kartica
    }

    private fun osvezi() {
        val redovi = ArrayList<String>()
        if (cfg.aktivno) {
            redovi.add("Snima od ${vremeTekst(cfg.pocetak)}, noć ${Raspored.nocDatum(cfg.pocetak)}")
            redovi.add("Slika do sada: ${cfg.brojSlika}")
            if (cfg.sledece > 0) redovi.add("Sledeća u ${vremeTekst(cfg.sledece)}")
        } else {
            redovi.add("Zaustavljeno")
            val poslednja = Noc.spisak(this).firstOrNull()
            if (poslednja != null) {
                val zapisnik = Noc(this, poslednja).ucitaj()
                redovi.add("Poslednja noć $poslednja: ${zapisnik.optInt("ukupno")} slika")
            }
        }
        redovi.add("Slobodno: ${gigabajti(Zapis.slobodno())}")
        if (!naPunjacu()) redovi.add("Telefon NIJE na punjaču")
        if (cfg.poslednjaGreska.isNotBlank()) redovi.add("Greška: ${cfg.poslednjaGreska}")
        if (!cfg.aktivno && cfg.razlogPrekida.isNotBlank()) {
            redovi.add("Prekinuto: ${cfg.razlogPrekida}")
        }
        stanje.text = redovi.joinToString("\n")
        provera.text = tekstProvere()
        brojSlikaOpis.text = opisRazmaka()
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
                opis.append("\nSnimak je trajao ${trajalo / 1000.0}s")
                if (ishod.bajtovi != null) {
                    val slika = BitmapFactory.decodeByteArray(ishod.bajtovi, 0, ishod.bajtovi.size)
                    probaSlika.setImageBitmap(slika)
                    opis.append("\nVeličina: ${ishod.bajtovi.size / 1024} KB")
                } else {
                    probaSlika.setImageDrawable(null)
                    opis.append("\nSnimak nije uspeo: ${ishod.greska}")
                }
                probaOpis.text = opis.toString()
            }
        }
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
        return "Osam sati na ${cfg.razmakMin} min je oko $broj slika, " +
                "približno %.1f GB po noći.".format(gb)
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

        sadrzaj.addView(switch(this, "Ručna ekspozicija", cfg.rucnaEkspozicija) {
            cfg.rucnaEkspozicija = it
        })
        sadrzaj.addView(body(this, "Automatika u mraku luta: jedna slika crna, sledeća " +
                "prosvetljena. Zaključane vrednosti čine sve slike jedne noći uporedivim."))

        sadrzaj.addView(field(this, "Ekspozicija, 1/x sekunde", ekspozicijaTekst()) { uneto ->
            uneto.toIntOrNull()?.let { if (it > 0) cfg.ekspozicijaNs = 1_000_000_000L / it }
        }.first)
        sadrzaj.addView(field(this, "ISO", cfg.iso.toString()) { uneto ->
            uneto.toIntOrNull()?.let { cfg.iso = it }
        }.first)
        sadrzaj.addView(field(this, "Rastojanje do kreveta, u metrima", cfg.rastojanjeM.toString()) { uneto ->
            uneto.toFloatOrNull()?.let { cfg.rastojanjeM = it }
        }.first)
        sadrzaj.addView(body(this, "Fokus se zaključava na to rastojanje. U mraku autofokus lovi " +
                "i vraća mutan kadar, bez ijedne greške."))
        return kartica
    }

    private fun ekspozicijaTekst(): String =
        (1_000_000_000L / cfg.ekspozicijaNs.coerceAtLeast(1)).toString()

    // ------------------------------------------------------------------ zvuk

    private fun karticaZvuk(): ViewGroup {
        val (kartica, sadrzaj) = card(this, "Zvučna potvrda")
        sadrzaj.addView(body(this, "Javi se na prvoj i drugoj slici pa ćuti do jutra. Druga je " +
                "važnija: prva kaže da je start prošao, druga da se ponavljanje stvarno vrti."))
        sadrzaj.addView(switch(this, "Uključeno", cfg.zvuk) { cfg.zvuk = it })
        sadrzaj.addView(field(this, "Jačina, 0 do 100", cfg.jacina.toString()) { uneto ->
            uneto.toIntOrNull()?.let { cfg.jacina = it }
        }.first)
        sadrzaj.addView(switch(this, "Vibracija", cfg.vibracija) { cfg.vibracija = it })
        sadrzaj.addView(switch(this, "Javi se na svakoj slici (samo za probu)", cfg.zvukSvaka) {
            cfg.zvukSvaka = it
        })
        sadrzaj.addView(button(this, "Čuj kako zvuči") { Zvuk.potvrda(this, cfg) })
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

    private fun vremeTekst(vreme: Long): String =
        Instant.ofEpochMilli(vreme).atZone(ZoneId.systemDefault()).toLocalTime()
            .withSecond(0).withNano(0).toString()

    private fun gigabajti(bajtova: Long): String = "%.1f GB".format(bajtova / 1024.0 / 1024.0 / 1024.0)

    override fun onDestroy() {
        posao.shutdown()
        super.onDestroy()
    }
}
