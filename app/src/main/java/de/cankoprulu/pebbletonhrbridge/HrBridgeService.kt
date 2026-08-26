package de.cankoprulu.pebbletonhrbridge

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds


class HrBridgeService : Service() {


    companion object {

        /*
         * =====================================================
         * Service-Kommandos
         * =====================================================
         */

        const val ACTION_START =
            "de.cankoprulu.pebbletonhrbridge.START_TRANSMISSION"

        const val ACTION_STOP =
            "de.cankoprulu.pebbletonhrbridge.STOP_TRANSMISSION"


        /*
         * =====================================================
         * Foreground Notification
         * =====================================================
         */

        private const val NOTIFICATION_CHANNEL_ID =
            "hr_bridge_service"

        private const val NOTIFICATION_CHANNEL_NAME =
            "Heart Rate Transmission"

        private const val NOTIFICATION_ID =
            1001


        /*
         * =====================================================
         * Keepalive
         * =====================================================
         *
         * Wir schicken maximal einmal pro Sekunde einen
         * Herzfrequenzwert.
         *
         * Wenn keine neuen Pebble-Daten eintreffen, wird der
         * letzte bekannte Wert höchstens 10 Sekunden lang
         * wiederholt.
         */

        private const val KEEPALIVE_INTERVAL_MS =
            1_000L

        private const val KEEP_LAST_VALUE_FOR_MS =
            10_000L


        /*
         * =====================================================
         * Öffentlicher Service-Zustand
         * =====================================================
         */

        private val _running =
            MutableStateFlow(false)

        val running: StateFlow<Boolean> =
            _running.asStateFlow()


        private val _status =
            MutableStateFlow("Ready")

        val status: StateFlow<String> =
            _status.asStateFlow()
    }


    /*
     * =========================================================
     * Coroutine-Scope des Services
     * =========================================================
     */

    private val serviceScope =
        CoroutineScope(
            SupervisorJob() +
                    Dispatchers.Main.immediate
        )


    /*
     * =========================================================
     * BLE Peripheral
     * =========================================================
     */

    private var heartRatePeripheral:
            HeartRateBlePeripheral? = null


    /*
     * Wurde die Übertragung vom Benutzer angefordert?
     */
    @Volatile
    private var startRequested =
        false


    /*
     * Läuft der Service bereits als Foreground Service?
     */
    private var foregroundStarted =
        false


    /*
     * Zeitpunkt, zu dem wir zuletzt einen BPM-Wert an unsere
     * BLE-Schicht übergeben haben.
     *
     * Damit verhindern wir unnötig häufige Keepalive-Sends.
     */
    @Volatile
    private var lastHeartRateSentAt =
        0L


    /*
     * =========================================================
     * Service erzeugen
     * =========================================================
     */

    override fun onCreate() {

        super.onCreate()


        /*
         * Sofort in den Foreground wechseln.
         *
         * Nicht erst auf Bluetooth oder Pebble warten.
         */
        foregroundStarted =
            startForegroundMode()


        if (!foregroundStarted) {

            _running.value =
                false

            _status.value =
                "Could not start foreground service"

            stopSelf()

            return
        }


        /*
         * =====================================================
         * Frische Pebble-Werte beobachten
         * =====================================================
         */

        serviceScope.launch {

            HeartRateState
                .heartRate
                .collect { bpm ->


                    /*
                     * Kein aktuell gültiger Wert.
                     *
                     * WICHTIG:
                     * BLE NICHT stoppen.
                     *
                     * Der Keepalive-Loop entscheidet separat,
                     * ob der letzte bekannte Wert noch jung genug
                     * ist, um kurz weitergesendet zu werden.
                     */
                    if (bpm == null) {

                        if (
                            startRequested &&
                            !_running.value
                        ) {

                            _status.value =
                                "Waiting for Pebble heart rate"
                        }

                        return@collect
                    }


                    /*
                     * BLE läuft bereits.
                     *
                     * Frischen Pebble-Wert sofort weiterreichen.
                     */
                    if (_running.value) {

                        heartRatePeripheral
                            ?.updateHeartRate(
                                bpm
                            )


                        lastHeartRateSentAt =
                            SystemClock.elapsedRealtime()


                        return@collect
                    }


                    /*
                     * Übertragung wurde angefordert, aber BLE
                     * konnte bislang mangels Pulswert noch nicht
                     * gestartet werden.
                     */
                    if (startRequested) {

                        startBridgeIfPossible()
                    }
                }
        }


        /*
         * =====================================================
         * Heart-Rate Keepalive
         * =====================================================
         *
         * Auch wenn derselbe Pulswert von der Pebble mehrfach
         * hintereinander kommt oder kurz keine neue Nachricht
         * eintrifft, senden wir etwa einmal pro Sekunde.
         */

        serviceScope.launch {

            while (isActive) {

                delay(
                    KEEPALIVE_INTERVAL_MS.milliseconds
                )


                sendHeartRateKeepaliveIfNeeded()
            }
        }
    }


    /*
     * =========================================================
     * Kommandos verarbeiten
     * =========================================================
     */

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int
    ): Int {


        if (!foregroundStarted) {

            return START_NOT_STICKY
        }


        when (intent?.action) {


            ACTION_START -> {

                startRequested =
                    true


                startBridgeIfPossible()


                return START_STICKY
            }


            ACTION_STOP -> {

                startRequested =
                    false


                stopBridge()


                stopForegroundService()

                stopSelf()


                return START_NOT_STICKY
            }


            /*
             * START_STICKY Wiederherstellung durch Android.
             */
            null -> {

                startRequested =
                    true


                _status.value =
                    "Restoring transmission..."


                startBridgeIfPossible()


                return START_STICKY
            }


            else -> {

                return START_STICKY
            }
        }
    }


    /*
     * =========================================================
     * BLE starten
     * =========================================================
     */

    private fun startBridgeIfPossible() {


        if (!startRequested) {
            return
        }


        /*
         * Läuft bereits.
         *
         * Bestehenden GATT-Server niemals unnötig neu erstellen.
         */
        if (_running.value) {

            val bpm =
                HeartRateState
                    .heartRate
                    .value
                    ?: HeartRateState
                        .lastKnownHeartRate


            if (bpm != null) {

                heartRatePeripheral
                    ?.updateHeartRate(
                        bpm
                    )


                lastHeartRateSentAt =
                    SystemClock.elapsedRealtime()
            }


            return
        }


        /*
         * Primär aktuellen Wert nehmen.
         *
         * Falls HeartRateState gerade null ist, darf ein sehr
         * frischer letzter Wert ebenfalls als Startwert dienen.
         */
        val initialHeartRate =
            getUsableHeartRate()


        if (initialHeartRate == null) {

            _status.value =
                "Waiting for Pebble heart rate"

            return
        }


        _status.value =
            "Starting BLE heart rate service"


        /*
         * Alte BLE-Instanz vollständig aufräumen.
         */
        heartRatePeripheral
            ?.stop()


        heartRatePeripheral =
            null


        val peripheral =
            HeartRateBlePeripheral(
                applicationContext
            ) { newStatus ->

                handlePeripheralStatus(
                    newStatus
                )
            }


        heartRatePeripheral =
            peripheral


        val started =
            peripheral.start(
                initialHeartRate
            )


        if (!started) {

            _running.value =
                false

            startRequested =
                false


            peripheral.stop()

            heartRatePeripheral =
                null


            stopForegroundService()

            stopSelf()


            return
        }


        /*
         * GATT-Service wurde erfolgreich zur Registrierung
         * an Android übergeben.
         */
        _running.value =
            true


        lastHeartRateSentAt =
            SystemClock.elapsedRealtime()
    }


    /*
     * =========================================================
     * Welcher Herzfrequenzwert ist aktuell verwendbar?
     * =========================================================
     */

    private fun getUsableHeartRate(): Int? {


        /*
         * Frischer aktueller Wert hat Vorrang.
         */
        HeartRateState
            .heartRate
            .value
            ?.let {

                return it
            }


        val lastKnown =
            HeartRateState
                .lastKnownHeartRate
                ?: return null


        val lastRealUpdate =
            HeartRateState
                .lastUpdateElapsedRealtime


        if (lastRealUpdate <= 0L) {
            return null
        }


        val age =
            SystemClock.elapsedRealtime() -
                    lastRealUpdate


        /*
         * Nur einen höchstens 10 Sekunden alten Wert verwenden.
         */
        return if (
            age <=
            KEEP_LAST_VALUE_FOR_MS
        ) {

            lastKnown

        } else {

            null
        }
    }


    /*
     * =========================================================
     * Keepalive senden
     * =========================================================
     */

    private fun sendHeartRateKeepaliveIfNeeded() {


        /*
         * Keine laufende Übertragung.
         */
        if (
            !startRequested ||
            !_running.value
        ) {
            return
        }


        val bpm =
            getUsableHeartRate()
                ?: return


        val now =
            SystemClock.elapsedRealtime()


        /*
         * Nicht öfter als ungefähr einmal pro Sekunde.
         */
        if (
            now -
            lastHeartRateSentAt <
            KEEPALIVE_INTERVAL_MS
        ) {

            return
        }


        heartRatePeripheral
            ?.updateHeartRate(
                bpm
            )


        lastHeartRateSentAt =
            now
    }


    /*
     * =========================================================
     * Status vom BLE Peripheral
     * =========================================================
     */

    private fun handlePeripheralStatus(
        newStatus: String
    ) {


        _status.value =
            newStatus


        if (
            isTerminalPeripheralError(
                newStatus
            )
        ) {

            serviceScope.launch {

                if (
                    !_running.value &&
                    heartRatePeripheral == null
                ) {

                    return@launch
                }


                startRequested =
                    false

                _running.value =
                    false


                heartRatePeripheral
                    ?.stop()


                heartRatePeripheral =
                    null


                lastHeartRateSentAt =
                    0L


                /*
                 * Fehlertext stehen lassen.
                 */
                stopForegroundService()

                stopSelf()
            }
        }
    }


    /*
     * =========================================================
     * Endgültige BLE-Fehler
     * =========================================================
     */

    private fun isTerminalPeripheralError(
        status: String
    ): Boolean {


        return (
                status.startsWith(
                    "BLE advertising failed"
                ) ||

                        status ==
                        "BLE advertising is not available" ||

                        status ==
                        "Could not start BLE GATT server" ||

                        status ==
                        "Could not create BLE heart rate service" ||

                        status ==
                        "Could not create heart rate characteristic" ||

                        status ==
                        "Could not add BLE heart rate service" ||

                        status ==
                        "Bluetooth permission missing" ||

                        status ==
                        "Bluetooth is unavailable"
                )
    }


    /*
     * =========================================================
     * BLE aufräumen
     * =========================================================
     */

    private fun stopBridge() {


        heartRatePeripheral
            ?.stop()


        heartRatePeripheral =
            null


        _running.value =
            false


        lastHeartRateSentAt =
            0L


        _status.value =
            "Stopped"
    }


    /*
     * =========================================================
     * Foreground Service aktivieren
     * =========================================================
     */

    private fun startForegroundMode(): Boolean {


        createNotificationChannel()


        val openAppIntent =
            Intent(
                this,
                MainActivity::class.java
            ).apply {

                flags =
                    Intent.FLAG_ACTIVITY_SINGLE_TOP or
                            Intent.FLAG_ACTIVITY_CLEAR_TOP
            }


        val pendingIntent =
            PendingIntent.getActivity(
                this,
                0,
                openAppIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or
                        PendingIntent.FLAG_IMMUTABLE
            )


        val notification =
            NotificationCompat
                .Builder(
                    this,
                    NOTIFICATION_CHANNEL_ID
                )
                .setSmallIcon(
                    R.mipmap.ic_launcher
                )
                .setContentTitle(
                    "Can's Pebbleton HR Bridge"
                )
                .setContentText(
                    "Heart-rate transmission is active"
                )
                .setContentIntent(
                    pendingIntent
                )
                .setOngoing(
                    true
                )
                .setOnlyAlertOnce(
                    true
                )
                .setCategory(
                    NotificationCompat.CATEGORY_SERVICE
                )
                .setPriority(
                    NotificationCompat.PRIORITY_LOW
                )
                .build()


        return try {


            val foregroundServiceType =
                if (
                    Build.VERSION.SDK_INT >=
                    Build.VERSION_CODES.Q
                ) {

                    ServiceInfo
                        .FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE

                } else {

                    0
                }


            ServiceCompat.startForeground(
                this,
                NOTIFICATION_ID,
                notification,
                foregroundServiceType
            )


            true

        } catch (
            _: SecurityException
        ) {

            _status.value =
                "Foreground service permission missing"

            false

        } catch (
            e: Exception
        ) {

            _status.value =
                "Foreground service failed: " +
                        (
                                e.message
                                    ?: e.javaClass.simpleName
                                )

            false
        }
    }


    /*
     * =========================================================
     * Notification Channel
     * =========================================================
     */

    private fun createNotificationChannel() {


        val channel =
            NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                NOTIFICATION_CHANNEL_NAME,
                NotificationManager
                    .IMPORTANCE_LOW
            ).apply {

                description =
                    "Keeps heart-rate transmission active during workouts"
            }


        val notificationManager =
            getSystemService(
                NotificationManager::class.java
            )


        notificationManager
            .createNotificationChannel(
                channel
            )
    }


    /*
     * =========================================================
     * Foreground-Modus beenden
     * =========================================================
     */

    private fun stopForegroundService() {


        if (!foregroundStarted) {
            return
        }


        stopForeground(
            STOP_FOREGROUND_REMOVE
        )


        foregroundStarted =
            false
    }


    /*
     * =========================================================
     * Kein Binder
     * =========================================================
     */

    override fun onBind(
        intent: Intent?
    ): IBinder? {

        return null
    }


    /*
     * =========================================================
     * Service endgültig zerstören
     * =========================================================
     */

    override fun onDestroy() {


        heartRatePeripheral
            ?.stop()


        heartRatePeripheral =
            null


        startRequested =
            false


        _running.value =
            false


        lastHeartRateSentAt =
            0L


        serviceScope.cancel()


        super.onDestroy()
    }
}
