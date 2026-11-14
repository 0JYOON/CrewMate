package com.example.crewmate.login

import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.security.GeneralSecurityException
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class SecureCredentialStore(context: Context) {
    private val preferences: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    fun hasSavedCredentials(): Boolean {
        return preferences.getBoolean(KEY_REMEMBER, false) &&
            !preferences.getString(KEY_EMPLOYEE_NUMBER, null).isNullOrBlank() &&
            !preferences.getString(KEY_PASSWORD, null).isNullOrBlank()
    }

    suspend fun read(): CredentialReadResult = withContext(Dispatchers.IO) {
        if (!preferences.getBoolean(KEY_REMEMBER, false)) return@withContext CredentialReadResult.Empty
        val encryptedEmployeeNumber = preferences.getString(KEY_EMPLOYEE_NUMBER, null)
        val encryptedPassword = preferences.getString(KEY_PASSWORD, null)
        if (encryptedEmployeeNumber == null || encryptedPassword == null) {
            clearBlocking()
            return@withContext CredentialReadResult.Unreadable
        }

        try {
            CredentialReadResult.Available(
                SavedLoginCredentials(
                    employeeNumber = decrypt(encryptedEmployeeNumber),
                    password = decrypt(encryptedPassword),
                ),
            )
        } catch (_: Exception) {
            clearBlocking()
            CredentialReadResult.Unreadable
        }
    }

    suspend fun save(employeeNumber: String, password: String) = withContext(Dispatchers.IO) {
        val encryptedEmployeeNumber = encrypt(employeeNumber)
        val encryptedPassword = encrypt(password)
        val saved = preferences.edit()
            .putBoolean(KEY_REMEMBER, true)
            .putString(KEY_EMPLOYEE_NUMBER, encryptedEmployeeNumber)
            .putString(KEY_PASSWORD, encryptedPassword)
            .commit()
        if (!saved) throw CredentialStorageException()
    }

    suspend fun clear() = withContext(Dispatchers.IO) {
        clearBlocking()
    }

    private fun clearBlocking() {
        if (!preferences.edit().clear().commit()) throw CredentialStorageException()
        val keyStore = KeyStore.getInstance(ANDROID_KEY_STORE).apply { load(null) }
        if (keyStore.containsAlias(KEY_ALIAS)) keyStore.deleteEntry(KEY_ALIAS)
    }

    private fun encrypt(plainText: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val cipherText = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))
        val iv = Base64.encodeToString(cipher.iv, Base64.NO_WRAP)
        val encrypted = Base64.encodeToString(cipherText, Base64.NO_WRAP)
        return "$iv:$encrypted"
    }

    private fun decrypt(payload: String): String {
        val parts = payload.split(':', limit = 2)
        if (parts.size != 2) throw GeneralSecurityException("Invalid encrypted credential")
        val iv = Base64.decode(parts[0], Base64.NO_WRAP)
        val cipherText = Base64.decode(parts[1], Base64.NO_WRAP)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, getExistingKey(), GCMParameterSpec(GCM_TAG_BITS, iv))
        return cipher.doFinal(cipherText).toString(Charsets.UTF_8)
    }

    private fun getExistingKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEY_STORE).apply { load(null) }
        return keyStore.getKey(KEY_ALIAS, null) as? SecretKey
            ?: throw GeneralSecurityException("Encryption key is unavailable")
    }

    private fun getOrCreateKey(): SecretKey {
        getExistingKeyOrNull()?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEY_STORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(KEY_SIZE_BITS)
                .setRandomizedEncryptionRequired(true)
                .build(),
        )
        return generator.generateKey()
    }

    private fun getExistingKeyOrNull(): SecretKey? {
        val keyStore = KeyStore.getInstance(ANDROID_KEY_STORE).apply { load(null) }
        return keyStore.getKey(KEY_ALIAS, null) as? SecretKey
    }

    private companion object {
        const val PREFERENCES_NAME = "crewmate_secure_login"
        const val KEY_REMEMBER = "remember_login"
        const val KEY_EMPLOYEE_NUMBER = "encrypted_employee_number"
        const val KEY_PASSWORD = "encrypted_password"
        const val KEY_ALIAS = "crewmate_login_aes_gcm_v1"
        const val ANDROID_KEY_STORE = "AndroidKeyStore"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val KEY_SIZE_BITS = 256
        const val GCM_TAG_BITS = 128
    }
}

sealed interface CredentialReadResult {
    data object Empty : CredentialReadResult
    data object Unreadable : CredentialReadResult
    data class Available(val credentials: SavedLoginCredentials) : CredentialReadResult
}

class SavedLoginCredentials(val employeeNumber: String, val password: String) {
    override fun toString(): String = "SavedLoginCredentials(employeeNumber=redacted, password=redacted)"
}

class CredentialStorageException : Exception()
