package de.cankoprulu.pebbletonhrbridge

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.lifecycle.lifecycleScope
import de.cankoprulu.pebbletonhrbridge.ui.theme.CansPebbletonHRBridgeTheme
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
         */
        if (!HrBridgeService.running.value) {
            startWatchApp()
        }


        /*
         * Pebble-Herzfrequenz für die UI beobachten.
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
         * Zustand des Foreground Service beobachten.
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
         * Statusmeldungen vom HrBridgeService beobachten.
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

            CansPebbletonHRBridgeTheme {

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


        stopTransmission()


        lifecycleScope.launch {

            try {

                pebbleSender
                    .stopAppOnTheWatch(
                        pebbleAppUuid
                    )

            } finally {

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

    var showPrivacyPolicy
            by remember {
                mutableStateOf(false)
            }


    Box(
        modifier =
            Modifier.fillMaxSize()
    ) {

        /*
         * =====================================================
         * Hauptfunktionen
         * =====================================================
         *
         * Bewusst zentral und klar voneinander getrennt von
         * sekundären Informationen wie der Privacy Policy.
         */
        Column(
            modifier =
                Modifier
                    .align(
                        Alignment.Center
                    )
                    .fillMaxWidth(),

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


        /*
         * =====================================================
         * Sekundäre Funktion: Privacy Policy
         * =====================================================
         *
         * Kleiner TextButton oben rechts.
         *
         * WindowInsets.statusBars sorgt dafür, dass der Button
         * unterhalb der Android-Statusleiste bleibt und nicht vom
         * System-UI verdeckt wird.
         */
        TextButton(
            modifier =
                Modifier
                    .align(
                        Alignment.TopEnd
                    )
                    .windowInsetsPadding(
                        WindowInsets.statusBars
                    )
                    .padding(
                        end = 12.dp,
                        top = 4.dp
                    ),

            onClick = {
                showPrivacyPolicy = true
            }
        ) {

            Text(
                "Privacy Policy"
            )
        }
    }


    if (showPrivacyPolicy) {

        PrivacyPolicyDialog(
            onDismiss = {
                showPrivacyPolicy = false
            }
        )
    }
}


/*
 * =============================================================
 * Privacy Policy
 * =============================================================
 */

@Composable
private fun PrivacyPolicyDialog(
    onDismiss: () -> Unit
) {

    val context =
        LocalContext.current


    AlertDialog(
        onDismissRequest =
            onDismiss,

        title = {

            Text(
                "Privacy Policy"
            )
        },

        text = {

            Column(
                modifier =
                    Modifier
                        .heightIn(
                            max = 520.dp
                        )
                        .verticalScroll(
                            rememberScrollState()
                        )
            ) {

                Text(
                    PRIVACY_POLICY_TEXT
                )


                Spacer(
                    modifier =
                        Modifier.height(24.dp)
                )


                Text(
                    "Support the project"
                )


                Spacer(
                    modifier =
                        Modifier.height(6.dp)
                )


                Text(
                    "Can's Pebbleton HR Bridge is free to use. " +
                            "If you find the project useful and would like " +
                            "to support continued development:"
                )


                TextButton(
                    onClick = {
                        openBuyMeACoffee(
                            context
                        )
                    }
                ) {

                    Text(
                        "☕ Buy me a coffee"
                    )
                }
            }
        },

        confirmButton = {

            TextButton(
                onClick =
                    onDismiss
            ) {

                Text(
                    "Close"
                )
            }
        }
    )
}


/*
 * =============================================================
 * Buy Me a Coffee
 * =============================================================
 */

private fun openBuyMeACoffee(
    context: Context
) {

    val uri =
        BUY_ME_A_COFFEE_URL.toUri()


    /*
     * Gewünscht ist ausdrücklich Chrome.
     *
     * Falls Chrome auf einem Gerät nicht installiert ist,
     * fällt die App automatisch auf den Standardbrowser zurück.
     */
    val chromeIntent =
        Intent(
            Intent.ACTION_VIEW,
            uri
        ).apply {

            setPackage(
                "com.android.chrome"
            )
        }


    try {

        context.startActivity(
            chromeIntent
        )

    } catch (
        _: ActivityNotFoundException
    ) {

        val browserIntent =
            Intent(
                Intent.ACTION_VIEW,
                uri
            )

        context.startActivity(
            browserIntent
        )
    }
}


private const val BUY_ME_A_COFFEE_URL =
    "https://buymeacoffee.com/koprulucan"


private const val PRIVACY_POLICY_TEXT =
    """Can's Pebbleton HR Bridge – Privacy Policy

Last updated: August 26, 2026

Can's Pebbleton HR Bridge is an Android application that receives heart-rate data from a compatible Pebble smartwatch and forwards that data to a Bluetooth Low Energy heart-rate client selected by the user, such as compatible fitness equipment.

Data accessed and used

The app accesses heart-rate measurements received from the Pebble watch through the Pebble/Core and PebbleKit environment.

The heart-rate data is used only to:

• display the current heart rate in the Android app;
• provide the app's Bluetooth Low Energy Heart Rate Service; and
• forward heart-rate measurements to a BLE client connected by the user.

Data sharing

Heart-rate measurements are transmitted over Bluetooth Low Energy to the BLE client that the user chooses to connect to the app.

Can's Pebbleton HR Bridge does not sell heart-rate data.

The app does not include advertising or analytics SDKs and does not send heart-rate measurements to a developer-operated cloud service.

Data storage and retention

Heart-rate measurements are not stored in a persistent database by Can's Pebbleton HR Bridge.

The current and most recently received heart-rate values may exist temporarily in the app's process memory while a session is active. A recent value may be reused briefly during a short interruption in Pebble data in order to maintain BLE heart-rate transmission.

Using "End session & close" clears the app's in-memory heart-rate session state.

Permissions

Bluetooth permissions are used to advertise and operate the Bluetooth Low Energy Heart Rate Service and to communicate with compatible Bluetooth devices.

Notification permission, where requested by Android, is used for the foreground-service notification that keeps heart-rate transmission active during a workout.

No user account

Can's Pebbleton HR Bridge does not provide or require a user account.

Security

Heart-rate processing performed by Can's Pebbleton HR Bridge is local to the Android device, except for the intended Bluetooth transmission to the connected BLE client.

Deletion

Because the app does not persistently store heart-rate measurements in its own database, there is no stored heart-rate history to delete from the app. Ending the session clears the in-memory heart-rate state. Uninstalling the app removes its local application data.

Contact

For privacy questions regarding Can's Pebbleton HR Bridge, use the developer contact information published with the app's Google Play listing."""