package com.mathgate.core

import kotlin.random.Random

/** A single math problem. The answer is always a non-negative integer. */
data class Problem(val text: String, val answer: Int)

/**
 * Generates the challenge shown when the time limit is reached (phase 2).
 *
 * Levels:
 * 1. addition / subtraction within 20,
 * 2. addition / subtraction within 100,
 * 3. multiplication table,
 * 4. multiplication / division + two-step expressions.
 *
 * Generation is deterministic for a given [seed] so tests are reproducible, and
 * [generate] avoids returning the same expression twice in a row.
 */
object ProblemGenerator {

    const val MIN_LEVEL = 1
    const val MAX_LEVEL = 4

    private const val MAX_REGENERATIONS = 20

    fun generate(level: Int, seed: Long, previous: Problem? = null): Problem {
        val random = Random(seed)
        var candidate = create(level, random)
        var guard = 0
        while (previous != null && candidate.text == previous.text && guard < MAX_REGENERATIONS) {
            candidate = create(level, random)
            guard++
        }
        return candidate
    }

    private fun create(level: Int, random: Random): Problem =
        when (level.coerceIn(MIN_LEVEL, MAX_LEVEL)) {
            1 -> addSubtract(random, max = 20)
            2 -> addSubtract(random, max = 100)
            3 -> multiplication(random)
            else -> multiplicationAndDivision(random)
        }

    private fun addSubtract(random: Random, max: Int): Problem {
        val a = random.nextInt(1, max + 1)
        val b = random.nextInt(1, max + 1)
        return if (random.nextBoolean()) {
            Problem("$a + $b", a + b)
        } else {
            val hi = maxOf(a, b)
            val lo = minOf(a, b)
            Problem("$hi − $lo", hi - lo)
        }
    }

    private fun multiplication(random: Random): Problem {
        val a = random.nextInt(1, 11)
        val b = random.nextInt(1, 11)
        return Problem("$a × $b", a * b)
    }

    private fun multiplicationAndDivision(random: Random): Problem =
        when (random.nextInt(3)) {
            0 -> {
                val a = random.nextInt(2, 10)
                val b = random.nextInt(2, 10)
                Problem("$a × $b", a * b)
            }

            1 -> {
                // Exact division: pick the answer first, then the dividend.
                val divisor = random.nextInt(2, 10)
                val quotient = random.nextInt(2, 10)
                Problem("${divisor * quotient} ÷ $divisor", quotient)
            }

            else -> {
                val a = random.nextInt(2, 10)
                val b = random.nextInt(2, 10)
                val product = a * b
                if (random.nextBoolean()) {
                    val c = random.nextInt(1, 20)
                    Problem("$a × $b + $c", product + c)
                } else {
                    val subtrahend = random.nextInt(1, product + 1)
                    Problem("$a × $b − $subtrahend", product - subtrahend)
                }
            }
        }
}
