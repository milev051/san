package studio.room211.san

import android.content.Context
import android.graphics.ImageFormat
import android.graphics.SurfaceTexture
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraExtensionCharacteristics
import android.hardware.camera2.CameraExtensionSession
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CaptureRequest
import android.hardware.camera2.params.ExtensionSessionConfiguration
import android.hardware.camera2.params.OutputConfiguration
import android.media.ImageReader
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.util.Size
import android.view.Surface
import androidx.annotation.RequiresApi
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executor
import java.util.concurrent.TimeUnit

/**
 * Nocni rezim samog telefona, preko Camera2 Extensions.
 *
 * Ovo je isti postupak koji radi Samsungova aplikacija kad u mraku sacekas par
 * sekundi: senzor snimi vise kadrova razlicite duzine i proizvodjacev kod ih
 * spoji u jednu svetliju sliku sa manje suma. Ne pisemo mi to slaganje, nego
 * ga trazimo od telefona.
 *
 * Postoji od Androida 12 i samo na uredjajima koji ga prijave. Ako ga nema,
 * pozivalac se vraca na obican snimak; nema pretvaranja da radi.
 *
 * **Rucne vrednosti ovde ne postoje.** U prosirenoj sesiji su dozvoljeni samo
 * kljucevi koje uredjaj prijavi, a `SENSOR_EXPOSURE_TIME` nije medju njima:
 * ekspoziciju bira proizvodjacev kod. Zato su nocni rezim i rucna ekspozicija
 * dva odvojena izbora, ne dva prekidaca koja se sabiraju.
 */
@RequiresApi(Build.VERSION_CODES.S)
object NocniRezim {

    /** Da li telefon uopste nudi nocni rezim na toj kameri. */
    fun dostupan(manager: CameraManager, id: String): Boolean = try {
        manager.getCameraExtensionCharacteristics(id)
            .supportedExtensions.contains(CameraExtensionCharacteristics.EXTENSION_NIGHT)
    } catch (e: Exception) {
        false
    }

    fun imenaRezima(manager: CameraManager, id: String): List<String> = try {
        manager.getCameraExtensionCharacteristics(id).supportedExtensions.map {
            when (it) {
                CameraExtensionCharacteristics.EXTENSION_NIGHT -> "noćni"
                CameraExtensionCharacteristics.EXTENSION_HDR -> "HDR"
                CameraExtensionCharacteristics.EXTENSION_BOKEH -> "bokeh"
                CameraExtensionCharacteristics.EXTENSION_FACE_RETOUCH -> "lice"
                CameraExtensionCharacteristics.EXTENSION_AUTOMATIC -> "automatski"
                else -> "režim $it"
            }
        }
    } catch (e: Exception) {
        emptyList()
    }

    /**
     * Velicine koje prosireni rezim daje.
     *
     * Ne uzimaju se iz `SCALER_STREAM_CONFIGURATION_MAP`: prosirenja podrzavaju
     * svoj, uzi spisak, i velicina van njega rusi konfiguraciju sesije.
     */
    fun velicine(manager: CameraManager, id: String): List<Size> = try {
        manager.getCameraExtensionCharacteristics(id)
            .getExtensionSupportedSizes(CameraExtensionCharacteristics.EXTENSION_NIGHT, ImageFormat.JPEG)
            .sortedByDescending { it.width.toLong() * it.height }
    } catch (e: Exception) {
        emptyList()
    }

    fun snimi(
        ctx: Context,
        manager: CameraManager,
        id: String,
        velicina: Size,
        kvalitet: Int,
        ugao: Int,
    ): Kamera.Rezultat {
        val nit = HandlerThread("nocni").apply { start() }
        val ruka = Handler(nit.looper)
        val izvrsilac = Executor { it.run() }
        val karakteristike = manager.getCameraExtensionCharacteristics(id)
        val dozvoljeni = try {
            karakteristike.getAvailableCaptureRequestKeys(CameraExtensionCharacteristics.EXTENSION_NIGHT)
        } catch (e: Exception) {
            emptyList<CaptureRequest.Key<*>>()
        }

        val citac = ImageReader.newInstance(velicina.width, velicina.height, ImageFormat.JPEG, 2)
        val pregledVelicina = try {
            karakteristike.getExtensionSupportedSizes(
                CameraExtensionCharacteristics.EXTENSION_NIGHT, SurfaceTexture::class.java
            ).minByOrNull { it.width.toLong() * it.height } ?: Size(640, 480)
        } catch (e: Exception) {
            Size(640, 480)
        }
        val tekstura = SurfaceTexture(0).apply {
            setDefaultBufferSize(pregledVelicina.width, pregledVelicina.height)
        }
        val pregled = Surface(tekstura)

        var bajtovi: ByteArray? = null
        var greska = ""
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
        var sesija: CameraExtensionSession? = null
        try {
            val otvoren = CountDownLatch(1)
            manager.openCamera(id, object : CameraDevice.StateCallback() {
                override fun onOpened(kamera: CameraDevice) { uredjaj = kamera; otvoren.countDown() }
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
                return Kamera.Rezultat(null, greska.ifBlank { "kamera se ne otvara" })
            }

            val kamera = uredjaj!!
            val spremna = CountDownLatch(1)
            kamera.createExtensionSession(
                ExtensionSessionConfiguration(
                    CameraExtensionCharacteristics.EXTENSION_NIGHT,
                    listOf(OutputConfiguration(pregled), OutputConfiguration(citac.surface)),
                    izvrsilac,
                    object : CameraExtensionSession.StateCallback() {
                        override fun onConfigured(s: CameraExtensionSession) {
                            sesija = s; spremna.countDown()
                        }
                        override fun onConfigureFailed(s: CameraExtensionSession) {
                            greska = "telefon ne prihvata noćni režim sa ovim izlazima"
                            spremna.countDown()
                        }
                        override fun onClosed(s: CameraExtensionSession) {}
                    },
                )
            )
            if (!spremna.await(8, TimeUnit.SECONDS) || sesija == null) {
                return Kamera.Rezultat(null, greska.ifBlank { "noćna sesija se ne otvara" })
            }
            val s = sesija!!

            // Prosirenja traze zivi pregled pre snimka: bez ponavljajuceg
            // zahteva proizvodjacev kod nema iz cega da slaze kadrove.
            val zaPregled = kamera.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
                .apply { addTarget(pregled) }
            s.setRepeatingRequest(zaPregled.build(), izvrsilac,
                object : CameraExtensionSession.ExtensionCaptureCallback() {})
            Thread.sleep(600)

            val zahtev = kamera.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE).apply {
                addTarget(citac.surface)
                // Kljuc koji uredjaj nije prijavio rusi ceo snimak izuzetkom,
                // pa se postavlja samo ono sto je dozvoljeno.
                if (dozvoljeni.contains(CaptureRequest.JPEG_QUALITY)) {
                    set(CaptureRequest.JPEG_QUALITY, kvalitet.toByte())
                }
                if (dozvoljeni.contains(CaptureRequest.JPEG_ORIENTATION)) {
                    set(CaptureRequest.JPEG_ORIENTATION, ugao)
                }
            }
            s.capture(zahtev.build(), izvrsilac, object : CameraExtensionSession.ExtensionCaptureCallback() {
                override fun onCaptureFailed(sesija: CameraExtensionSession, zahtev: CaptureRequest) {
                    greska = "noćni snimak odbijen"
                    gotovo.countDown()
                }
            })

            // Nocni rezim spaja vise kadrova i traje sekundama, ne milisekundama.
            if (!gotovo.await(45, TimeUnit.SECONDS)) greska = "noćni snimak nije stigao"
            try { s.stopRepeating() } catch (e: Exception) {}
            try { s.close() } catch (e: Exception) {}
        } catch (e: SecurityException) {
            greska = "nema dozvole za kameru"
        } catch (e: Exception) {
            greska = e.message ?: e.javaClass.simpleName
        } finally {
            try { uredjaj?.close() } catch (e: Exception) {}
            try { citac.close() } catch (e: Exception) {}
            pregled.release()
            tekstura.release()
            nit.quitSafely()
        }

        return Kamera.Rezultat(
            bajtovi,
            if (bajtovi == null && greska.isBlank()) "prazan noćni snimak" else greska,
            nocni = true,
        )
    }
}
