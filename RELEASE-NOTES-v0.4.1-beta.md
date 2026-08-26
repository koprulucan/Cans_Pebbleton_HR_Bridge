Can's Pebbleton HR Bridge v0.4.1

Patch release of Can's Pebbleton HR Bridge for Android.

Version 0.4.1 focuses on Google Play readiness, privacy disclosure, UI cleanup and removal of non-critical Android Studio warnings following the larger v0.4.0 BLE/background-service update.

Android application ID

The Android application ID remains:

de.cankoprulu.pebbletonhrbridge

This package name has been accepted for use with Google Play.

The previous beta package identifier was:

com.example.canspebbletonhrbridge

The package migration itself was introduced in v0.4.0.

What's new

Added

Added an in-app Privacy Policy.

Added a dedicated Privacy Policy action separated from the main workout controls.

Added an optional Buy Me a Coffee support section.

Added a browser link to:

https://buymeacoffee.com/koprulucan

The support link prefers Chrome when available and falls back to the device's normal browser.

Privacy

The in-app Privacy Policy explains that:

heart-rate data is received from the Pebble watch,

heart-rate data is processed locally on the Android device,

the current heart rate is displayed in the app,

heart-rate data is forwarded through the local Bluetooth LE Heart Rate Service to the BLE client selected by the user,

the app does not include advertising or analytics SDKs,

heart-rate measurements are not sent to a developer-operated cloud service,

heart-rate measurements are not stored in a persistent app database,

ending a session clears the app's in-memory heart-rate session state.

A repository-level privacy statement is also provided in PRIVACY.md for use as the public privacy-policy source.

UI

Moved the Privacy Policy control away from the frequently used workout buttons.

The Privacy Policy action is now presented as a smaller secondary action in the upper-right area of the app.

Added status-bar inset handling so the Privacy Policy action is not covered by Android system UI.

Main workout actions remain centered and visually dominant.

The app now uses CansPebbletonHRBridgeTheme() instead of wrapping the UI in a generic MaterialTheme.

Code cleanup

Removed an unused theme import.

Removed an unused HeartRateBlePeripheral.isActive() helper.

Replaced unused exception variables with _.

Removed SDK checks that are unnecessary because the project now has minSdk 26.

Simplified foreground-service cleanup.

Simplified stopBridge() because its final status was always Stopped.

Updated coroutine delay usage to Kotlin's Duration API.

Updated URI handling to the Android KTX String.toUri() extension.

Removed minor Android Studio/Kotlin warnings without changing BLE behavior.

BLE behavior

The BLE architecture introduced in v0.4.0 remains unchanged.

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

HrBridgeService continues to own the BLE GATT peripheral independently of the Activity lifecycle.

Keepalive behavior

The v0.4.0 keepalive behavior remains unchanged:

Fresh heart-rate values are forwarded immediately.

A keepalive check runs approximately once per second.

The latest valid BPM may be reused for up to 10 seconds after the most recent real Pebble message.

After more than 10 seconds without fresh Pebble data, heart-rate notifications stop.

The BLE connection itself remains open.

When fresh Pebble data returns, transmission resumes automatically.

No artificial or zero BPM value is sent when heart-rate data is unavailable.

Foreground service

The app continues to use a connectedDevice foreground service for active BLE heart-rate transmission.

The Android manifest declares:

FOREGROUND_SERVICE
FOREGROUND_SERVICE_CONNECTED_DEVICE

and HrBridgeService uses:

android:foregroundServiceType="connectedDevice"

The corresponding foreground-service use and justification must also be declared in Google Play Console for release.

Pebble companion note

The Pebble watch app has not yet been updated as part of the Android package migration.

Current Pebble watch-app UUID:

49b5977c-c9d1-4819-9410-0b7c2a9716f9

Current heart-rate AppMessage key:

10000

The Pebble companion metadata still needs to reference:

de.cankoprulu.pebbletonhrbridge

before the newly packaged Android app is expected to receive normal companion communication from the Pebble watch app.

Build information

versionName: 0.4.1
versionCode: 5
applicationId: de.cankoprulu.pebbletonhrbridge
minSdk: 26
targetSdk: 37
compileSdk: 37

Compatibility

Current development and test setup:

Pebble Time 2

Can's HR Sender for Pebble v0.2.0

Pebble/Core mobile environment on Android

Can's Pebbleton HR Bridge v0.4.1

Peloton Bike

Beta status

This remains a beta release.

Version 0.4.1 primarily prepares the Android app for distribution and policy compliance while preserving the BLE and heart-rate behavior introduced in v0.4.0.