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
    private val context: Context,
    private val onStatusChanged: (String) -> Unit
) {

    companion object {

        private val HEART_RATE_SERVICE_UUID =
            UUID.fromString("0000180d-0000-1000-8000-00805f9b34fb")

        private val HEART_RATE_MEASUREMENT_UUID =
            UUID.fromString("00002a37-0000-1000-8000-00805f9b34fb")

        private val CLIENT_CONFIGURATION_UUID =
            UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
    }

    private val bluetoothManager =
        context.getSystemService(BluetoothManager::class.java)

    private val bluetoothAdapter =
        bluetoothManager.adapter

    private var advertiser =
        bluetoothAdapter.bluetoothLeAdvertiser

    private var gattServer: BluetoothGattServer? = null

    /*
     * All notification state is protected by this lock.
     *
     * Android requires us to wait for onNotificationSent()
     * before sending another notification.
     */
    private val notificationLock = Any()

    private val subscribedDevices =
        mutableSetOf<BluetoothDevice>()

    private val notificationInFlight =
        mutableSetOf<BluetoothDevice>()

    /*
     * Only the latest pending heart rate is stored for each device.
     *
     * Example:
     * 140 is being sent
     * 141 arrives
     * 142 arrives
     * 143 arrives
     *
     * After 140 has finished, we send 143.
     * Old intermediate values are not queued.
     */
    private val pendingHeartRates =
        mutableMapOf<BluetoothDevice, Int>()

    private var notificationErrorActive = false

    @Volatile
    private var currentHeartRate = 60

    private val heartRateCharacteristic =
        BluetoothGattCharacteristic(
            HEART_RATE_MEASUREMENT_UUID,
            BluetoothGattCharacteristic.PROPERTY_NOTIFY,
            0
        )

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

    private val advertiseCallback =
        object : AdvertiseCallback() {

            override fun onStartSuccess(
                settingsInEffect: AdvertiseSettings
            ) {
                onStatusChanged(
                    "Advertising BLE heart rate service"
                )
            }

            override fun onStartFailure(
                errorCode: Int
            ) {
                onStatusChanged(
                    "BLE advertising failed ($errorCode)"
                )
            }
        }

    private val gattServerCallback =
        object : BluetoothGattServerCallback() {

            override fun onServiceAdded(
                status: Int,
                service: BluetoothGattService
            ) {

                if (status == BluetoothGatt.GATT_SUCCESS) {

                    startAdvertising()

                } else {

                    onStatusChanged(
                        "Could not create BLE heart rate service"
                    )
                }
            }

            override fun onConnectionStateChange(
                device: BluetoothDevice,
                status: Int,
                newState: Int
            ) {

                when (newState) {

                    BluetoothProfile.STATE_CONNECTED -> {

                        onStatusChanged(
                            "BLE client connected"
                        )
                    }

                    BluetoothProfile.STATE_DISCONNECTED -> {

                        synchronized(notificationLock) {

                            subscribedDevices.remove(device)

                            notificationInFlight.remove(device)

                            pendingHeartRates.remove(device)
                        }

                        onStatusChanged(
                            "Waiting for BLE client"
                        )
                    }
                }
            }

            override fun onDescriptorWriteRequest(
                device: BluetoothDevice,
                requestId: Int,
                descriptor: BluetoothGattDescriptor,
                preparedWrite: Boolean,
                responseNeeded: Boolean,
                offset: Int,
                value: ByteArray
            ) {

                if (
                    descriptor.uuid ==
                    CLIENT_CONFIGURATION_UUID
                ) {

                    val notificationsEnabled =
                        value.contentEquals(
                            BluetoothGattDescriptor
                                .ENABLE_NOTIFICATION_VALUE
                        )

                    synchronized(notificationLock) {

                        if (notificationsEnabled) {

                            subscribedDevices.add(device)

                        } else {

                            subscribedDevices.remove(device)

                            notificationInFlight.remove(device)

                            pendingHeartRates.remove(device)
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

                        /*
                         * Immediately give the client the current
                         * heart-rate value after subscribing.
                         */
                        queueHeartRate(
                            device,
                            currentHeartRate
                        )
                    }
                }
            }

            override fun onDescriptorReadRequest(
                device: BluetoothDevice,
                requestId: Int,
                offset: Int,
                descriptor: BluetoothGattDescriptor
            ) {

                if (
                    descriptor.uuid ==
                    CLIENT_CONFIGURATION_UUID
                ) {

                    val subscribed =
                        synchronized(notificationLock) {
                            device in subscribedDevices
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
            }

            /*
             * Android calls this after the previous notification
             * has finished.
             *
             * Only now do we send the newest pending value.
             */
            override fun onNotificationSent(
                device: BluetoothDevice,
                status: Int
            ) {

                val recoveredFromError: Boolean

                synchronized(notificationLock) {

                    notificationInFlight.remove(device)

                    recoveredFromError =
                        status == BluetoothGatt.GATT_SUCCESS &&
                                notificationErrorActive

                    if (status == BluetoothGatt.GATT_SUCCESS) {

                        notificationErrorActive = false

                    } else {

                        notificationErrorActive = true
                    }
                }

                if (status != BluetoothGatt.GATT_SUCCESS) {

                    onStatusChanged(
                        "BLE heart rate notification failed ($status)"
                    )

                    /*
                     * Do not immediately retry here.
                     * The next incoming heart-rate value will
                     * restart the notification pipeline.
                     */
                    return
                }

                if (recoveredFromError) {

                    onStatusChanged(
                        "BLE client connected"
                    )
                }

                /*
                 * There may already be a newer BPM value waiting.
                 */
                sendNextPendingHeartRate(device)
            }
        }

    fun start(
        initialHeartRate: Int
    ) {

        stop()

        currentHeartRate =
            initialHeartRate.coerceIn(30, 220)

        advertiser =
            bluetoothAdapter.bluetoothLeAdvertiser

        if (advertiser == null) {

            onStatusChanged(
                "BLE advertising is not available"
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
                "Could not start BLE GATT server"
            )

            return
        }

        val heartRateService =
            BluetoothGattService(
                HEART_RATE_SERVICE_UUID,
                BluetoothGattService.SERVICE_TYPE_PRIMARY
            )

        heartRateService.addCharacteristic(
            heartRateCharacteristic
        )

        onStatusChanged(
            "Starting BLE heart rate service"
        )

        gattServer?.addService(
            heartRateService
        )
    }

    fun updateHeartRate(
        heartRate: Int
    ) {

        currentHeartRate =
            heartRate.coerceIn(30, 220)

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
     * Store the newest BPM for this device.
     *
     * If a notification is already in progress,
     * nothing else happens yet.
     */
    private fun queueHeartRate(
        device: BluetoothDevice,
        heartRate: Int
    ) {

        synchronized(notificationLock) {

            if (device !in subscribedDevices) {
                return
            }

            pendingHeartRates[device] =
                heartRate.coerceIn(30, 220)
        }

        sendNextPendingHeartRate(device)
    }

    /*
     * Send a notification only if there is currently
     * no notification in flight for this device.
     */
    private fun sendNextPendingHeartRate(
        device: BluetoothDevice
    ) {

        val heartRateToSend =
            synchronized(notificationLock) {

                if (device !in subscribedDevices) {
                    return
                }

                if (device in notificationInFlight) {
                    return
                }

                val pending =
                    pendingHeartRates.remove(device)
                        ?: return

                notificationInFlight.add(device)

                pending
            }

        val notificationStarted =
            sendHeartRateNotification(
                device,
                heartRateToSend
            )

        if (!notificationStarted) {

            synchronized(notificationLock) {

                notificationInFlight.remove(device)

                /*
                 * Preserve the value unless an even newer
                 * value arrived in the meantime.
                 */
                if (
                    device in subscribedDevices &&
                    device !in pendingHeartRates
                ) {

                    pendingHeartRates[device] =
                        heartRateToSend
                }

                notificationErrorActive = true
            }

            onStatusChanged(
                "BLE heart rate notification could not be queued"
            )
        }
    }

    /*
     * Actually hand one notification to Android's
     * Bluetooth stack.
     */
    private fun sendHeartRateNotification(
        device: BluetoothDevice,
        heartRate: Int
    ): Boolean {

        val server =
            gattServer ?: return false

        val value =
            byteArrayOf(
                0x00,
                heartRate.coerceIn(
                    30,
                    220
                ).toByte()
            )

        return if (
            Build.VERSION.SDK_INT >=
            Build.VERSION_CODES.TIRAMISU
        ) {

            val result =
                server.notifyCharacteristicChanged(
                    device,
                    heartRateCharacteristic,
                    false,
                    value
                )

            result ==
                    BluetoothStatusCodes.SUCCESS

        } else {

            @Suppress("DEPRECATION")
            heartRateCharacteristic.value =
                value

            @Suppress("DEPRECATION")
            server.notifyCharacteristicChanged(
                device,
                heartRateCharacteristic,
                false
            )
        }
    }

    private fun startAdvertising() {

        val settings =
            AdvertiseSettings.Builder()
                .setAdvertiseMode(
                    AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY
                )
                .setConnectable(true)
                .setTimeout(0)
                .setTxPowerLevel(
                    AdvertiseSettings.ADVERTISE_TX_POWER_MEDIUM
                )
                .build()

        /*
         * Keep the Heart Rate Service in the main
         * advertisement packet.
         */
        val advertiseData =
            AdvertiseData.Builder()
                .addServiceUuid(
                    ParcelUuid(
                        HEART_RATE_SERVICE_UUID
                    )
                )
                .build()

        /*
         * Put the phone's Bluetooth device name into
         * the scan response.
         */
        val scanResponse =
            AdvertiseData.Builder()
                .setIncludeDeviceName(true)
                .build()

        advertiser?.startAdvertising(
            settings,
            advertiseData,
            scanResponse,
            advertiseCallback
        )
    }

    fun stop() {

        advertiser?.stopAdvertising(
            advertiseCallback
        )

        synchronized(notificationLock) {

            subscribedDevices.clear()

            notificationInFlight.clear()

            pendingHeartRates.clear()

            notificationErrorActive = false
        }

        gattServer?.close()

        gattServer = null
    }
}