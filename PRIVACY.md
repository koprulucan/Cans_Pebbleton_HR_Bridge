Privacy Policy — Can's Pebbleton HR Bridge

Last updated: August 26, 2026

Overview

Can's Pebbleton HR Bridge is an Android application that receives heart-rate data from a compatible Pebble smartwatch and forwards that data to a Bluetooth Low Energy heart-rate client selected by the user, such as compatible fitness equipment.

This Privacy Policy explains what data the app accesses, how that data is used, how long it is retained, and when it is shared.

Data accessed and used

The app accesses heart-rate measurements received from the Pebble watch through the Pebble/Core and PebbleKit environment.

Heart-rate data is used only to:

display the current heart rate in the Android app;

provide the app's Bluetooth Low Energy Heart Rate Service; and

forward heart-rate measurements to a BLE client connected by the user.

Health and fitness data

Heart-rate measurements are health and fitness data.

Can's Pebbleton HR Bridge processes these measurements only for the user-requested heart-rate bridge functionality described above.

The app does not use heart-rate measurements for advertising, profiling, marketing or analytics.

Data sharing

Heart-rate measurements are transmitted over Bluetooth Low Energy to the BLE client that the user chooses to connect to the app.

This Bluetooth transmission is a core feature of the app.

Can's Pebbleton HR Bridge does not sell heart-rate data.

The app does not include advertising or analytics SDKs and does not send heart-rate measurements to a developer-operated cloud service.

Data storage and retention

Heart-rate measurements are not stored in a persistent database by Can's Pebbleton HR Bridge.

The current and most recently received heart-rate values may exist temporarily in the app's process memory while a session is active.

A recent value may be reused briefly during a short interruption in Pebble data in order to maintain heart-rate transmission. The current implementation may reuse the most recent valid measurement for up to 10 seconds after the latest real Pebble message.

After that grace period, the app stops sending heart-rate notifications until fresh Pebble data becomes available again.

Using End session & close clears the app's in-memory heart-rate session state.

Bluetooth permissions

Bluetooth permissions are used to:

advertise the Android device as a BLE heart-rate peripheral;

operate the Bluetooth Low Energy Heart Rate Service;

accept a connection from a compatible BLE client; and

transmit heart-rate measurements to the connected BLE client.

Notification permission

On Android versions where notification permission is requested, it is used for the foreground-service notification that keeps active BLE heart-rate transmission running during a workout.

Foreground service

During an active heart-rate transmission session, the app may run a foreground service of type connectedDevice.

This service keeps the BLE heart-rate bridge active when the app's main screen is not visible or the phone display is turned off.

No user account

Can's Pebbleton HR Bridge does not provide or require a user account.

No advertising or analytics

The app does not include advertising SDKs.

The app does not include analytics SDKs.

The app does not use heart-rate data for targeted advertising, profiling or marketing.

Security

Heart-rate processing performed by Can's Pebbleton HR Bridge is local to the Android device, except for the intended Bluetooth Low Energy transmission to the BLE client selected by the user.

Users should connect only to BLE devices they trust.

Data deletion

Because the app does not persistently store heart-rate measurements in its own database, there is no stored heart-rate history to request from the developer or delete from a developer-operated server.

Using End session & close clears the current in-memory heart-rate session state.

Uninstalling the app removes its local application data from the Android device according to normal Android behavior.

Third-party components

The app uses PebbleKit Android 2 and depends on the Pebble/Core environment to receive data from the Pebble watch.

Those components and applications may have their own privacy practices and policies.

Can's Pebbleton HR Bridge does not control the data practices of independent third-party applications or devices.

Children's privacy

Can's Pebbleton HR Bridge is not designed to collect personal information from children and does not provide social, account or advertising features directed at children.

Changes to this Privacy Policy

This Privacy Policy may be updated when app functionality or data-handling behavior changes.

The latest version should be made available with the project and through the application's distribution listing.

Contact

For privacy questions regarding Can's Pebbleton HR Bridge, use the developer contact information published with the application's Google Play listing.

Project support

Can's Pebbleton HR Bridge is free to use.

If you find the project useful and would like to support continued development:

Buy Me a Coffee

https://buymeacoffee.com/koprulucan

Support is optional and is not required to use the app.