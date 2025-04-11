package com.smallmlmodelencryption.utility

import android.content.Context
import android.util.Base64
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.URL
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

object AESKeyManager {

    private const val KEY_ALIAS = "MODEL_AES_KEY_ALIAS"
    private const val ENCRYPTED_KEY_FILE = "encrypted_model_key"
    private const val ANDROID_KEYSTORE = "AndroidKeyStore"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"

    suspend fun downloadAndStoreAESKey(context: Context, keyUrl: String) {
        withContext(Dispatchers.IO) {
            val keyText = URL(keyUrl).readText().trim()
            val decodedKey = Base64.decode(keyText, Base64.DEFAULT)

            val cipher = Cipher.getInstance(TRANSFORMATION)
            val secretKey = getOrCreateSecretKey()
            cipher.init(Cipher.ENCRYPT_MODE, secretKey)

            val encryptedKey = cipher.doFinal(decodedKey)
            val iv = cipher.iv

            val output = iv + encryptedKey
            val file = File(context.filesDir, ENCRYPTED_KEY_FILE)
            file.writeBytes(output)
        }
    }

    suspend fun getDecryptedAESKey(context: Context): SecretKey {
        return withContext(Dispatchers.IO) {
            val file = File(context.filesDir, ENCRYPTED_KEY_FILE)
            if (!file.exists()) throw IllegalStateException("Encrypted AES key not found.")

            val data = file.readBytes()
            val iv = data.copyOfRange(0, 12)
            val encryptedKey = data.copyOfRange(12, data.size)

            val cipher = Cipher.getInstance(TRANSFORMATION)
            val secretKey = getOrCreateSecretKey()
            val spec = GCMParameterSpec(128, iv)
            cipher.init(Cipher.DECRYPT_MODE, secretKey, spec)

            val decodedKey = cipher.doFinal(encryptedKey)
            Log.d("Decrypt-AESKey", "🔐 Decrypted AES key (Base64): ${Base64.encodeToString(decodedKey, Base64.NO_WRAP)}")
            SecretKeySpec(decodedKey, "AES")
        }
    }

    private fun getOrCreateSecretKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE)
        keyStore.load(null)

        return if (keyStore.containsAlias(KEY_ALIAS)) {
            (keyStore.getEntry(KEY_ALIAS, null) as KeyStore.SecretKeyEntry).secretKey
        } else {
            val keyGenerator = KeyGenerator.getInstance("AES", ANDROID_KEYSTORE)
            keyGenerator.init(android.security.keystore.KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                android.security.keystore.KeyProperties.PURPOSE_ENCRYPT or android.security.keystore.KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(android.security.keystore.KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(android.security.keystore.KeyProperties.ENCRYPTION_PADDING_NONE)
                .setUserAuthenticationRequired(false)
                .build())
            keyGenerator.generateKey()
        }
    }
}

/*object AESKeyManager {

    private const val KEY_ALIAS = "MODEL_AES_KEY_ALIAS"
    private const val ANDROID_KEYSTORE = "AndroidKeyStore"

    suspend fun downloadAndStoreAESKey(context: Context, keyUrl: String) {
        Log.d("AESKeyManager", "📥 downloadAndStoreAESKey() called with URL: $keyUrl")

        withContext(Dispatchers.IO) {
            Log.d("AESKeyManager", "🌐 Downloading AES key from server...")
            val keyText = URL(keyUrl).readText().trim()
            val decodedKey = Base64.decode(keyText, Base64.DEFAULT)
            Log.d("AESKeyManager", "✅ AES key downloaded and base64-decoded.")

            val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
            Log.d("AESKeyManager", "🔐 Android Keystore loaded.")
            // Store downloaded key into Keystore
            val secretKey = SecretKeySpec(decodedKey, "AES")
            val entry = KeyStore.SecretKeyEntry(secretKey)

            // Manually store the key
            keyStore.setEntry(
                KEY_ALIAS,
                entry,
                null // No password protection; Android Keystore handles security
            )

            Log.d("AESKeyManager", "✅ AES key downloaded and securely stored in Keystore.")
        }
    }

    suspend fun getDecryptedAESKey(context: Context): SecretKey {
        Log.d("AESKeyManager", "🔎 getDecryptedAESKey() called")
        return withContext(Dispatchers.IO) {
            val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE)
            keyStore.load(null)
            Log.d("AESKeyManager", "🔐 Android Keystore loaded.")

            if (!keyStore.containsAlias(KEY_ALIAS)) {
                Log.e("AESKeyManager", "❌ Key alias not found: $KEY_ALIAS")
                throw IllegalStateException("AES key not found in Keystore.")
            }

            val secretKey = (keyStore.getEntry(KEY_ALIAS, null) as KeyStore.SecretKeyEntry).secretKey
            Log.d("AESKeyManager", "🔓 Retrieved AES key from Keystore.")
            secretKey
        }
    }
}*/
