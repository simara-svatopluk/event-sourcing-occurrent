import com.fairtiq.domain.DomainEvent
import com.fairtiq.domain.GameStarted
import com.fairtiq.domain.GuessWordCommand
import com.fairtiq.domain.GuessedCorrectly
import com.fairtiq.domain.GuessedWrongly
import com.fairtiq.domain.StartNewGameCommand
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import io.cloudevents.CloudEvent
import io.cloudevents.core.builder.CloudEventBuilder
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Nested
import org.occurrent.application.converter.jackson.JacksonCloudEventConverter
import org.occurrent.application.service.blocking.generic.GenericApplicationService
import org.occurrent.eventstore.api.blocking.EventStream
import org.occurrent.eventstore.inmemory.InMemoryEventStore
import java.net.URI
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID
import java.util.stream.Stream
import kotlin.jvm.optionals.getOrNull
import kotlin.test.Test

class Learning {

    @Test
    fun `what is event`() {
        val event = CloudEventBuilder.v1()
            .withId(UUID.randomUUID().toString())
            .withSource(URI.create("com:fairtiq:guess_game"))
            .withType("GameStarted")
            .withTime(Instant.now().atOffset(ZoneOffset.UTC))
            .withDataContentType("application/json")
            .withData("{ \"message\" : \"hello\" }".toByteArray())
            .build();

        val eventStore = InMemoryEventStore()

        eventStore.write("game1", Stream.of(event))

        val eventStream: EventStream<CloudEvent> = eventStore.read("game1")
        eventStream.events().toList().map {
            println(it)
            println(String(it.data!!.toBytes()))
        }
    }

    @Nested
    inner class Domain {
        @Test
        fun correctGuess() {
            val setUp = Stream.of<DomainEvent>(GameStarted("game1", "hello"))
            val result = GuessWordCommand("hello").decide(setUp)
            assertThat(result.findFirst().getOrNull()).isEqualTo(GuessedCorrectly("game1", "hello"))
        }

        @Test
        fun wrongGuess() {
            val setUp = Stream.of<DomainEvent>(GameStarted("game1", "hello"))
            val result = GuessWordCommand("xxx").decide(setUp)
            assertThat(result.findFirst().getOrNull()).isEqualTo(GuessedWrongly("game1", "xxx"))
        }

        @Test
        fun gameAlreadyStarted() {
            val setUp = Stream.of<DomainEvent>(GameStarted("game1", "hello"))
            assertThatThrownBy {
                StartNewGameCommand("game1", "hello").decide(setUp)
            }
        }
    }

    @Test
    fun `manual lifecycle`() {
        val eventStore = InMemoryEventStore()
        val objectMapper = ObjectMapper().apply {
            registerModule(KotlinModule.Builder().build())
        }
        val cloudEventConverter = JacksonCloudEventConverter<DomainEvent>(
            objectMapper,
            URI.create("com:fairtiq:guess_game")
        )

        eventStore.read("game1").let { oldStoredEvents ->
            val oldDomainEvents = oldStoredEvents.events().map(cloudEventConverter::toDomainEvent)
            val newDomainEvents = StartNewGameCommand("game1", "hello").decide(oldDomainEvents)
            val newStoredEvents = newDomainEvents.map(cloudEventConverter::toCloudEvent)
            eventStore.write("game1", newStoredEvents)
        }
//
//        eventStore.read("game1").let { oldStoredEvents ->
//            val oldDomainEvents = oldStoredEvents.events().map(cloudEventConverter::toDomainEvent)
//            val newDomainEvents = GuessWordCommand("hello").decide(oldDomainEvents)
//            val newStoredEvents = newDomainEvents.map(cloudEventConverter::toCloudEvent)
//            eventStore.write("game1", newStoredEvents)
//        }

        eventStore.read("game1").toList().map(::println)
    }

    @Test
    fun `wrapped into service`() {
        val eventStore = InMemoryEventStore()
        val objectMapper = ObjectMapper().apply {
            registerModule(KotlinModule.Builder().build())
        }
        val cloudEventConverter = JacksonCloudEventConverter<DomainEvent>(
            objectMapper,
            URI.create("com:fairtiq:guess_game")
        )

        val applicationService = GenericApplicationService(eventStore, cloudEventConverter)

        applicationService.execute("game1") { events ->
            StartNewGameCommand("game1", "hello").decide(events)
        }
        applicationService.execute("game1") { events ->
            GuessWordCommand("xxx").decide(events)
        }

        applicationService.execute("game1") { events ->
            GuessWordCommand("hello").decide(events)
        }

        eventStore.read("game1").toList().map(::println)
    }

    /**
     * TODO
     * * MongoDB
     * * explain concurrency
     */

    /**
     * Not part of this 101
     * * snapshots (closing books)
     * * dynamic consistency boundary
     */
}























