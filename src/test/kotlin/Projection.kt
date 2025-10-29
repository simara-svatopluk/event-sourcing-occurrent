package com.fairtiq

import org.assertj.core.api.Assertions.assertThat
import kotlin.test.Test

class Projection {
    @Test
    fun projected() {
        var games: Map<String, GameProgress> = mapOf()
        val testCases = mapOf(
            GameStarted("game-1", "wolf")
                    to GameProgress("game-1", GameProgress.State.JustStarted, 0),

            GuessedWrongly("dog", "Viturin")
                    to GameProgress("game-1", GameProgress.State.InProgress, 1),

            GuessedCorrectly("wolf", "Roberto")
                    to GameProgress("game-1", GameProgress.State.Won, 2),
        )
        testCases.forEach { (event, expected) ->
            val nextGames = games.applyEvent("game-1", event)
            assertThat(nextGames.getValue("game-1")).isEqualTo(expected)
            games = nextGames
        }
    }
}