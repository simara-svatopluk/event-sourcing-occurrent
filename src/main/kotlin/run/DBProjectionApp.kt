package com.fairtiq.run

import com.fairtiq.GameProgress
import com.fairtiq.Games
import com.fairtiq.applyEvent
import com.fairtiq.createEventConverter
import com.fairtiq.createObjectMapper
import com.fairtiq.dbName
import com.fairtiq.eventCollectionName
import com.fairtiq.mongoUri
import com.fasterxml.jackson.databind.ObjectMapper
import com.mongodb.client.MongoClients
import com.mongodb.client.MongoCollection
import com.mongodb.client.model.ReplaceOptions
import org.bson.Document
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
    val objectMapper = createObjectMapper()

    database.createCollection("games-projection")

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
    val subscriptionId = "games-durable-db"
    val position = positionStorage.read(subscriptionId)

    val (modelToSubscribe, startAt) = if (position == null) {
        catchup to StartAtTime.beginningOfTime()
    } else {
        durable to StartAt.subscriptionModelDefault()
    }

    val gamesCollection = database.getCollection("games-projection")

    modelToSubscribe.subscribe(subscriptionId, filter(source(create("com.fairtiq.guessGame"))), startAt) { cloudEvent ->
        val data = String(cloudEvent.data!!.toBytes())
        println("${cloudEvent.type}: $data")
        val streamId = cloudEvent.getExtension("streamid") as String

        val games: Games = loadGamesFromMong(gamesCollection, objectMapper)

        games.applyEvent(streamId, eventConverter.toDomainEvent(cloudEvent))

        storeGamesToMongo(games, objectMapper, gamesCollection)

        games.forEach { (id, game) ->
            println("$id: $game")
        }
        println()
    }.waitUntilStarted()
}

private fun storeGamesToMongo(
    games: Games,
    objectMapper: ObjectMapper,
    gamesCollection: MongoCollection<Document>
) {
    games.forEach { game ->
        val document = Document("_id", game.key).append("value", objectMapper.writeValueAsString(game.value))

        gamesCollection
            .replaceOne(Document("_id", game.key), document, ReplaceOptions().upsert(true))
    }
}

private fun loadGamesFromMong(
    gamesCollection: MongoCollection<Document>,
    objectMapper: ObjectMapper
): MutableMap<String, GameProgress> = gamesCollection.find().map { document ->
    val id = document["_id"] as String
    val gameProgress = objectMapper.readValue(
        document["value"] as String,
        GameProgress::class.java
    )
    id to gameProgress
}.toMap().toMutableMap()
