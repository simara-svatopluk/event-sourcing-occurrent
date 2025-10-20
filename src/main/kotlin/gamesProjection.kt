package com.fairtiq

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