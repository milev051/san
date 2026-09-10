package studio.room211.san

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface

/**
 * Upis vremena na sliku.
 *
 * Postoji zbog analize, ne zbog izgleda: kad se slike prevuku u razgovor sa
 * modelom, svaka mora sama da kaze kad je nastala. Bez toga model pomesa
 * redosled i vrati uredne redove sa pogresnim vremenima, sto je najgora vrsta
 * greske jer izgleda tacno.
 *
 * Traka je u gornjem levom uglu, gde je najmanja verovatnoca da pokrije coveka.
 */
object Zig {

    fun upisi(slika: Bitmap, tekst: String) {
        val platno = Canvas(slika)
        val visina = (slika.height * 0.020f).coerceIn(11f, 64f)

        val slova = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = visina
            typeface = Typeface.MONOSPACE
            isFakeBoldText = true
        }
        val podloga = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(150, 0, 0, 0)
        }

        val margina = visina * 0.5f
        val sirina = slova.measureText(tekst)
        val okvir = RectF(
            margina, margina,
            margina + sirina + visina, margina + visina * 1.9f
        )
        platno.drawRoundRect(okvir, visina * 0.35f, visina * 0.35f, podloga)
        platno.drawText(tekst, okvir.left + visina * 0.5f, okvir.bottom - visina * 0.55f, slova)
    }

    /** Siva slicica 64x64 kao niz svetlina, ulaz za merenje pokreta. */
    fun slicica(slika: Bitmap): IntArray {
        val mala = Bitmap.createScaledBitmap(slika, Pokret.STRANA, Pokret.STRANA, true)
        val piksele = IntArray(Pokret.STRANA * Pokret.STRANA)
        mala.getPixels(piksele, 0, Pokret.STRANA, 0, 0, Pokret.STRANA, Pokret.STRANA)
        if (mala !== slika) mala.recycle()
        return IntArray(piksele.size) { i ->
            val p = piksele[i]
            // Priblizna svetlina; tacna formula ovde ne menja nista jer se
            // porede dve slicice napravljene na isti nacin.
            (((p shr 16 and 0xFF) * 30 + (p shr 8 and 0xFF) * 59 + (p and 0xFF) * 11) / 100)
        }
    }
}
