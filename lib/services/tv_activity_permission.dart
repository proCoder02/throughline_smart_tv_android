import 'package:flutter/services.dart';

/// Notification Listener permission for TvActivityListenerService.kt --
/// same native channel push_service.dart's _kCallChannel talks to
/// (com.nodexdata.speechtotext/call), just two more method names on it.
/// Can't be requested silently: the system only lets the user grant this
/// from its own settings screen, see MainActivity.kt's
/// openTvNotificationAccessSettings handler.
class TvActivityPermission {
  static const _channel = MethodChannel('com.nodexdata.speechtotext/call');

  static Future<bool> isGranted() async {
    final result = await _channel.invokeMethod('isTvNotificationAccessGranted');
    return result == true;
  }

  static Future<void> openSettings() async {
    await _channel.invokeMethod('openTvNotificationAccessSettings');
  }
}
