Can's Pebbleton HR Bridge v0.3.0

Third public beta release of Can's Pebbleton HR Bridge for Android.

Version 0.3.0 focused on making BLE heart-rate forwarding more efficient and improving notification handling during active sessions.

What's new

Improved

Improved the BLE heart-rate sending algorithm.

Reduced unnecessary heart-rate notification work.

Improved efficiency during continuous heart-rate transmission.

Improved handling of repeated heart-rate values.

Improved BLE notification queue behavior.

Improved stability during longer active sessions.

Reduced the chance of stale heart-rate values being queued behind newer measurements.

Improved handling of rapid heart-rate updates while a previous BLE notification was still in progress.

BLE notification behavior

The BLE transmission logic was changed so that the bridge keeps only the latest pending heart-rate value for a connected client instead of building up a queue of outdated BPM values.

Example:

140 BPM is being transmitted
141 BPM arrives
142 BPM arrives
143 BPM arrives

Instead of sending every intermediate value after 140, the bridge can continue with the newest relevant value:

140 → 143

This reduces unnecessary BLE traffic and keeps the transmitted heart rate closer to the most recent Pebble measurement.

Compatibility

Current development and test setup at the time of this release:

Pebble Time 2

Can's HR Sender for Pebble v0.2.0

Pebble/Core mobile environment on Android

Can's Pebbleton HR Bridge v0.3.0

Peloton Bike

Android package identifier

Version 0.3.0 still used the original Android application ID:

com.example.canspebbletonhrbridge

The migration to:

de.cankoprulu.pebbletonhrbridge

was introduced later in v0.4.0.

The visible application name was already:

Can's Pebbleton HR Bridge

Pebble communication identifiers

Pebble watch-app UUID:

49b5977c-c9d1-4819-9410-0b7c2a9716f9

Heart-rate AppMessage key:

10000

Beta status

This is a beta release.

Long-duration BLE stability and real-world workout behavior continued to be tested after this release. Version 0.4.0 later introduced additional work on Android background operation, foreground-service ownership and heart-rate keepalive behavior.