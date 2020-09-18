import 'dart:async';

import 'package:flutter/services.dart';

class SecurityStorage {
  static const MethodChannel _channel = const MethodChannel('security_storage');

  static Future<String> get platformVersion async {
    final String version = await _channel.invokeMethod('getPlatformVersion');
    return version;
  }

  static Future<String> encrypt() async {
    final String result = await _channel.invokeMethod('encrypt');
    return result;
  }

  static Future<String> decrypt() async {
    final String result = await _channel.invokeMethod('decrypt');
    return result;
  }
}
