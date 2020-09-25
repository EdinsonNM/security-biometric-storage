package com.pacificoseguros.securitystorage.security_storage

import android.app.Activity
import android.content.Context
import android.hardware.fingerprint.FingerprintManager
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.annotation.NonNull
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.fragment.app.FragmentActivity
import com.google.gson.Gson
import com.pacificoseguros.securitystorage.security_storage.PreferenceHelper.get
import com.pacificoseguros.securitystorage.security_storage.PreferenceHelper.set
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import io.flutter.embedding.engine.plugins.FlutterPlugin
import io.flutter.embedding.engine.plugins.activity.ActivityAware
import io.flutter.embedding.engine.plugins.activity.ActivityPluginBinding
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import io.flutter.plugin.common.MethodChannel.MethodCallHandler
import io.flutter.plugin.common.MethodChannel.Result
import io.flutter.plugin.common.PluginRegistry.Registrar
import java.nio.charset.Charset
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors


/** SecurityStoragePlugin */
public class SecurityStoragePlugin: FlutterPlugin, MethodCallHandler, ActivityAware {
  /// The MethodChannel that will the communication between Flutter and native Android
  ///
  /// This local reference serves to register the plugin with the Flutter Engine and unregister it
  /// when the Flutter Engine is detached from the Activity
  private lateinit var channel : MethodChannel
  private lateinit var context: Context
  private lateinit var activity: FragmentActivity
  private lateinit var fingerprintMgr: FingerprintManager
  private lateinit var cryptographyManager: CryptographyManager
  private lateinit var biometricPrompt: BiometricPrompt
  private lateinit var promptInfo: BiometricPrompt.PromptInfo

  private val storageItems = mutableMapOf<String, StorageItem>()
  //private  var cryptographyManagers= mutableMapOf<String, CryptographyManager>()
  override fun onAttachedToEngine(@NonNull flutterPluginBinding: FlutterPlugin.FlutterPluginBinding) {
    channel = MethodChannel(flutterPluginBinding.getFlutterEngine().getDartExecutor(), "security_storage")
    channel.setMethodCallHandler(this);
    context = flutterPluginBinding.applicationContext
    cryptographyManager = CryptographyManager()

  }

  // This static function is optional and equivalent to onAttachedToEngine. It supports the old
  // pre-Flutter-1.12 Android projects. You are encouraged to continue supporting
  // plugin registration via this function while apps migrate to use the new Android APIs
  // post-flutter-1.12 via https://flutter.dev/go/android-project-migration.
  //
  // It is encouraged to share logic between onAttachedToEngine and registerWith to keep
  // them functionally equivalent. Only one of onAttachedToEngine or registerWith will be called
  // depending on the user's project. onAttachedToEngine or registerWith must both be defined
  // in the same class.
  companion object {
    @JvmStatic
    fun registerWith(registrar: Registrar) {
      val channel = MethodChannel(registrar.messenger(), "security_storage")
      channel.setMethodCallHandler(SecurityStoragePlugin())
      SecurityStoragePlugin().apply {
        updateFingerPrintManager(registrar.activity().getSystemService(FingerprintManager::class.java))
        print("initialize plugin")
      }
    }
    val executor : ExecutorService = Executors.newSingleThreadExecutor()
    private val handler: Handler = Handler(Looper.getMainLooper())
    private const val TAG = "SecurityStoragePlugin"
    val moshi = Moshi.Builder()
            .add(KotlinJsonAdapterFactory())
            .build() as Moshi
    const val PARAM_ANDROID_PROMPT_INFO = "androidPromptInfo"
    const val PARAM_NAME = "name"
    const val PARAM_WRITE_CONTENT = "content"

  }

  override fun onMethodCall(@NonNull call: MethodCall, @NonNull result: Result) {

    fun <T> requiredArgument(name: String) =
            call.argument<T>(name) ?: throw MethodCallException(
                    "MissingArgument",
                    "Missing required argument '$name'"
            )

    val getAndroidPromptInfo = {
      requiredArgument<Map<String, Any>>(PARAM_ANDROID_PROMPT_INFO).let {
        moshi.adapter(AndroidPromptInfo::class.java).fromJsonValue(it) ?: throw MethodCallException(
                "BadArgument",
                "'$PARAM_ANDROID_PROMPT_INFO' is not well formed"
        )
      }
    }
    val getName = { requiredArgument<String>(PARAM_NAME) }
    val getContent = { requiredArgument<String>(PARAM_WRITE_CONTENT) }
    fun withStorage(cb: StorageItem.() -> Unit) {
      val name = getName()
      storageItems[name]?.apply(cb) ?: return {
        result.error("Storage $name was not initialized.", null, null)
      }()
    }
    when(call.method){
      "canAuthenticate" -> result.success(canAuthenticate().toString())
      "getPlatformVersion" -> result.success("test Android ${android.os.Build.VERSION.RELEASE}")
      "init" -> {

        val options = moshi.adapter<InitOptions>(InitOptions::class.java)
                .fromJsonValue(call.argument("options") ?: emptyMap<String, Any>())
                ?: InitOptions()


        storageItems[getName()] = StorageItem(getName(), options)
      }
      "write" -> {
        withStorage {
          val name = getName()
          val content= getContent()
          promptInfo = createPromptInfo(getAndroidPromptInfo())
          biometricPrompt = createBiometricPrompt({
            processDataEncrypt(name,it.cryptoObject, content) { data ->
              result.success(data)
            }
          }, {
            result.error(it.error.toString(), it.message.toString(), it.errorDetails)
          }, getContent())
          authenticateToEncrypt(getName(), getContent()) {
            result.error(it.error.toString(), it.message.toString(), it.errorDetails)
          }
        }
      }
      "read" -> {
        val name = getName()
        withStorage {
          promptInfo = createPromptInfo(getAndroidPromptInfo())
          biometricPrompt = createBiometricPrompt({
            processDataDecrypt(name,it.cryptoObject){ data ->
              result.success(data)
            }
          }, {

            result.error(it.error.toString(), it.message.toString(), it.errorDetails)
          })
          authenticateToDecrypt(getName()) {
            result.error(it.error.toString(), it.message.toString(), it.errorDetails)
          }
        }
      }
      "delete"-> {
        val name = getName()
        withStorage {
          promptInfo = createPromptInfo(getAndroidPromptInfo())
          biometricPrompt = createBiometricPrompt({
            storageItems.remove(name)
            cryptographyManager.removeStore(name)
            result.success(true)
          }, {
            result.error(it.error.toString(), it.message.toString(), it.errorDetails)
          })
          authenticateToRemove(name){
            result.error(it.error.toString(), it.message.toString(), it.errorDetails)
          }
        }
      }
      else -> result.notImplemented()
    }
  }
  private fun canAuthenticate(): Int {
    return BiometricManager.from(this.context).canAuthenticate()
  }
  fun updateFingerPrintManager(fingerprintMgr: FingerprintManager){
    this.fingerprintMgr = fingerprintMgr
  }
  private fun createBiometricPrompt( onSuccess: (result: BiometricPrompt.AuthenticationResult) -> Unit, onError: ErrorCallback, data: String = ""): BiometricPrompt {
    val callback = object : BiometricPrompt.AuthenticationCallback() {
      override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
        super.onAuthenticationError(errorCode, errString)
        Log.d(TAG, "$errorCode :: $errString")
        ui(onError) { onError(AuthenticationErrorInfo(errorCode, errString)) }
      }

      override fun onAuthenticationFailed() {
        super.onAuthenticationFailed()
        Log.d(TAG, "Authentication failed for an unknown reason")
      }

      override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
        super.onAuthenticationSucceeded(result)
        Log.d(TAG, "Authentication was successful")

        ui(onError) {
          onSuccess(result)
        }
      }
    }

    return BiometricPrompt(activity, executor, callback)
  }

  private fun createPromptInfo(promptInfo: AndroidPromptInfo): BiometricPrompt.PromptInfo {
    return BiometricPrompt.PromptInfo.Builder()
            .setTitle(promptInfo.title) // e.g. "Sign in"
            .setSubtitle(promptInfo.subtitle) // e.g. "Biometric for My App"
            .setDescription(promptInfo.description) // e.g. "Confirm biometric to continue"
            .setConfirmationRequired(promptInfo.confirmationRequired)
            .setNegativeButtonText(promptInfo.negativeButton) // e.g. "Use Account Password"
            // .setDeviceCredentialAllowed(true) // Allow PIN/pattern/password authentication.
            // Also note that setDeviceCredentialAllowed and setNegativeButtonText are
            // incompatible so that if you uncomment one you must comment out the other
            .build()
  }

  private fun authenticateToEncrypt(secretKeyName: String, content: String, onError: ErrorCallback) {
    if (BiometricManager.from(this.context).canAuthenticate() == BiometricManager.BIOMETRIC_SUCCESS) {
      var options = storageItems[secretKeyName]!!.options;
      cryptographyManager.getInitializedCipherForEncryption(secretKeyName, options!!, {
        biometricPrompt.authenticate(promptInfo, BiometricPrompt.CryptoObject(it))

      }, {
        Log.d(TAG, it.message)
        ui(onError) {
          onError(AuthenticationErrorInfo(CryptographyManager.KeyPermanentlyInvalidatedExceptionCode, it.message.toString(), it.cause!!.message))
        }
      })
    }
  }

  private fun authenticateToDecrypt(secretKeyName: String, onError: ErrorCallback) {
    if (BiometricManager.from(this.context).canAuthenticate() == BiometricManager.BIOMETRIC_SUCCESS) {
      if(storageItems[secretKeyName]!!.encryptedData==null){
        var prefs = PreferenceHelper.customPrefs(this.context, "security-storage")
        val json: String = prefs[secretKeyName]
        val gson = Gson()
        storageItems[secretKeyName]!!.encryptedData = gson.fromJson(json, EncryptedData::class.java)
      }
      var initializationVector: ByteArray? = storageItems[secretKeyName]?.encryptedData?.initializationVector
      var options = storageItems[secretKeyName]?.options
      Log.d(secretKeyName, initializationVector.toString())
      cryptographyManager.getInitializedCipherForDecryption(secretKeyName, initializationVector, options!!, {
        biometricPrompt.authenticate(promptInfo, BiometricPrompt.CryptoObject(it))
      }, {
        Log.d(TAG, it.message)
        ui(onError) {
          onError(AuthenticationErrorInfo(CryptographyManager.KeyPermanentlyInvalidatedExceptionCode, it.message.toString(), it.cause!!.message))
        }
      })

    }
  }
  private fun authenticateToRemove(secretKeyName: String, onError: ErrorCallback) {
    if (BiometricManager.from(this.context).canAuthenticate() == BiometricManager.BIOMETRIC_SUCCESS) {

        var initializationVector: ByteArray? = storageItems[secretKeyName]?.encryptedData?.initializationVector
        var options = storageItems[secretKeyName]?.options

        cryptographyManager.getInitializedCipherForDecryption(secretKeyName, initializationVector, options!!, {
          biometricPrompt.authenticate(promptInfo, BiometricPrompt.CryptoObject(it))
        }, {
          Log.d(TAG, it.message)
          ui(onError) {
            onError(AuthenticationErrorInfo(CryptographyManager.KeyPermanentlyInvalidatedExceptionCode, it.message.toString(), it.cause!!.message))
          }
        })

    }
  }

  private fun processDataEncrypt(secretKeyName: String, cryptoObject: BiometricPrompt.CryptoObject?, data: String, onSuccess: (String) -> Unit) {

    val encryptedData = cryptographyManager.encryptData(data, cryptoObject?.cipher!!)
    storageItems[secretKeyName]?.encryptedData = encryptedData

    val gson = Gson()
    val json: String = gson.toJson(encryptedData)
    var prefs = PreferenceHelper.customPrefs(this.context, "security-storage")
    prefs[secretKeyName] = json

    val data =  String(encryptedData.ciphertext, Charset.forName("UTF-8"))
    onSuccess(data)

  }
  private fun processDataDecrypt(secretKeyName: String, cryptoObject: BiometricPrompt.CryptoObject?, onSuccess: (String) -> Unit) {
    var cipherText = storageItems[secretKeyName]?.encryptedData?.ciphertext
    val data =  cryptographyManager.decryptData(cipherText!!, cryptoObject?.cipher!!)
    onSuccess(data)
  }

  override fun onDetachedFromEngine(@NonNull binding: FlutterPlugin.FlutterPluginBinding) {
    channel.setMethodCallHandler(null)
  }

  override fun onDetachedFromActivity() {
    TODO("Not yet implemented")
  }

  override fun onReattachedToActivityForConfigChanges(binding: ActivityPluginBinding) {
    TODO("Not yet implemented")
  }

  override fun onAttachedToActivity(binding: ActivityPluginBinding) {
    updateAttachedActivity(binding.activity)
    updateFingerPrintManager(binding.activity.getSystemService(FingerprintManager::class.java))
  }
  private fun updateAttachedActivity(activity: Activity) {
    if (activity !is FragmentActivity) {
      //logger.error { "Got attached to activity which is not a FragmentActivity: $activity" }
      return
    }
    this.activity = activity
  }

  override fun onDetachedFromActivityForConfigChanges() {
    TODO("Not yet implemented")
  }

  private inline fun ui(crossinline onError: ErrorCallback, crossinline cb: () -> Unit) = handler.post {
    try {
      cb()
    } catch (e: Throwable) {
      Log.e(TAG, "Error while calling UI callback. This must not happen.")
      onError(AuthenticationErrorInfo(0, "Unexpected authentication error. ${e.localizedMessage}", e))
    }
  }
}
typealias ErrorCallback = (errorInfo: AuthenticationErrorInfo) -> Unit

@Suppress("unused")
enum class AuthenticationError(val code: Int) {
  Canceled(BiometricPrompt.ERROR_CANCELED),
  Timeout(BiometricPrompt.ERROR_TIMEOUT),
  UserCanceled(BiometricPrompt.ERROR_USER_CANCELED),
  Unknown(-1),
  /** Authentication valid, but unknown */
  Failed(-2),
  ;

  companion object {
    fun forCode(code: Int) =
            values().firstOrNull { it.code == code } ?: Unknown
  }
}

data class AuthenticationErrorInfo(
        val error: Int,
        val message: CharSequence,
        val errorDetails: String? = null
) {
  constructor(
          error: Int,
          message: CharSequence,
          e: Throwable
  ) : this(error, message, e.toString())
}

@JsonClass(generateAdapter = true)
data class AndroidPromptInfo(
        val title: String,
        val subtitle: String?,
        val description: String?,
        val negativeButton: String,
        val confirmationRequired: Boolean
)


class MethodCallException(
        val errorCode: String,
        val errorMessage: String?,
        val errorDetails: Any? = null
) : Exception(errorMessage ?: errorCode)



data class StorageItem(
        val name: String,
        val options: InitOptions? = null,
        var encryptedData: EncryptedData? = null
){
  constructor(name: String, options: InitOptions):this(name, options, null)
}