package com.mathgate.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ChoiceOptionsTest {

    @Test
    fun `options always contain the correct answer`() {
        val problem = Problem("7 × 8", 56)
        assertTrue(56 in ChoiceOptions.forProblem(problem, seed = 1L))
    }

    @Test
    fun `there are exactly four distinct options`() {
        val problem = Problem("12 + 9", 21)
        val options = ChoiceOptions.forProblem(problem, seed = 42L)
        assertEquals(ChoiceOptions.OPTION_COUNT, options.size)
        assertEquals(options.size, options.toSet().size)
    }

    @Test
    fun `options are sorted ascending and non-negative`() {
        val options = ChoiceOptions.forProblem(Problem("3 × 3", 9), seed = 7L)
        assertEquals(options.sorted(), options)
        assertTrue(options.all { it >= 0 })
    }

    @Test
    fun `generation is deterministic for the same seed`() {
        val problem = Problem("9 × 9 + 19", 100)
        assertEquals(
            ChoiceOptions.forProblem(problem, seed = 5L),
            ChoiceOptions.forProblem(problem, seed = 5L),
        )
    }

    @Test
    fun `a zero answer still yields non-negative options`() {
        val options = ChoiceOptions.forProblem(Problem("4 − 4", 0), seed = 3L)
        assertEquals(ChoiceOptions.OPTION_COUNT, options.size)
        assertEquals(0, options.first())
    }
}
