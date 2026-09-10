package studio.room211.san

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.ByteArrayOutputStream
import java.time.LocalDate

/**
 * Put od snimka do fajlova na disku.
 *
 * Redosled u ovoj datoteci nije proizvoljan: pokret se meri PRE nego sto se na
 * sliku upise vreme. Traka sa vremenom se menja na svakom kadru, pa bi merenje
 * inace na svakoj slici videlo promenu i ceo grafikon noci bio bi smece.
 */
object Obrada {

    class Ishod(val ime: String, val pokret: Double, val greska: String)

    fun jednaSlika(
        ctx: Context,
        cfg: Config,
        kamera: Kamera,
        vreme: Long,
        redni: Int,
        nocDatum: LocalDate,
    ): Ishod {
        val snimak = kamera.snimi()
        val sirovo = snimak.bajtovi
            ?: return Ishod("", 0.0, snimak.greska.ifBlank { "snimak nije uspeo" })

        val ime = Raspored.imeFajla(vreme)
        val putanja = Raspored.putanjaNoci(nocDatum)
        val zigTekst = Raspored.zigTekst(vreme, redni)

        // Mala kopija: iz nje se meri pokret i ona ide modelu. Puna slika se
        // zbog toga ne mora drzati u memoriji dva puta.
        val mala = smanji(sirovo, Config.ANALIZA_STRANA)
        var pokret = 0.0
        if (mala != null) {
            val slicica = Zig.slicica(mala)
            cfg.prethodnaSlicica?.let { pre ->
                if (pre.size == slicica.size) pokret = Pokret.razlika(pre, slicica)
            }
            cfg.prethodnaSlicica = slicica

            Zig.upisi(mala, zigTekst)
            Zapis.sacuvajKopiju(
                ctx, uJpeg(mala, 85), Zapis.putanjaAnaliza(putanja), ime, vreme
            )
            mala.recycle()
        }

        val zaArhivu = if (cfg.zigNaArhivi) saZigom(sirovo, zigTekst, cfg.kvalitet) ?: sirovo else sirovo
        Zapis.sacuvajSliku(ctx, zaArhivu, Zapis.putanjaSlika(putanja), ime, vreme)
            ?: return Ishod(ime, pokret, "upis u DCIM nije uspeo")

        return Ishod(ime, pokret, "")
    }

    /** Puna slika sa upisanim vremenom. `null` znaci da se cuva original, netaknut. */
    private fun saZigom(sirovo: ByteArray, tekst: String, kvalitet: Int): ByteArray? {
        for (konfiguracija in listOf(Bitmap.Config.ARGB_8888, Bitmap.Config.RGB_565)) {
            try {
                val opcije = BitmapFactory.Options().apply {
                    inPreferredConfig = konfiguracija
                    inMutable = true
                }
                val slika = BitmapFactory.decodeByteArray(sirovo, 0, sirovo.size, opcije)
                    ?: return null
                Zig.upisi(slika, tekst)
                val izlaz = uJpeg(slika, kvalitet)
                slika.recycle()
                return izlaz
            } catch (e: OutOfMemoryError) {
                // Puna slika od 12 MP je 48 MB u memoriji. Ako ne stane, ide se
                // na upola manji zapis, pa tek onda na original bez trake.
            } catch (e: Exception) {
                return null
            }
        }
        return null
    }

    private fun smanji(sirovo: ByteArray, duzaStrana: Int): Bitmap? {
        return try {
            val mere = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(sirovo, 0, sirovo.size, mere)
            val veca = maxOf(mere.outWidth, mere.outHeight)
            if (veca <= 0) return null

            var uzorak = 1
            while (veca / (uzorak * 2) >= duzaStrana) uzorak *= 2

            val opcije = BitmapFactory.Options().apply {
                inSampleSize = uzorak
                inPreferredConfig = Bitmap.Config.ARGB_8888
                inMutable = true
            }
            val slika = BitmapFactory.decodeByteArray(sirovo, 0, sirovo.size, opcije) ?: return null
            val trenutna = maxOf(slika.width, slika.height)
            if (trenutna <= duzaStrana) return slika

            val odnos = duzaStrana.toFloat() / trenutna
            val skalirana = Bitmap.createScaledBitmap(
                slika, (slika.width * odnos).toInt(), (slika.height * odnos).toInt(), true
            )
            if (skalirana !== slika) slika.recycle()
            skalirana
        } catch (e: OutOfMemoryError) {
            null
        } catch (e: Exception) {
            null
        }
    }

    private fun uJpeg(slika: Bitmap, kvalitet: Int): ByteArray {
        val izlaz = ByteArrayOutputStream()
        slika.compress(Bitmap.CompressFormat.JPEG, kvalitet, izlaz)
        return izlaz.toByteArray()
    }
}
