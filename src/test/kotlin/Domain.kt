package com.fairtiq

import org.assertj.core.api.Assertions
import org.junit.jupiter.api.Nested
import java.util.stream.Stream
import kotlin.jvm.optionals.getOrNull
import kotlin.test.Test

@Nested
class Domain {
    @Test
    fun correctGuess() {
        val setUp = Stream.of<DomainEvent>(GameStarted("game1", "hello"))
        val result = GuessWordCommand("hello", "Minerva").decide(setUp)
        Assertions.assertThat(result.findFirst().getOrNull()).isEqualTo(GuessedCorrectly("hello", "Minerva"))
    }

    @Test
    fun wrongGuess() {
        val setUp = Stream.of<DomainEvent>(GameStarted("game1", "hello"))
        val result = GuessWordCommand("xxx", "Minerva").decide(setUp)
        Assertions.assertThat(result.findFirst().getOrNull()).isEqualTo(GuessedWrongly("xxx", "Minerva"))
    }

    @Test
    fun gameAlreadyStarted() {
        val setUp = Stream.of<DomainEvent>(GameStarted("game1", "hello"))
        Assertions.assertThatThrownBy {
            StartNewGameCommand("game1", "hello").decide(setUp)
        }
    }

    @Test
    fun randomGame() {
        val events = playRandomGame()

        println(events.size)
        println(events)
    }
}
