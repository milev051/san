package studio.room211.san

import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

/**
 * Zvucna potvrda na prve dve slike, pa tisina do jutra.
 *
 * Druga slika je vaznija od prve: prva samo kaze da je Start prosao, druga da
 * je ceo ciklus ponavljanja ziv. Zato se javlja na obe.
 *
 * Ide na `STREAM_ALARM`, ne na obavestenja. Telefon koji nocu stoji u tihom
 * rezimu inace ne bi pustio nista, a covek bi legao misleci da nesto ne radi.
 */
object Zvuk {

    fun potvrda(ctx: Context, cfg: Config) = oglasi(ctx, cfg, ToneGenerator.TONE_PROP_BEEP, 150)

    /**
     * Pritisnuto je dugme.
     *
     * Drugi ton od potvrde snimka, namerno: u mraku se ne gleda ekran, pa se po
     * zvuku mora razlikovati „primljeno je" od „slika je napravljena".
     */
    fun pocetak(ctx: Context, cfg: Config) = oglasi(ctx, cfg, ToneGenerator.TONE_PROP_BEEP2, 300)

    /** Greska usred noci. Tisina inace znaci „sve je u redu" i kad nije. */
    fun greska(ctx: Context, cfg: Config) = oglasi(ctx, cfg, ToneGenerator.TONE_SUP_ERROR, 700)

    private fun oglasi(ctx: Context, cfg: Config, ton: Int, trajanje: Int) {
        if (cfg.jacina > 0) {
            try {
                val zvono = ToneGenerator(AudioManager.STREAM_ALARM, cfg.jacina)
                zvono.startTone(ton, trajanje)
                // Oslobadja se tek posle tona: raniji `release()` preseca zvuk.
                android.os.Handler(ctx.mainLooper).postDelayed(
                    { try { zvono.release() } catch (e: Exception) {} },
                    (trajanje + 300).toLong(),
                )
            } catch (e: Exception) {
                // Neki telefoni ne daju ToneGenerator dok je u pozadini; vibracija ostaje.
            }
        }
        if (cfg.vibracija) zavibriraj(ctx, trajanje.toLong())
    }

    private fun zavibriraj(ctx: Context, trajanje: Long) {
        try {
            val v = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                (ctx.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                ctx.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            }
            v.vibrate(VibrationEffect.createOneShot(trajanje, VibrationEffect.DEFAULT_AMPLITUDE))
        } catch (e: Exception) {
        }
    }
}
