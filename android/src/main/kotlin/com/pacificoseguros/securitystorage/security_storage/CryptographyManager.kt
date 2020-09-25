package com.pacificoseguros.securitystorage.security_storage

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyPermanentlyInvalidatedException
import android.security.keystore.KeyProperties
import android.util.Log
import com.squareup.moshi.JsonClass
import java.nio.charset.Charset
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec


@JsonClass(generateAdapter = true)
data class InitOptions(
        val authenticationValidityDurationSeconds: Int = 30,
        val authenticationRequired: Boolean = true
)
interface CryptographyManager {
    companion object{
        const val KeyPermanentlyInvalidatedExceptionCode=99
    }
    /**
     * This method first gets or generates an instance of SecretKey and then initializes the Cipher
     * with the key. The secret key uses [ENCRYPT_MODE][Cipher.ENCRYPT_MODE] is used.
     */
    fun getInitializedCipherForEncryption(keyName: String,options:InitOptions, onSuccess: (Cipher)-> Unit, onError : (KeyPermanentlyInvalidatedException)-> Unit): Unit

    /**
     * This method first gets or generates an instance of SecretKey and then initializes the Cipher
     * with the key. The secret key uses [DECRYPT_MODE][Cipher.DECRYPT_MODE] is used.
     */
    fun getInitializedCipherForDecryption(keyName: String, initializationVector: ByteArray?, options:InitOptions, onSuccess: (Cipher)-> Unit, onError : (KeyPermanentlyInvalidatedException)-> Unit): Unit

    /**
     * The Cipher created with [getInitializedCipherForEncryption] is used here
     */
    fun encryptData(plaintext: String, cipher: Cipher): EncryptedData

    /**
     * The Cipher created with [getInitializedCipherForDecryption] is used here
     */
    fun decryptData(ciphertext: ByteArray, cipher: Cipher): String
    fun removeStore(keyName:String): Unit

}

fun CryptographyManager(): CryptographyManager = CryptographyManagerImpl()

data class EncryptedData(val ciphertext: ByteArray, val initializationVector: ByteArray)

private class CryptographyManagerImpl : CryptographyManager {

    private val KEY_SIZE: Int = 256
    val ANDROID_KEYSTORE = "AndroidKeyStore"
    private val ENCRYPTION_BLOCK_MODE = KeyProperties.BLOCK_MODE_GCM
    private val ENCRYPTION_PADDING = KeyProperties.ENCRYPTION_PADDING_NONE
    private val ENCRYPTION_ALGORITHM = KeyProperties.KEY_ALGORITHM_AES
    override fun getInitializedCipherForEncryption(keyName: String, options:InitOptions , onSuccess: (Cipher)-> Unit, onError : (KeyPermanentlyInvalidatedException)-> Unit) {

        try {
            val cipher = getCipher()
            val secretKey = getOrCreateSecretKey(keyName, options)
            cipher.init(Cipher.ENCRYPT_MODE, secretKey)
            onSuccess(cipher)
        }catch (e: KeyPermanentlyInvalidatedException)
        {
            onError(e)
            //removeStore(keyName)
        }
    }

    override fun getInitializedCipherForDecryption(keyName: String, initializationVector: ByteArray?, options:InitOptions , onSuccess: (Cipher)-> Unit, onError:(KeyPermanentlyInvalidatedException)->Unit) {
        try {
            val cipher = getCipher()
            val secretKey = getOrCreateSecretKey(keyName, options)
            cipher.init(Cipher.DECRYPT_MODE, secretKey, GCMParameterSpec(128, initializationVector))
            onSuccess(cipher)
        }catch (e: KeyPermanentlyInvalidatedException)
        {
            onError(e)
            //removeStore(keyName)
        }
    }

    override fun encryptData(plaintext: String, cipher: Cipher): EncryptedData {
        val ciphertext = cipher.doFinal(plaintext.toByteArray(Charset.forName("UTF-8")))
        Log.d("Crypto==============>\n", ciphertext.toString())
        return EncryptedData(ciphertext,cipher.iv)
    }

    override fun decryptData(ciphertext: ByteArray, cipher: Cipher): String {
        val plaintext = cipher.doFinal(ciphertext)
        return String(plaintext, Charset.forName("UTF-8"))
    }

    private fun getCipher(): Cipher {
        val transformation = "$ENCRYPTION_ALGORITHM/$ENCRYPTION_BLOCK_MODE/$ENCRYPTION_PADDING"
        return Cipher.getInstance(transformation)
    }

    private fun getOrCreateSecretKey(keyName: String, options:InitOptions): SecretKey {
        // If Secretkey was previously created for that keyName, then grab and return it.
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE)
        keyStore.load(null) // Keystore must be loaded before it can be accessed
        keyStore.getKey(keyName, null)?.let { return it as SecretKey }

        // if you reach here, then a new SecretKey must be generated for that keyName
        val paramsBuilder = KeyGenParameterSpec.Builder(keyName,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
        paramsBuilder.apply {
            setBlockModes(ENCRYPTION_BLOCK_MODE)
            setEncryptionPaddings(ENCRYPTION_PADDING)
            setKeySize(KEY_SIZE)
            //setUserAuthenticationValidityDurationSeconds(options.authenticationValidityDurationSeconds)
            setUserAuthenticationRequired(true)
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
                setInvalidatedByBiometricEnrollment(true)
            }
        }

        val keyGenParams = paramsBuilder.build()
        val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES,
                ANDROID_KEYSTORE)
        keyGenerator.init(keyGenParams)
        return keyGenerator.generateKey()
    }
    override fun removeStore(keyName: String){
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE)
        keyStore.load(null)
        keyStore.deleteEntry(keyName)
    }

}