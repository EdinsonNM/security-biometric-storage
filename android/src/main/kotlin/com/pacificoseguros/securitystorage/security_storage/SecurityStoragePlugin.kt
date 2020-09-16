package com.pacificoseguros.securitystorage.security_storage

import android.app.Activity
import android.content.Context
import androidx.annotation.NonNull;
import androidx.fragment.app.FragmentActivity

import io.flutter.embedding.engine.plugins.FlutterPlugin
import io.flutter.embedding.engine.plugins.activity.ActivityAware
import io.flutter.embedding.engine.plugins.activity.ActivityPluginBinding
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import io.flutter.plugin.common.MethodChannel.MethodCallHandler
import io.flutter.plugin.common.MethodChannel.Result
import io.flutter.plugin.common.PluginRegistry.Registrar
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
  override fun onAttachedToEngine(@NonNull flutterPluginBinding: FlutterPlugin.FlutterPluginBinding) {
    channel = MethodChannel(flutterPluginBinding.getFlutterEngine().getDartExecutor(), "security_storage")
    channel.setMethodCallHandler(this);
    context = flutterPluginBinding.applicationContext
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
    }
    val executor : ExecutorService = Executors.newSingleThreadExecutor()
  }

  override fun onMethodCall(@NonNull call: MethodCall, @NonNull result: Result) {
    when(call.method){
      "getPlatformVersion" -> result.success("test Android ${android.os.Build.VERSION.RELEASE}")
      "authenticate" -> result.success("test authenticate")
      "other" -> result.success("test other")
      else -> result.notImplemented()
    }
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
}
