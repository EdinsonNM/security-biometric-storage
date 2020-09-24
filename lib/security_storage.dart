import 'dart:async';
import 'dart:io';

import 'package:flutter/services.dart';
import 'package:logging/logging.dart';

final _logger = Logger('SecurityStorage');
enum CanAuthenticateResponse {
  success,
  errorHwUnavailable,
  errorNoBiometricEnrolled,
  errorNoHardware,
  unsupported,
}

const _canAuthenticateMapping = {
  'Success': CanAuthenticateResponse.success,
  'ErrorHwUnavailable': CanAuthenticateResponse.errorHwUnavailable,
  'ErrorNoBiometricEnrolled': CanAuthenticateResponse.errorNoBiometricEnrolled,
  'ErrorNoHardware': CanAuthenticateResponse.errorNoHardware,
  'ErrorUnknown': CanAuthenticateResponse.unsupported,
};

enum AuthExceptionCode {
  userCanceled,
  unknown,
  timeout,
}

class AndroidPromptInfo {
  const AndroidPromptInfo({
    this.title = 'Authenticate to unlock data',
    this.subtitle,
    this.description,
    this.negativeButton = 'Cancel',
    this.confirmationRequired = true,
  })  : assert(title != null),
        assert(negativeButton != null),
        assert(confirmationRequired != null);

  final String title;
  final String subtitle;
  final String description;
  final String negativeButton;
  final bool confirmationRequired;

  static const defaultValues = AndroidPromptInfo();

  Map<String, dynamic> _toJson() => <String, dynamic>{
        'title': title,
        'subtitle': subtitle,
        'description': description,
        'negativeButton': negativeButton,
        'confirmationRequired': confirmationRequired,
      };
}

class StorageInitOptions {
  StorageInitOptions({
    this.authenticationValidityDurationSeconds = 10,
    this.authenticationRequired = true,
  });

  final int authenticationValidityDurationSeconds;

  /// Whether an authentication is required. if this is
  /// false NO BIOMETRIC CHECK WILL BE PERFORMED! and the value
  /// will simply be save encrypted. (default: true)
  final bool authenticationRequired;

  Map<String, dynamic> toJson() => <String, dynamic>{
        'authenticationValidityDurationSeconds':
            authenticationValidityDurationSeconds,
        'authenticationRequired': authenticationRequired,
      };
}

class SecurityStorage {
  static const MethodChannel _channel = const MethodChannel('security_storage');
  String name;
  AndroidPromptInfo androidPromptInfo;
  SecurityStorage(this.name, this.androidPromptInfo);

  static Future<CanAuthenticateResponse> canAuthenticate() async {
    if (Platform.isAndroid) {
      return _canAuthenticateMapping[
          await _channel.invokeMethod<String>('canAuthenticate')];
    }
    return CanAuthenticateResponse.unsupported;
  }

  static Future<SecurityStorage> init(
    String name, {
    StorageInitOptions options,
    AndroidPromptInfo androidPromptInfo = AndroidPromptInfo.defaultValues,
  }) async {
    assert(name != null);
    try {
      _channel.invokeMethod<bool>(
        'init',
        {
          'name': name,
          'options': options?.toJson() ?? StorageInitOptions().toJson(),
        },
      );
      return SecurityStorage(
        name,
        androidPromptInfo,
      );
    } catch (e, stackTrace) {
      _logger.warning(
          'Error while initializing security storage.', e, stackTrace);
    }
    return null;
  }

  Future<String> read(String name) =>
      _channel.invokeMethod<String>('read', <String, dynamic>{
        'name': name,
        'androidPromptInfo': androidPromptInfo._toJson()
      });

  Future<bool> delete(String name) =>
      _channel.invokeMethod<bool>('delete', <String, dynamic>{
        'name': name,
        'androidPromptInfo': androidPromptInfo._toJson()
      });

  Future<void> write(String name, String content) =>
      _channel.invokeMethod('write', <String, dynamic>{
        'name': name,
        'content': content,
        'androidPromptInfo': androidPromptInfo._toJson()
      });
}
