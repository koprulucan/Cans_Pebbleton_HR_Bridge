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
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import io.rebble.pebblekit2.client.DefaultPebbleSender
import java.util.UUID
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.OutlinedButton
import androidx.compose.ui.unit.dp

class MainActivity : ComponentActivity() {

    private var bluetoothReady
            by mutableStateOf(false)

    private var advertisingSupported
            by mutableStateOf(false)

    private var pebbleHeartRate
            by mutableStateOf<Int?>(null)

    private var transmitting
            by mutableStateOf(false)

    private var status
            by mutableStateOf("Ready")

    private var heartRatePeripheral:
            HeartRateBlePeripheral? = null

    private val bluetoothPermissionLauncher =
        registerForActivityResult(
            ActivityResultContracts
                .RequestMultiplePermissions()
        ) { permissions ->

            val advertiseGranted =
                permissions[
                    Manifest.permission.BLUETOOTH_ADVERTISE
                ] ?: true

            val connectGranted =
                permissions[
                    Manifest.permission.BLUETOOTH_CONNECT
                ] ?: true

            bluetoothReady =
                advertiseGranted &&
                        connectGranted

            if (bluetoothReady) {
                checkBleSupport()
            }
        }
    private val pebbleSender by lazy {
        DefaultPebbleSender(this)
    }

    private val pebbleAppUuid =
        UUID.fromString(
            "49b5977c-c9d1-4819-9410-0b7c2a9716f9"
        )

    override fun onCreate(
        savedInstanceState: Bundle?
    ) {

        super.onCreate(savedInstanceState)

        checkBluetoothPermissions()
        startWatchApp()

        lifecycleScope.launch {

            HeartRateState
                .heartRate
                .collect { bpm ->

                    pebbleHeartRate = bpm

                    if (bpm == null) {

                        if (transmitting) {
                            stopTransmission()
                        }

                    } else if (transmitting) {

                        heartRatePeripheral
                            ?.updateHeartRate(bpm)
                    }
                }
        }

        setContent {

            MaterialTheme {

                Surface(
                    modifier =
                        Modifier.fillMaxSize()
                )
                {

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
    private fun endSessionAndExit() {

        status = "Ending session..."

        lifecycleScope.launch {

            pebbleSender.stopAppOnTheWatch(
                pebbleAppUuid
            )

            heartRatePeripheral?.stop()
            heartRatePeripheral = null

            transmitting = false

            HeartRateState.clear()

            finishAndRemoveTask()
        }
    }
    private fun startWatchApp() {

        lifecycleScope.launch {
            pebbleSender.startAppOnTheWatch(
                pebbleAppUuid
            )
        }
    }
    private fun checkBluetoothPermissions() {

        if (
            Build.VERSION.SDK_INT >=
            Build.VERSION_CODES.S
        ) {

            val advertiseGranted =
                ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission
                        .BLUETOOTH_ADVERTISE
                ) ==
                        PackageManager
                            .PERMISSION_GRANTED

            val connectGranted =
                ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission
                        .BLUETOOTH_CONNECT
                ) ==
                        PackageManager
                            .PERMISSION_GRANTED

            if (
                advertiseGranted &&
                connectGranted
            ) {

                bluetoothReady = true

                checkBleSupport()

            } else {

                bluetoothPermissionLauncher
                    .launch(
                        arrayOf(
                            Manifest.permission
                                .BLUETOOTH_ADVERTISE,

                            Manifest.permission
                                .BLUETOOTH_CONNECT
                        )
                    )
            }

        } else {

            bluetoothReady = true

            checkBleSupport()
        }
    }

    @SuppressLint("MissingPermission")
    private fun checkBleSupport() {

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

    private fun startTransmission() {

        val bpm =
            pebbleHeartRate
                ?: return

        heartRatePeripheral?.stop()

        heartRatePeripheral =
            HeartRateBlePeripheral(
                applicationContext
            ) { newStatus ->

                runOnUiThread {
                    status = newStatus
                }
            }

        transmitting = true

        heartRatePeripheral
            ?.start(bpm)
    }

    private fun stopTransmission() {

        heartRatePeripheral?.stop()

        heartRatePeripheral = null

        transmitting = false

        status = "Stopped"
    }

    override fun onDestroy() {

        heartRatePeripheral?.stop()
        pebbleSender.close()
        super.onDestroy()
    }
}

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
            "Pebble HR Bridge"
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
            modifier = Modifier.fillMaxWidth(0.85f),

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
            modifier = Modifier.height(12.dp)
        )

        OutlinedButton(
            modifier = Modifier.fillMaxWidth(0.85f),

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
