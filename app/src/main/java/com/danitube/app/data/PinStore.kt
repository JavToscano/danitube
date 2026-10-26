package com.danitube.app.data

import android.content.Context
import android.util.Base64
import dagger.hilt.android.qualifiers.ApplicationContext
import java.security.MessageDigest
import java.security.SecureRandom
import javax.inject.Inject
import javax.inject.Singleton

/** Keeps the parent PIN on the phone as a salted SHA-256 hash, never the PIN itself. */
@Singleton
class PinStore @Inject constructor(@ApplicationContext context: Context) {
    private val prefs = context.getSharedPreferences("parent", Context.MODE_PRIVATE)

    fun hasPin(): Boolean = prefs.contains(KEY_HASH)

    fun setPin(pin: String) {
        val salt = ByteArray(16).also(SecureRandom()::nextBytes)
        prefs.edit()
            .putString(KEY_SALT, Base64.encodeToString(salt, Base64.NO_WRAP))
            .putString(KEY_HASH, hash(salt, pin))
            .apply()
    }

    fun verify(pin: String): Boolean {
        val salt = prefs.getString(KEY_SALT, null)?.let { Base64.decode(it, Base64.NO_WRAP) } ?: return false
        val expected = prefs.getString(KEY_HASH, null) ?: return false
        return MessageDigest.isEqual(hash(salt, pin).toByteArray(), expected.toByteArray())
    }

    private fun hash(salt: ByteArray, pin: String): String {
        val digest = MessageDigest.getInstance("SHA-256").apply { update(salt) }.digest(pin.toByteArray())
        return Base64.encodeToString(digest, Base64.NO_WRAP)
    }

    companion object {
        const val PIN_LENGTH = 4
        private const val KEY_SALT = "pin_salt"
        private const val KEY_HASH = "pin_hash"
    }
}
