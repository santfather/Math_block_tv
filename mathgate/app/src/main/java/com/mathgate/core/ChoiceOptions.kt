package com.mathgate.core

import kotlin.random.Random

/**
 * Builds the answer options for the optional four-answer mode (D-09).
 *
 * The correct answer is always present, all options are distinct non-negative
 * integers returned in ascending order, and the result is deterministic for a
 * given [seed] so the screen can be tested.
 */
object ChoiceOptions {

    const val OPTION_COUNT: Int = 4

    private const val DISTRACTOR_RANGE = 10
    private const val MAX_ATTEMPTS = 40

    fun forProblem(problem: Problem, seed: Long): List<Int> {
        val random = Random(seed)
        val options = linkedSetOf(problem.answer)

        var attempts = 0
        while (options.size < OPTION_COUNT && attempts < MAX_ATTEMPTS) {
            options.add(distractor(problem.answer, random))
            attempts++
        }

        // Fallback in case random distractors keep colliding: fill with neighbours.
        var candidate = 0
        while (options.size < OPTION_COUNT) {
            options.add(candidate)
            candidate++
        }

        return options.sorted()
    }

    /** A value near the correct answer, never negative, never equal to it. */
    private fun distractor(answer: Int, random: Random): Int {
        val delta = random.nextInt(1, DISTRACTOR_RANGE + 1)
        val signed = if (random.nextBoolean()) answer + delta else answer - delta
        return signed.coerceAtLeast(0)
    }
}
