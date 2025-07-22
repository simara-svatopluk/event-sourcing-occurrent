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
import org.occurrent.filter.Filter.source
import org.occurrent.subscription.OccurrentSubscriptionFilter.filter
import org.occurrent.subscription.inmemory.InMemorySubscriptionModel
import java.net.URI
import java.net.URI.create
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
            val result = GuessWordCommand("hello", "Minerva").decide(setUp)
            assertThat(result.findFirst().getOrNull()).isEqualTo(GuessedCorrectly("hello", "Minerva"))
        }

        @Test
        fun wrongGuess() {
            val setUp = Stream.of<DomainEvent>(GameStarted("game1", "hello"))
            val result = GuessWordCommand("xxx", "Minerva").decide(setUp)
            assertThat(result.findFirst().getOrNull()).isEqualTo(GuessedWrongly("xxx", "Minerva"))
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
        val cloudEventConverter = JacksonCloudEventConverter<DomainEvent>(
            createObjectMapper(),
            create("com:fairtiq:guess_game")
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

        eventStore.read("game1").toList().map(::println)
    }

    object Games {
        val games: MutableMap<String, GameProgress> = mutableMapOf()

        fun apply(streamId: String, event: DomainEvent) {
            when (event) {
                is GameStarted -> games[streamId] = GameProgress(event.gameId)
                else -> games[streamId] = games.getValue(streamId).evolve(event)
            }
        }
    }

    data class GameProgress(
        val gameId: String,
        val state: State = State.JustStarted,
        val guessesCount: Int = 0,
        val whoWon: String? = null
    ) {
        fun evolve(event: DomainEvent): GameProgress {
            return when (event) {
                is GameStarted -> this
                is GuessedCorrectly -> copy(state = State.Won, guessesCount = guessesCount + 1, whoWon = event.player)
                is GuessedWrongly -> copy(state = State.InProgress, guessesCount = guessesCount + 1)
            }
        }

        enum class State {
            JustStarted,
            InProgress,
            Won,
        }
    }

    @Test
    fun projections() {
        val subscriptionModel = InMemorySubscriptionModel()
        val eventConverter = createEventConverter()

        subscriptionModel.subscribe("printing", filter(source(URI.create("com:fairtiq:guess_game")))) {
            val streamId = it.getExtension("streamid")
            println("stream: $streamId")
            println(it)
            Games.apply(streamId as String, eventConverter.toDomainEvent(it))
            Games.games.forEach { id, game ->
                println("$id: $game")
            }
            println()
        }

        val eventStore = InMemoryEventStore(subscriptionModel)

        val applicationService = GenericApplicationService(eventStore, eventConverter)
        applicationService.execute("game-1") {
            Stream.of(
                GameStarted("game1", "hello"),
                GuessedWrongly("foo", "Minerva"),
                GuessedWrongly("bar", "Livia"),
                GuessedWrongly("xxx", "Minerva"),
                GuessedCorrectly("hello", "Livia"),
            )
        }

        applicationService.execute("game-2") {
            Stream.of(
                GameStarted("game2", "hello"),
                GuessedWrongly("foo", "Livia"),
                GuessedWrongly("bar", "Minerva"),
                GuessedWrongly("xxx", "Livia"),
                GuessedCorrectly("hello", "Minerva"),
            )
        }
        Thread.sleep(100)
    }

    private fun createEventConverter(): JacksonCloudEventConverter<DomainEvent> =
        JacksonCloudEventConverter<DomainEvent>(
            createObjectMapper(),
            URI.create("com:fairtiq:guess_game")
        )

    private fun createObjectMapper(): ObjectMapper = ObjectMapper().apply {
        registerModule(KotlinModule.Builder().build())
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























