package de.cankoprulu.pebbletonhrbridge

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import io.rebble.pebblekit2.client.DefaultPebbleSender
import kotlinx.coroutines.launch
import java.util.UUID


class MainActivity : ComponentActivity() {

    private var bluetoothReady
            by mutableStateOf(false)

    private var advertisingSupported
            by mutableStateOf(false)

    private var pebbleHeartRate
            by mutableStateOf<Int?>(null)


    /*
     * Diese beiden Werte spiegeln nur den Zustand
     * des HrBridgeService wider.
     *
     * Die Activity besitzt selbst KEINEN BLE-GATT-Server.
     */
    private var transmitting
            by mutableStateOf(false)

    private var status
            by mutableStateOf("Ready")


    /*
     * =========================================================
     * Permission Launcher
     * =========================================================
     *
     * Bluetooth-Berechtigungen sind für die eigentliche
     * Bridge-Funktion notwendig.
     *
     * POST_NOTIFICATIONS ist dagegen nicht Voraussetzung dafür,
     * dass der Foreground Service technisch laufen darf.
     */
    private val permissionLauncher =
        registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) {

            bluetoothReady =
                hasBluetoothPermissions()

            if (bluetoothReady) {
                checkBleSupport()
            }
        }


    /*
     * =========================================================
     * Pebble Sender
     * =========================================================
     */

    private val pebbleSender by lazy {
        DefaultPebbleSender(this)
    }


    /*
     * Muss exakt mit der UUID von
     * "Can's HR Sender for Pebble" übereinstimmen.
     */
    private val pebbleAppUuid =
        UUID.fromString(
            "49b5977c-c9d1-4819-9410-0b7c2a9716f9"
        )


    /*
     * =========================================================
     * Activity erstellen
     * =========================================================
     */

    override fun onCreate(
        savedInstanceState: Bundle?
    ) {

        super.onCreate(savedInstanceState)


        checkRuntimePermissions()


        /*
         * Wenn bereits eine Übertragung läuft, starten wir die
         * Pebble-App NICHT erneut.
         *
         * Das ist besonders wichtig, wenn Android lediglich die
         * MainActivity neu erstellt hat.
         */
        if (!HrBridgeService.running.value) {
            startWatchApp()
        }


        /*
         * =====================================================
         * Pebble-Herzfrequenz für die UI beobachten
         * =====================================================
         *
         * Die Herzfrequenz wird hier NICHT direkt an BLE
         * weitergereicht.
         *
         * Das übernimmt HrBridgeService.
         */
        lifecycleScope.launch {

            HeartRateState
                .heartRate
                .collect { bpm ->

                    pebbleHeartRate =
                        bpm
                }
        }


        /*
         * =====================================================
         * Zustand des Foreground Service beobachten
         * =====================================================
         *
         * Dadurch kann die Activity verschwinden und später
         * neu erstellt werden, ohne dass die UI fälschlich
         * wieder einen neuen Start anbietet.
         */
        lifecycleScope.launch {

            HrBridgeService
                .running
                .collect { running ->

                    transmitting =
                        running
                }
        }


        /*
         * =====================================================
         * Statusmeldungen vom HrBridgeService beobachten
         * =====================================================
         */
        lifecycleScope.launch {

            HrBridgeService
                .status
                .collect { newStatus ->

                    status =
                        newStatus
                }
        }


        /*
         * =====================================================
         * Compose UI
         * =====================================================
         */
        setContent {

            MaterialTheme {

                Surface(
                    modifier =
                        Modifier.fillMaxSize()
                ) {

                    HeartRateScreen(
                        bluetoothReady =
                            bluetoothReady,

                        advertisingSupported =
                            advertisingSupported,

                        heartRate =
                            pebbleHeartRate,

                        transmitting =
                            transmitting,

                        status =
                            status,

                        onStart = {
                            startTransmission()
                        },

                        onStop = {
                            stopTransmission()
                        },

                        onExit = {
                            endSessionAndExit()
                        }
                    )
                }
            }
        }
    }


    /*
     * =========================================================
     * Session vollständig beenden
     * =========================================================
     */

    private fun endSessionAndExit() {

        status =
            "Ending session..."


        /*
         * Zuerst die Android-BLE-Bridge stoppen.
         */
        stopTransmission()


        /*
         * Danach die Pebble-App beenden.
         */
        lifecycleScope.launch {

            try {

                pebbleSender
                    .stopAppOnTheWatch(
                        pebbleAppUuid
                    )

            } finally {

                /*
                 * Vollständiger Reset:
                 *
                 * - aktueller Wert
                 * - letzter bekannter Wert
                 * - letzter Update-Zeitstempel
                 */
                HeartRateState.reset()


                finishAndRemoveTask()
            }
        }
    }


    /*
     * =========================================================
     * Pebble-App starten
     * =========================================================
     */

    private fun startWatchApp() {

        lifecycleScope.launch {

            pebbleSender
                .startAppOnTheWatch(
                    pebbleAppUuid
                )
        }
    }


    /*
     * =========================================================
     * Runtime Permissions
     * =========================================================
     */

    private fun checkRuntimePermissions() {

        val permissionsToRequest =
            mutableListOf<String>()


        /*
         * Android 12+
         *
         * Bluetooth Runtime Permissions
         */
        if (
            Build.VERSION.SDK_INT >=
            Build.VERSION_CODES.S
        ) {

            if (
                ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.BLUETOOTH_ADVERTISE
                ) !=
                PackageManager.PERMISSION_GRANTED
            ) {

                permissionsToRequest +=
                    Manifest.permission.BLUETOOTH_ADVERTISE
            }


            if (
                ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.BLUETOOTH_CONNECT
                ) !=
                PackageManager.PERMISSION_GRANTED
            ) {

                permissionsToRequest +=
                    Manifest.permission.BLUETOOTH_CONNECT
            }
        }


        /*
         * Android 13+
         *
         * Benachrichtigungsberechtigung für die sichtbare
         * Foreground-Service-Benachrichtigung.
         *
         * Eine Ablehnung blockiert die BLE-Funktion nicht.
         */
        if (
            Build.VERSION.SDK_INT >=
            Build.VERSION_CODES.TIRAMISU
        ) {

            if (
                ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) !=
                PackageManager.PERMISSION_GRANTED
            ) {

                permissionsToRequest +=
                    Manifest.permission.POST_NOTIFICATIONS
            }
        }


        /*
         * Bluetooth-Status unabhängig von der
         * Notification-Permission bestimmen.
         */
        bluetoothReady =
            hasBluetoothPermissions()


        if (bluetoothReady) {
            checkBleSupport()
        }


        if (permissionsToRequest.isNotEmpty()) {

            permissionLauncher.launch(
                permissionsToRequest.toTypedArray()
            )
        }
    }


    /*
     * =========================================================
     * Bluetooth-Berechtigungen prüfen
     * =========================================================
     */

    private fun hasBluetoothPermissions(): Boolean {

        if (
            Build.VERSION.SDK_INT <
            Build.VERSION_CODES.S
        ) {

            return true
        }


        val advertiseGranted =
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.BLUETOOTH_ADVERTISE
            ) ==
                    PackageManager.PERMISSION_GRANTED


        val connectGranted =
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.BLUETOOTH_CONNECT
            ) ==
                    PackageManager.PERMISSION_GRANTED


        return advertiseGranted &&
                connectGranted
    }


    /*
     * =========================================================
     * BLE-Unterstützung des Telefons prüfen
     * =========================================================
     */

    @SuppressLint("MissingPermission")
    private fun checkBleSupport() {

        if (!bluetoothReady) {

            advertisingSupported =
                false

            return
        }


        val bluetoothManager =
            getSystemService(
                BluetoothManager::class.java
            )


        val adapter =
            bluetoothManager.adapter


        advertisingSupported =
            adapter != null &&
                    adapter.isEnabled &&
                    adapter.isMultipleAdvertisementSupported
    }


    /*
     * =========================================================
     * Übertragung starten
     * =========================================================
     *
     * Hier wird KEIN HeartRateBlePeripheral erzeugt.
     *
     * Wir starten lediglich den Foreground Service.
     */

    private fun startTransmission() {

        if (!bluetoothReady) {

            status =
                "Bluetooth permission required"

            return
        }


        if (!advertisingSupported) {

            status =
                "BLE advertising unavailable"

            return
        }


        if (pebbleHeartRate == null) {

            status =
                "Waiting for Pebble heart rate"

            return
        }


        status =
            "Starting transmission..."


        val intent =
            Intent(
                this,
                HrBridgeService::class.java
            ).apply {

                action =
                    HrBridgeService.ACTION_START
            }


        try {

            /*
             * Der Foreground Service wird gestartet, während
             * die Activity sichtbar ist.
             *
             * HrBridgeService ruft danach selbst
             * startForeground() auf.
             */
            ContextCompat.startForegroundService(
                this,
                intent
            )

        } catch (e: Exception) {

            status =
                "Unable to start: " +
                        (
                                e.message
                                    ?: e.javaClass.simpleName
                                )
        }
    }


    /*
     * =========================================================
     * Übertragung stoppen
     * =========================================================
     */

    private fun stopTransmission() {

        status =
            "Stopping transmission..."


        val intent =
            Intent(
                this,
                HrBridgeService::class.java
            ).apply {

                action =
                    HrBridgeService.ACTION_STOP
            }


        try {

            /*
             * Der bereits laufende Service erhält das
             * Stop-Kommando und kann:
             *
             * - Advertising stoppen
             * - GATT Server schließen
             * - Foreground Notification entfernen
             * - sich selbst beenden
             */
            startService(
                intent
            )

        } catch (e: Exception) {

            status =
                "Unable to stop: " +
                        (
                                e.message
                                    ?: e.javaClass.simpleName
                                )
        }
    }


    /*
     * =========================================================
     * Activity wird zerstört
     * =========================================================
     *
     * ABSICHTLICH KEIN:
     *
     * heartRatePeripheral?.stop()
     * stopTransmission()
     * stopService(...)
     *
     * Die BLE-Übertragung gehört HrBridgeService und muss
     * weiterlaufen, wenn die Activity verschwindet.
     */

    override fun onDestroy() {

        pebbleSender.close()

        super.onDestroy()
    }
}


/*
 * =============================================================
 * UI
 * =============================================================
 */

@Composable
fun HeartRateScreen(
    bluetoothReady: Boolean,
    advertisingSupported: Boolean,
    heartRate: Int?,
    transmitting: Boolean,
    status: String,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onExit: () -> Unit
) {

    Column(
        modifier =
            Modifier.fillMaxSize(),

        verticalArrangement =
            Arrangement.Center,

        horizontalAlignment =
            Alignment.CenterHorizontally
    ) {

        Text(
            "Can's Pebbleton HR Bridge"
        )


        Text(
            if (heartRate != null) {

                "Pebble heart rate: $heartRate BPM"

            } else {

                "Pebble heart rate: -- BPM"
            }
        )


        Text(
            if (bluetoothReady) {

                "Bluetooth: ready"

            } else {

                "Bluetooth permission required"
            }
        )


        Text(
            if (advertisingSupported) {

                "BLE advertising: ready"

            } else {

                "BLE advertising: unavailable"
            }
        )


        Text(
            "Status: $status"
        )


        Button(
            modifier =
                Modifier.fillMaxWidth(0.85f),

            enabled =
                transmitting ||
                        (
                                bluetoothReady &&
                                        advertisingSupported &&
                                        heartRate != null
                                ),

            onClick = {

                if (transmitting) {

                    onStop()

                } else {

                    onStart()
                }
            }
        ) {

            Text(
                if (transmitting) {

                    "Stop transmission"

                } else {

                    "Start heart rate transmission"
                }
            )
        }


        Spacer(
            modifier =
                Modifier.height(12.dp)
        )


        OutlinedButton(
            modifier =
                Modifier.fillMaxWidth(0.85f),

            onClick = {

                onExit()
            }
        ) {

            Text(
                "End session & close"
            )
        }
    }
}