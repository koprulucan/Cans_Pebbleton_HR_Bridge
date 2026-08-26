package de.cankoprulu.pebbletonhrbridge

import android.os.SystemClock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow


object HeartRateState {

    /*
     * Aktuell gültiger Pebble-Puls.
     *
     * null bedeutet:
     * Aktuell liegt kein frischer Pulswert von der Pebble vor.
     */
    private val _heartRate =
        MutableStateFlow<Int?>(null)

    val heartRate: StateFlow<Int?> =
        _heartRate.asStateFlow()


    /*
     * Letzter gültiger Pulswert.
     *
     * Dieser bleibt auch nach clear() erhalten.
     *
     * Dadurch kann HrBridgeService bei einem kurzen Aussetzer
     * den letzten Wert bis zu 10 Sekunden lang weitergeben.
     */
    @Volatile
    var lastKnownHeartRate: Int? = null
        private set


    /*
     * Zeitpunkt, zu dem zuletzt wirklich eine gültige
     * Herzfrequenz-Nachricht von der Pebble eingetroffen ist.
     *
     * elapsedRealtime() ist hierfür besser als die normale
     * Systemuhrzeit, da manuelle Zeitänderungen keinen Einfluss
     * auf die Altersberechnung haben.
     */
    @Volatile
    var lastUpdateElapsedRealtime: Long = 0L
        private set


    /*
     * =========================================================
     * Neuen Pebble-Puls übernehmen
     * =========================================================
     */

    fun update(
        bpm: Int
    ) {

        /*
         * Nur plausible Herzfrequenzwerte übernehmen.
         */
        if (bpm !in 30..220) {
            return
        }


        /*
         * WICHTIG:
         *
         * Den Zeitstempel bei JEDER gültigen Pebble-Nachricht
         * aktualisieren.
         *
         * Auch wenn beispielsweise mehrfach hintereinander
         * exakt 149 BPM empfangen werden.
         *
         * StateFlow selbst muss bei einem identischen Wert kein
         * neues Event erzeugen, der Zeitstempel zeigt uns aber
         * trotzdem, dass die Pebble weiterhin frische Daten
         * liefert.
         */
        lastUpdateElapsedRealtime =
            SystemClock.elapsedRealtime()


        lastKnownHeartRate =
            bpm


        _heartRate.value =
            bpm
    }


    /*
     * =========================================================
     * Aktuell kein frischer Pebble-Puls
     * =========================================================
     *
     * clear() bedeutet ausdrücklich NICHT:
     *
     * - Session beenden
     * - BLE stoppen
     * - letzten Wert vergessen
     *
     * Der letzte bekannte Wert und sein Zeitstempel bleiben
     * erhalten, damit HrBridgeService kurze Aussetzer überbrücken
     * kann.
     */

    fun clear() {

        _heartRate.value =
            null
    }


    /*
     * =========================================================
     * Session vollständig zurücksetzen
     * =========================================================
     *
     * Wird verwendet, wenn der Benutzer ausdrücklich
     * "End session & close" auswählt.
     *
     * Dann sollen wirklich alle Herzfrequenzdaten der alten
     * Session verworfen werden.
     */

    fun reset() {

        _heartRate.value =
            null


        lastKnownHeartRate =
            null


        lastUpdateElapsedRealtime =
            0L
    }
}