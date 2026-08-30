package com.xxx.carelorie.data

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * Turns a password into something safe to store.
 *
 * The app deliberately does not use Supabase Auth — accounts live in our own `users` table — so
 * protecting the password is our job. Storing it as typed meant anyone holding the anon key,
 * which ships inside the APK, could read every password in the project.
 *
 * PBKDF2-HMAC-SHA256 with a per-user random salt. Deliberately built on `javax.crypto` and
 * `java.util.Base64`, both in the platform since API 26 (the app targets 28), so this needs no
 * new dependency and can be unit tested on the JVM without a device.
 *
 * Stored form is `pbkdf2:<base64 salt>:<base64 hash>`, which fits the existing `password` column
 * as an ordinary string — no database migration, so no wiping anyone's local data.
 */
object PasswordHasher {

    private const val ALGORITHM = "PBKDF2WithHmacSHA256"
    private const val PREFIX = "pbkdf2"
    private const val SALT_BYTES = 16
    private const val KEY_BITS = 256

    /**
     * Work factor. Higher is harder to brute force but slower to log in; this sits at roughly a
     * few hundred milliseconds on a mid-range phone, which the login spinner already covers.
     * Production systems use considerably more.
     */
    private const val ITERATIONS = 100_000

    /** @return the salted hash of [password], in the stored form described above. */
    fun hash(password: String): String {
        val salt = ByteArray(SALT_BYTES).also { SecureRandom().nextBytes(it) }
        val derived = derive(password, salt)
        return "$PREFIX:${salt.encode()}:${derived.encode()}"
    }

    /**
     * Checks [password] against a value produced by [hash].
     *
     * Returns false for anything not in the expected form — including a password stored in
     * plaintext by an older build. Those accounts cannot log in and must be recreated, which is
     * the intended outcome: no code path here will ever accept a plaintext password.
     */
    fun verify(password: String, stored: String): Boolean {
        val parts = stored.split(':')
        if (parts.size != 3 || parts[0] != PREFIX) return false

        val salt = parts[1].decodeOrNull() ?: return false
        val expected = parts[2].decodeOrNull() ?: return false

        // Constant-time compare, so a wrong password can't be narrowed down by timing.
        return MessageDigest.isEqual(expected, derive(password, salt))
    }

    /** True when [stored] was produced by [hash] rather than left as plaintext by an old build. */
    fun isHashed(stored: String): Boolean =
        stored.split(':').let { it.size == 3 && it[0] == PREFIX }

    private fun derive(password: String, salt: ByteArray): ByteArray {
        val spec = PBEKeySpec(password.toCharArray(), salt, ITERATIONS, KEY_BITS)
        return try {
            SecretKeyFactory.getInstance(ALGORITHM).generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
    }

    private fun ByteArray.encode(): String = Base64.getEncoder().withoutPadding().encodeToString(this)

    private fun String.decodeOrNull(): ByteArray? =
        try {
            Base64.getDecoder().decode(this)
        } catch (e: IllegalArgumentException) {
            null
        }
}
