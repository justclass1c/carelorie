package com.xxx.carelorie

import com.xxx.carelorie.data.PasswordHasher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Runs on the JVM — no device needed — because [PasswordHasher] deliberately uses `javax.crypto`
 * and `java.util.Base64` rather than the Android-only equivalents.
 */
class PasswordHasherTest {

    @Test
    fun `correct password verifies`() {
        val stored = PasswordHasher.hash("Nasi123!")
        assertTrue(PasswordHasher.verify("Nasi123!", stored))
    }

    @Test
    fun `wrong password is rejected`() {
        val stored = PasswordHasher.hash("Nasi123!")
        assertFalse(PasswordHasher.verify("nasi123!", stored))
        assertFalse(PasswordHasher.verify("Nasi123", stored))
        assertFalse(PasswordHasher.verify("", stored))
    }

    @Test
    fun `hash does not contain the password`() {
        val stored = PasswordHasher.hash("Nasi123!")
        assertFalse(stored.contains("Nasi123!"))
    }

    @Test
    fun `same password hashes differently each time`() {
        // Distinct salts, so two users with the same password get different stored values and
        // one cracked hash does not reveal the other.
        val a = PasswordHasher.hash("Nasi123!")
        val b = PasswordHasher.hash("Nasi123!")
        assertNotEquals(a, b)
        assertTrue(PasswordHasher.verify("Nasi123!", a))
        assertTrue(PasswordHasher.verify("Nasi123!", b))
    }

    @Test
    fun `plaintext left by an older build is never accepted`() {
        // The pre-hashing builds stored the password as typed. Those rows must fail, not pass.
        assertFalse(PasswordHasher.verify("Nasi123!", "Nasi123!"))
        assertFalse(PasswordHasher.isHashed("Nasi123!"))
    }

    @Test
    fun `malformed stored values are rejected rather than crashing`() {
        listOf("", "pbkdf2", "pbkdf2:only-two", "pbkdf2:!!!:!!!", "bcrypt:a:b").forEach { stored ->
            assertFalse("should reject: $stored", PasswordHasher.verify("Nasi123!", stored))
        }
    }

    @Test
    fun `stored form is recognised as hashed`() {
        assertTrue(PasswordHasher.isHashed(PasswordHasher.hash("Nasi123!")))
    }

    @Test
    fun `unicode and long passwords round trip`() {
        val password = "nasi lemak 🇲🇾 " + "x".repeat(200)
        val stored = PasswordHasher.hash(password)
        assertTrue(PasswordHasher.verify(password, stored))
        assertFalse(PasswordHasher.verify(password + "y", stored))
    }

    @Test
    fun `stored form has the expected three parts`() {
        val parts = PasswordHasher.hash("Nasi123!").split(':')
        assertEquals(3, parts.size)
        assertEquals("pbkdf2", parts[0])
        assertTrue(parts[1].isNotEmpty())
        assertTrue(parts[2].isNotEmpty())
    }
}
