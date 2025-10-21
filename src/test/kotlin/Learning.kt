package com.fairtiq

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import io.cloudevents.CloudEvent
import io.cloudevents.core.builder.CloudEventBuilder
import org.occurrent.application.converter.jackson.JacksonCloudEventConverter
import org.occurrent.application.service.blocking.generic.GenericApplicationService
import org.occurrent.eventstore.api.blocking.EventStream
import org.occurrent.eventstore.inmemory.InMemoryEventStore
import java.net.URI.create
import java.time.Instant
import java.time.ZoneOffset
import java.util.*
import java.util.stream.Stream
import kotlin.test.Test

class Learning {

    @Test
    fun `what is event`() {
        val event = CloudEventBuilder.v1()
            .withId(UUID.randomUUID().toString())
            .withSource(create("com.fairtiq.guessGame"))
            .withType("GameStarted")
            .withTime(Instant.now().atOffset(ZoneOffset.UTC))
            .withDataContentType("application/json")
            .withData("{ \"message\" : \"hello\" }".toByteArray())
            .build()

        val eventStore = InMemoryEventStore()

        eventStore.write("game1", Stream.of(event))

        val eventStream: EventStream<CloudEvent> = eventStore.read("game1")
        eventStream.events().toList().map {
            println(it)
            println(String(it.data!!.toBytes()))
        }
    }

    @Test
    fun `manual lifecycle`() {
        val eventStore = InMemoryEventStore()
        val cloudEventConverter = JacksonCloudEventConverter<DomainEvent>(
            createObjectMapper(),
            create("com.fairtiq.guessGame")
        )

        eventStore.read("game1").let { oldStoredEvents ->
            val oldDomainEvents = oldStoredEvents.events().map(cloudEventConverter::toDomainEvent)
            val newDomainEvents = StartNewGameCommand("game1", "hello").decide(oldDomainEvents)
            val newStoredEvents = newDomainEvents.map(cloudEventConverter::toCloudEvent)
            eventStore.write("game1", newStoredEvents)
        }

        eventStore.read("game1").toList().map(::println)
    }

    @Test
    fun `wrapped into service`() {
        val eventStore = InMemoryEventStore()

        val applicationService = GenericApplicationService(eventStore, createEventConverter())

        applicationService.execute("game1") { events ->
            StartNewGameCommand("game1", "hello").decide(events)
        }
        applicationService.execute("game1") { events ->
            GuessWordCommand("xxx", "Livia").decide(events)
        }
        applicationService.execute("game1") { events ->
            GuessWordCommand("hello", "Minerva").decide(events)
        }

        eventStore.read("game1").toList().map {
            println(it)
            println(String(it.data.toBytes()))
        }
    }

    private fun createEventConverter(): JacksonCloudEventConverter<DomainEvent> =
        JacksonCloudEventConverter<DomainEvent>(
            createObjectMapper(),
            create("com.fairtiq.guessGame")
        )

    private fun createObjectMapper(): ObjectMapper = ObjectMapper().apply {
        registerModule(KotlinModule.Builder().build())
    }


    /**
     * TODO
     * * explain concurrency
     */

    /**
     * Not part of this 101
     * * snapshots (closing books)
     * * dynamic consistency boundary
     */
}
