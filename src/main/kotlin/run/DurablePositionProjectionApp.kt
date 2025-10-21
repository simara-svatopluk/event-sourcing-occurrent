package com.fairtiq.run

import com.fairtiq.Games
import com.fairtiq.createEventConverter
import com.fairtiq.dbName
import com.fairtiq.eventCollectionName
import com.fairtiq.mongoUri
import com.mongodb.client.MongoClients
import org.occurrent.eventstore.mongodb.nativedriver.EventStoreConfig
import org.occurrent.eventstore.mongodb.nativedriver.MongoEventStore
import org.occurrent.filter.Filter.source
import org.occurrent.mongodb.timerepresentation.TimeRepresentation
import org.occurrent.subscription.OccurrentSubscriptionFilter.filter
import org.occurrent.subscription.StartAt
import org.occurrent.subscription.blocking.durable.DurableSubscriptionModel
import org.occurrent.subscription.blocking.durable.catchup.CatchupSubscriptionModel
import org.occurrent.subscription.blocking.durable.catchup.CatchupSubscriptionModelConfig
import org.occurrent.subscription.blocking.durable.catchup.StartAtTime
import org.occurrent.subscription.blocking.durable.catchup.SubscriptionPositionStorageConfig.useSubscriptionPositionStorage
import org.occurrent.subscription.mongodb.nativedriver.blocking.NativeMongoSubscriptionModel
import org.occurrent.subscription.mongodb.nativedriver.blocking.NativeMongoSubscriptionPositionStorage
import java.net.URI.create
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
        Executors.newCachedThreadPool()
    )

    val positionStorage = NativeMongoSubscriptionPositionStorage(database, "subscription-positions")
    val durable = DurableSubscriptionModel(subscriptionModel, positionStorage)
    val catchup = CatchupSubscriptionModel(
        durable, eventStore, CatchupSubscriptionModelConfig(
            useSubscriptionPositionStorage(positionStorage)
        )
    )
    val position = positionStorage.read("games-durable")

    val (modelToSubscribe, startAt) = if (position == null) {
        catchup to StartAtTime.beginningOfTime()
    } else {
        durable to StartAt.subscriptionModelDefault()
    }

    modelToSubscribe.subscribe("games-durable", filter(source(create("com.fairtiq.guessGame"))), startAt) {
        val data = String(it.data!!.toBytes())
        println("${it.type}: $data")
        val streamId = it.getExtension("streamid")

        Games.apply(streamId as String, eventConverter.toDomainEvent(it))

        Games.games.forEach { (id, game) ->
            println("$id: $game")
        }
        println()
    }.waitUntilStarted()
}
