package com.mathgate.core

import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class GateEngineTest {

    private val limitMs = 15 * 60_000L
    private val settings = Settings(limitMs = limitMs)

    private fun newEngine(clock: FakeClock, state: GateState = GateState.Idle(0L)) =
        GateEngine(clock = clock, settings = settings, initialState = state)

    /** Drives the engine to [GateState.ChallengePending] and returns it. */
    private fun reachChallenge(clock: FakeClock, engine: GateEngine): GateState.ChallengePending {
        engine.onEvent(GateEvent.YouTubeForeground)
        clock.advance(limitMs)
        engine.onEvent(GateEvent.Tick)
        return assertIs<GateState.ChallengePending>(engine.currentState)
    }

    private fun wrongAnswer(engine: GateEngine): Int {
        val pending = assertIs<GateState.ChallengePending>(engine.currentState)
        return pending.problem.answer + 1
    }

    @Test
    fun `time accumulates only while watching`() {
        val clock = FakeClock()
        val engine = newEngine(clock)

        clock.advance(60_000)
        engine.onEvent(GateEvent.Tick)
        assertEquals(GateState.Idle(0L), engine.currentState)

        engine.onEvent(GateEvent.YouTubeForeground)
        clock.advance(60_000)
        engine.onEvent(GateEvent.Tick)
        assertEquals(GateState.Counting(60_000L, 120_000L), engine.currentState)
    }

    @Test
    fun `closing and reopening YouTube keeps the accumulated time`() {
        val clock = FakeClock()
        val engine = newEngine(clock)

        engine.onEvent(GateEvent.YouTubeForeground)
        clock.advance(10 * 60_000L)
        engine.onEvent(GateEvent.YouTubeBackground)
        assertEquals(GateState.Idle(600_000L), engine.currentState)

        engine.onEvent(GateEvent.YouTubeForeground)
        clock.advance(5 * 60_000L)
        val (state, effects) = engine.onEvent(GateEvent.Tick)

        assertIs<GateState.ChallengePending>(state)
        assertContains(effects, GateEffect.ShowChallenge)
    }

    @Test
    fun `screen off pauses the counter and screen on resumes it`() {
        val clock = FakeClock()
        val engine = newEngine(clock)

        engine.onEvent(GateEvent.YouTubeForeground)
        clock.advance(60_000)
        engine.onEvent(GateEvent.Tick)
        engine.onEvent(GateEvent.ScreenOff)
        assertEquals(GateState.Idle(60_000L), engine.currentState)

        clock.advance(60_000)
        engine.onEvent(GateEvent.Tick)
        assertEquals(GateState.Idle(60_000L), engine.currentState)

        engine.onEvent(GateEvent.ScreenOn)
        engine.onEvent(GateEvent.Tick)
        assertEquals(GateState.Counting(60_000L, 120_000L), engine.currentState)
    }

    @Test
    fun `reaching the limit starts the challenge and persists it`() {
        val clock = FakeClock()
        val engine = newEngine(clock)

        engine.onEvent(GateEvent.YouTubeForeground)
        clock.advance(limitMs)
        val (state, effects) = engine.onEvent(GateEvent.Tick)

        assertIs<GateState.ChallengePending>(state)
        assertContains(effects, GateEffect.PersistState)
        assertContains(effects, GateEffect.ShowChallenge)
    }

    @Test
    fun `the limit warning fires once shortly before the challenge`() {
        val clock = FakeClock()
        val engine = newEngine(clock)
        engine.onEvent(GateEvent.YouTubeForeground)

        clock.advance(limitMs - 61_000L)
        assertFalse(engine.onEvent(GateEvent.Tick).second.contains(GateEffect.WarnAboutLimit))

        clock.advance(1_000L)
        assertContains(engine.onEvent(GateEvent.Tick).second, GateEffect.WarnAboutLimit)

        clock.advance(1_000L)
        assertFalse(engine.onEvent(GateEvent.Tick).second.contains(GateEffect.WarnAboutLimit))
    }

    @Test
    fun `the limit warning can be disabled`() {
        val clock = FakeClock()
        val engine = GateEngine(
            clock = clock,
            settings = Settings(limitMs = limitMs, warnBeforeMs = 0L),
            initialState = GateState.Idle(0L),
        )
        engine.onEvent(GateEvent.YouTubeForeground)

        clock.advance(limitMs - 1_000L)
        assertFalse(engine.onEvent(GateEvent.Tick).second.contains(GateEffect.WarnAboutLimit))
    }

    @Test
    fun `a pending challenge survives a process restart`() {
        val clock = FakeClock()
        val engine = newEngine(clock)
        val saved = reachChallenge(clock, engine)

        val restored = newEngine(clock, state = saved)
        assertEquals(saved, restored.currentState)

        val (state, effects) = restored.onEvent(GateEvent.YouTubeForeground)
        assertEquals(saved, state)
        assertEquals(0, assertIs<GateState.ChallengePending>(state).attempts)
        assertContains(effects, GateEffect.ShowChallenge)
    }

    @Test
    fun `a wrong answer does not unlock and shows a new problem`() {
        val clock = FakeClock()
        val engine = newEngine(clock)
        val pending = reachChallenge(clock, engine)

        val (state, effects) = engine.onEvent(GateEvent.AnswerSubmitted(pending.problem.answer + 1))
        val next = assertIs<GateState.ChallengePending>(state)

        assertEquals(1, next.attempts)
        assertTrue(next.problem.text != pending.problem.text)
        assertContains(effects, GateEffect.PersistState)
        assertFalse(effects.contains(GateEffect.HideChallenge))
    }

    @Test
    fun `brute force is slowed down with a growing cooldown`() {
        val clock = FakeClock()
        val engine = newEngine(clock)
        reachChallenge(clock, engine)

        engine.onEvent(GateEvent.AnswerSubmitted(wrongAnswer(engine)))
        var pending = assertIs<GateState.ChallengePending>(engine.currentState)
        assertEquals(1, pending.attempts)
        val firstCooldown = pending.cooldownUntilElapsedMs - clock.elapsedMs
        assertEquals(3_000L, firstCooldown)

        // A submission during the cooldown is ignored.
        val (ignored, ignoredEffects) = engine.onEvent(GateEvent.AnswerSubmitted(wrongAnswer(engine)))
        assertEquals(1, assertIs<GateState.ChallengePending>(ignored).attempts)
        assertTrue(ignoredEffects.isEmpty())

        var previousCooldown = firstCooldown
        var lastCooldown = firstCooldown
        for (attempt in 2..4) {
            clock.advance(previousCooldown)
            engine.onEvent(GateEvent.AnswerSubmitted(wrongAnswer(engine)))
            pending = assertIs<GateState.ChallengePending>(engine.currentState)
            assertEquals(attempt, pending.attempts)
            lastCooldown = pending.cooldownUntilElapsedMs - clock.elapsedMs
            previousCooldown = lastCooldown
        }

        assertTrue(lastCooldown > firstCooldown, "cooldown must grow after repeated failures")
    }

    @Test
    fun `changing the wall clock does not affect the counter`() {
        val clock = FakeClock()
        val engine = newEngine(clock)

        engine.onEvent(GateEvent.YouTubeForeground)
        clock.advance(60_000)
        engine.onEvent(GateEvent.Tick)
        val before = assertIs<GateState.Counting>(engine.currentState).accumulatedMs

        clock.wallTimeMs += 24 * 60 * 60 * 1000L
        engine.onEvent(GateEvent.Tick)
        val after = assertIs<GateState.Counting>(engine.currentState).accumulatedMs

        assertEquals(before, after)
    }

    @Test
    fun `a reboot does not zero the accumulated time`() {
        val clock = FakeClock()
        val engine = newEngine(clock)

        engine.onEvent(GateEvent.YouTubeForeground)
        clock.advance(10 * 60_000L)
        engine.onEvent(GateEvent.Tick)

        val (state, effects) = engine.onEvent(GateEvent.Reboot)
        assertEquals(GateState.Idle(600_000L), state)
        assertContains(effects, GateEffect.PersistState)
    }

    @Test
    fun `the correct answer unlocks and resets the counter`() {
        val clock = FakeClock()
        val engine = newEngine(clock)
        val pending = reachChallenge(clock, engine)

        val (state, effects) = engine.onEvent(GateEvent.AnswerSubmitted(pending.problem.answer))
        assertEquals(GateState.Counting(0L, clock.elapsedMs), state)
        assertContains(effects, GateEffect.HideChallenge)
        assertContains(effects, GateEffect.PersistState)
    }

    @Test
    fun `the parent override unlocks the gate`() {
        val clock = FakeClock()
        val engine = newEngine(clock)
        reachChallenge(clock, engine)

        val (state, effects) = engine.onEvent(GateEvent.ParentOverride)
        assertEquals(GateState.Counting(0L, clock.elapsedMs), state)
        assertContains(effects, GateEffect.HideChallenge)
    }

    @Test
    fun `the limit reached while backgrounded is enforced on return`() {
        val clock = FakeClock()
        val engine = newEngine(clock)

        engine.onEvent(GateEvent.YouTubeForeground)
        clock.advance(limitMs)
        engine.onEvent(GateEvent.YouTubeBackground)
        assertEquals(GateState.Idle(limitMs), engine.currentState)

        val (state, effects) = engine.onEvent(GateEvent.YouTubeForeground)
        assertIs<GateState.ChallengePending>(state)
        assertContains(effects, GateEffect.ShowChallenge)
    }

    @Test
    fun `screen and foreground events are ignored while idle`() {
        val clock = FakeClock()
        val engine = newEngine(clock)

        engine.onEvent(GateEvent.ScreenOff)
        engine.onEvent(GateEvent.ScreenOn)
        engine.onEvent(GateEvent.YouTubeBackground)
        engine.onEvent(GateEvent.Tick)

        assertEquals(GateState.Idle(0L), engine.currentState)
    }

    @Test
    fun `answers and overrides are ignored outside the challenge`() {
        val clock = FakeClock()
        val engine = newEngine(clock)

        engine.onEvent(GateEvent.AnswerSubmitted(5))
        engine.onEvent(GateEvent.ParentOverride)
        assertEquals(GateState.Idle(0L), engine.currentState)

        engine.onEvent(GateEvent.YouTubeForeground)
        clock.advance(1_000)
        engine.onEvent(GateEvent.Tick)
        engine.onEvent(GateEvent.ParentOverride)
        assertEquals(GateState.Counting(1_000L, 1_000L), engine.currentState)
    }

    @Test
    fun `ticks are ignored while a challenge is pending`() {
        val clock = FakeClock()
        val engine = newEngine(clock)
        val pending = reachChallenge(clock, engine)

        clock.advance(60_000)
        val (state, effects) = engine.onEvent(GateEvent.Tick)
        assertEquals(pending, state)
        assertTrue(effects.isEmpty())
    }

    @Test
    fun `a reboot keeps a pending challenge but clears its cooldown`() {
        val clock = FakeClock()
        val engine = newEngine(clock)
        val pending = reachChallenge(clock, engine)

        engine.onEvent(GateEvent.AnswerSubmitted(pending.problem.answer + 1))
        clock.advance(1_000)
        val afterWrong = assertIs<GateState.ChallengePending>(engine.currentState)
        assertTrue(afterWrong.cooldownUntilElapsedMs > 1_000)

        engine.onEvent(GateEvent.Reboot)

        val restored = assertIs<GateState.ChallengePending>(engine.currentState)
        assertEquals(1, restored.attempts)
        assertEquals(0L, restored.cooldownUntilElapsedMs)
        assertEquals(afterWrong.problem.text, restored.problem.text)
    }
}
