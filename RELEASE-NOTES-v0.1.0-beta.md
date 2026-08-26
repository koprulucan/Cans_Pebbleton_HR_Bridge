Can's Pebbleton HR Bridge v0.1.0-beta

First public beta release of Can's Pebbleton HR Bridge for Android together with Can's HR Sender for Pebble.

Version 0.1.0-beta introduced the first complete working path from Pebble heart-rate data to a standard Bluetooth Low Energy Heart Rate Service on Android.

What works

Live heart-rate reading on Pebble.

Pebble → Android communication through PebbleKit Android 2.

Live heart-rate display in Can's Pebbleton HR Bridge.

Android BLE Heart Rate Service.

BLE Heart Rate Measurement notifications.

Live heart-rate forwarding to a connected BLE client.

Successfully tested with Pebble Time 2 and Peloton Bike.

Basic architecture

The first beta used the following data path:

Pebble
↓
Can's HR Sender for Pebble
↓
PebbleKit Android
↓
Can's Pebbleton HR Bridge
↓
Bluetooth LE Heart Rate Service
↓
Peloton Bike

BLE standard

Heart Rate Service:

0000180d-0000-1000-8000-00805f9b34fb

Heart Rate Measurement:

00002a37-0000-1000-8000-00805f9b34fb

Client Characteristic Configuration Descriptor:

00002902-0000-1000-8000-00805f9b34fb

Android package identifier

Version 0.1.0-beta used the original Android application ID:

com.example.canspebbletonhrbridge

The migration to:

de.cankoprulu.pebbletonhrbridge

was introduced later in v0.4.0.

The visible Android application name was already:

Can's Pebbleton HR Bridge

Pebble communication identifiers

Pebble watch-app UUID:

49b5977c-c9d1-4819-9410-0b7c2a9716f9

Heart-rate AppMessage key:

10000

Tested with

Pebble Time 2

Xperia 1 VII

Peloton Bike

Beta status

This release was intended for early real-world testing.

Feedback was especially useful for:

connection drops

failures during longer workouts

background-related issues

reconnection problems

other Pebble models that work or fail

Android phones that work or fail

additional fitness devices or apps that work or fail

Later releases added workflow improvements, more efficient BLE notification handling, foreground-service ownership and heart-rate keepalive behavior.