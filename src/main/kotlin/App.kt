package com.fairtiq

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.mongodb.client.MongoClients
import org.occurrent.application.converter.jackson.JacksonCloudEventConverter
import org.occurrent.application.service.blocking.generic.GenericApplicationService
import org.occurrent.eventstore.mongodb.nativedriver.EventStoreConfig
import org.occurrent.eventstore.mongodb.nativedriver.MongoEventStore
import org.occurrent.filter.Filter.source
import org.occurrent.mongodb.timerepresentation.TimeRepresentation
import org.occurrent.retry.RetryStrategy
import org.occurrent.subscription.OccurrentSubscriptionFilter.filter
import org.occurrent.subscription.mongodb.nativedriver.blocking.NativeMongoSubscriptionModel
import java.net.URI.create
import java.util.concurrent.Executors
import java.util.stream.Stream

fun main() {
    val mongoUri = "mongodb://admin:secretpass@127.0.0.1:27020/"
    val dbName = "occurrent_demo"
    val eventCollectionName = "events"

    val mongoClient = MongoClients.create(mongoUri)
    val config = EventStoreConfig(TimeRepresentation.RFC_3339_STRING)

    val eventStore = MongoEventStore(mongoClient, dbName, eventCollectionName, config)
    val eventConverter = createEventConverter()

    val subscriptionModel = NativeMongoSubscriptionModel(
        mongoClient.getDatabase(dbName),
        eventCollectionName,
        TimeRepresentation.RFC_3339_STRING,
        Executors.newCachedThreadPool(),
        RetryStrategy.retry().maxAttempts(3)
    )

    subscriptionModel.subscribe("games", filter(source(create("com.fairtiq.guessGame")))) {
        println(String(it.data!!.toBytes()))
        val streamId = it.getExtension("streamid")
        Games.apply(streamId as String, eventConverter.toDomainEvent(it))

        Games.games.forEach { (id, game) ->
            println("$id: $game")
        }
        println()
    }


    val applicationService = GenericApplicationService(eventStore, eventConverter)

    applicationService.execute("game-1") {
        playRandomGame("game-1").stream()
    }

    // Keep the process alive to receive events
    Runtime.getRuntime().addShutdownHook(Thread {
        println("bye!")
    })
    // Block forever
    while (true) Thread.sleep(1_000)
}


private fun createEventConverter(): JacksonCloudEventConverter<DomainEvent> =
    JacksonCloudEventConverter<DomainEvent>(
        createObjectMapper(),
        create("com.fairtiq.guessGame")
    )

private fun createObjectMapper(): ObjectMapper = ObjectMapper().apply {
    registerModule(KotlinModule.Builder().build())
}