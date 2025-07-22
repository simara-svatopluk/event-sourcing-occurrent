import io.cloudevents.CloudEvent
import io.cloudevents.core.builder.CloudEventBuilder
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Nested
import org.occurrent.eventstore.api.blocking.EventStream
import org.occurrent.eventstore.inmemory.InMemoryEventStore
import java.net.URI
import java.time.Instant
import java.time.ZoneOffset
import java.util.stream.Stream
import kotlin.test.Test


sealed class DomainEvent
data class GameStarted(val gameId: String, val wordToGuess: String) : DomainEvent()
data class GuessedCorrectly(val gameId: String, val guess: String) : DomainEvent()
data class GuessedWrongly(val gameId: String, val guess: String) : DomainEvent()

data class StartNewGameCommand(val gameId: String, val wordToGuess: String) {
    fun decide(events: Sequence<DomainEvent>): Sequence<GameStarted> {
        guardStarted(events)

        return GameStarted(gameId, wordToGuess).asSequence()
    }

    private fun guardStarted(events: Sequence<DomainEvent>) {
        if (events.any { it is GameStarted }) {
            throw IllegalStateException("Game with ID '$gameId' has already been started.")
        }
    }
}

private fun <T> T.asSequence(): Sequence<T> {
    return sequenceOf(this)
}

data class GuessWordCommand(val guess: String) {
    fun decide(events: Sequence<DomainEvent>): Sequence<DomainEvent> {
        val state = events.fold(State()) { acc, event ->
            evolve(acc, event)
        }

        requireNotNull(state.gameId)
        requireNotNull(state.wordToGuess)

        return if (state.wordToGuess == guess) {
            GuessedCorrectly(state.gameId, guess)
        } else {
            GuessedWrongly(state.gameId, guess)
        }.asSequence()
    }

    data class State(val gameId: String? = null, val wordToGuess: String? = null)

    private fun evolve(state: State, event: DomainEvent): State {
        return when (event) {
            is GameStarted -> state.copy(gameId = event.gameId, wordToGuess = event.wordToGuess)
            else -> state
        }
    }
}

class Learning {

    @Test
    fun foo() {
        val event = CloudEventBuilder.v1()
            .withId("eventId")
            .withSource(URI.create("urn:mydomain"))
            .withType("HelloWorld")
            .withTime(Instant.now().atOffset(ZoneOffset.UTC))
            .withSubject("subject")
            .withDataContentType("application/json")
            .withData("{ \"message\" : \"hello\" }".toByteArray())
            .build();

        val eventStore = InMemoryEventStore()

        val eventStreamBefore: EventStream<CloudEvent> = eventStore.read("user1")

        eventStore.write(
            "user1",
            eventStreamBefore.version(),
            Stream.of(event)
        )

        val eventStream: EventStream<CloudEvent> = eventStore.read("user1")
        println(eventStream.events().findFirst())
    }
}


@Nested
class Domain {
    @Test
    fun correctGuess() {
        val events = StartNewGameCommand("game1", "hello").decide(sequenceOf())
        val result = GuessWordCommand("hello").decide(events)
        assertThat(result.first()).isEqualTo(GuessedCorrectly("game1", "hello"))
    }

    @Test
    fun wrongGuess() {
        val events = StartNewGameCommand("game1", "hello").decide(sequenceOf())
        val result = GuessWordCommand("xxx").decide(events)
        assertThat(result.first()).isEqualTo(GuessedWrongly("game1", "xxx"))
    }

    @Test
    fun gameAlreadyStarted() {
        val events = sequenceOf(GameStarted("game1", "hello"))
        assertThatThrownBy {
            StartNewGameCommand("game1", "hello").decide(events)
        }
    }
}





















