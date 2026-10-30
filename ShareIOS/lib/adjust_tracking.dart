import 'package:adjust_sdk/adjust.dart';
import 'package:adjust_sdk/adjust_ad_revenue.dart';
import 'package:adjust_sdk/adjust_config.dart';
import 'package:adjust_sdk/adjust_event.dart';
import 'package:flutter/foundation.dart';
import 'package:share_ios/app_config.dart';

/// Adjust attribution and revenue events for the iOS release.
class AdjustTracking {
  static Future<void> init() async {
    final env = kReleaseMode ? AdjustEnvironment.production : AdjustEnvironment.sandbox;
    final config = AdjustConfig(AppConfig.adjustAppToken, env);
    config.logLevel = kReleaseMode ? AdjustLogLevel.warn : AdjustLogLevel.verbose;
    Adjust.initSdk(config);
  }

  static void trackStartTrial() {
    Adjust.trackEvent(AdjustEvent(AppConfig.adjustStartTrialToken));
  }

  /// Impression-level AdMob revenue + the dashboard `ad_revenue_adjust` event.
  static void trackAdRevenue({
    required int valueMicros,
    required String currencyCode,
    String? network,
    String? unit,
  }) {
    final dollars = valueMicros / 1000000.0;
    final revenue = AdjustAdRevenue('admob_sdk');
    revenue.setRevenue(dollars, currencyCode);
    if (network != null) revenue.adRevenueNetwork = network;
    if (unit != null) revenue.adRevenueUnit = unit;
    Adjust.trackAdRevenue(revenue);

    final event = AdjustEvent(AppConfig.adjustAdRevenueToken);
    event.setRevenue(dollars, currencyCode);
    Adjust.trackEvent(event);
  }
}
