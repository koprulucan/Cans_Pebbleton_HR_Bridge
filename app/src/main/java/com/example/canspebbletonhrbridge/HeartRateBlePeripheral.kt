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

    private val subscribedDevices =
        mutableSetOf<BluetoothDevice>()

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
                        subscribedDevices.remove(device)

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

                    if (
                        value.contentEquals(
                            BluetoothGattDescriptor
                                .ENABLE_NOTIFICATION_VALUE
                        )
                    ) {

                        subscribedDevices.add(device)

                    } else {

                        subscribedDevices.remove(device)
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

                    if (device in subscribedDevices) {
                        sendHeartRate(device)
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

                    val value =
                        if (device in subscribedDevices) {
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

        subscribedDevices
            .toList()
            .forEach { device ->
                sendHeartRate(device)
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

        val advertiseData =
            AdvertiseData.Builder()
                .addServiceUuid(
                    ParcelUuid(
                        HEART_RATE_SERVICE_UUID
                    )
                )
                .build()

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

    private fun sendHeartRate(
        device: BluetoothDevice
    ) {

        val value =
            byteArrayOf(
                0x00,
                currentHeartRate.toByte()
            )

        if (
            Build.VERSION.SDK_INT >=
            Build.VERSION_CODES.TIRAMISU
        ) {

            gattServer?.notifyCharacteristicChanged(
                device,
                heartRateCharacteristic,
                false,
                value
            )

        } else {

            @Suppress("DEPRECATION")
            heartRateCharacteristic.value =
                value

            @Suppress("DEPRECATION")
            gattServer?.notifyCharacteristicChanged(
                device,
                heartRateCharacteristic,
                false
            )
        }
    }

    fun stop() {

        advertiser?.stopAdvertising(
            advertiseCallback
        )

        subscribedDevices.clear()

        gattServer?.close()

        gattServer = null
    }
}