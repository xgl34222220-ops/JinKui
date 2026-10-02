package com.xiguli.jinkui.core.crypto

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class PasswordGeneratorTest {
    @Test
    fun respectsRequestedLength() {
        for (length in listOf(6, 12, 20, 64)) {
            assertEquals(length, PasswordGenerator.generate(GeneratorOptions(length = length)).length)
        }
    }

    @Test
    fun lengthIsClampedToSupportedRange() {
        assertEquals(PasswordGenerator.MIN_LENGTH, PasswordGenerator.generate(GeneratorOptions(length = 1)).length)
        assertEquals(PasswordGenerator.MAX_LENGTH, PasswordGenerator.generate(GeneratorOptions(length = 500)).length)
    }

    @Test
    fun everySelectedClassAppears() {
        repeat(200) {
            val password = PasswordGenerator.generate(GeneratorOptions(length = 8))
            assertTrue(password.any { it.isUpperCase() })
            assertTrue(password.any { it.isLowerCase() })
            assertTrue(password.any { it.isDigit() })
            assertTrue(password.any { !it.isLetterOrDigit() })
        }
    }

    @Test
    fun onlySelectedClassesAppear() {
        val options = GeneratorOptions(length = 40, upper = false, lower = false, digits = true, symbols = false)
        repeat(50) { assertTrue(PasswordGenerator.generate(options).all { it.isDigit() }) }
    }

    @Test
    fun ambiguousCharactersAreExcludedWhenAsked() {
        val options = GeneratorOptions(length = 64, avoidAmbiguous = true)
        repeat(200) { assertTrue(PasswordGenerator.generate(options).none { it in "0O1lI|" }) }
    }

    @Test
    fun noCharsetSelectedIsRefused() {
        val options = GeneratorOptions(upper = false, lower = false, digits = false, symbols = false)
        assertThrows(IllegalArgumentException::class.java) { PasswordGenerator.generate(options) }
    }

    @Test
    fun consecutivePasswordsDiffer() {
        val seen = (1..100).map { PasswordGenerator.generate(GeneratorOptions()) }.toSet()
        assertEquals(100, seen.size)
        assertNotEquals(PasswordGenerator.generate(GeneratorOptions()), PasswordGenerator.generate(GeneratorOptions()))
    }

    @Test
    fun entropyGrowsWithLengthAndCharset() {
        val short = PasswordGenerator.entropyBits(GeneratorOptions(length = 8))
        val long = PasswordGenerator.entropyBits(GeneratorOptions(length = 24))
        val digitsOnly = PasswordGenerator.entropyBits(
            GeneratorOptions(length = 24, upper = false, lower = false, symbols = false),
        )
        assertTrue(long > short)
        assertTrue(long > digitsOnly)
        assertTrue(long > 128)
    }

    @Test
    fun estimateRanksWeakBelowStrong() {
        assertTrue(PasswordGenerator.estimateBits("123456") < 40)
        assertTrue(PasswordGenerator.estimateBits("aaaaaaaaaaaaaaaa") < PasswordGenerator.estimateBits("aB3\$kP9!xQ2#mL7&"))
        assertEquals(0.0, PasswordGenerator.estimateBits(""), 0.0)
    }
}
