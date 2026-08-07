package com.example.canspebbletonhrbridge
import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat

class MainActivity : ComponentActivity() {

    private var bluetoothPermissionGranted
            by mutableStateOf(false)

    private var bleAdvertisingSupported
            by mutableStateOf(false)

    private var running
            by mutableStateOf(false)

    private var bleStatus
            by mutableStateOf("Bereit")

    private var heartRatePeripheral:
            HeartRateBlePeripheral? = null


    private val bluetoothPermissionLauncher =
        registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) { permissions ->

            val advertiseGranted =
                permissions[
                    Manifest.permission.BLUETOOTH_ADVERTISE
                ] ?: true

            val connectGranted =
                permissions[
                    Manifest.permission.BLUETOOTH_CONNECT
                ] ?: true

            bluetoothPermissionGranted =
                advertiseGranted && connectGranted

            if (bluetoothPermissionGranted) {
                checkBleAdvertisingSupport()
            }
        }


    override fun onCreate(
        savedInstanceState: Bundle?
    ) {

        super.onCreate(savedInstanceState)

        checkBluetoothPermissions()

        setContent {

            MaterialTheme {

                Surface(
                    modifier = Modifier.fillMaxSize()
                ) {

                    HeartRateScreen(
                        bluetoothAllowed =
                            bluetoothPermissionGranted,

                        advertisingSupported =
                            bleAdvertisingSupported,

                        running =
                            running,

                        status =
                            bleStatus,

                        onStart = {
                            startHeartRateTest()
                        },

                        onStop = {
                            stopHeartRateTest()
                        }
                    )
                }
            }
        }
    }


    private fun checkBluetoothPermissions() {

        if (
            Build.VERSION.SDK_INT >=
            Build.VERSION_CODES.S
        ) {

            val advertisePermission =
                ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.BLUETOOTH_ADVERTISE
                )

            val connectPermission =
                ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.BLUETOOTH_CONNECT
                )


            if (
                advertisePermission ==
                PackageManager.PERMISSION_GRANTED
                &&
                connectPermission ==
                PackageManager.PERMISSION_GRANTED
            ) {

                bluetoothPermissionGranted = true

                checkBleAdvertisingSupport()

            } else {

                bluetoothPermissionLauncher.launch(
                    arrayOf(
                        Manifest.permission.BLUETOOTH_ADVERTISE,
                        Manifest.permission.BLUETOOTH_CONNECT
                    )
                )
            }

        } else {

            bluetoothPermissionGranted = true

            checkBleAdvertisingSupport()
        }
    }


    @SuppressLint("MissingPermission")
    private fun checkBleAdvertisingSupport() {

        val bluetoothManager =
            getSystemService(
                BluetoothManager::class.java
            )

        val bluetoothAdapter =
            bluetoothManager.adapter

        bleAdvertisingSupported =
            bluetoothAdapter != null &&
                    bluetoothAdapter
                        .isMultipleAdvertisementSupported
    }


    private fun startHeartRateTest() {

        if (
            !bluetoothPermissionGranted ||
            !bleAdvertisingSupported
        ) {
            return
        }


        bleStatus =
            "Starte Bluetooth..."

        running = true


        heartRatePeripheral =
            HeartRateBlePeripheral(
                applicationContext
            ) { newStatus ->

                runOnUiThread {

                    bleStatus =
                        newStatus
                }
            }


        heartRatePeripheral?.start(
            120
        )
    }


    private fun stopHeartRateTest() {

        heartRatePeripheral?.stop()

        heartRatePeripheral = null

        running = false

        bleStatus =
            "Übertragung gestoppt"
    }


    override fun onDestroy() {

        heartRatePeripheral?.stop()

        super.onDestroy()
    }
}


@Composable
fun HeartRateScreen(
    bluetoothAllowed: Boolean,
    advertisingSupported: Boolean,
    running: Boolean,
    status: String,
    onStart: () -> Unit,
    onStop: () -> Unit
) {

    Column(
        modifier = Modifier.fillMaxSize(),

        verticalArrangement =
            Arrangement.Center,

        horizontalAlignment =
            Alignment.CenterHorizontally
    ) {

        Text(
            "Pebble HR Bridge"
        )

        Text(
            "Testpuls: 120 BPM"
        )


        Text(
            if (bluetoothAllowed)
                "Bluetooth-Berechtigung: OK"
            else
                "Bluetooth-Berechtigung fehlt"
        )


        Text(
            if (advertisingSupported)
                "BLE Advertising: unterstützt"
            else
                "BLE Advertising: NICHT unterstützt"
        )


        Text(
            "Status: $status"
        )


        Button(
            enabled =
                bluetoothAllowed &&
                        advertisingSupported,

            onClick = {

                if (running)
                    onStop()
                else
                    onStart()
            }
        ) {

            Text(
                if (running)
                    "Stoppen"
                else
                    "120 BPM senden"
            )
        }
    }
}