Can's Pebbleton HR Bridge

Can's Pebbleton HR Bridge is an Android app that forwards heart-rate data from a Pebble smartwatch to compatible BLE fitness equipment such as a Peloton Bike.

The project consists of two parts:

Can's Pebbleton HR Bridge — Android BLE bridge

Can's HR Sender for Pebble — Pebble watch app

The Pebble watch provides heart-rate data to Android through PebbleKit. The Android app then exposes a standard Bluetooth Low Energy Heart Rate Service so that compatible fitness equipment can connect to it as if it were a regular BLE heart-rate sensor.


Support the project

Can's Pebbleton HR Bridge is free to use.

If you find the project useful and would like to support continued development:

☕ https://buymeacoffee.com/koprulucan


Current versions

Android

Can's Pebbleton HR Bridge v0.4.0

Android application ID:

de.cankoprulu.pebbletonhrbridge

Beginning with v0.4.0, the Android package/application ID was changed from:

com.example.canspebbletonhrbridge

to:

de.cankoprulu.pebbletonhrbridge

This change gives the project a unique, publication-ready Android application ID and avoids conflicts with the previous com.example namespace.

Because Android identifies applications by their application ID, v0.4.0 is treated as a different Android application from builds using the old package ID. Older beta builds may therefore coexist with v0.4.0 instead of being updated in place.

Pebble

Can's HR Sender for Pebble v0.2.0

The Pebble watch-app UUID remains:

49b5977c-c9d1-4819-9410-0b7c2a9716f9

The AppMessage heart-rate key remains:

10000

The Pebble companion-app metadata must eventually reference the new Android package ID before normal Pebble-to-Android communication is expected to work with the newly packaged Android app.

How it works

Pebble smartwatch
↓
Can's HR Sender for Pebble
↓
PebbleKit Android
↓
PebbleListenerService
↓
HeartRateState
↓
HrBridgeService
↓
HeartRateBlePeripheral
↓
Bluetooth LE Heart Rate Service
↓
Peloton / compatible BLE client

Main features

Receives heart-rate data from a Pebble smartwatch.

Uses PebbleKit Android 2 for watch-to-phone communication.

Exposes the standard Bluetooth LE Heart Rate Service.

Advertises as a BLE heart-rate peripheral.

Supports BLE Heart Rate Measurement notifications.

Designed for use with Peloton Bike and similar BLE heart-rate clients.

Runs BLE transmission in a foreground service.

BLE transmission continues when the Android Activity is no longer visible.

Keeps the BLE GATT server independent from the Activity lifecycle.

Sends heart-rate keepalive updates approximately once per second.

Reuses the last valid heart-rate value for up to 10 seconds during short Pebble data interruptions.

Keeps the BLE connection open if fresh heart-rate data temporarily stops.

Automatically resumes heart-rate transmission when fresh Pebble data returns.

Uses latest-value BLE notification queuing instead of building up stale heart-rate notifications.

Handles Android 12+ Bluetooth runtime permissions.

Supports the connected-device foreground-service type on modern Android versions.

BLE implementation

The Android app exposes the standard Bluetooth SIG Heart Rate Service.

Heart Rate Service

UUID: 0000180d-0000-1000-8000-00805f9b34fb

Heart Rate Measurement characteristic

UUID: 00002a37-0000-1000-8000-00805f9b34fb

The characteristic uses BLE notifications.

Client Characteristic Configuration Descriptor

UUID: 00002902-0000-1000-8000-00805f9b34fb

The connected BLE client enables notifications through the CCCD.

Heart-rate handling

Heart-rate values accepted by the Android bridge are currently limited to:

30–220 BPM

Every valid Pebble heart-rate message refreshes the internal last-update timestamp, even when the BPM value is identical to the previous value.

This is important because a state-based UI stream may not emit repeated identical values, while the bridge still needs to know that the Pebble is actively supplying fresh measurements.

During an active BLE session:

A new heart-rate value is forwarded immediately.

A keepalive loop runs approximately once per second.

If Pebble data temporarily stops, the last valid BPM may continue to be sent for up to 10 seconds.

After more than 10 seconds without a fresh Pebble message, no further heart-rate notifications are sent.

The BLE GATT connection itself is intentionally kept open.

When fresh Pebble data returns, heart-rate notifications resume automatically.

A missing heart-rate value is never encoded as a fake BPM value.

Android background operation

Starting with v0.4.0, BLE transmission is owned by HrBridgeService, not by MainActivity.

This avoids terminating the BLE peripheral simply because:

the display turns off,

Android recreates the Activity,

the user temporarily leaves the app,

or the Activity lifecycle changes during a workout.

HrBridgeService runs as a connected-device foreground service while transmission is active.

Session behavior

When the app is opened, it can start the Pebble watch app automatically.

The normal workflow is:

Open Can's Pebbleton HR Bridge.

Wait until a valid Pebble heart-rate value is shown.

Start heart-rate transmission.

Connect the Peloton or another BLE client.

Leave the bridge running during the workout.

Use End session & close when the session is finished.

A full session end resets the current heart rate, the last known heart rate and its timestamp.

Package migration in v0.4.0

The Android application ID changed in v0.4.0.

Old:

com.example.canspebbletonhrbridge

New:

de.cankoprulu.pebbletonhrbridge

The Kotlin namespace was migrated to the same value:

de.cankoprulu.pebbletonhrbridge

The source packages now use:

package de.cankoprulu.pebbletonhrbridge

and the Compose theme package uses:

package de.cankoprulu.pebbletonhrbridge.ui.theme

The visible application name remains:

Can's Pebbleton HR Bridge

The Pebble watch-app UUID and AppMessage key were not changed by this Android package migration.

Compatibility

Current development setup:

Pebble Time 2

Can's HR Sender for Pebble v0.2.0

Pebble/Core mobile environment on Android

Can's Pebbleton HR Bridge v0.4.0

Peloton Bike

Compatibility with other BLE Heart Rate Service clients may work but is not yet guaranteed.

Communication identifiers

Android application ID

de.cankoprulu.pebbletonhrbridge

Previous Android application ID

com.example.canspebbletonhrbridge

Pebble watch-app UUID

49b5977c-c9d1-4819-9410-0b7c2a9716f9

Pebble AppMessage heart-rate key

10000

Latest features

v0.4.0

Moved BLE GATT peripheral ownership from MainActivity to HrBridgeService.

Added a connected-device foreground service for more reliable background operation.

Added approximately 1 Hz heart-rate keepalive transmission.

Added a 10-second grace period using the most recent valid Pebble heart rate.

BLE connection is no longer intentionally closed when the Pebble value temporarily becomes unavailable.

Added automatic resume when fresh Pebble heart-rate data returns.

Improved cleanup of asynchronous BLE callbacks.

Improved BLE advertising and GATT-server lifecycle handling.

Added explicit session reset behavior.

Changed Android application ID from com.example.canspebbletonhrbridge to de.cankoprulu.pebbletonhrbridge.

Migrated Kotlin namespace and source packages to de.cankoprulu.pebbletonhrbridge.

Standardized the Android app name as Can's Pebbleton HR Bridge.

v0.3.0

Improved BLE heart-rate sending efficiency.

Reduced unnecessary BLE notification work.

Added latest-value notification queuing.

Improved behavior when heart-rate values change faster than BLE notifications can complete.

Improved long-session BLE notification handling.

v0.2.0

Improved Android/Pebble workflow.

Added support for starting the Pebble watch app from the Android companion workflow.

Continued development of Peloton-compatible BLE heart-rate forwarding.

Build

The Android project uses Gradle with Kotlin DSL.

Current Android configuration includes:

minSdk: 26
targetSdk: 37
compileSdk: 37
versionName: 0.4.0
versionCode: 4

The current Android application ID is:

de.cankoprulu.pebbletonhrbridge

Privacy

Can's Pebbleton HR Bridge is designed to process heart-rate data locally between the Pebble smartwatch, the Android device and the connected BLE fitness device.

The bridge does not require a cloud service for normal heart-rate forwarding.

Beta status

This project is still under active development and should currently be considered beta software.

Long-duration workout stability, device compatibility and Android background behavior continue to be tested.

Support the project

Can's Pebbleton HR Bridge is free to use.

If you find the project useful and would like to support continued development:

☕ https://buymeacoffee.com/koprulucan