package com.fairtiq.domain

import java.util.stream.Stream
import kotlin.streams.asSequence


sealed class DomainEvent
data class GameStarted(val gameId: String, val wordToGuess: String) : DomainEvent()
data class GuessedCorrectly(val guess: String, val player: String) : DomainEvent()
data class GuessedWrongly(val guess: String, val player: String) : DomainEvent()

data class StartNewGameCommand(val gameId: String, val wordToGuess: String) {
    fun decide(events: Stream<DomainEvent>): Stream<DomainEvent> {
        guardStarted(events)

        return GameStarted(gameId, wordToGuess).asStream()
    }

    private fun guardStarted(events: Stream<DomainEvent>) {
        if (events.anyMatch { it is GameStarted }) {
            throw IllegalStateException("Game with ID '$gameId' has already been started.")
        }
    }
}

data class GuessWordCommand(val guess: String, val player: String) {
    fun decide(events: Stream<DomainEvent>): Stream<DomainEvent> {
        val state = events.asSequence().fold(State(), ::evolve)

        requireNotNull(state.gameId)
        requireNotNull(state.wordToGuess)

        return if (state.wordToGuess == guess) {
            GuessedCorrectly(guess, player)
        } else {
            GuessedWrongly(guess, player)
        }.asStream()
    }
    data class State(val gameId: String? = null, val wordToGuess: String? = null)

    private fun evolve(state: State, event: DomainEvent): State {
        return when (event) {
            is GameStarted -> state.copy(gameId = event.gameId, wordToGuess = event.wordToGuess)
            else -> state
        }
    }

}

private fun <T> T.asStream(): Stream<T> {
    return Stream.of(this)
}
