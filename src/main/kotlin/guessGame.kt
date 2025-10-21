package com.fairtiq

import java.util.stream.Stream
import kotlin.streams.asSequence

// Events: The gold!
sealed class DomainEvent
data class GameStarted(val gameId: String, val wordToGuess: String) : DomainEvent()
data class GuessedCorrectly(val guess: String, val player: String) : DomainEvent()
data class GuessedWrongly(val guess: String, val player: String) : DomainEvent()


// Write model
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


// "real" work
fun playGame(
    gameId: String,
    players: List<String>,
    wordToGuess: String,
    wordsGuessed: List<String>
): List<DomainEvent> {
    val init = listOf<DomainEvent>(GameStarted(gameId, wordToGuess))

    val events = wordsGuessed.fold(init) { events, it ->
        val lastEvent = events.last()
        if (lastEvent is GuessedCorrectly) {
            return@fold events
        }

        val currentPlayer = when (lastEvent) {
            is GuessedWrongly -> players.nextPlayer(lastEvent.player)
            is GameStarted -> players.first()
            is GuessedCorrectly -> error("This is not possible")
        }
        val newEvents = GuessWordCommand(it, currentPlayer).decide(events.stream())
        events + newEvents.toList()
    }
    return events
}

private fun List<String>.nextPlayer(current: String) = get((indexOf(current) + 1) % size)

fun playRandomGame(gameId: String = "game1", wordCount: Int = 5): List<DomainEvent> {

    val words = setOf(
        "horizon",
        "cascade",
        "ember",
        "whisper",
        "compass",
        "harbor",
        "journey",
        "meadow",
        "serenity",
        "anchor",
        "twilight",
        "echo",
        "velocity",
        "wander",
        "silhouette",
        "haven",
        "momentum",
        "radiant",
        "solace",
        "pulse"
    )

    val shuffled = words.shuffled().take(wordCount)
    val wordToGuess = shuffled.random()
    val players = listOf("Roberto", "Viturin")

    val events = playGame(gameId, players, wordToGuess, shuffled)
    return events
}

private fun <T> T.asStream(): Stream<T> {
    return Stream.of(this)
}