package studio.room211.san

import android.content.Context
import android.graphics.Bitmap
import java.io.ByteArrayOutputStream
import java.io.File
import java.time.LocalDate

/**
 * Put od snimka do fajlova na disku.
 *
 * Redosled u ovoj datoteci nije proizvoljan: pokret se meri PRE nego sto se na
 * sliku upise vreme. Traka sa vremenom se menja na svakom kadru, pa bi merenje
 * inace na svakoj slici videlo promenu i ceo grafikon noci bio bi smece.
 *
 * Mala kopija ide u privatni folder aplikacije, ne u `Documents`. U `Documents`
 * je pravila drugi album u galeriji, sa istim imenom i istim slikama kao onaj
 * u DCIM-u; iz aplikacije se izvozi tek kad se noc prebacuje na racunar.
 */
object Obrada {

    class Ishod(
        val ime: String,
        val pokret: Double,
        val greska: String,
        val ekspozicijaNs: Long = 0,
        val iso: Int = 0,
        val rucnoPrimenjeno: Boolean = false,
        val uri: String = "",
        val nocni: Boolean = false,
    )

    /** Folder sa malim kopijama jedne noci, unutar aplikacije. */
    fun folderNoci(ctx: Context, noc: String): File =
        File(File(ctx.filesDir, "analiza"), noc).apply { mkdirs() }

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

        // Mala kopija: iz nje se meri pokret, ona se prikazuje u aplikaciji i
        // ona ide modelu. Puna slika se zbog toga ne drzi u memoriji dvaput.
        var pokret = 0.0
        val mala = Slika.uspravno(sirovo, Config.ANALIZA_STRANA)
        if (mala != null) {
            val slicica = Zig.slicica(mala)
            cfg.prethodnaSlicica?.let { pre ->
                // Bez sjaja: nocni rezim bira ekspoziciju po snimku, pa bi
                // sirova razlika svaku promenu svetline prijavila kao pokret.
                if (pre.size == slicica.size) pokret = Pokret.razlikaBezSjaja(pre, slicica)
            }
            cfg.prethodnaSlicica = slicica

            Zig.upisi(mala, zigTekst)
            try {
                File(folderNoci(ctx, nocDatum.toString()), ime).writeBytes(uJpeg(mala, 85))
            } catch (e: Exception) {
                // Mala kopija je dodatak; puna slika je ono sto se ne sme izgubiti.
            }
            mala.recycle()
        }

        var okrenuto = false
        val zaArhivu = if (cfg.zigNaArhivi) {
            val puna = Slika.uspravno(sirovo)
            if (puna == null) sirovo else {
                Zig.upisi(puna, zigTekst)
                val izlaz = uJpeg(puna, cfg.kvalitet)
                puna.recycle()
                okrenuto = true
                izlaz
            }
        } else sirovo

        val uri = Zapis.sacuvajSliku(
            ctx, zaArhivu, Zapis.putanjaSlika(putanja), ime, vreme, uspravna = okrenuto
        ) ?: return Ishod(ime, pokret, "upis u DCIM nije uspeo")

        return Ishod(
            ime, pokret, snimak.greska,
            snimak.ekspozicijaNs, snimak.iso, snimak.rucnoPrimenjeno, uri.toString(), snimak.nocni,
        )
    }

    private fun uJpeg(slika: Bitmap, kvalitet: Int): ByteArray {
        val izlaz = ByteArrayOutputStream()
        slika.compress(Bitmap.CompressFormat.JPEG, kvalitet, izlaz)
        return izlaz.toByteArray()
    }
}
