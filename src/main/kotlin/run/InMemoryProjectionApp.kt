package com.fairtiq.run

import com.fairtiq.*
import com.mongodb.client.MongoClients
import org.occurrent.eventstore.mongodb.nativedriver.EventStoreConfig
import org.occurrent.eventstore.mongodb.nativedriver.MongoEventStore
import org.occurrent.mongodb.timerepresentation.TimeRepresentation
import org.occurrent.retry.RetryStrategy
import org.occurrent.subscription.blocking.durable.catchup.CatchupSubscriptionModel
import org.occurrent.subscription.blocking.durable.catchup.CatchupSubscriptionModelConfig
import org.occurrent.subscription.blocking.durable.catchup.SubscriptionPositionStorageConfig
import org.occurrent.subscription.mongodb.nativedriver.blocking.NativeMongoSubscriptionModel
import java.util.concurrent.Executors


fun main() {
    val eventConverter = createEventConverter()
    val mongoClient = MongoClients.create(mongoUri)
    val database = mongoClient.getDatabase(dbName)

    val eventStore = MongoEventStore(
        mongoClient,
        dbName,
        eventCollectionName,
        EventStoreConfig(TimeRepresentation.RFC_3339_STRING)
    )

    val subscriptionModel = NativeMongoSubscriptionModel(
        database,
        eventCollectionName,
        TimeRepresentation.RFC_3339_STRING,
        Executors.newCachedThreadPool(),
        RetryStrategy.retry().maxAttempts(3)
    )

    val catchup = CatchupSubscriptionModel(
        subscriptionModel, eventStore, CatchupSubscriptionModelConfig(
            SubscriptionPositionStorageConfig.DontUseSubscriptionPositionInStorage()
        )
    )

    catchup.subscribeFromBeginningOfTime("games-catchup") { cloudEvent ->
        val data = String(cloudEvent.data!!.toBytes())
        println("${cloudEvent.type}: $data")
        val streamId = cloudEvent.getExtension("streamid")

        GamesInMemory.applyEvent(streamId as String, eventConverter.toDomainEvent(cloudEvent))

        GamesInMemory.games.forEach { (id, game) ->
            println("$id: $game")
        }
        println()
    }.waitUntilStarted()
}