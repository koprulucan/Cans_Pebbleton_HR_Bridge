Can's Pebbleton HR Bridge

Can's Pebbleton HR Bridge is an Android app that forwards heart-rate data from a Pebble smartwatch to compatible Bluetooth Low Energy fitness equipment such as a Peloton Bike.

The project consists of two parts:

Can's Pebbleton HR Bridge — Android BLE bridge

Can's HR Sender for Pebble — Pebble watch app

The Pebble watch provides heart-rate data to Android through PebbleKit. The Android app then exposes a standard Bluetooth Low Energy Heart Rate Service so that compatible fitness equipment can connect to it like a regular BLE heart-rate sensor.


Can's Pebbleton HR Bridge is free to use.

If you find the project useful and would like to support continued development:

☕ https://buymeacoffee.com/koprulucan


Current versions

Android

Can's Pebbleton HR Bridge v0.4.1

Android application ID:

de.cankoprulu.pebbletonhrbridge

The new application ID has been accepted for use with Google Play.

Beginning with v0.4.0, the Android application ID changed from:

com.example.canspebbletonhrbridge

to:

de.cankoprulu.pebbletonhrbridge

Because Android identifies applications by their application ID, builds using the new ID are treated as a different Android application from older beta builds that used the previous com.example package.

Pebble

Can's HR Sender for Pebble v0.2.0

Pebble watch-app UUID:

49b5977c-c9d1-4819-9410-0b7c2a9716f9

AppMessage heart-rate key:

10000

The Pebble companion-app metadata still needs to be updated to reference the new Android application ID before normal Pebble-to-Android communication is expected to work with the newly packaged Android app.

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

Uses PebbleKit Android 2 for Pebble-to-phone communication.

Displays the current Pebble heart rate in the Android app.

Exposes the standard Bluetooth LE Heart Rate Service.

Advertises as a BLE heart-rate peripheral.

Sends Heart Rate Measurement notifications to subscribed BLE clients.

Designed for Peloton Bike and similar BLE heart-rate clients.

Runs active BLE transmission inside a foreground service.

Keeps BLE transmission independent from the Android Activity lifecycle.

Continues operating when the screen turns off or the Activity is recreated.

Sends heart-rate keepalive updates approximately once per second.

Reuses the latest valid heart-rate value for up to 10 seconds during short Pebble data interruptions.

Keeps the BLE GATT connection open if fresh heart-rate data temporarily stops.

Automatically resumes heart-rate notifications when fresh Pebble data returns.

Keeps only the newest pending heart-rate value while a BLE notification is already in flight.

Handles modern Android Bluetooth runtime permissions.

Uses the Android connected-device foreground-service type.

Includes an in-app Privacy Policy.

Includes an optional Buy Me a Coffee support link.

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

This is important because state-based UI streams do not necessarily emit repeated identical values, while the bridge still needs to know that the Pebble is actively supplying fresh measurements.

During an active BLE session:

A fresh heart-rate value is forwarded immediately.

A keepalive loop runs approximately once per second.

If Pebble data temporarily stops, the most recent valid BPM may continue to be sent for up to 10 seconds.

After more than 10 seconds without a fresh Pebble message, heart-rate notifications stop.

The BLE GATT connection itself is intentionally kept open.

When fresh Pebble data returns, heart-rate notifications resume automatically.

A missing heart-rate value is never converted into an artificial or zero BPM value.

Android background operation

BLE transmission is owned by HrBridgeService, not by MainActivity.

This prevents the BLE peripheral from being torn down simply because:

the display turns off,

Android recreates the Activity,

the user temporarily leaves the app,

or the Activity lifecycle changes during a workout.

HrBridgeService runs as a connectedDevice foreground service while transmission is active.

Session behavior

The normal workflow is:

Open Can's Pebbleton HR Bridge.

Wait until a valid Pebble heart-rate value is shown.

Start heart-rate transmission.

Connect the Peloton or another BLE client.

Leave the bridge running during the workout.

Use End session & close when the session is finished.

A full session end clears:

the current heart-rate value,

the last known heart-rate value,

and the timestamp of the most recent Pebble update.

Privacy

Can's Pebbleton HR Bridge processes heart-rate data locally on the Android device.

The app:

receives heart-rate measurements from the Pebble watch,

displays the current value,

exposes the value through the local Bluetooth LE Heart Rate Service,

and forwards it only to the BLE client selected by the user.

The app does not include advertising or analytics SDKs and does not send heart-rate measurements to a developer-operated cloud service.

Heart-rate measurements are not stored in a persistent database by the app.

A complete Privacy Policy is available inside the Android app and in the repository's PRIVACY.md.

Support the project

Can's Pebbleton HR Bridge is free to use.

If you find the project useful and would like to support continued development:

☕ https://buymeacoffee.com/koprulucan

The same support link is also available from the in-app Privacy Policy dialog.

Android package migration

The Android application ID changed in v0.4.0.

Old:

com.example.canspebbletonhrbridge

New:

de.cankoprulu.pebbletonhrbridge

The Kotlin namespace and source packages now use:

de.cankoprulu.pebbletonhrbridge

Compose theme sources use:

de.cankoprulu.pebbletonhrbridge.ui.theme

The visible application name remains:

Can's Pebbleton HR Bridge

The Pebble watch-app UUID and AppMessage key were not changed by this Android package migration.

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

v0.4.1

Added an in-app Privacy Policy.

Added a secondary Privacy Policy action separated from the primary workout controls.

Added an optional Buy Me a Coffee support link that opens in the browser.

Updated the Compose UI to use CansPebbletonHRBridgeTheme.

Cleaned up unused imports and unused helper methods.

Cleaned up unnecessary SDK-version checks made redundant by minSdk 26.

Updated coroutine delay handling to the Kotlin Duration API.

Cleaned up unused exception variables and minor Kotlin/Android Studio warnings.

Prepared the Android project for Google Play policy and disclosure requirements.

Kept the accepted Android application ID de.cankoprulu.pebbletonhrbridge.

v0.4.0

Moved BLE GATT peripheral ownership from MainActivity to HrBridgeService.

Added a connected-device foreground service for more reliable background operation.

Added approximately 1 Hz heart-rate keepalive transmission.

Added a 10-second grace period using the most recent valid Pebble heart rate.

Kept the BLE connection open during short or extended Pebble data gaps.

Added automatic resume when fresh Pebble heart-rate data returns.

Improved cleanup of asynchronous BLE callbacks.

Improved BLE advertising and GATT-server lifecycle handling.

Added explicit full-session reset behavior.

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

Improved the normal Android/Pebble workflow.

Added support for starting the Pebble watch app from the Android app.

Added End session & close.

Improved session cleanup.

v0.1.0-beta

First public beta.

Introduced the basic Pebble → Android → BLE → Peloton heart-rate bridge.

Build

The Android project uses Gradle with Kotlin DSL.

Current Android configuration:

minSdk: 26
targetSdk: 37
compileSdk: 37
versionName: 0.4.1
versionCode: 5
applicationId: de.cankoprulu.pebbletonhrbridge

Compatibility

Current development setup:

Pebble Time 2

Can's HR Sender for Pebble v0.2.0

Pebble/Core mobile environment on Android

Can's Pebbleton HR Bridge v0.4.1

Peloton Bike

Compatibility with other BLE Heart Rate Service clients may work but is not yet guaranteed.

Beta status

This project is still under active development and should currently be considered beta software.

Long-duration workout stability, device compatibility and Android background behavior continue to be tested.