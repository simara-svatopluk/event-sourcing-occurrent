package com.fairtiq

typealias Games =  Map<String, GameProgress>

object GamesInMemory {
    var games: Games = mapOf()
    fun applyEvent(streamId: String, event: DomainEvent) {
        games = games.applyEvent(streamId, event)
    }
}

fun Games.applyEvent(streamId: String, event: DomainEvent): Games {
    return when (event) {
        is GameStarted -> this + (streamId to GameProgress(event.gameId))
        else -> this + (streamId to getValue(streamId).evolve(event))
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