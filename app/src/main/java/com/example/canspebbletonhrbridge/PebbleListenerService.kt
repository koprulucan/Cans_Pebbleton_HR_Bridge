package com.example.canspebbletonhrbridge

import io.rebble.pebblekit2.client.BasePebbleListenerService
import io.rebble.pebblekit2.common.model.PebbleDictionary
import io.rebble.pebblekit2.common.model.PebbleDictionaryItem
import io.rebble.pebblekit2.common.model.ReceiveResult
import io.rebble.pebblekit2.common.model.WatchIdentifier
import java.util.UUID

class PebbleListenerService : BasePebbleListenerService() {

    companion object {
        private val APP_UUID =
            UUID.fromString("49b5977c-c9d1-4819-9410-0b7c2a9716f9")

        private const val HEART_RATE_KEY = 10000u
    }

    override suspend fun onMessageReceived(
        watchappUUID: UUID,
        data: PebbleDictionary,
        watch: WatchIdentifier
    ): ReceiveResult {

        if (watchappUUID == APP_UUID) {

            val bpm =
                (data[HEART_RATE_KEY] as? PebbleDictionaryItem.Int32)
                    ?.value

            if (bpm != null && bpm in 30..220) {
                HeartRateState.update(bpm)
            }
        }

        return ReceiveResult.Ack
    }

    override fun onAppClosed(
        watchappUUID: UUID,
        watch: WatchIdentifier
    ) {
        if (watchappUUID == APP_UUID) {
            HeartRateState.clear()
        }
    }
}