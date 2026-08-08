# Pebble HR Bridge

**Pebble HR Bridge** is an Android app that receives live heart-rate data from a Pebble smartwatch and exposes it as a standard Bluetooth Low Energy Heart Rate Service.

It is designed to let a Pebble act as a heart-rate sensor for devices and apps that can connect to a standard BLE heart-rate monitor.

## Status

**Beta / real-world testing**

The current version is functional and has been tested successfully with:

- Pebble Time 2
- Pebble/Core mobile app
- Pebble HR Sender
- Android
- Peloton Bike

More phones, Pebble models, BLE clients, and long-term connection scenarios still need testing.

## How it works

```text
Pebble watch
    │
    │ HealthService
    ▼
Pebble HR Sender
    │
    │ AppMessage
    ▼
Pebble/Core mobile app
    │
    │ PebbleKit Android 2
    ▼
Pebble HR Bridge
    │
    │ Bluetooth LE Heart Rate Service
    ▼
Peloton / other BLE HR client
```

## Features

- receives live heart-rate values from Pebble HR Sender
- uses PebbleKit Android 2
- exposes the standard BLE Heart Rate Service
- updates the BLE heart-rate value live while transmission is running
- simple start/stop interface
- no fixed or simulated heart-rate value during normal use

## Requirements

- Android phone with Bluetooth Low Energy peripheral/advertising support
- Pebble/Core mobile app
- compatible Pebble connected to the phone
- Pebble HR Sender installed and running on the watch

The current beta has been tested with a **Pebble Time 2**.

## Installation

Download the latest APK from the repository's **Releases** page and install it on the Android phone connected to your Pebble.

Because this beta is distributed outside the Play Store, Android may ask you to allow installation from the app you used to open the APK.

## Usage

1. Connect the Pebble to the Pebble/Core mobile app.
2. Open **Pebble HR Sender** on the watch.
3. Wait until the watch shows a valid heart rate and `Phone: connected`.
4. Open **Pebble HR Bridge** on Android.
5. Confirm that the live Pebble heart rate appears.
6. Tap **Start heart rate transmission**.
7. On the target device or app, search for Bluetooth heart-rate sensors.
8. Connect to the heart-rate service exposed by the Android phone.

## Bluetooth implementation

Pebble HR Bridge acts as a Bluetooth LE GATT server and exposes the standard Bluetooth Heart Rate Service.

```text
Heart Rate Service:     0x180D
Heart Rate Measurement: 0x2A37
```

## Pebble communication

Watch-app UUID:

```text
49b5977c-c9d1-4819-9410-0b7c2a9716f9
```

Heart-rate message:

```text
Message key: HEART_RATE
Numeric key: 10000
Type: Int32
```

Android application ID:

```text
com.example.canspebbletonhrbridge
```

Do not change these identifiers independently unless you also update the corresponding watch app.

## Building from source

Open the project in Android Studio and build normally.

For a release APK:

1. Open **Build**
2. Choose **Generate Signed App Bundle / APK**
3. Select **APK**
4. Sign with your release keystore
5. Build the release variant

Keep the signing keystore safe. Future updates must be signed consistently if they are intended to update an already installed build.

## Privacy

Heart-rate forwarding happens locally:

```text
Pebble → phone → Bluetooth LE client
```

Pebble HR Bridge does not need to upload heart-rate data to a cloud service in order to perform the bridge function.

## Known limitations

This is an early beta. Areas that still need wider real-world testing include:

- background behavior on different Android manufacturers
- reconnection after Bluetooth interruptions
- long workouts
- stale heart-rate handling after an unexpected disconnect
- multiple BLE clients
- additional Pebble models
- additional fitness equipment and apps

## Feedback

Bug reports and real-world test results are welcome through GitHub Issues.

Please include:

- Pebble model
- Pebble firmware version
- Android phone model
- Android version
- Pebble/Core app version
- target BLE device or app
- expected behavior
- actual behavior
