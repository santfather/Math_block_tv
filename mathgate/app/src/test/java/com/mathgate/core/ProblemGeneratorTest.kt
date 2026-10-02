package com.mathgate.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class ProblemGeneratorTest {

    private val twoStep = Regex("""^(\d+) × (\d+) ([+−]) (\d+)$""")
    private val simple = Regex("""^(\d+) ([+−×÷]) (\d+)$""")
    private val anyNumber = Regex("""\d+""")

    @Test
    fun `generation is deterministic for a given seed`() {
        for (level in 1..4) {
            assertEquals(
                ProblemGenerator.generate(level, 12_345L),
                ProblemGenerator.generate(level, 12_345L),
            )
        }
    }

    @Test
    fun `the same problem is never returned twice in a row`() {
        for (level in 1..4) {
            val first = ProblemGenerator.generate(level, 99L)
            val second = ProblemGenerator.generate(level, 99L, previous = first)
            assertNotEquals(first.text, second.text, "level=$level")
        }
    }

    @Test
    fun `answers are correct and never negative`() {
        for (level in 1..4) {
            for (seed in 0L until 500L) {
                val problem = ProblemGenerator.generate(level, seed)
                assertTrue(problem.answer >= 0, "negative answer for $problem")
                assertEquals(
                    problem.answer,
                    evaluate(problem.text),
                    "level=$level seed=$seed text='${problem.text}'",
                )
            }
        }
    }

    @Test
    fun `operand ranges match the level specification`() {
        for (seed in 0L until 300L) {
            assertTrue(numbers(ProblemGenerator.generate(1, seed).text).all { it <= 20 })
            assertTrue(numbers(ProblemGenerator.generate(2, seed).text).all { it <= 100 })
            assertTrue(numbers(ProblemGenerator.generate(3, seed).text).all { it in 1..10 })
        }
    }

    @Test
    fun `division problems are exact`() {
        for (seed in 0L until 500L) {
            val problem = ProblemGenerator.generate(4, seed)
            val match = simple.matchEntire(problem.text) ?: continue
            if (match.groupValues[2] == "÷") {
                assertEquals(0, match.groupValues[1].toInt() % match.groupValues[3].toInt())
            }
        }
    }

    @Test
    fun `levels outside the supported range are clamped`() {
        assertTrue(numbers(ProblemGenerator.generate(0, 7L).text).all { it <= 20 })
        assertTrue(ProblemGenerator.generate(9, 7L).answer >= 0)
    }

    private fun numbers(text: String): List<Int> =
        anyNumber.findAll(text).map { it.value.toInt() }.toList()

    private fun evaluate(text: String): Int {
        twoStep.matchEntire(text)?.let { match ->
            val (a, b, operator, c) = match.destructured
            val product = a.toInt() * b.toInt()
            return if (operator == "+") product + c.toInt() else product - c.toInt()
        }
        val match = simple.matchEntire(text) ?: error("Cannot parse '$text'")
        val a = match.groupValues[1].toInt()
        val b = match.groupValues[3].toInt()
        return when (match.groupValues[2]) {
            "+" -> a + b
            "−" -> a - b
            "×" -> a * b
            "÷" -> a / b
            else -> error("Unknown operator in '$text'")
        }
    }
}
