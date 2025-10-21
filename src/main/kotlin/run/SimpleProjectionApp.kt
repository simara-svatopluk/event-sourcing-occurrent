package com.fairtiq.run

import com.fairtiq.GamesInMemory
import com.fairtiq.createEventConverter
import com.fairtiq.dbName
import com.fairtiq.eventCollectionName
import com.fairtiq.mongoUri
import com.mongodb.client.MongoClients
import org.occurrent.filter.Filter.source
import org.occurrent.mongodb.timerepresentation.TimeRepresentation
import org.occurrent.retry.RetryStrategy
import org.occurrent.subscription.OccurrentSubscriptionFilter.filter
import org.occurrent.subscription.mongodb.nativedriver.blocking.NativeMongoSubscriptionModel
import java.net.URI.create
import java.util.concurrent.Executors

fun main() {
    val eventConverter = createEventConverter()
    val mongoClient = MongoClients.create(mongoUri)

    val subscriptionModel = NativeMongoSubscriptionModel(
        mongoClient.getDatabase(dbName),
        eventCollectionName,
        TimeRepresentation.RFC_3339_STRING,
        Executors.newCachedThreadPool(),
        RetryStrategy.retry().maxAttempts(3)
    )

    subscriptionModel.subscribe("games", filter(source(create("com.fairtiq.guessGame")))) {
        val data = String(it.data!!.toBytes())
        println("${it.type}: $data")
        val streamId = it.getExtension("streamid")

        GamesInMemory.applyEvent(streamId as String, eventConverter.toDomainEvent(it))

        GamesInMemory.games.forEach { (id, game) ->
            println("$id: $game")
        }
        println()
    }
}