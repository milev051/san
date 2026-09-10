package studio.room211.san

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.graphics.Color
import android.os.BatteryManager
import android.os.Build
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.material.button.MaterialButton
import com.google.android.material.color.DynamicColors
import studio.room211.san.Ui.dp
import java.time.Instant
import java.time.ZoneId

/**
 * Prvi ekran: jedno dugme preko celog ekrana.
 *
 * Telefon visi iznad kreveta, okrenut od tebe, i uveče se pipa u mraku. Sitno
 * dugme među karticama se tada ne nalazi. Zato je ceo ekran dugme, a
 * podešavanja su sklonjena na tri tačkice u uglu.
 *
 * **Zaustavljanje traži DUGI pritisak.** Ako bi i ono bilo dodir preko celog
 * ekrana, jedan slučajan dodir u mraku prekinuo bi noć, a to se ne bi videlo
 * do jutra.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var cfg: Config
    private lateinit var glavno: MaterialButton
    private lateinit var stanje: TextView
    private lateinit var poslednja: ImageView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        DynamicColors.applyToActivityIfAvailable(this)
        cfg = Config(this)
        trraziDozvole()

        val koren = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val p = dp(12)
            setPadding(p, dp(10), p, dp(12))
        }
        koren.addView(zaglavlje())
        koren.addView(glavnoDugme())
        koren.addView(podnozje())
        setContentView(koren)
    }

    override fun onResume() {
        super.onResume()
        osvezi()
    }

    // --------------------------------------------------------------- zaglavlje

    private fun zaglavlje(): ViewGroup {
        val red = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        red.addView(TextView(this).apply {
            text = "San"
            setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_TitleLarge)
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            setPadding(dp(6), 0, 0, 0)
        })
        red.addView(uglovnoDugme("noći") { Noci.otvori(this) })
        red.addView(uglovnoDugme("⋮") {
            startActivity(Intent(this, Podesavanja::class.java))
        })
        return red
    }

    private fun uglovnoDugme(oznaka: String, klik: () -> Unit) = MaterialButton(
        this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle
    ).apply {
        text = oznaka
        minWidth = dp(56)
        setOnClickListener { klik() }
        layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { marginStart = dp(6) }
    }

    // ------------------------------------------------------------ glavno dugme

    private fun glavnoDugme(): ViewGroup {
        glavno = MaterialButton(this).apply {
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 30f)
            cornerRadius = dp(28)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f
            ).apply { topMargin = dp(8); bottomMargin = dp(8) }

            setOnClickListener {
                if (cfg.aktivno) {
                    // Kratak dodir dok snima ne radi nista. Zaustavlja se
                    // dugim pritiskom, da se noc ne prekine slucajno.
                    stanje.text = "Za zaustavljanje DRŽI dugme.\n\n" + stanje.text
                } else {
                    pocni()
                }
            }
            setOnLongClickListener {
                if (cfg.aktivno) {
                    Snimanje.zaustavi(this@MainActivity)
                    postDelayed({ osvezi() }, 700)
                }
                true
            }
        }
        val okvir = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f
            )
        }
        okvir.addView(glavno)
        return okvir
    }

    private fun pocni() {
        if (!imaKameru()) { trraziDozvole(); return }
        Snimanje.pokreni(this)
        stanje.text = "Pokrenuto. Prva slika za ${cfg.odlozenoSek}s."
        glavno.postDelayed({ osvezi() }, 1200)
        glavno.postDelayed({ osvezi() }, (cfg.odlozenoSek + 4) * 1000L)
    }

    // ---------------------------------------------------------------- podnožje

    private fun podnozje(): ViewGroup {
        val okvir = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        poslednja = ImageView(this).apply {
            adjustViewBounds = true
            scaleType = ImageView.ScaleType.FIT_CENTER
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(150)
            )
            setOnClickListener {
                val noc = cfg.tekucaNoc.ifBlank { Noci.spisak(this@MainActivity).firstOrNull() ?: "" }
                if (noc.isNotBlank()) Noci.otvori(this@MainActivity, noc)
            }
        }
        stanje = TextView(this).apply {
            setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodyMedium)
            alpha = 0.8f
            setPadding(dp(6), dp(6), dp(6), 0)
        }
        okvir.addView(poslednja)
        okvir.addView(stanje)
        return okvir
    }

    // ------------------------------------------------------------------ stanje

    private fun osvezi() {
        val redovi = ArrayList<String>()
        if (cfg.aktivno) {
            glavno.text = "SNIMA\n\ndrži da zaustaviš"
            glavno.setBackgroundColor(Color.parseColor("#7F1D1D"))
            glavno.setTextColor(Color.WHITE)
            redovi.add("Noć ${cfg.tekucaNoc}, od ${vremeTekst(cfg.pocetak)}")
            redovi.add("Slika: ${cfg.brojSlika}" +
                    if (cfg.sledece > 0) ", sledeća u ${vremeTekst(cfg.sledece)}" else "")
            if (cfg.zakljucanaEkspozicijaNs > 0) {
                redovi.add("Zaključano: ${Kamera.Sposobnosti.vremeTekst(cfg.zakljucanaEkspozicijaNs)}, " +
                        "ISO ${cfg.zakljucaniIso}")
            }
        } else {
            glavno.text = "POČNI"
            glavno.setBackgroundColor(Color.parseColor("#1E3A5F"))
            glavno.setTextColor(Color.WHITE)
            val poslednjaNoc = Noci.spisak(this).firstOrNull()
            if (poslednjaNoc != null) {
                redovi.add("Poslednja noć $poslednjaNoc: " +
                        "${Noci.zapisnik(this, poslednjaNoc).optInt("ukupno")} slika")
                if (Raspored.nastavakIste(System.currentTimeMillis(), cfg.poslednjaSlika, Config.NASTAVAK_MS)) {
                    redovi.add("Nastavlja tu noć: isti folder i isto brojanje")
                }
            }
            redovi.add("Režim: ${imeRezima()}, razmak ${cfg.razmakMin} min, " +
                    "prva slika za ${cfg.odlozenoSek}s")
        }
        redovi.add("Baterija ${baterija()}%" + (if (naPunjacu()) ", na punjaču" else "") +
                ", slobodno ${gigabajti(Zapis.slobodno())}")
        if (cfg.poslednjaGreska.isNotBlank()) redovi.add("Greška: ${cfg.poslednjaGreska}")
        if (!cfg.aktivno && cfg.razlogPrekida.isNotBlank()) {
            redovi.add("Prekinuto: ${cfg.razlogPrekida}")
        }
        stanje.text = redovi.joinToString("\n")
        prikaziPoslednju()
    }

    private fun imeRezima() = when (cfg.rezim) {
        "noc" -> "noćni"
        "rucno" -> "ručno"
        else -> "automatika"
    }

    private fun prikaziPoslednju() {
        val noc = cfg.tekucaNoc.ifBlank { Noci.spisak(this).firstOrNull() ?: "" }
        val fajl = if (noc.isBlank()) null else Noci.slike(this, noc).lastOrNull()
        if (fajl == null) {
            poslednja.setImageDrawable(null)
            poslednja.visibility = android.view.View.GONE
        } else {
            poslednja.visibility = android.view.View.VISIBLE
            poslednja.setImageBitmap(
                BitmapFactory.decodeFile(fajl.absolutePath, BitmapFactory.Options().apply {
                    inSampleSize = 4
                })
            )
        }
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
        if (trazene.isNotEmpty()) ActivityCompat.requestPermissions(this, trazene.toTypedArray(), 1)
    }

    private fun naPunjacu(): Boolean =
        (getSystemService(Context.BATTERY_SERVICE) as BatteryManager).isCharging

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
}
