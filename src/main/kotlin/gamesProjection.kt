package com.fairtiq

typealias Games =  MutableMap<String, GameProgress>

object GamesInMemory {
    val games: Games = mutableMapOf()
    fun applyEvent(streamId: String, event: DomainEvent) {
        games.applyEvent(streamId, event)
    }
}

fun Games.applyEvent(streamId: String, event: DomainEvent) {
    when (event) {
        is GameStarted -> this[streamId] = GameProgress(event.gameId)
        else -> this[streamId] = getValue(streamId).evolve(event)
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