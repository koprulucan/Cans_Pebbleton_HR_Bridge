package de.cankoprulu.pebbletonhrbridge

import io.rebble.pebblekit2.client.BasePebbleListenerService
import io.rebble.pebblekit2.common.model.PebbleDictionary
import io.rebble.pebblekit2.common.model.PebbleDictionaryItem
import io.rebble.pebblekit2.common.model.ReceiveResult
import io.rebble.pebblekit2.common.model.WatchIdentifier
import java.util.UUID


class PebbleListenerService :
    BasePebbleListenerService() {


    companion object {

        /*
         * Muss exakt mit der UUID der Pebble-Watch-App
         * übereinstimmen.
         */
        private val APP_UUID =
            UUID.fromString(
                "49b5977c-c9d1-4819-9410-0b7c2a9716f9"
            )


        /*
         * AppMessage-Key für die Herzfrequenz.
         */
        private const val HEART_RATE_KEY =
            10000u
    }


    /*
     * =========================================================
     * AppMessage von der Pebble empfangen
     * =========================================================
     */

    override suspend fun onMessageReceived(
        watchappUUID: UUID,
        data: PebbleDictionary,
        watch: WatchIdentifier
    ): ReceiveResult {


        /*
         * Nur Nachrichten unserer eigenen Pebble-App
         * verarbeiten.
         */
        if (watchappUUID != APP_UUID) {

            /*
             * Die Nachricht wurde technisch korrekt empfangen.
             * Sie gehört lediglich nicht zu unserer App.
             */
            return ReceiveResult.Ack
        }


        /*
         * HEART_RATE wird von der Pebble als Int32 übertragen.
         */
        val bpm =
            (
                    data[HEART_RATE_KEY]
                            as? PebbleDictionaryItem.Int32
                    )
                ?.value


        /*
         * Nur plausible Werte übernehmen.
         *
         * HeartRateState ist die zentrale Quelle
         * für den aktuellen Pebble-Puls:
         *
         * Pebble
         *   ↓
         * PebbleListenerService
         *   ↓
         * HeartRateState
         *   ↓
         * ├── MainActivity (Anzeige)
         *   └── HrBridgeService (BLE-Übertragung)
         */
        if (
            bpm != null &&
            bpm in 30..220
        ) {

            /*
             * HeartRateState.update() aktualisiert auch dann
             * den Zeitstempel, wenn derselbe BPM-Wert mehrfach
             * hintereinander empfangen wird.
             *
             * Dadurch weiß HrBridgeService, dass weiterhin
             * frische Daten von der Pebble eintreffen.
             */
            HeartRateState.update(
                bpm
            )
        }


        /*
         * Nachricht gegenüber PebbleKit bestätigen.
         */
        return ReceiveResult.Ack
    }


    /*
     * =========================================================
     * Pebble-App wurde geschlossen
     * =========================================================
     */

    override fun onAppClosed(
        watchappUUID: UUID,
        watch: WatchIdentifier
    ) {

        if (watchappUUID != APP_UUID) {
            return
        }


        /*
         * Es gibt aktuell keinen frischen Pebble-Puls mehr.
         *
         * SEHR WICHTIG:
         *
         * Das beendet NICHT den BLE-GATT-Server und trennt
         * NICHT das Peloton.
         *
         * HeartRateState.clear() setzt nur den aktuellen
         * sichtbaren Wert auf null.
         *
         * Der letzte bekannte BPM-Wert und sein Zeitstempel
         * bleiben erhalten, sodass HrBridgeService einen kurzen
         * Aussetzer bis zu 10 Sekunden überbrücken kann.
         *
         * Danach werden keine weiteren HR-Notifications gesendet,
         * die BLE-Verbindung selbst bleibt aber bestehen.
         */
        HeartRateState.clear()
    }
}