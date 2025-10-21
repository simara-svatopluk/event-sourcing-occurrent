package com.fairtiq.run

import com.fairtiq.createEventConverter
import com.fairtiq.dbName
import com.fairtiq.eventCollectionName
import com.fairtiq.mongoUri
import com.fairtiq.playRandomGame
import com.mongodb.client.MongoClients
import org.occurrent.application.service.blocking.generic.GenericApplicationService
import org.occurrent.eventstore.mongodb.nativedriver.EventStoreConfig
import org.occurrent.eventstore.mongodb.nativedriver.MongoEventStore
import org.occurrent.mongodb.timerepresentation.TimeRepresentation
import java.util.stream.Stream

fun main() {
    val mongoClient = MongoClients.create(mongoUri)

    val eventStore = MongoEventStore(
        mongoClient,
        dbName,
        eventCollectionName,
        EventStoreConfig(TimeRepresentation.RFC_3339_STRING)
    )

    val applicationService = GenericApplicationService(eventStore, createEventConverter())

    (1..100).forEach { gameNumber ->
        println("Game $gameNumber started. Generating events...")

        playRandomGame("game-$gameNumber").forEach { event ->
            applicationService.execute("game-$gameNumber") {
                Stream.of(event).also {
                    Thread.sleep(500)
                }
            }
        }
        println("Game ended. Press Enter for next game.")
        readln();
    }
}