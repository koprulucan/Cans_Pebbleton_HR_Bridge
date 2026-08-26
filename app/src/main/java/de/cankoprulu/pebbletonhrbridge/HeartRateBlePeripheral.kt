package de.cankoprulu.pebbletonhrbridge

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
import android.bluetooth.BluetoothStatusCodes
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.content.Context
import android.os.Build
import android.os.ParcelUuid
import java.util.UUID


@SuppressLint("MissingPermission")
class HeartRateBlePeripheral(
    context: Context,
    private val onStatusChanged: (String) -> Unit
) {

    companion object {

        private val HEART_RATE_SERVICE_UUID =
            UUID.fromString(
                "0000180d-0000-1000-8000-00805f9b34fb"
            )

        private val HEART_RATE_MEASUREMENT_UUID =
            UUID.fromString(
                "00002a37-0000-1000-8000-00805f9b34fb"
            )

        private val CLIENT_CONFIGURATION_UUID =
            UUID.fromString(
                "00002902-0000-1000-8000-00805f9b34fb"
            )
    }


    /*
     * Niemals eine Activity als Context festhalten.
     *
     * Diese Klasse wird künftig vom Foreground Service besessen
     * und darf problemlos über längere Workouts hinweg leben.
     */
    private val appContext =
        context.applicationContext


    private val bluetoothManager =
        appContext.getSystemService(
            BluetoothManager::class.java
        )


    private val bluetoothAdapter =
        bluetoothManager.adapter


    private var advertiser =
        bluetoothAdapter?.bluetoothLeAdvertiser


    private var gattServer:
            BluetoothGattServer? = null


    /*
     * True, solange diese Instanz für eine aktive BLE-Session
     * zuständig ist.
     *
     * Das verhindert unter anderem, dass verspätete Android-
     * Callbacks nach stop() wieder den UI-Status verändern.
     */
    @Volatile
    private var active = false


    /*
     * Aktuellster Herzfrequenzwert.
     *
     * Er wird insbesondere benötigt, wenn sich ein Client gerade
     * erst anmeldet. Dann bekommt er sofort den aktuellen Wert.
     */
    @Volatile
    private var currentHeartRate = 60


    /*
     * Sämtlicher Notification-Zustand wird durch diesen Lock
     * geschützt.
     *
     * Wichtig:
     *
     * Android möchte nicht, dass wir für dasselbe Gerät beliebig
     * viele GATT Notifications gleichzeitig losschicken.
     *
     * Wir warten deshalb jeweils auf onNotificationSent().
     */
    private val notificationLock =
        Any()


    /*
     * Clients, die über den CCCD Descriptor Notifications
     * eingeschaltet haben.
     */
    private val subscribedDevices =
        mutableSetOf<BluetoothDevice>()


    /*
     * Geräte, für die gerade bereits eine Notification von
     * Android verarbeitet wird.
     */
    private val notificationInFlight =
        mutableSetOf<BluetoothDevice>()


    /*
     * Nur der jeweils NEUESTE noch nicht gesendete Pulswert wird
     * gespeichert.
     *
     * Beispiel:
     *
     * 140 wird gerade übertragen
     * 141 kommt
     * 142 kommt
     * 143 kommt
     *
     * Nach Abschluss von 140 senden wir direkt 143.
     *
     * 141 und 142 müssen nicht unnötig nachgereicht werden.
     */
    private val pendingHeartRates =
        mutableMapOf<BluetoothDevice, Int>()


    /*
     * Fehlerzustand jetzt pro Client statt global.
     *
     * Das ist robuster, falls irgendwann mehr als ein BLE-Client
     * verbunden sein sollte.
     */
    private val notificationErrorDevices =
        mutableSetOf<BluetoothDevice>()


    /*
     * Standard BLE Heart Rate Measurement Characteristic 0x2A37
     */
    private val heartRateCharacteristic =
        BluetoothGattCharacteristic(
            HEART_RATE_MEASUREMENT_UUID,
            BluetoothGattCharacteristic.PROPERTY_NOTIFY,
            0
        )


    /*
     * Standard Client Characteristic Configuration Descriptor
     * 0x2902.
     *
     * Darüber aktiviert beispielsweise das Peloton die
     * Heart-Rate-Notifications.
     */
    private val clientConfigurationDescriptor =
        BluetoothGattDescriptor(
            CLIENT_CONFIGURATION_UUID,
            BluetoothGattDescriptor.PERMISSION_READ or
                    BluetoothGattDescriptor.PERMISSION_WRITE
        )


    init {

        heartRateCharacteristic.addDescriptor(
            clientConfigurationDescriptor
        )
    }


    /*
     * =========================================================
     * BLE Advertising Callback
     * =========================================================
     */

    private val advertiseCallback =
        object : AdvertiseCallback() {

            override fun onStartSuccess(
                settingsInEffect: AdvertiseSettings
            ) {

                /*
                 * Falls stop() während des asynchronen Starts
                 * aufgerufen wurde, soll ein verspäteter Callback
                 * die Session nicht wieder "auferstehen" lassen.
                 */
                if (!active) {

                    try {

                        advertiser?.stopAdvertising(
                            this
                        )

                    } catch (_: Exception) {
                        // Session ist sowieso bereits beendet.
                    }

                    return
                }


                onStatusChanged(
                    "Waiting for BLE client"
                )
            }


            override fun onStartFailure(
                errorCode: Int
            ) {

                if (!active) {
                    return
                }


                /*
                 * Wenn Advertising nicht gestartet werden konnte,
                 * brauchen wir auch keinen offenen GATT-Server.
                 */
                active = false


                try {

                    gattServer?.close()

                } catch (_: Exception) {
                    // Cleanup best effort.
                }


                gattServer = null


                onStatusChanged(
                    "BLE advertising failed ($errorCode)"
                )
            }
        }


    /*
     * =========================================================
     * GATT Server Callback
     * =========================================================
     */

    private val gattServerCallback =
        object : BluetoothGattServerCallback() {


            /*
             * Heart Rate Service wurde von Android registriert.
             *
             * Erst danach starten wir Advertising.
             */
            override fun onServiceAdded(
                status: Int,
                service: BluetoothGattService
            ) {

                if (!active) {
                    return
                }


                if (
                    status ==
                    BluetoothGatt.GATT_SUCCESS
                ) {

                    startAdvertising()

                } else {

                    active = false


                    try {

                        gattServer?.close()

                    } catch (_: Exception) {
                        // Cleanup best effort.
                    }


                    gattServer = null


                    onStatusChanged(
                        "Could not create BLE heart rate service"
                    )
                }
            }


            /*
             * Verbindung eines BLE-Clients.
             *
             * CONNECTED allein bedeutet noch nicht, dass der
             * Client Heart-Rate-Notifications abonniert hat.
             */
            override fun onConnectionStateChange(
                device: BluetoothDevice,
                status: Int,
                newState: Int
            ) {

                if (!active) {
                    return
                }


                when (newState) {

                    BluetoothProfile.STATE_CONNECTED -> {

                        onStatusChanged(
                            "BLE client connected"
                        )
                    }


                    BluetoothProfile.STATE_DISCONNECTED -> {

                        synchronized(notificationLock) {

                            subscribedDevices.remove(
                                device
                            )

                            notificationInFlight.remove(
                                device
                            )

                            pendingHeartRates.remove(
                                device
                            )

                            notificationErrorDevices.remove(
                                device
                            )
                        }


                        onStatusChanged(
                            "Waiting for BLE client"
                        )
                    }
                }
            }


            /*
             * =================================================
             * Client aktiviert/deaktiviert Notifications
             * =================================================
             */

            override fun onDescriptorWriteRequest(
                device: BluetoothDevice,
                requestId: Int,
                descriptor: BluetoothGattDescriptor,
                preparedWrite: Boolean,
                responseNeeded: Boolean,
                offset: Int,
                value: ByteArray
            ) {

                if (!active) {

                    if (responseNeeded) {

                        gattServer?.sendResponse(
                            device,
                            requestId,
                            BluetoothGatt.GATT_FAILURE,
                            offset,
                            null
                        )
                    }

                    return
                }


                if (
                    descriptor.uuid !=
                    CLIENT_CONFIGURATION_UUID
                ) {

                    if (responseNeeded) {

                        gattServer?.sendResponse(
                            device,
                            requestId,
                            BluetoothGatt.GATT_FAILURE,
                            offset,
                            null
                        )
                    }

                    return
                }


                val notificationsEnabled =
                    value.contentEquals(
                        BluetoothGattDescriptor
                            .ENABLE_NOTIFICATION_VALUE
                    )


                val notificationsDisabled =
                    value.contentEquals(
                        BluetoothGattDescriptor
                            .DISABLE_NOTIFICATION_VALUE
                    )


                /*
                 * Unbekannten Descriptor-Wert ablehnen.
                 */
                if (
                    !notificationsEnabled &&
                    !notificationsDisabled
                ) {

                    if (responseNeeded) {

                        gattServer?.sendResponse(
                            device,
                            requestId,
                            BluetoothGatt.GATT_FAILURE,
                            offset,
                            null
                        )
                    }

                    return
                }


                synchronized(notificationLock) {

                    if (notificationsEnabled) {

                        subscribedDevices.add(
                            device
                        )

                        notificationErrorDevices.remove(
                            device
                        )

                    } else {

                        subscribedDevices.remove(
                            device
                        )

                        notificationInFlight.remove(
                            device
                        )

                        pendingHeartRates.remove(
                            device
                        )

                        notificationErrorDevices.remove(
                            device
                        )
                    }
                }


                if (responseNeeded) {

                    gattServer?.sendResponse(
                        device,
                        requestId,
                        BluetoothGatt.GATT_SUCCESS,
                        offset,
                        null
                    )
                }


                if (notificationsEnabled) {

                    onStatusChanged(
                        "Heart rate transmission active"
                    )


                    /*
                     * Dem Client unmittelbar nach der Anmeldung
                     * den aktuellsten Pulswert geben.
                     */
                    queueHeartRate(
                        device,
                        currentHeartRate
                    )

                } else {

                    onStatusChanged(
                        "BLE client connected"
                    )
                }
            }


            /*
             * =================================================
             * CCCD Descriptor lesen
             * =================================================
             */

            override fun onDescriptorReadRequest(
                device: BluetoothDevice,
                requestId: Int,
                offset: Int,
                descriptor: BluetoothGattDescriptor
            ) {

                if (!active) {

                    gattServer?.sendResponse(
                        device,
                        requestId,
                        BluetoothGatt.GATT_FAILURE,
                        offset,
                        null
                    )

                    return
                }


                if (
                    descriptor.uuid !=
                    CLIENT_CONFIGURATION_UUID
                ) {

                    gattServer?.sendResponse(
                        device,
                        requestId,
                        BluetoothGatt.GATT_FAILURE,
                        offset,
                        null
                    )

                    return
                }


                val subscribed =
                    synchronized(notificationLock) {

                        device in
                                subscribedDevices
                    }


                val value =
                    if (subscribed) {

                        BluetoothGattDescriptor
                            .ENABLE_NOTIFICATION_VALUE

                    } else {

                        BluetoothGattDescriptor
                            .DISABLE_NOTIFICATION_VALUE
                    }


                gattServer?.sendResponse(
                    device,
                    requestId,
                    BluetoothGatt.GATT_SUCCESS,
                    offset,
                    value
                )
            }


            /*
             * =================================================
             * Vorherige Heart-Rate-Notification abgeschlossen
             * =================================================
             *
             * Jetzt darf die nächste Notification raus.
             */

            override fun onNotificationSent(
                device: BluetoothDevice,
                status: Int
            ) {

                if (!active) {
                    return
                }


                val recoveredFromError:
                        Boolean


                synchronized(notificationLock) {

                    notificationInFlight.remove(
                        device
                    )


                    recoveredFromError =
                        status ==
                                BluetoothGatt.GATT_SUCCESS &&
                                device in
                                notificationErrorDevices


                    if (
                        status ==
                        BluetoothGatt.GATT_SUCCESS
                    ) {

                        notificationErrorDevices.remove(
                            device
                        )

                    } else {

                        notificationErrorDevices.add(
                            device
                        )
                    }
                }


                if (
                    status !=
                    BluetoothGatt.GATT_SUCCESS
                ) {

                    onStatusChanged(
                        "BLE heart rate notification failed ($status)"
                    )


                    /*
                     * Kein sofortiger Retry.
                     *
                     * Der nächste eingehende Pebble-Pulswert
                     * startet die Pipeline erneut.
                     */
                    return
                }


                if (recoveredFromError) {

                    onStatusChanged(
                        "Heart rate transmission active"
                    )
                }


                /*
                 * Während die vorherige Notification unterwegs
                 * war, könnte bereits ein neuerer BPM-Wert
                 * eingetroffen sein.
                 */
                sendNextPendingHeartRate(
                    device
                )
            }
        }


    /*
     * =========================================================
     * BLE Peripheral starten
     * =========================================================
     */

    fun start(
        initialHeartRate: Int
    ): Boolean {

        /*
         * Eventuelle alte Session vollständig aufräumen.
         */
        stop()


        currentHeartRate =
            initialHeartRate.coerceIn(
                30,
                220
            )


        advertiser =
            bluetoothAdapter
                ?.bluetoothLeAdvertiser


        if (advertiser == null) {

            onStatusChanged(
                "BLE advertising is not available"
            )

            return false
        }


        /*
         * Ab jetzt dürfen die asynchronen Callbacks arbeiten.
         */
        active = true


        gattServer =
            try {

                bluetoothManager.openGattServer(
                    appContext,
                    gattServerCallback
                )

            } catch (
                e: SecurityException
            ) {

                active = false

                onStatusChanged(
                    "Bluetooth permission missing"
                )

                return false
            }


        if (gattServer == null) {

            active = false


            onStatusChanged(
                "Could not start BLE GATT server"
            )

            return false
        }


        val heartRateService =
            BluetoothGattService(
                HEART_RATE_SERVICE_UUID,
                BluetoothGattService
                    .SERVICE_TYPE_PRIMARY
            )


        val characteristicAdded =
            heartRateService
                .addCharacteristic(
                    heartRateCharacteristic
                )


        if (!characteristicAdded) {

            active = false


            try {

                gattServer?.close()

            } catch (_: Exception) {
                // Cleanup best effort.
            }


            gattServer = null


            onStatusChanged(
                "Could not create heart rate characteristic"
            )

            return false
        }


        onStatusChanged(
            "Starting BLE heart rate service"
        )


        val serviceQueued =
            try {

                gattServer
                    ?.addService(
                        heartRateService
                    ) == true

            } catch (
                e: SecurityException
            ) {

                false
            }


        if (!serviceQueued) {

            active = false


            try {

                gattServer?.close()

            } catch (_: Exception) {
                // Cleanup best effort.
            }


            gattServer = null


            onStatusChanged(
                "Could not add BLE heart rate service"
            )

            return false
        }


        /*
         * Der tatsächliche Erfolg kommt asynchron über
         * onServiceAdded().
         */
        return true
    }


    /*
     * =========================================================
     * Neuen Pebble-Puls übernehmen
     * =========================================================
     */

    fun updateHeartRate(
        heartRate: Int
    ) {

        /*
         * Den neuesten Wert merken.
         *
         * Auch falls gerade noch kein Client subscribed ist,
         * erhält er dadurch beim späteren Subscribe sofort den
         * aktuellen Puls.
         */
        currentHeartRate =
            heartRate.coerceIn(
                30,
                220
            )


        if (!active) {
            return
        }


        val devices =
            synchronized(notificationLock) {

                subscribedDevices.toList()
            }


        devices.forEach { device ->

            queueHeartRate(
                device,
                currentHeartRate
            )
        }
    }


    /*
     * =========================================================
     * Aktuellen Betriebszustand abfragen
     * =========================================================
     */

    fun isActive(): Boolean =
        active


    /*
     * =========================================================
     * Neuesten Wert für Client vormerken
     * =========================================================
     */

    private fun queueHeartRate(
        device: BluetoothDevice,
        heartRate: Int
    ) {

        if (!active) {
            return
        }


        synchronized(notificationLock) {

            if (
                device !in
                subscribedDevices
            ) {
                return
            }


            pendingHeartRates[device] =
                heartRate.coerceIn(
                    30,
                    220
                )
        }


        sendNextPendingHeartRate(
            device
        )
    }


    /*
     * =========================================================
     * Nächste Notification losschicken
     * =========================================================
     */

    private fun sendNextPendingHeartRate(
        device: BluetoothDevice
    ) {

        if (!active) {
            return
        }


        val heartRateToSend =
            synchronized(notificationLock) {

                if (
                    device !in
                    subscribedDevices
                ) {
                    return
                }


                /*
                 * Für diesen Client läuft bereits eine
                 * Notification.
                 */
                if (
                    device in
                    notificationInFlight
                ) {
                    return
                }


                val pending =
                    pendingHeartRates
                        .remove(device)
                        ?: return


                notificationInFlight.add(
                    device
                )


                pending
            }


        val notificationStarted =
            sendHeartRateNotification(
                device,
                heartRateToSend
            )


        if (!notificationStarted) {

            synchronized(notificationLock) {

                notificationInFlight.remove(
                    device
                )


                /*
                 * Wert erhalten, sofern nicht zwischenzeitlich
                 * bereits ein neuerer Puls eingetroffen ist.
                 */
                if (
                    device in subscribedDevices &&
                    device !in pendingHeartRates
                ) {

                    pendingHeartRates[device] =
                        heartRateToSend
                }


                notificationErrorDevices.add(
                    device
                )
            }


            onStatusChanged(
                "BLE heart rate notification could not be queued"
            )
        }
    }


    /*
     * =========================================================
     * Eine Heart-Rate-Notification an Android übergeben
     * =========================================================
     */

    private fun sendHeartRateNotification(
        device: BluetoothDevice,
        heartRate: Int
    ): Boolean {

        if (!active) {
            return false
        }


        val server =
            gattServer
                ?: return false


        /*
         * Bluetooth Heart Rate Measurement:
         *
         * Byte 0:
         * Flags = 0
         *
         * Damit wird der Puls als UInt8 übertragen.
         *
         * Byte 1:
         * BPM
         */
        val value =
            byteArrayOf(
                0x00,
                heartRate
                    .coerceIn(
                        30,
                        220
                    )
                    .toByte()
            )


        return try {

            if (
                Build.VERSION.SDK_INT >=
                Build.VERSION_CODES.TIRAMISU
            ) {

                /*
                 * API 33+:
                 *
                 * Neue, speichersichere Variante, bei der der
                 * Wert direkt an notifyCharacteristicChanged()
                 * übergeben wird.
                 */
                val result =
                    server
                        .notifyCharacteristicChanged(
                            device,
                            heartRateCharacteristic,
                            false,
                            value
                        )


                result ==
                        BluetoothStatusCodes.SUCCESS

            } else {

                /*
                 * Android 12 und älter.
                 */
                @Suppress("DEPRECATION")
                heartRateCharacteristic.value =
                    value


                @Suppress("DEPRECATION")
                server
                    .notifyCharacteristicChanged(
                        device,
                        heartRateCharacteristic,
                        false
                    )
            }

        } catch (
            e: SecurityException
        ) {

            false

        } catch (
            e: IllegalArgumentException
        ) {

            false
        }
    }


    /*
     * =========================================================
     * BLE Advertising starten
     * =========================================================
     */

    private fun startAdvertising() {

        if (!active) {
            return
        }


        val currentAdvertiser =
            advertiser


        if (currentAdvertiser == null) {

            active = false


            try {

                gattServer?.close()

            } catch (_: Exception) {
                // Cleanup best effort.
            }


            gattServer = null


            onStatusChanged(
                "BLE advertising is not available"
            )

            return
        }


        val settings =
            AdvertiseSettings
                .Builder()
                .setAdvertiseMode(
                    AdvertiseSettings
                        .ADVERTISE_MODE_LOW_LATENCY
                )
                .setConnectable(
                    true
                )
                .setTimeout(
                    0
                )
                .setTxPowerLevel(
                    AdvertiseSettings
                        .ADVERTISE_TX_POWER_MEDIUM
                )
                .build()


        /*
         * Heart Rate Service bleibt im eigentlichen
         * Advertisement-Paket.
         */
        val advertiseData =
            AdvertiseData
                .Builder()
                .addServiceUuid(
                    ParcelUuid(
                        HEART_RATE_SERVICE_UUID
                    )
                )
                .build()


        /*
         * Gerätename kommt separat in die Scan Response.
         *
         * Dadurch bleibt das Haupt-Advertisement klein.
         */
        val scanResponse =
            AdvertiseData
                .Builder()
                .setIncludeDeviceName(
                    true
                )
                .build()


        try {

            currentAdvertiser
                .startAdvertising(
                    settings,
                    advertiseData,
                    scanResponse,
                    advertiseCallback
                )

        } catch (
            e: SecurityException
        ) {

            active = false


            try {

                gattServer?.close()

            } catch (_: Exception) {
                // Cleanup best effort.
            }


            gattServer = null


            onStatusChanged(
                "Bluetooth permission missing"
            )

        } catch (
            e: IllegalStateException
        ) {

            active = false


            try {

                gattServer?.close()

            } catch (_: Exception) {
                // Cleanup best effort.
            }


            gattServer = null


            onStatusChanged(
                "Bluetooth is unavailable"
            )
        }
    }


    /*
     * =========================================================
     * BLE Peripheral vollständig stoppen
     * =========================================================
     */

    fun stop() {

        /*
         * Als ERSTES deaktivieren.
         *
         * Damit werden eventuell danach eintreffende asynchrone
         * Bluetooth-Callbacks ignoriert.
         */
        active = false


        /*
         * Wichtig:
         *
         * stopAdvertising() wird auch dann aufgerufen, wenn der
         * Start möglicherweise noch asynchron läuft.
         *
         * Android verlangt dafür denselben AdvertiseCallback wie
         * bei startAdvertising().
         */
        try {

            advertiser
                ?.stopAdvertising(
                    advertiseCallback
                )

        } catch (_: SecurityException) {
            // Permission möglicherweise während Session entzogen.
        } catch (_: IllegalStateException) {
            // Bluetooth möglicherweise ausgeschaltet.
        }


        synchronized(notificationLock) {

            subscribedDevices.clear()

            notificationInFlight.clear()

            pendingHeartRates.clear()

            notificationErrorDevices.clear()
        }


        try {

            gattServer?.close()

        } catch (_: Exception) {
            // Cleanup best effort.
        }


        gattServer = null
    }
}