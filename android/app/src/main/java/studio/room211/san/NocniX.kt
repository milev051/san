package studio.room211.san

import android.content.Context
import android.graphics.SurfaceTexture
import android.os.Handler
import android.os.Looper
import android.view.Surface
import androidx.camera.camera2.interop.Camera2CameraInfo
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.extensions.ExtensionMode
import androidx.camera.extensions.ExtensionsManager
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executor
import java.util.concurrent.TimeUnit

/**
 * Nocni rezim proizvodjaca, drugim putem: preko CameraX prosirenja.
 *
 * Postoji zato sto je platformski `CameraExtensionCharacteristics` na Samsung
 * S23 prijavio samo automatski, lice i bokeh, dakle bez nocnog rezima, a
 * Samsungova Kamera ga ocigledno ima. CameraX ne gleda isti spisak: ono
 * razgovara direktno sa OEM bibliotekom prosirenja na uredjaju, i ume sa
 * starijim izdanjem te biblioteke koje platforma ne izlaze.
 *
 * Ako ni ovuda nema nocnog rezima, znaci da ga proizvodjac drzi u svojoj
 * aplikaciji i nijednim API-jem ga ne daje. Tada ostaje `Slaganje`.
 *
 * Ceo posao ide sa GLAVNE niti: `LifecycleRegistry` i `bindToLifecycle` se ne
 * smeju dirati ni sa jedne druge, a pozivalac ceka na bravi.
 */
object NocniX {

    /** Zivotni ciklus koji traje koliko i jedan snimak. */
    private class KratkiZivot : LifecycleOwner {
        private val registar = LifecycleRegistry(this)
        override val lifecycle: Lifecycle get() = registar
        fun pokreni() { registar.currentState = Lifecycle.State.RESUMED }
        fun ugasi() { registar.currentState = Lifecycle.State.DESTROYED }
    }

    private fun izborKamere(id: String?): CameraSelector {
        if (id.isNullOrBlank()) return CameraSelector.DEFAULT_BACK_CAMERA
        return CameraSelector.Builder().addCameraFilter { podaci ->
            podaci.filter { Camera2CameraInfo.from(it).cameraId == id }
        }.build()
    }

    /** Da li ta kamera nudi nocni rezim kroz CameraX. */
    fun dostupan(ctx: Context, id: String?): Boolean {
        val odgovor = CountDownLatch(1)
        var ima = false
        Handler(Looper.getMainLooper()).post {
            try {
                val provajder = ProcessCameraProvider.getInstance(ctx).get(5, TimeUnit.SECONDS)
                val prosirenja = ExtensionsManager.getInstanceAsync(ctx, provajder)
                    .get(5, TimeUnit.SECONDS)
                ima = prosirenja.isExtensionAvailable(izborKamere(id), ExtensionMode.NIGHT)
            } catch (e: Exception) {
                ima = false
            }
            odgovor.countDown()
        }
        odgovor.await(12, TimeUnit.SECONDS)
        return ima
    }

    fun snimi(ctx: Context, id: String?, ugao: Int): Kamera.Rezultat {
        val gotovo = CountDownLatch(1)
        var bajtovi: ByteArray? = null
        var greska = ""
        val glavna = Handler(Looper.getMainLooper())
        val izvrsilac = Executor { glavna.post(it) }
        val zivot = KratkiZivot()
        var provajder: ProcessCameraProvider? = null
        var tekstura: SurfaceTexture? = null
        var povrsina: Surface? = null

        glavna.post {
            try {
                val p = ProcessCameraProvider.getInstance(ctx).get(6, TimeUnit.SECONDS)
                provajder = p
                val prosirenja = ExtensionsManager.getInstanceAsync(ctx, p).get(6, TimeUnit.SECONDS)
                val osnovni = izborKamere(id)
                if (!prosirenja.isExtensionAvailable(osnovni, ExtensionMode.NIGHT)) {
                    greska = "CameraX ne nudi noćni režim na toj kameri"
                    gotovo.countDown()
                    return@post
                }
                val nocni = prosirenja.getExtensionEnabledCameraSelector(osnovni, ExtensionMode.NIGHT)

                // Pregled se vezuje iako se ne prikazuje: deo proizvodjaceve
                // obrade radi nad tokom pregleda, a bez njega neki uredjaji
                // odbiju snimak.
                val pregled = Preview.Builder().build()
                pregled.setSurfaceProvider(izvrsilac) { zahtev ->
                    val t = SurfaceTexture(0).apply {
                        setDefaultBufferSize(zahtev.resolution.width, zahtev.resolution.height)
                    }
                    val s = Surface(t)
                    tekstura = t
                    povrsina = s
                    zahtev.provideSurface(s, izvrsilac) { }
                }

                val snimac = ImageCapture.Builder()
                    .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
                    // Odredjeno, ne po polozaju ekrana: telefon visi na zidu i
                    // snima ugasenog ekrana, pa se na polozaj ne moze osloniti.
                    .setTargetRotation(Surface.ROTATION_0)
                    .build()

                p.unbindAll()
                zivot.pokreni()
                p.bindToLifecycle(zivot, nocni, pregled, snimac)

                snimac.takePicture(izvrsilac, object : ImageCapture.OnImageCapturedCallback() {
                    override fun onCaptureSuccess(slika: ImageProxy) {
                        try {
                            val bafer = slika.planes[0].buffer
                            bajtovi = ByteArray(bafer.remaining()).also { bafer.get(it) }
                        } catch (e: Exception) {
                            greska = "čitanje: ${e.message}"
                        } finally {
                            slika.close()
                            gotovo.countDown()
                        }
                    }

                    override fun onError(izuzetak: ImageCaptureException) {
                        greska = "CameraX: ${izuzetak.message}"
                        gotovo.countDown()
                    }
                })
            } catch (e: Exception) {
                greska = e.message ?: e.javaClass.simpleName
                gotovo.countDown()
            }
        }

        // Nocni rezim slaze vise kadrova i traje sekundama.
        if (!gotovo.await(60, TimeUnit.SECONDS)) greska = "noćni snimak nije stigao"

        val pospremljeno = CountDownLatch(1)
        glavna.post {
            try { provajder?.unbindAll() } catch (e: Exception) {}
            try { zivot.ugasi() } catch (e: Exception) {}
            try { povrsina?.release() } catch (e: Exception) {}
            try { tekstura?.release() } catch (e: Exception) {}
            pospremljeno.countDown()
        }
        pospremljeno.await(5, TimeUnit.SECONDS)

        return Kamera.Rezultat(
            bajtovi,
            if (bajtovi == null && greska.isBlank()) "prazan noćni snimak" else greska,
            nocni = bajtovi != null,
        )
    }
}
