package com.smallmlmodelencryption.utility

import android.content.Context
import android.util.Log
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileOutputStream
import javax.crypto.Cipher
import javax.crypto.CipherInputStream
import javax.crypto.spec.GCMParameterSpec

object Model_Decryptor_KeyStore {
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val IV_SIZE = 12 // 12 bytes for GCM IV
    private const val TAG_SIZE = 16 // 128-bit authentication tag
    private const val BUFFER_SIZE = 8192 // 8KB buffer

    suspend fun decryptModel(context: Context, encryptedFileName: String): File {
        val encryptedFile = File(context.getExternalFilesDir(null), encryptedFileName)
        val outputFile = File(context.getExternalFilesDir(null), "decrypted_model.tflite")
        val secretKey = AESKeyManager.getDecryptedAESKey(context)

        val fullBytes = encryptedFile.readBytes()

        val iv = fullBytes.copyOfRange(0, IV_SIZE)
        val ciphertextWithTag = fullBytes.copyOfRange(IV_SIZE, fullBytes.size)

        Log.d("Decrypt", "File size: ${fullBytes.size}")
        Log.d("Decrypt", "IV = ${iv.joinToString(",")}")
        Log.d("Decrypt", "Cipher+Tag size = ${ciphertextWithTag.size}")


        val cipher = Cipher.getInstance(TRANSFORMATION)
        val spec = GCMParameterSpec(TAG_SIZE * 8, iv)
        cipher.init(Cipher.DECRYPT_MODE, secretKey, spec)

        ByteArrayInputStream(ciphertextWithTag).use { byteStream ->
            CipherInputStream(byteStream, cipher).use { cis ->
                FileOutputStream(outputFile).use { fos ->
                    val buffer = ByteArray(BUFFER_SIZE)
                    var bytesRead: Int
                    while (cis.read(buffer).also { bytesRead = it } != -1) {
                        fos.write(buffer, 0, bytesRead)
                    }
                }
            }
        }
        return outputFile
    }
}
