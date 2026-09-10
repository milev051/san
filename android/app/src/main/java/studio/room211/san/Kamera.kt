package studio.room211.san

import android.content.Context
import android.graphics.ImageFormat
import android.graphics.SurfaceTexture
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CameraMetadata
import android.hardware.camera2.CaptureRequest
import android.hardware.camera2.CaptureResult
import android.hardware.camera2.params.StreamConfigurationMap
import android.media.ImageReader
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.util.Range
import android.util.Size
import android.view.Surface
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Jedan snimak: otvori kameru, snimi, zatvori.
 *
 * Kamera se NE drzi otvorena celu noc. Otvorena greje telefon i trosi bateriju
 * osam sati zbog posla koji traje sekundu, a sa zakljucanom ekspozicijom nema
 * ni sta da se smiruje izmedju dva kadra.
 *
 * `snimi()` blokira nit sa koje je pozvan i ne sme se zvati sa glavne.
 */
class Kamera(private val ctx: Context, private val cfg: Config) {

    data class Sposobnosti(
        val id: String,
        val nivo: String,
        val strana: String = "",
        val rezimi: List<String> = emptyList(),
        val rucnaMoguca: Boolean,
        val ekspozicija: Range<Long>?,
        val iso: Range<Int>?,
        val najblizeM: Float,
        val velicine: List<Size>,
    ) {
        fun opis(): String {
            val e = ekspozicija
            val raspon = if (e == null) "nepoznato" else
                "${vremeTekst(e.lower)} do ${vremeTekst(e.upper)}"
            val najveca = velicine.firstOrNull()
            val nocni = if (rezimi.any { it == "noćni" }) "IMA noćni režim"
            else "nema noćni režim" + if (rezimi.isEmpty()) "" else " (${rezimi.joinToString(", ")})"
            return buildString {
                append("Kamera $id ($strana), nivo $nivo\n")
                append("$nocni\n")
                append("Ekspozicija: $raspon\n")
                append("ISO: ${iso?.lower ?: "?"} do ${iso?.upper ?: "?"}\n")
                append("Najveca slika: ${najveca?.width ?: 0}x${najveca?.height ?: 0}\n")
                append(if (rucnaMoguca) "Rucna ekspozicija: podrzana" else
                    "Rucna ekspozicija: NIJE podrzana, radi se automatika")
            }
        }

        companion object {
            fun vremeTekst(ns: Long): String {
                val s = ns / 1_000_000_000.0
                return if (s >= 1) "%.1fs".format(s) else "1/${(1 / s).toInt()}s"
            }
        }
    }

    private val manager = ctx.getSystemService(Context.CAMERA_SERVICE) as CameraManager

    fun idKamere(): String? {
        // Izabrana kamera ima prednost; ako je nema (druga aplikacija, druga
        // verzija sistema), pada se na izbor po strani umesto na gresku.
        val izabrana = cfg.kameraId
        if (izabrana.isNotBlank() && manager.cameraIdList.contains(izabrana)) return izabrana

        val trazeno = if (cfg.prednjaKamera) CameraCharacteristics.LENS_FACING_FRONT
        else CameraCharacteristics.LENS_FACING_BACK
        return manager.cameraIdList.firstOrNull {
            manager.getCameraCharacteristics(it).get(CameraCharacteristics.LENS_FACING) == trazeno
        } ?: manager.cameraIdList.firstOrNull()
    }

    /** Sve kamere telefona, sa onim po cemu se ovde razlikuju. */
    fun sveKamere(): List<Sposobnosti> = manager.cameraIdList.mapNotNull { id ->
        try { sposobnosti(id) } catch (e: Exception) { null }
    }

    fun sposobnosti(): Sposobnosti? {
        val id = idKamere() ?: return null
        return sposobnosti(id)
    }

    fun sposobnosti(id: String): Sposobnosti? {
        val c = manager.getCameraCharacteristics(id)
        val nivo = when (c.get(CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL)) {
            CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_LEGACY -> "LEGACY"
            CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_LIMITED -> "LIMITED"
            CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_FULL -> "FULL"
            CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_3 -> "LEVEL_3"
            else -> "EXTERNAL"
        }
        val mogucnosti = c.get(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES) ?: IntArray(0)
        val mapa = c.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
        val strana = when (c.get(CameraCharacteristics.LENS_FACING)) {
            CameraCharacteristics.LENS_FACING_FRONT -> "prednja"
            CameraCharacteristics.LENS_FACING_BACK -> "zadnja"
            else -> "spoljna"
        }
        return Sposobnosti(
            id = id,
            nivo = nivo,
            strana = strana,
            rezimi = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
                NocniRezim.imenaRezima(manager, id) else emptyList(),
            rucnaMoguca = mogucnosti.contains(
                CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_MANUAL_SENSOR
            ),
            ekspozicija = c.get(CameraCharacteristics.SENSOR_INFO_EXPOSURE_TIME_RANGE),
            iso = c.get(CameraCharacteristics.SENSOR_INFO_SENSITIVITY_RANGE),
            najblizeM = c.get(CameraCharacteristics.LENS_INFO_MINIMUM_FOCUS_DISTANCE) ?: 0f,
            velicine = velicine(mapa),
        )
    }

    private fun velicine(mapa: StreamConfigurationMap?): List<Size> =
        mapa?.getOutputSizes(ImageFormat.JPEG)?.sortedByDescending { it.width.toLong() * it.height }
            ?: emptyList()

    /** Najveca velicina koja staje u izabranu granicu. */
    private fun izabranaVelicina(sve: List<Size>): Size? {
        if (sve.isEmpty()) return null
        val granica = when (cfg.rezolucija) {
            "2mp" -> 2_500_000L
            "8mp" -> 8_500_000L
            else -> Long.MAX_VALUE
        }
        return sve.firstOrNull { it.width.toLong() * it.height <= granica } ?: sve.last()
    }

    /**
     * `ekspozicijaNs` i `iso` su ono sto je senzor STVARNO upotrebio, procitano
     * iz `CaptureResult`, a ne ono sto je trazeno.
     *
     * Bez te razlike se ne moze videti da telefon odbija traizenu vrednost:
     * granice ekspozicije se razlikuju od modela do modela, pa se svaki
     * trazeni broj iznad granice tiho stegne na istu vrednost, i izgleda kao
     * da podesavanje ne radi.
     */
    class Rezultat(
        val bajtovi: ByteArray?,
        val greska: String,
        val ekspozicijaNs: Long = 0,
        val iso: Int = 0,
        val rucnoPrimenjeno: Boolean = false,
        val nocni: Boolean = false,
        val slozeno: Int = 0,
    )

    /** Da li telefon nudi svoj nocni rezim na izabranoj kameri. */
    fun nocniDostupan(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return false
        val id = idKamere() ?: return false
        return NocniRezim.dostupan(manager, id)
    }

    fun nocniRezimi(): List<String> {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return emptyList()
        val id = idKamere() ?: return emptyList()
        return NocniRezim.imenaRezima(manager, id)
    }

    /** Najduza ekspozicija koju izabrana kamera uopste daje, u nanosekundama. */
    fun najduzaEkspozicija(): Long =
        sposobnosti()?.ekspozicija?.upper ?: 0L

    fun najveciIso(): Int = sposobnosti()?.iso?.upper ?: 0

    fun snimi(): Rezultat {
        val id = idKamere() ?: return Rezultat(null, "nema kamere")
        val karakteristike = manager.getCameraCharacteristics(id)

        // Nocni rezim ide svojim putem: druga vrsta sesije, drugi spisak
        // velicina, i bez ijedne rucne vrednosti. Kad ga nema, pada se na
        // obican snimak umesto da se pravimo da radi.
        //
        // Dva puta do istog proizvodjacevog koda, i nisu isti spisak: na S23
        // platformski API ne prijavljuje nocni rezim ni na jednoj kameri, a
        // CameraX razgovara sa OEM bibliotekom direktno. Prvo se proba lakse.
        if (cfg.nocniRezim) {
            val ugao = karakteristike.get(CameraCharacteristics.SENSOR_ORIENTATION) ?: 90
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                NocniRezim.dostupan(manager, id)
            ) {
                // platformski put, ispod
            } else {
                val prekoX = NocniX.snimi(ctx, id, ugao)
                if (prekoX.bajtovi != null) return prekoX
                val rezerva = obicanSnimak(id, karakteristike)
                return Rezultat(
                    rezerva.bajtovi,
                    if (rezerva.bajtovi != null)
                        "nema noćnog režima (${prekoX.greska}), snimljeno obično"
                    else rezerva.greska,
                    rezerva.ekspozicijaNs, rezerva.iso, rezerva.rucnoPrimenjeno,
                )
            }
        }

        if (cfg.nocniRezim && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            NocniRezim.dostupan(manager, id)
        ) {
            val velicine = NocniRezim.velicine(manager, id)
            val velicina = izabranaVelicina(velicine)
            if (velicina != null) {
                val ishod = NocniRezim.snimi(
                    ctx, manager, id, velicina, cfg.kvalitet,
                    karakteristike.get(CameraCharacteristics.SENSOR_ORIENTATION) ?: 90,
                )
                if (ishod.bajtovi != null) return ishod
                // Nocni snimak nije uspeo: obican je bolji od nijednog, ali se
                // razlog prenosi dalje da se vidi da nocni ne radi.
                val rezerva = obicanSnimak(id, karakteristike)
                return Rezultat(
                    rezerva.bajtovi,
                    if (rezerva.bajtovi != null) "noćni režim nije uspeo (${ishod.greska}), snimljeno obično"
                    else rezerva.greska,
                    rezerva.ekspozicijaNs, rezerva.iso, rezerva.rucnoPrimenjeno,
                )
            }
        }
        if (cfg.slaganje) {
            val kadrovi = ArrayList<ByteArray>()
            var greska = ""
            repeat(cfg.kadrovaZaSlaganje) {
                val jedan = obicanSnimak(id, karakteristike)
                if (jedan.bajtovi != null) kadrovi.add(jedan.bajtovi) else greska = jedan.greska
            }
            val spojeno = Slaganje.spoji(kadrovi, cfg.kvalitet, cfg.autoPojacanje)
            if (spojeno != null) {
                return Rezultat(
                    spojeno,
                    if (kadrovi.size < cfg.kadrovaZaSlaganje)
                        "složeno ${kadrovi.size} od ${cfg.kadrovaZaSlaganje} kadrova ($greska)" else "",
                    cfg.ekspozicijaNs * kadrovi.size, cfg.iso, true, slozeno = kadrovi.size,
                )
            }
            return Rezultat(null, greska.ifBlank { "slaganje nije uspelo" })
        }

        return obicanSnimak(id, karakteristike)
    }

    private fun obicanSnimak(id: String, karakteristike: CameraCharacteristics): Rezultat {
        val velicina = izabranaVelicina(velicine(karakteristike.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)))
            ?: return Rezultat(null, "kamera ne nudi JPEG")


        val nit = HandlerThread("kamera").apply { start() }
        val ruka = Handler(nit.looper)
        val citac = ImageReader.newInstance(velicina.width, velicina.height, ImageFormat.JPEG, 2)

        // Lazna povrsina za zagrevanje. Bez nje jedan jedini snimak stize pre
        // nego sto se automatika smiri, pa je kadar ili crn ili prosvetljen.
        val tekstura = SurfaceTexture(0).apply { setDefaultBufferSize(640, 480) }
        val laznaPovrsina = Surface(tekstura)

        var bajtovi: ByteArray? = null
        var greska = ""
        var stvarnaEkspozicija = 0L
        var stvarniIso = 0
        var rucnoPrimenjeno = false
        val gotovo = CountDownLatch(1)

        citac.setOnImageAvailableListener({ c ->
            try {
                c.acquireNextImage()?.use { slika ->
                    val bafer = slika.planes[0].buffer
                    bajtovi = ByteArray(bafer.remaining()).also { bafer.get(it) }
                }
            } catch (e: Exception) {
                greska = "citanje: ${e.message}"
            }
            gotovo.countDown()
        }, ruka)

        var uredjaj: CameraDevice? = null
        try {
            val otvoren = CountDownLatch(1)
            manager.openCamera(id, object : CameraDevice.StateCallback() {
                override fun onOpened(kamera: CameraDevice) {
                    uredjaj = kamera; otvoren.countDown()
                }

                override fun onDisconnected(kamera: CameraDevice) {
                    greska = "kameru je preuzela druga aplikacija"
                    kamera.close(); otvoren.countDown(); gotovo.countDown()
                }

                override fun onError(kamera: CameraDevice, kod: Int) {
                    greska = "kamera, greska $kod"
                    kamera.close(); otvoren.countDown(); gotovo.countDown()
                }
            }, ruka)

            if (!otvoren.await(6, TimeUnit.SECONDS) || uredjaj == null) {
                return Rezultat(null, greska.ifBlank { "kamera se ne otvara" })
            }

            val kamera = uredjaj!!
            val sesija = CountDownLatch(1)
            var sesijaRef: CameraCaptureSession? = null

            @Suppress("DEPRECATION")
            kamera.createCaptureSession(
                listOf(laznaPovrsina, citac.surface),
                object : CameraCaptureSession.StateCallback() {
                    override fun onConfigured(s: CameraCaptureSession) {
                        sesijaRef = s; sesija.countDown()
                    }

                    override fun onConfigureFailed(s: CameraCaptureSession) {
                        greska = "sesija nije prihvacena"; sesija.countDown()
                    }
                },
                ruka,
            )

            if (!sesija.await(6, TimeUnit.SECONDS) || sesijaRef == null) {
                return Rezultat(null, greska.ifBlank { "sesija se ne otvara" })
            }
            val s = sesijaRef!!

            val zagrevanje = kamera.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
                .apply { addTarget(laznaPovrsina); podesi(this, karakteristike) }
            s.setRepeatingRequest(zagrevanje.build(), null, ruka)
            // Automatika trazi vise kadrova da se smiri; zakljucane vrednosti
            // ne traze nista osim da senzor krene.
            Thread.sleep(if (cfg.rucnaEkspozicija) 350 else 1200)
            s.stopRepeating()

            val snimak = kamera.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE).apply {
                addTarget(citac.surface)
                podesi(this, karakteristike)
                set(CaptureRequest.JPEG_QUALITY, cfg.kvalitet.toByte())
                set(CaptureRequest.JPEG_ORIENTATION,
                    (karakteristike.get(CameraCharacteristics.SENSOR_ORIENTATION) ?: 90))
            }
            val ocitano = CountDownLatch(1)
            s.capture(snimak.build(), object : CameraCaptureSession.CaptureCallback() {
                override fun onCaptureCompleted(
                    sesija: CameraCaptureSession,
                    zahtev: CaptureRequest,
                    rezultat: android.hardware.camera2.TotalCaptureResult,
                ) {
                    stvarnaEkspozicija = rezultat.get(CaptureResult.SENSOR_EXPOSURE_TIME) ?: 0L
                    stvarniIso = rezultat.get(CaptureResult.SENSOR_SENSITIVITY) ?: 0
                    rucnoPrimenjeno =
                        rezultat.get(CaptureResult.CONTROL_AE_MODE) == CameraMetadata.CONTROL_AE_MODE_OFF
                    ocitano.countDown()
                }

                override fun onCaptureFailed(
                    sesija: CameraCaptureSession,
                    zahtev: CaptureRequest,
                    razlog: android.hardware.camera2.CaptureFailure,
                ) {
                    greska = "snimak odbijen, razlog ${razlog.reason}"
                    ocitano.countDown()
                    gotovo.countDown()
                }
            }, ruka)

            if (!gotovo.await(20, TimeUnit.SECONDS)) greska = "snimak nije stigao"
            ocitano.await(2, TimeUnit.SECONDS)
            s.close()
        } catch (e: SecurityException) {
            greska = "nema dozvole za kameru"
        } catch (e: Exception) {
            greska = e.message ?: e.javaClass.simpleName
        } finally {
            try { uredjaj?.close() } catch (e: Exception) {}
            try { citac.close() } catch (e: Exception) {}
            laznaPovrsina.release()
            tekstura.release()
            nit.quitSafely()
        }

        return Rezultat(
            bajtovi,
            if (bajtovi == null && greska.isBlank()) "prazan snimak" else greska,
            stvarnaEkspozicija, stvarniIso, rucnoPrimenjeno,
        )
    }

    /**
     * Zakljucavanje ekspozicije i fokusa.
     *
     * Kad telefon ne podrzava rucni senzor, ostaje automatika: bolje losije
     * uporediva slika nego nijedna. Fokus se zakljucava i u automatskom rezimu,
     * jer autofokus u mraku lovi i vrati mutan kadar bez ijedne greske.
     */
    private fun podesi(zahtev: CaptureRequest.Builder, c: CameraCharacteristics) {
        val mogucnosti = c.get(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES) ?: IntArray(0)
        val rucnoMoguce = mogucnosti.contains(
            CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_MANUAL_SENSOR
        )

        // Rucno unete vrednosti imaju prednost; posle njih dolaze one
        // zakljucane iz prve slike; ako nema ni jednih, radi automatika.
        val trazenaEkspozicija = when {
            cfg.rucnaEkspozicija -> cfg.ekspozicijaNs
            cfg.zakljucanaEkspozicijaNs > 0 -> cfg.zakljucanaEkspozicijaNs
            else -> 0L
        }
        val trazeniIso = when {
            cfg.rucnaEkspozicija -> cfg.iso
            cfg.zakljucaniIso > 0 -> cfg.zakljucaniIso
            else -> 0
        }

        if (trazenaEkspozicija > 0 && rucnoMoguce) {
            val raspon = c.get(CameraCharacteristics.SENSOR_INFO_EXPOSURE_TIME_RANGE)
            val isoRaspon = c.get(CameraCharacteristics.SENSOR_INFO_SENSITIVITY_RANGE)
            // Trazena vrednost izvan prijavljenog raspona je dozvoljena: radi
            // na nekim uredjajima, a `CaptureResult` posle kaze da li je
            // stvarno prosla. Na Samsungu vredi probati, jer njihova Kamera ide
            // do 30 sekundi dok Camera2 prijavljuje 1/6.
            val vreme = if (cfg.prekoGranice) trazenaEkspozicija
            else raspon?.clamp(trazenaEkspozicija) ?: trazenaEkspozicija
            val osetljivost = isoRaspon?.clamp(trazeniIso) ?: trazeniIso

            // Scene rezim i HDR umeju da nadglasaju rucne vrednosti: telefon
            // primi zahtev, ne prijavi gresku, i snimi po svome.
            zahtev.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO)
            zahtev.set(CaptureRequest.CONTROL_SCENE_MODE, CameraMetadata.CONTROL_SCENE_MODE_DISABLED)
            zahtev.set(CaptureRequest.CONTROL_AE_MODE, CameraMetadata.CONTROL_AE_MODE_OFF)
            zahtev.set(CaptureRequest.CONTROL_AE_LOCK, false)
            zahtev.set(CaptureRequest.SENSOR_EXPOSURE_TIME, vreme)
            zahtev.set(CaptureRequest.SENSOR_SENSITIVITY, osetljivost)

            // Duzina kadra ne sme biti kraca od ekspozicije, inace je senzor
            // preseca. Uzima se veca od dve, uz gornju granicu uredjaja.
            val najduzi = c.get(CameraCharacteristics.SENSOR_INFO_MAX_FRAME_DURATION) ?: vreme
            zahtev.set(CaptureRequest.SENSOR_FRAME_DURATION, minOf(maxOf(vreme, 33_333_333L), najduzi))
        } else {
            zahtev.set(CaptureRequest.CONTROL_AE_MODE, CameraMetadata.CONTROL_AE_MODE_ON)
        }

        val najblize = c.get(CameraCharacteristics.LENS_INFO_MINIMUM_FOCUS_DISTANCE) ?: 0f
        if (najblize > 0f) {
            zahtev.set(CaptureRequest.CONTROL_AF_MODE, CameraMetadata.CONTROL_AF_MODE_OFF)
            zahtev.set(CaptureRequest.LENS_FOCUS_DISTANCE, (1f / cfg.rastojanjeM).coerceIn(0f, najblize))
        }
        zahtev.set(CaptureRequest.CONTROL_AWB_MODE, CameraMetadata.CONTROL_AWB_MODE_AUTO)
        zahtev.set(CaptureRequest.CONTROL_AE_ANTIBANDING_MODE,
            CameraMetadata.CONTROL_AE_ANTIBANDING_MODE_AUTO)
    }
}
