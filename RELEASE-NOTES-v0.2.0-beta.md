# v0.1.0-beta

First public beta release of **Pebble HR Sender** and **Pebble HR Bridge**.

## What works

- live heart-rate reading on Pebble
- Pebble → Android communication through PebbleKit Android 2
- live heart-rate display in Pebble HR Bridge
- Android BLE Heart Rate Service
- live heart-rate forwarding to a connected BLE client
- successfully tested with Pebble Time 2 and Peloton Bike

## Beta status

This release is intended for real-world testing.

Please report:

- connection drops
- failures after long workouts
- background-related issues
- reconnection problems
- other Pebble models that work or fail
- Android phones that work or fail
- additional fitness devices/apps that work or fail

## What's new in v0.2.0

### Added
- Added a new app icon.
- Added **End session & close**.

### Improved
- Pebble HR Bridge now automatically starts the watch app when the Android app is opened.
- **End session & close** now stops the BLE heart-rate bridge, closes the watch app, and closes Pebble HR Bridge.

## Tested with
- Pebble Time 2
- Xperia 1 VII
- Peloton Bike

## Beta status
This version is still being tested in real-world workouts. Feedback and bug reports are welcome.
