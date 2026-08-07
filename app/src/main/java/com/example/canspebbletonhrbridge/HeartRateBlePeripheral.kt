package com.example.canspebbletonhrbridge

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothGattServer
import android.bluetooth.BluetoothGattServerCallback
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.bluetooth.le.BluetoothLeAdvertiser
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.ParcelUuid
import java.util.UUID

class HeartRateBlePeripheral(
    private val context: Context,
    private val onStatusChanged: (String) -> Unit
) {

    companion object {

        // Standard Bluetooth Heart Rate Service
        private val HEART_RATE_SERVICE_UUID =
            UUID.fromString("0000180d-0000-1000-8000-00805f9b34fb")

        // Standard Heart Rate Measurement
        private val HEART_RATE_MEASUREMENT_UUID =
            UUID.fromString("00002a37-0000-1000-8000-00805f9b34fb")

        // Body Sensor Location
        private val BODY_SENSOR_LOCATION_UUID =
            UUID.fromString("00002a38-0000-1000-8000-00805f9b34fb")

        // Client Characteristic Configuration Descriptor
        private val CCCD_UUID =
            UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
    }

    private val bluetoothManager =
        context.getSystemService(BluetoothManager::class.java)

    private val bluetoothAdapter =
        bluetoothManager.adapter

    private val handler =
        Handler(Looper.getMainLooper())

    private var advertiser: BluetoothLeAdvertiser? = null

    private var gattServer: BluetoothGattServer? = null

    private var heartRateCharacteristic:
            BluetoothGattCharacteristic? = null

    private var connectedDevice:
            BluetoothDevice? = null

    private var notificationsEnabled = false

    private var currentHeartRate = 120

    private var heartRateRunnable: Runnable? = null


    /*
     * Wird aufgerufen, wenn Android das Bluetooth-
     * Advertising gestartet hat.
     */
    private val advertiseCallback =
        object : AdvertiseCallback() {

            override fun onStartSuccess(
                settingsInEffect: AdvertiseSettings?
            ) {
                onStatusChanged(
                    "BLE-Herzfrequenzsensor ist sichtbar"
                )
            }

            override fun onStartFailure(errorCode: Int) {
                onStatusChanged(
                    "Advertising-Fehler: $errorCode"
                )
            }
        }


    /*
     * Hier reagieren wir auf das Peloton bzw.
     * einen anderen Bluetooth-Client.
     */
    private val gattServerCallback =
        object : BluetoothGattServerCallback() {

            @SuppressLint("MissingPermission")
            override fun onServiceAdded(
                status: Int,
                service: BluetoothGattService
            ) {

                if (
                    status == BluetoothGatt.GATT_SUCCESS &&
                    service.uuid == HEART_RATE_SERVICE_UUID
                ) {
                    startAdvertising()
                } else {
                    onStatusChanged(
                        "Heart-Rate-Service konnte nicht gestartet werden"
                    )
                }
            }


            override fun onConnectionStateChange(
                device: BluetoothDevice,
                status: Int,
                newState: Int
            ) {

                if (newState == BluetoothProfile.STATE_CONNECTED) {

                    connectedDevice = device

                    onStatusChanged(
                        "Gerät verbunden – warte auf Herzfrequenz-Abonnement"
                    )

                } else if (
                    newState == BluetoothProfile.STATE_DISCONNECTED
                ) {

                    connectedDevice = null
                    notificationsEnabled = false

                    stopHeartRateLoop()

                    onStatusChanged(
                        "Sensor sichtbar – kein Gerät verbunden"
                    )
                }
            }


            /*
             * Peloton aktiviert hier die Notifications
             * für den Pulswert.
             */
            @SuppressLint("MissingPermission")
            override fun onDescriptorWriteRequest(
                device: BluetoothDevice,
                requestId: Int,
                descriptor: BluetoothGattDescriptor,
                preparedWrite: Boolean,
                responseNeeded: Boolean,
                offset: Int,
                value: ByteArray
            ) {

                if (descriptor.uuid == CCCD_UUID) {

                    notificationsEnabled =
                        value.contentEquals(
                            BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                        )

                    if (responseNeeded) {
                        gattServer?.sendResponse(
                            device,
                            requestId,
                            BluetoothGatt.GATT_SUCCESS,
                            0,
                            null
                        )
                    }

                    if (notificationsEnabled) {

                        onStatusChanged(
                            "Verbunden – sende $currentHeartRate BPM"
                        )

                        startHeartRateLoop()

                    } else {

                        stopHeartRateLoop()

                        onStatusChanged(
                            "Verbunden – Pulsübertragung deaktiviert"
                        )
                    }

                } else {

                    if (responseNeeded) {
                        gattServer?.sendResponse(
                            device,
                            requestId,
                            BluetoothGatt.GATT_FAILURE,
                            0,
                            null
                        )
                    }
                }
            }


            @SuppressLint("MissingPermission")
            override fun onDescriptorReadRequest(
                device: BluetoothDevice,
                requestId: Int,
                offset: Int,
                descriptor: BluetoothGattDescriptor
            ) {

                if (descriptor.uuid == CCCD_UUID) {

                    val value =
                        if (notificationsEnabled)
                            BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                        else
                            BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE

                    gattServer?.sendResponse(
                        device,
                        requestId,
                        BluetoothGatt.GATT_SUCCESS,
                        0,
                        value
                    )

                } else {

                    gattServer?.sendResponse(
                        device,
                        requestId,
                        BluetoothGatt.GATT_FAILURE,
                        0,
                        null
                    )
                }
            }


            /*
             * Optional: Das Gerät kann fragen,
             * wo der Sensor sitzt.
             *
             * 2 = wrist / Handgelenk
             */
            @SuppressLint("MissingPermission")
            override fun onCharacteristicReadRequest(
                device: BluetoothDevice,
                requestId: Int,
                offset: Int,
                characteristic: BluetoothGattCharacteristic
            ) {

                if (
                    characteristic.uuid ==
                    BODY_SENSOR_LOCATION_UUID
                ) {

                    gattServer?.sendResponse(
                        device,
                        requestId,
                        BluetoothGatt.GATT_SUCCESS,
                        0,
                        byteArrayOf(2)
                    )

                } else {

                    gattServer?.sendResponse(
                        device,
                        requestId,
                        BluetoothGatt.GATT_FAILURE,
                        0,
                        null
                    )
                }
            }
        }


    /*
     * Startet unseren virtuellen Herzfrequenzsensor.
     */
    @SuppressLint("MissingPermission")
    fun start(heartRate: Int = 120) {

        currentHeartRate =
            heartRate.coerceIn(30, 220)

        if (!bluetoothAdapter.isEnabled) {

            onStatusChanged(
                "Bluetooth ist ausgeschaltet"
            )

            return
        }

        advertiser =
            bluetoothAdapter.bluetoothLeAdvertiser

        if (advertiser == null) {

            onStatusChanged(
                "BLE Advertising nicht verfügbar"
            )

            return
        }


        gattServer =
            bluetoothManager.openGattServer(
                context,
                gattServerCallback
            )

        if (gattServer == null) {

            onStatusChanged(
                "GATT-Server konnte nicht gestartet werden"
            )

            return
        }


        /*
         * Heart Rate Measurement
         *
         * PROPERTY_NOTIFY bedeutet:
         * Der Wert wird aktiv an das Peloton geschickt.
         */
        val measurementCharacteristic =
            BluetoothGattCharacteristic(
                HEART_RATE_MEASUREMENT_UUID,
                BluetoothGattCharacteristic.PROPERTY_NOTIFY,
                BluetoothGattCharacteristic.PERMISSION_READ
            )


        /*
         * Der Client muss Notifications aktivieren können.
         */
        val cccd =
            BluetoothGattDescriptor(
                CCCD_UUID,
                BluetoothGattDescriptor.PERMISSION_READ or
                        BluetoothGattDescriptor.PERMISSION_WRITE
            )

        measurementCharacteristic.addDescriptor(cccd)

        heartRateCharacteristic =
            measurementCharacteristic


        /*
         * Sensorposition: Handgelenk
         */
        val bodyLocationCharacteristic =
            BluetoothGattCharacteristic(
                BODY_SENSOR_LOCATION_UUID,
                BluetoothGattCharacteristic.PROPERTY_READ,
                BluetoothGattCharacteristic.PERMISSION_READ
            )


        /*
         * Heart Rate Service erstellen.
         */
        val heartRateService =
            BluetoothGattService(
                HEART_RATE_SERVICE_UUID,
                BluetoothGattService.SERVICE_TYPE_PRIMARY
            )

        heartRateService.addCharacteristic(
            measurementCharacteristic
        )

        heartRateService.addCharacteristic(
            bodyLocationCharacteristic
        )


        onStatusChanged(
            "Starte Heart-Rate-Service..."
        )


        val serviceStarted =
            gattServer?.addService(
                heartRateService
            ) ?: false


        if (!serviceStarted) {

            onStatusChanged(
                "GATT-Service konnte nicht hinzugefügt werden"
            )
        }
    }


    /*
     * Jetzt wird das Handy für andere BLE-Geräte sichtbar.
     */
    @SuppressLint("MissingPermission")
    private fun startAdvertising() {

        val settings =
            AdvertiseSettings.Builder()
                .setAdvertiseMode(
                    AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY
                )
                .setTxPowerLevel(
                    AdvertiseSettings.ADVERTISE_TX_POWER_MEDIUM
                )
                .setConnectable(true)
                .setTimeout(0)
                .build()


        val advertiseData =
            AdvertiseData.Builder()
                .addServiceUuid(
                    ParcelUuid(HEART_RATE_SERVICE_UUID)
                )
                .setIncludeDeviceName(true)
                .build()


        advertiser?.startAdvertising(
            settings,
            advertiseData,
            advertiseCallback
        )
    }


    /*
     * Ein Pulswert pro Sekunde.
     */
    private fun startHeartRateLoop() {

        stopHeartRateLoop()

        heartRateRunnable =
            object : Runnable {

                override fun run() {

                    if (notificationsEnabled) {

                        sendHeartRate()

                        handler.postDelayed(
                            this,
                            1000
                        )
                    }
                }
            }

        handler.post(
            heartRateRunnable!!
        )
    }


    private fun stopHeartRateLoop() {

        heartRateRunnable?.let {
            handler.removeCallbacks(it)
        }

        heartRateRunnable = null
    }


    /*
     * Das eigentliche Bluetooth-Datenpaket.
     */
    @SuppressLint("MissingPermission")
    private fun sendHeartRate() {

        val device =
            connectedDevice ?: return

        val characteristic =
            heartRateCharacteristic ?: return

        val server =
            gattServer ?: return


        /*
         * Byte 0 = Flags
         *
         * 0 bedeutet:
         * Herzfrequenz wird als 8-Bit-Wert übertragen.
         *
         * Byte 1 = BPM
         */
        val value =
            byteArrayOf(
                0x00,
                currentHeartRate.toByte()
            )


        if (
            Build.VERSION.SDK_INT >=
            Build.VERSION_CODES.TIRAMISU
        ) {

            server.notifyCharacteristicChanged(
                device,
                characteristic,
                false,
                value
            )

        } else {

            @Suppress("DEPRECATION")
            characteristic.value = value

            @Suppress("DEPRECATION")
            server.notifyCharacteristicChanged(
                device,
                characteristic,
                false
            )
        }
    }


    /*
     * Alles sauber beenden.
     */
    @SuppressLint("MissingPermission")
    fun stop() {

        stopHeartRateLoop()

        notificationsEnabled = false
        connectedDevice = null

        advertiser?.stopAdvertising(
            advertiseCallback
        )

        advertiser = null

        gattServer?.clearServices()
        gattServer?.close()

        gattServer = null

        onStatusChanged(
            "Übertragung gestoppt"
        )
    }
}