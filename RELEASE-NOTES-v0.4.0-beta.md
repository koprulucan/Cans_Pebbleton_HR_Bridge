# Pebble HR Bridge v0.4.0

Fourth public beta release of **Pebble HR Bridge** for Android.

This release focuses on connection stability during longer workouts and reliable operation while the phone display is off or the app is in the background.

## What's new

### Added

* Added a dedicated Android foreground service for active heart-rate transmission.
* BLE GATT server and advertising now run independently of `MainActivity`.
* Added a 1 Hz heart-rate keepalive for improved compatibility with BLE fitness equipment.
* During short interruptions, the last known valid heart rate can continue to be transmitted for up to 10 seconds.
* Added persistent transmission state so reopening the Android activity reflects an already running session.
* Added a foreground-service notification while heart-rate transmission is active.

### Improved

* Improved connection stability during longer workouts.
* Improved behavior when the phone display turns off.
* Improved Android background behavior.
* Improved BLE GATT server lifecycle and cleanup.
* Improved handling of delayed Bluetooth callbacks.
* Improved BLE advertising and GATT startup error handling.
* Improved handling of temporary gaps in incoming Pebble heart-rate data.
* Improved protection against BLE clients disconnecting because no new heart-rate notifications were received.

### Changed

* `MainActivity` no longer owns the BLE heart-rate peripheral.
* Active BLE transmission is now managed by `HrBridgeService`.
* Temporary loss of Pebble heart-rate data no longer stops the BLE connection.
* Missing heart-rate data no longer automatically disconnects the connected fitness device.
* The last valid heart-rate value is repeated for up to 10 seconds during short input interruptions.
* After the 10-second grace period, new heart-rate notifications pause while the BLE connection itself remains active.

## Connection architecture

The Android bridge now uses:

```text
Pebble
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
Peloton / other BLE client
```

The BLE peripheral is therefore no longer tied to the Android activity lifecycle.

## Background operation

Active heart-rate transmission now runs as an Android foreground service of type `connectedDevice`.

This allows the BLE GATT server and advertising session to remain active when:

* the phone display turns off
* the Android activity enters the background
* the activity is recreated
* the user temporarily switches away from the app

The transmission is stopped explicitly through the app's session controls rather than through the activity lifecycle.

## Heart-rate keepalive

Pebble HR Bridge now maintains regular BLE heart-rate notifications during short gaps in incoming data.

Example:

```text
Last real Pebble value: 149 BPM

+1 s  → 149
+2 s  → 149
+3 s  → 149
...
+10 s → 149

After 10 seconds:
heart-rate notifications pause
BLE connection remains active
```

When fresh Pebble data returns, live transmission resumes immediately without intentionally rebuilding the BLE connection.

## Compatibility

Current test setup:

* Pebble Time 2
* **Can's HR Sender for Pebble v0.2.0**
* Pebble/Core mobile app on Android
* **Pebble HR Bridge v0.4.0**
* Bluetooth LE Heart Rate Service
* Peloton Bike

## Privacy

Heart-rate forwarding remains completely local:

```text
Pebble → Android phone → Bluetooth LE client
```

No cloud service is required for heart-rate forwarding.

Can's Pebbleton HR Bridge v0.4.0

Fourth public beta release of Can's Pebbleton HR Bridge for Android.

Version 0.4.0 focuses on long-session BLE stability, Android background operation and migration to a unique Android application ID suitable for future distribution.

Important package-name change

The Android application ID has changed in this release.

Previous application ID:

com.example.canspebbletonhrbridge

New application ID:

de.cankoprulu.pebbletonhrbridge

The Kotlin namespace and Android source packages have also been migrated to:

de.cankoprulu.pebbletonhrbridge

This removes the old com.example namespace and gives the project a dedicated application identifier.

Upgrade note

Android identifies applications by their application ID.

Because v0.4.0 uses a new application ID, Android treats it as a different application from older beta builds that used:

com.example.canspebbletonhrbridge

An older beta and v0.4.0 may therefore coexist on the same device rather than v0.4.0 replacing the old installation.

The visible application name remains:

Can's Pebbleton HR Bridge

The Pebble watch-app UUID and AppMessage heart-rate key have not changed.

What's new

Added

Added HrBridgeService as a foreground service for BLE heart-rate transmission.

Added Android connectedDevice foreground-service support.

Added approximately 1 Hz BLE heart-rate keepalive transmission.

Added a 10-second grace period using the last valid Pebble heart-rate value.

Added explicit full-session reset behavior.

Added service-backed transmission state so reopening or recreating the Activity reflects the currently running bridge service.

Improved

Improved BLE stability when the Android display is turned off.

Improved behavior when the app Activity moves into the background.

Improved handling of Activity recreation during active workouts.

Improved BLE GATT server lifecycle management.

Improved BLE advertising cleanup.

Improved handling of delayed asynchronous Bluetooth callbacks.

Improved handling of repeated identical Pebble heart-rate values.

Improved recovery from short gaps in Pebble heart-rate delivery.

Improved BLE notification queuing so only the latest pending value is retained.

Improved cleanup after BLE notification errors.

Changed

BLE GATT peripheral ownership moved from MainActivity to HrBridgeService.

MainActivity no longer stops BLE transmission from onDestroy().

Missing Pebble heart-rate data no longer automatically tears down the BLE connection.

The last valid heart-rate value may be reused for up to 10 seconds after the most recent real Pebble message.

After the 10-second grace period, heart-rate notifications stop but the BLE GATT connection remains available.

Fresh Pebble data can resume transmission without intentionally rebuilding the BLE connection.

Android application ID changed from com.example.canspebbletonhrbridge to de.cankoprulu.pebbletonhrbridge.

Kotlin namespace and source packages changed to de.cankoprulu.pebbletonhrbridge.

Foreground-service notification now uses the full application name Can's Pebbleton HR Bridge.

Architecture

The Android bridge now follows this structure:

Pebble
↓
PebbleListenerService
↓
HeartRateState
├── MainActivity
└── HrBridgeService
↓
HeartRateBlePeripheral
↓
BLE Heart Rate Service
↓
Peloton

MainActivity is now primarily responsible for UI and user commands.

HrBridgeService owns the BLE peripheral and continues running independently of the Activity lifecycle while transmission is active.

Heart-rate keepalive behavior

Every valid Pebble message updates the timestamp of the most recent real measurement, including repeated identical BPM values.

During an active transmission:

Fresh heart-rate changes are forwarded immediately.

The service checks approximately once per second whether a keepalive should be sent.

The latest valid BPM can be reused for up to 10 seconds after the most recent Pebble message.

After more than 10 seconds without fresh Pebble data, the bridge stops sending BPM notifications.

The BLE connection itself remains open.

When fresh Pebble data returns, transmission resumes automatically.

No artificial or zero BPM value is sent when heart-rate data is unavailable.

BLE standard

Heart Rate Service:

0000180d-0000-1000-8000-00805f9b34fb

Heart Rate Measurement:

00002a37-0000-1000-8000-00805f9b34fb

Client Characteristic Configuration Descriptor:

00002902-0000-1000-8000-00805f9b34fb

Compatibility

Current development and test setup:

Pebble Time 2

Can's HR Sender for Pebble v0.2.0

Pebble/Core mobile environment on Android

Can's Pebbleton HR Bridge v0.4.0

Peloton Bike

Pebble companion note

The Pebble watch app itself has not yet changed as part of the Android v0.4.0 package migration.

Its watch-app UUID remains:

49b5977c-c9d1-4819-9410-0b7c2a9716f9

and its heart-rate AppMessage key remains:

10000

However, the Pebble companion-app metadata must be updated to reference:

de.cankoprulu.pebbletonhrbridge

before the newly packaged Android application is expected to receive normal companion communication from the Pebble app.

Beta status

This is still a beta release.

The v0.4.0 changes are specifically intended to improve reliability during longer workouts and periods where the Android UI is not actively displayed.

Further long-duration testing with Pebble and Peloton is recommended before considering the bridge fully stable.