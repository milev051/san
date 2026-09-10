package studio.room211.san

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.concurrent.Executors

/**
 * Servis koji drzi noc.
 *
 * Tri stvari drze ovaj posao zivim dok telefon spava, i sve tri su obavezne:
 * servis u prvom planu tipa `camera` (bez njega Android 14 ne da kameru),
 * `PARTIAL_WAKE_LOCK` (bez njega procesor stane izmedju dva okidanja), i
 * `setExactAndAllowWhileIdle` (bez njega Doze rezim pomeri okidanje po
 * desetak minuta, tiho).
 *
 * Alarm gadja OVAJ servis preko `getForegroundService`. Ranija zamisao je bila
 * prijemnik koji pali servis, ali od Androida 12 pokretanje servisa u prvom
 * planu iz pozadine nije dozvoljeno; ovako se samo budi servis koji vec radi.
 */
class Snimanje : Service() {

    companion object {
        const val KANAL = "san_snimanje"
        const val OZNAKA = 1

        const val POCNI = "studio.room211.san.POCNI"
        const val KADAR = "studio.room211.san.KADAR"
        const val STANI = "studio.room211.san.STANI"

        fun pokreni(ctx: Context) {
            val namera = Intent(ctx, Snimanje::class.java).setAction(POCNI)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) ctx.startForegroundService(namera)
            else ctx.startService(namera)
        }

        fun zaustavi(ctx: Context) {
            ctx.startService(Intent(ctx, Snimanje::class.java).setAction(STANI))
        }
    }

    private lateinit var cfg: Config
    private val posao = Executors.newSingleThreadExecutor()
    private var budnica: PowerManager.WakeLock? = null
    private var nocDatum: LocalDate = LocalDate.now()

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        cfg = Config(this)
        napraviKanal()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Prvo obavestenje, pa tek onda posao: sistem ubija servis koji se ne
        // prijavi u prvih pet sekundi, i to izgleda kao da kamera ne radi.
        if (!prijaviSe(tekstStanja())) {
            // Servis je bio ubijen pa ga budi alarm, a sistem vise ne da
            // pokretanje u prvom planu. Pad ovde bi u tri ujutru izgledao isto
            // kao da kamera ne radi, pa se umesto toga zapisuje razlog.
            cfg.aktivno = false
            cfg.razlogPrekida = "sistem nije dozvolio servis u prvom planu"
            stopSelf()
            return START_NOT_STICKY
        }

        when (intent?.action) {
            POCNI -> pocni()
            KADAR -> kadar()
            STANI -> stani("rucno")
            else -> if (!cfg.aktivno) stopSelf()
        }
        return START_STICKY
    }

    // ------------------------------------------------------------------ tok

    private fun pocni() {
        val sada = System.currentTimeMillis()
        cfg.razlogPrekida = ""
        cfg.aktivno = true
        cfg.pocetak = sada
        cfg.brojSlika = 0
        cfg.prethodnaSlicica = null
        cfg.poslednjaGreska = ""
        nocDatum = Raspored.nocDatum(sada)
        drziBudnim(true)
        // Prva slika ide odmah, ne na sledecu tacku mreze. Sa razmakom od sat
        // vremena bi se inace do prve potvrde cekalo sat vremena, a poenta
        // potvrde je da se cuje dok jos stojis pored telefona.
        posao.execute { snimiJedan(sada) }
    }

    private fun kadar() {
        if (!cfg.aktivno) { stopSelf(); return }
        posao.execute { snimiJedan(System.currentTimeMillis()) }
    }

    private fun snimiJedan(vreme: Long) {
        if (!cfg.aktivno) return

        if (Zapis.slobodno() < Config.NAJMANJE_MESTA) {
            cfg.poslednjaGreska = "nema mesta na telefonu"
            Zvuk.greska(this, cfg)
            stani("nema mesta")
            return
        }

        if (cfg.autoStopSat in 0..23) {
            val sat = Instant.ofEpochMilli(vreme).atZone(ZoneId.systemDefault()).hour
            val odPocetka = vreme - cfg.pocetak
            // Sat se proverava tek posle dva sata snimanja, da pokretanje u
            // 07:50 sa prekidom u 08:00 ne stane odmah po prvom kadru.
            if (sat == cfg.autoStopSat && odPocetka > 2 * 3600_000L) {
                stani("dosao je zadati sat")
                return
            }
        }

        val redni = cfg.brojSlika + 1
        val ishod = Obrada.jednaSlika(this, cfg, Kamera(this, cfg), vreme, redni, nocDatum)
        val zapisnik = Noc(this, nocDatum.toString())

        if (ishod.greska.isNotBlank()) {
            cfg.poslednjaGreska = ishod.greska
            zapisnik.rupa(vreme, ishod.greska, cfg.razmakMin)
            // Rupa se upisuje i cuje. Tisina bi znacila da je sve u redu.
            Zvuk.greska(this, cfg)
        } else {
            cfg.brojSlika = redni
            cfg.poslednjaGreska = ""
            zapisnik.dodaj(vreme, ishod.ime, ishod.pokret, cfg.razmakMin)
            if (cfg.zvuk && (redni <= 2 || cfg.zvukSvaka)) Zvuk.potvrda(this, cfg)
        }

        // Zapisnik ide i u Documents, uz smanjene kopije, da se ceo folder
        // prebaci na racunar jednim potezom. Ne posle svake slike: to bi bilo
        // 96 upisa preko MediaStore-a bez ikakve koristi.
        if (redni <= 2 || redni % 12 == 0) izveziZapisnik(zapisnik)

        zakaziSledeci()
        prijaviSe(tekstStanja())
    }

    private fun izveziZapisnik(zapisnik: Noc) {
        Zapis.sacuvajTekst(
            this, zapisnik.tekst(),
            Zapis.putanjaAnaliza(Raspored.putanjaNoci(nocDatum)), "noc.json",
        )
    }

    private fun stani(razlog: String) {
        if (cfg.aktivno) {
            cfg.razlogPrekida = razlog
            val zapisnik = Noc(this, nocDatum.toString())
            if (zapisnik.postoji()) izveziZapisnik(zapisnik)
        }
        cfg.aktivno = false
        cfg.sledece = 0
        otkaziAlarm()
        drziBudnim(false)
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    // ---------------------------------------------------------------- alarm

    private fun alarmNamera(): PendingIntent {
        val namera = Intent(this, Snimanje::class.java).setAction(KADAR)
        return PendingIntent.getForegroundService(
            this, 0, namera,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun zakaziSledeci() {
        val kada = Raspored.sledece(System.currentTimeMillis(), cfg.razmakMin)
        cfg.sledece = kada
        val budilnik = getSystemService(Context.ALARM_SERVICE) as AlarmManager
        try {
            budilnik.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, kada, alarmNamera())
        } catch (e: SecurityException) {
            // Nema dozvole za tacan alarm: bolje neprecizno nego nista, i to se
            // vidi na ekranu „Provera".
            budilnik.set(AlarmManager.RTC_WAKEUP, kada, alarmNamera())
            cfg.poslednjaGreska = "nema dozvole za tacan alarm"
        }
    }

    private fun otkaziAlarm() {
        (getSystemService(Context.ALARM_SERVICE) as AlarmManager).cancel(alarmNamera())
    }

    // -------------------------------------------------------------- sitnice

    private fun drziBudnim(drzi: Boolean) {
        if (drzi) {
            if (budnica == null) {
                val struja = getSystemService(Context.POWER_SERVICE) as PowerManager
                budnica = struja.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "san:noc").apply {
                    setReferenceCounted(false)
                    acquire(14 * 3600_000L)   // sigurnosna granica, ne ocekivano trajanje
                }
            }
        } else {
            try { budnica?.release() } catch (e: Exception) {}
            budnica = null
        }
    }

    private fun napraviKanal() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val kanal = NotificationChannel(KANAL, "Snimanje noci", NotificationManager.IMPORTANCE_LOW)
        kanal.setShowBadge(false)
        kanal.setSound(null, null)      // zvuk pusta aplikacija, kad treba i koliko treba
        (getSystemService(NotificationManager::class.java)).createNotificationChannel(kanal)
    }

    private fun tekstStanja(): String {
        if (!cfg.aktivno) return "Zaustavljeno"
        val sledece = cfg.sledece
        val kada = if (sledece > 0) {
            Instant.ofEpochMilli(sledece).atZone(ZoneId.systemDefault()).toLocalTime()
                .withSecond(0).withNano(0).toString()
        } else "uskoro"
        val greska = cfg.poslednjaGreska
        return if (greska.isBlank()) "${cfg.brojSlika} slika, sledeca u $kada"
        else "${cfg.brojSlika} slika, greska: $greska"
    }

    private fun prijaviSe(tekst: String): Boolean {
        val otvori = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val obavestenje: Notification = Notification.Builder(this, KANAL)
            .setContentTitle("San snima")
            .setContentText(tekst)
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .setContentIntent(otvori)
            .setOngoing(true)
            .build()

        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(OZNAKA, obavestenje, ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA)
            } else {
                startForeground(OZNAKA, obavestenje)
            }
            true
        } catch (e: Exception) {
            false
        }
    }

    override fun onDestroy() {
        drziBudnim(false)
        posao.shutdown()
        super.onDestroy()
    }
}
