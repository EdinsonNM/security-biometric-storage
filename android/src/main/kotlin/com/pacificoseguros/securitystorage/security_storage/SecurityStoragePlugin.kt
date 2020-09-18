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
  private var readyToEncrypt: Boolean = false
  private lateinit var cryptographyManager: CryptographyManager
  private  var secretKeyName: String="edinson-key"
  private lateinit var biometricPrompt: BiometricPrompt
  private lateinit var promptInfo: BiometricPrompt.PromptInfo
  private lateinit var ciphertext:ByteArray
  private lateinit var initializationVector: ByteArray

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
    private const val TAG = "MainActivity"
  }

  override fun onMethodCall(@NonNull call: MethodCall, @NonNull result: Result) {

    promptInfo = createPromptInfo()
    when(call.method){
      "getPlatformVersion" -> result.success("test Android ${android.os.Build.VERSION.RELEASE}")
      "encrypt" -> {
        biometricPrompt = createBiometricPrompt( {
          result.success("success")
        },{
          result.error("0" ,"error","")
        })
        authenticateToEncrypt()


      }
      "decrypt" -> {
        biometricPrompt = createBiometricPrompt({
          result.success(it)
        },{
          result.error("0" ,"error","")
        })
        authenticateToDecrypt()
      }
      else -> result.notImplemented()
    }
  }
  fun updateFingerPrintManager(fingerprintMgr: FingerprintManager){
    this.fingerprintMgr = fingerprintMgr
  }
  private fun createBiometricPrompt(onSuccess: (data:String) -> Unit, onError: ErrorCallback): BiometricPrompt {
    val callback = object : BiometricPrompt.AuthenticationCallback() {
      override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
        super.onAuthenticationError(errorCode, errString)
        Log.d(TAG, "$errorCode :: $errString")
      }

      override fun onAuthenticationFailed() {
        super.onAuthenticationFailed()
        Log.d(TAG, "Authentication failed for an unknown reason")
      }

      override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
        super.onAuthenticationSucceeded(result)
        Log.d(TAG, "Authentication was successful")
        val data = processData("Hola mundo!!",result.cryptoObject)
        ui(onError) { onSuccess(data) }
      }
    }

    return BiometricPrompt(activity, executor, callback)
  }

  private fun createPromptInfo(): BiometricPrompt.PromptInfo {
    val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Hola Mundo") // e.g. "Sign in"
            .setSubtitle("Login biometrico") // e.g. "Biometric for My App"
            .setDescription("Loguin biometrico creado con kotlin") // e.g. "Confirm biometric to continue"
            .setConfirmationRequired(true)
            .setNegativeButtonText("Cancelar") // e.g. "Use Account Password"
            // .setDeviceCredentialAllowed(true) // Allow PIN/pattern/password authentication.
            // Also note that setDeviceCredentialAllowed and setNegativeButtonText are
            // incompatible so that if you uncomment one you must comment out the other
            .build()
    return promptInfo
  }

  private fun authenticateToEncrypt() {
    readyToEncrypt = true
    if (BiometricManager.from(this.context).canAuthenticate() == BiometricManager
                    .BIOMETRIC_SUCCESS) {
      val cipher = cryptographyManager.getInitializedCipherForEncryption(secretKeyName)
      biometricPrompt.authenticate(promptInfo, BiometricPrompt.CryptoObject(cipher))
    }
  }

  private fun authenticateToDecrypt() {
    readyToEncrypt = false
    if (BiometricManager.from(this.context).canAuthenticate() == BiometricManager
                    .BIOMETRIC_SUCCESS) {
      val cipher = cryptographyManager.getInitializedCipherForDecryption(secretKeyName,initializationVector)
      biometricPrompt.authenticate(promptInfo, BiometricPrompt.CryptoObject(cipher))
    }

  }

  private fun processData(text:String, cryptoObject: BiometricPrompt.CryptoObject?) :String{
    val data = if (readyToEncrypt) {
      val encryptedData = cryptographyManager.encryptData(text, cryptoObject?.cipher!!)
      ciphertext = encryptedData.ciphertext
      initializationVector = encryptedData.initializationVector

      String(ciphertext, Charset.forName("UTF-8"))
    } else {
      cryptographyManager.decryptData(ciphertext, cryptoObject?.cipher!!)
    }
    return data
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
      Log.e( TAG,"Error while calling UI callback. This must not happen.")
      onError(AuthenticationErrorInfo(AuthenticationError.Unknown, "Unexpected authentication error. ${e.localizedMessage}", e))
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
        val error: AuthenticationError,
        val message: CharSequence,
        val errorDetails: String? = null
) {
  constructor(
          error: AuthenticationError,
          message: CharSequence,
          e: Throwable
  ) : this(error, message, e.toString())
}