package studio.room211.san

import android.app.Dialog
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.color.DynamicColors
import org.json.JSONObject
import studio.room211.san.Ui.body
import studio.room211.san.Ui.button
import studio.room211.san.Ui.card
import studio.room211.san.Ui.dp
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * Pregled snimljenih noci.
 *
 * Bez oznake u nameri prikazuje spisak noci; sa oznakom prikazuje jednu noc
 * kao mrezu slicica.
 *
 * Prikazuju se MALE kopije iz privatnog foldera aplikacije, ne pune slike iz
 * galerije. Sto pune slike od po cetiri megabajta ne bi stalo u memoriju, a
 * ovako se cela noc otvara odmah.
 */
class Noci : AppCompatActivity() {

    companion object {
        const val NOC = "noc"

        fun otvori(ctx: Context, noc: String? = null) {
            ctx.startActivity(Intent(ctx, Noci::class.java).apply {
                if (noc != null) putExtra(NOC, noc)
            })
        }

        /** Sve noci koje imaju svoj folder, najnovija prva. */
        fun spisak(ctx: Context): List<String> =
            File(ctx.filesDir, "analiza").listFiles()
                ?.filter { it.isDirectory }
                ?.map { it.name }
                ?.sortedDescending()
                ?: emptyList()

        fun slike(ctx: Context, noc: String): List<File> =
            File(File(ctx.filesDir, "analiza"), noc).listFiles()
                ?.filter { it.name.endsWith(".jpg") }
                ?.sortedBy { it.name }
                ?: emptyList()

        fun zapisnik(ctx: Context, noc: String): JSONObject {
            val fajl = File(File(File(ctx.filesDir, "analiza"), noc), "noc.json")
            return if (fajl.exists()) JSONObject(fajl.readText()) else JSONObject()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        DynamicColors.applyToActivityIfAvailable(this)
        val noc = intent.getStringExtra(NOC)

        val koren = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val p = dp(14)
            setPadding(p, dp(18), p, dp(28))
        }
        koren.addView(TextView(this).apply {
            text = noc ?: "Snimljene noći"
            setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_HeadlineSmall)
            setPadding(dp(6), 0, 0, dp(8))
        })

        if (noc == null) spisakNoci(koren) else jednaNoc(koren, noc)

        setContentView(ScrollView(this).apply {
            addView(koren, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        })
    }

    // ----------------------------------------------------------- spisak noci

    private fun spisakNoci(koren: LinearLayout) {
        val noci = spisak(this)
        if (noci.isEmpty()) {
            koren.addView(body(this, "Još nema nijedne snimljene noći."))
            return
        }
        for (noc in noci) {
            val zapisnik = zapisnik(this, noc)
            val (kartica, sadrzaj) = card(this, noc)
            sadrzaj.addView(body(this, opisNoci(zapisnik, slike(this, noc).size)))
            sadrzaj.addView(button(this, "Otvori") { otvori(this, noc) })
            koren.addView(kartica)
        }
    }

    private fun opisNoci(zapisnik: JSONObject, naDisku: Int): String {
        val ukupno = zapisnik.optInt("ukupno", naDisku)
        val pocetak = zapisnik.optString("pocetak", "")
        val kraj = zapisnik.optString("kraj", "")
        val redovi = ArrayList<String>()
        redovi.add("$ukupno slika, $naDisku za pregled")
        if (pocetak.isNotBlank() && kraj.isNotBlank()) {
            redovi.add("od ${sat(pocetak)} do ${sat(kraj)}${trajanje(pocetak, kraj)}")
        }
        val niz = zapisnik.optJSONArray("slike")
        if (niz != null) {
            var promena = 0
            var rupa = 0
            for (i in 0 until niz.length()) {
                val r = niz.getJSONObject(i)
                if (r.optBoolean("promena")) promena++
                if (r.optString("greska").isNotBlank()) rupa++
            }
            redovi.add("promena položaja: $promena" + if (rupa > 0) ", rupa: $rupa" else "")
        }
        return redovi.joinToString("\n")
    }

    private fun sat(iso: String): String = try {
        LocalDateTime.parse(iso).format(DateTimeFormatter.ofPattern("dd.MM. HH:mm"))
    } catch (e: Exception) {
        iso
    }

    private fun trajanje(pocetak: String, kraj: String): String = try {
        val minuta = java.time.Duration.between(
            LocalDateTime.parse(pocetak), LocalDateTime.parse(kraj)
        ).toMinutes()
        ", ${minuta / 60}h ${minuta % 60}min"
    } catch (e: Exception) {
        ""
    }

    // -------------------------------------------------------------- jedna noc

    private fun jednaNoc(koren: LinearLayout, noc: String) {
        val slike = slike(this, noc)
        val zapisnik = zapisnik(this, noc)
        koren.addView(body(this, opisNoci(zapisnik, slike.size)))

        koren.addView(button(this, "Izvezi za računar") {
            val folder = File(File(filesDir, "analiza"), noc)
            val putanja = Zapis.putanjaAnaliza(
                Raspored.putanjaNoci(java.time.LocalDate.parse(noc))
            )
            val upisano = Zapis.izveziNoc(this, folder, putanja)
            android.widget.Toast.makeText(
                this, "Izvezeno $upisano fajlova u $putanja", android.widget.Toast.LENGTH_LONG
            ).show()
        })
        koren.addView(body(this, "Izvoz upisuje male kopije i noc.json u Documents/San/, " +
                "odakle se cela noć prebacuje na računar. Pune slike ostaju u galeriji."))

        if (slike.isEmpty()) {
            koren.addView(body(this, "Nema slika za pregled."))
            return
        }

        // Tri u redu; slicice se dekoduju umanjeno, da se cela noc otvori odmah.
        var red: LinearLayout? = null
        for ((i, fajl) in slike.withIndex()) {
            if (i % 3 == 0) {
                red = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    layoutParams = LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
                    ).apply { topMargin = dp(4) }
                }
                koren.addView(red)
            }
            red!!.addView(slicica(fajl))
        }
    }

    private fun slicica(fajl: File): ImageView = ImageView(this).apply {
        layoutParams = LinearLayout.LayoutParams(0, dp(150), 1f).apply {
            marginEnd = dp(4)
        }
        scaleType = ImageView.ScaleType.CENTER_CROP
        setImageBitmap(
            BitmapFactory.decodeFile(fajl.absolutePath, BitmapFactory.Options().apply {
                inSampleSize = 2
            })
        )
        setOnClickListener { prikaziCelu(fajl) }
    }

    private fun prikaziCelu(fajl: File) {
        val prozor = Dialog(this, android.R.style.Theme_Black_NoTitleBar_Fullscreen)
        val okvir = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(Color.BLACK)
        }
        okvir.addView(ImageView(this).apply {
            adjustViewBounds = true
            setImageBitmap(BitmapFactory.decodeFile(fajl.absolutePath))
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            )
        })
        okvir.setOnClickListener { prozor.dismiss() }
        prozor.setContentView(okvir)
        prozor.show()
    }
}
