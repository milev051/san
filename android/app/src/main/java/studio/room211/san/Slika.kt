package studio.room211.san

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import androidx.exifinterface.media.ExifInterface
import java.io.ByteArrayInputStream

/**
 * Dekodiranje snimka u USPRAVNU sliku.
 *
 * Kamera ne okrece piksele. Ona snimi kadar onako kako senzor stoji, a u EXIF
 * upise koliko ga treba okrenuti pri prikazu. Galerija to postuje,
 * `BitmapFactory` ne. Zato je probna slika u aplikaciji izgledala zarotirano.
 *
 * Vaznije od izgleda: kad se na sliku upisuje vreme, slika se iznova kodira i
 * EXIF nestane. Traka bi tada bila nacrtana na polozenom kadru i ostala bi
 * bocno zauvek. Zato se okretanje primenjuje na piksele PRE crtanja trake, a
 * gotova slika se upisuje sa uspravnom oznakom.
 */
object Slika {

    fun ugao(bajtovi: ByteArray): Int = try {
        when (ExifInterface(ByteArrayInputStream(bajtovi))
            .getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90
            ExifInterface.ORIENTATION_ROTATE_180 -> 180
            ExifInterface.ORIENTATION_ROTATE_270 -> 270
            else -> 0
        }
    } catch (e: Exception) {
        0
    }

    /**
     * Dekoduj i okreni. `duzaStrana = 0` znaci punu velicinu.
     *
     * Vraca `null` kad slika ne stane u memoriju; pozivalac tada cuva original
     * netaknut, jer je slika bez trake bolja od nijedne slike.
     */
    fun uspravno(bajtovi: ByteArray, duzaStrana: Int = 0): Bitmap? {
        val ugao = ugao(bajtovi)
        for (konfiguracija in listOf(Bitmap.Config.ARGB_8888, Bitmap.Config.RGB_565)) {
            try {
                val opcije = BitmapFactory.Options().apply {
                    inPreferredConfig = konfiguracija
                    inMutable = true
                    if (duzaStrana > 0) inSampleSize = uzorak(bajtovi, duzaStrana)
                }
                var slika = BitmapFactory.decodeByteArray(bajtovi, 0, bajtovi.size, opcije)
                    ?: return null

                if (duzaStrana > 0) {
                    val trenutna = maxOf(slika.width, slika.height)
                    if (trenutna > duzaStrana) {
                        val odnos = duzaStrana.toFloat() / trenutna
                        val skalirana = Bitmap.createScaledBitmap(
                            slika, (slika.width * odnos).toInt(), (slika.height * odnos).toInt(), true
                        )
                        if (skalirana !== slika) slika.recycle()
                        slika = skalirana
                    }
                }

                if (ugao == 0) return slika
                val okrenuta = Bitmap.createBitmap(
                    slika, 0, 0, slika.width, slika.height,
                    Matrix().apply { postRotate(ugao.toFloat()) }, true,
                )
                if (okrenuta !== slika) slika.recycle()
                return okrenuta
            } catch (e: OutOfMemoryError) {
                // Puna slika od 12 MP je 48 MB u memoriji, a okretanje trazi
                // jos toliko. Ako ne stane, pokusava se upola manji zapis.
            } catch (e: Exception) {
                return null
            }
        }
        return null
    }

    private fun uzorak(bajtovi: ByteArray, duzaStrana: Int): Int {
        val mere = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bajtovi, 0, bajtovi.size, mere)
        val veca = maxOf(mere.outWidth, mere.outHeight)
        var uzorak = 1
        while (veca / (uzorak * 2) >= duzaStrana) uzorak *= 2
        return uzorak
    }
}
