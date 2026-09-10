package studio.room211.san

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.provider.MediaStore
import androidx.exifinterface.media.ExifInterface
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Upis na disk.
 *
 * Dva odredista, namerno razdvojena:
 *
 *  `DCIM/San/<god>/<mes>/<noc>/`      pune slike, vidljive u galeriji
 *  `Documents/San/<god>/<mes>/<noc>/` smanjene kopije i `noc.json`, ovo ide na racunar
 *
 * Razlog za drugo odrediste nije samo velicina. `Documents` prima bilo koji tip
 * fajla, a `DCIM` prima samo slike i video: `noc.json` u DCIM-u MediaStore
 * odbija. Uz to je to tacno onaj folder koji se kablom prebacuje na racunar,
 * bez 400 MB punih slika uz njega.
 */
object Zapis {

    const val KOREN = "San"

    fun putanjaSlika(podfolder: String) = "${Environment.DIRECTORY_DCIM}/$KOREN/$podfolder"
    fun putanjaAnaliza(podfolder: String) = "${Environment.DIRECTORY_DOCUMENTS}/$KOREN/$podfolder"

    /** Slobodno mesto na deljenom skladistu, u bajtovima. */
    fun slobodno(): Long {
        val st = StatFs(Environment.getExternalStorageDirectory().absolutePath)
        return st.availableBlocksLong * st.blockSizeLong
    }

    /**
     * Sacuvaj sliku. Vraca `null` kad upis ne uspe.
     *
     * Na Androidu 10 i novijem se upisuje preko MediaStore-a sa `IS_PENDING`,
     * pa galerija ne vidi polusnimljen fajl. Ispod toga se pise direktno.
     */
    fun sacuvajSliku(
        ctx: Context,
        bajtovi: ByteArray,
        relativna: String,
        ime: String,
        vreme: Long,
        uspravna: Boolean = false,
    ): Uri? {
        val uri = upisi(ctx, bajtovi, relativna, ime, "image/jpeg", MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
            ?: return null
        upisiExif(ctx, uri, vreme, uspravna)
        return uri
    }

    fun sacuvajTekst(ctx: Context, tekst: String, relativna: String, ime: String): Uri? =
        upisi(ctx, tekst.toByteArray(), relativna, ime, "application/json", filesUri())

    /**
     * Izvezi malu kopiju noci u `Documents`, za prenos na racunar.
     *
     * Radi se na zahtev, ne posle svake slike. Kad je islo automatski, galerija
     * je prikazivala dva albuma istog imena sa istim slikama: jedan iz DCIM-a,
     * drugi iz Documents-a. Uz izvoz se upisuje i `.nomedia`, da se to ne
     * ponovi ni kad je folder tu.
     */
    fun izveziNoc(ctx: Context, folder: File, relativna: String): Int {
        var upisano = 0
        upisi(ctx, ByteArray(0), relativna, ".nomedia", "application/octet-stream", filesUri())
        for (fajl in folder.listFiles()?.sortedBy { it.name } ?: emptyList()) {
            val tip = if (fajl.name.endsWith(".json")) "application/json" else "image/jpeg"
            if (upisi(ctx, fajl.readBytes(), relativna, fajl.name, tip, filesUri()) != null) upisano++
        }
        return upisano
    }

    private fun filesUri(): Uri =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        } else {
            MediaStore.Files.getContentUri("external")
        }

    private fun upisi(
        ctx: Context,
        bajtovi: ByteArray,
        relativna: String,
        ime: String,
        tip: String,
        zbirka: Uri,
    ): Uri? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            val folder = File(Environment.getExternalStorageDirectory(), relativna)
            if (!folder.exists() && !folder.mkdirs()) return null
            val fajl = File(folder, ime)
            fajl.writeBytes(bajtovi)
            return Uri.fromFile(fajl)
        }

        // Isti fajl se ume pisati vise puta (`noc.json` posle svake slike).
        // Bez ovoga MediaStore pravi `noc (1).json`, `noc (2).json` i tako u
        // nedogled, a na racunar stigne dvadeset zapisnika iste noci.
        obrisiPostojeci(ctx, zbirka, relativna, ime)

        val podaci = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, ime)
            put(MediaStore.MediaColumns.MIME_TYPE, tip)
            put(MediaStore.MediaColumns.RELATIVE_PATH, relativna)
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val uri = ctx.contentResolver.insert(zbirka, podaci) ?: return null
        return try {
            ctx.contentResolver.openOutputStream(uri)?.use { it.write(bajtovi) }
                ?: return null
            ctx.contentResolver.update(
                uri, ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }, null, null
            )
            uri
        } catch (e: Exception) {
            ctx.contentResolver.delete(uri, null, null)
            null
        }
    }

    /**
     * Obrisi sve sto je aplikacija upisala u `Documents/San/`.
     *
     * Postoji zbog greske iz verzije 0.1: male kopije su isle u `Documents`
     * posle svake slike, pa je galerija pokazivala dva albuma istog imena sa
     * istim slikama. Pune slike u DCIM-u se ne diraju.
     */
    fun obrisiIzvoz(ctx: Context): Int = try {
        ctx.contentResolver.delete(
            filesUri(),
            "${MediaStore.MediaColumns.RELATIVE_PATH} LIKE ?",
            arrayOf("${Environment.DIRECTORY_DOCUMENTS}/$KOREN/%"),
        )
    } catch (e: Exception) {
        0
    }

    private fun obrisiPostojeci(ctx: Context, zbirka: Uri, relativna: String, ime: String) {
        try {
            ctx.contentResolver.delete(
                zbirka,
                "${MediaStore.MediaColumns.RELATIVE_PATH}=? AND ${MediaStore.MediaColumns.DISPLAY_NAME}=?",
                arrayOf("$relativna/", ime),
            )
        } catch (e: Exception) {
            // Nema sta da se obrise ili nam sistem ne da; upis svejedno ide dalje.
        }
    }

    /**
     * Vreme snimanja u EXIF.
     *
     * Ime fajla ga vec nosi, ali galerija i macOS sortiraju po EXIF-u. Kad se
     * na sliku upise traka sa vremenom, slika se iznova kodira i originalni
     * EXIF nestane, pa se mora vratiti rukom.
     */
    private fun upisiExif(ctx: Context, uri: Uri, vreme: Long, uspravna: Boolean = false) {
        try {
            ctx.contentResolver.openFileDescriptor(uri, "rw")?.use { pfd ->
                val exif = ExifInterface(pfd.fileDescriptor)
                val oblik = SimpleDateFormat("yyyy:MM:dd HH:mm:ss", Locale.US).format(Date(vreme))
                exif.setAttribute(ExifInterface.TAG_DATETIME_ORIGINAL, oblik)
                exif.setAttribute(ExifInterface.TAG_DATETIME, oblik)
                exif.setAttribute(ExifInterface.TAG_DATETIME_DIGITIZED, oblik)
                // Slika sa upisanim vremenom je vec okrenuta u pikselima. Da
                // ostane stara oznaka, galerija bi je okrenula jos jednom.
                if (uspravna) {
                    exif.setAttribute(
                        ExifInterface.TAG_ORIENTATION,
                        ExifInterface.ORIENTATION_NORMAL.toString(),
                    )
                }
                exif.saveAttributes()
            }
        } catch (e: Exception) {
            // EXIF je dodatak; slika bez njega i dalje nosi vreme u imenu i na sebi.
        }
    }
}
