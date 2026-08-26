Can's Pebbleton HR Bridge v0.2.0

Second public beta release of Can's Pebbleton HR Bridge for Android together with Can's HR Sender for Pebble.

Version 0.2.0 improves the normal session workflow between the Pebble watch app and the Android BLE bridge.

What works

Live heart-rate reading on Pebble.

Pebble → Android communication through PebbleKit Android 2.

Live heart-rate display in Can's Pebbleton HR Bridge.

Android BLE Heart Rate Service.

Live heart-rate forwarding to a connected BLE client.

Successfully tested with Pebble Time 2 and Peloton Bike.

What's new in v0.2.0

Added

Added a new app icon.

Added End session & close.

Improved

Can's Pebbleton HR Bridge now automatically starts the watch app when the Android app is opened.

End session & close now stops the BLE heart-rate bridge, closes the watch app and closes Can's Pebbleton HR Bridge.

Improved the companion workflow between the Android app and the Pebble watch app.

Tested with

Pebble Time 2

Xperia 1 VII

Peloton Bike

Android package identifier

Version 0.2.0 used the original Android application ID:

com.example.canspebbletonhrbridge

The migration to:

de.cankoprulu.pebbletonhrbridge

was introduced later in v0.4.0.

The visible Android application name was:

Can's Pebbleton HR Bridge

Pebble communication identifiers

Pebble watch-app UUID:

49b5977c-c9d1-4819-9410-0b7c2a9716f9

Heart-rate AppMessage key:

10000

Beta status

This release is intended for real-world testing.

Feedback is especially useful for:

connection drops

failures after long workouts

background-related issues

reconnection problems

other Pebble models that work or fail

Android phones that work or fail

additional fitness devices or apps that work or fail

This version is still being tested in real-world workouts. Feedback and bug reports are welcome.