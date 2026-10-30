import 'dart:async';

import 'package:flutter/foundation.dart';
import 'package:in_app_purchase/in_app_purchase.dart';
import 'package:share_ios/adjust_tracking.dart';
import 'package:share_ios/app_config.dart';
import 'package:shared_preferences/shared_preferences.dart';

/// Apple In-App Purchase (StoreKit). Digital Premium must be sold through Apple.
class PremiumStore extends ChangeNotifier {
  static const monthlyId = 'zapshare_premium_monthly';
  static const yearlyId = 'zapshare_premium_yearly';
  static const lifetimeId = 'zapshare_premium_lifetime';
  static const ids = {monthlyId, yearlyId, lifetimeId};
  static const freeFileLimit = 100 * 1024 * 1024;

  static const privacyPolicyUrl = AppConfig.privacyPolicyUrl;
  static const termsUrl = AppConfig.termsUrl;
  static const manageUrl = AppConfig.manageSubscriptionsUrl;

  final InAppPurchase _iap = InAppPurchase.instance;
  StreamSubscription<List<PurchaseDetails>>? _sub;

  bool available = false;
  bool isPremium = false;
  bool loading = true;
  String? message;
  List<ProductDetails> products = [];

  Future<void> start() async {
    final prefs = await SharedPreferences.getInstance();
    isPremium = prefs.getBool('apple_premium') ?? false;
    available = await _iap.isAvailable();
    _sub = _iap.purchaseStream.listen(_onPurchases, onError: (Object _) {
      message = 'Purchase could not be completed. Try again.';
      notifyListeners();
    });
    if (available) {
      final response = await _iap.queryProductDetails(ids);
      products = response.productDetails.toList()
        ..sort((a, b) => _order(a.id).compareTo(_order(b.id)));
      if (products.isEmpty) {
        message =
            'These plans are not on the App Store yet. Create them in App Store Connect, then install Share from TestFlight.';
      }
    } else {
      message = 'The App Store is not available on this device.';
    }
    loading = false;
    notifyListeners();
  }

  int _order(String id) {
    if (id == monthlyId) return 0;
    if (id == yearlyId) return 1;
    return 2;
  }

  String titleFor(String id) {
    switch (id) {
      case monthlyId:
        return 'Monthly';
      case yearlyId:
        return 'Yearly';
      default:
        return 'Lifetime';
    }
  }

  String periodFor(String id) {
    switch (id) {
      case monthlyId:
        return '1 month, renews automatically';
      case yearlyId:
        return '1 year, renews automatically';
      default:
        return 'One-time purchase. Does not renew.';
    }
  }

  bool blocks(int bytes) => !isPremium && bytes > freeFileLimit;

  Future<void> buy(ProductDetails product) async {
    message = null;
    notifyListeners();
    final param = PurchaseParam(productDetails: product);
    await _iap.buyNonConsumable(purchaseParam: param);
  }

  Future<void> restore() async {
    message = null;
    notifyListeners();
    await _iap.restorePurchases();
  }

  Future<void> _onPurchases(List<PurchaseDetails> purchases) async {
    for (final purchase in purchases) {
      switch (purchase.status) {
        case PurchaseStatus.purchased:
        case PurchaseStatus.restored:
          if (ids.contains(purchase.productID)) {
            final wasPremium = isPremium;
            isPremium = true;
            final prefs = await SharedPreferences.getInstance();
            await prefs.setBool('apple_premium', true);
            message = 'Welcome to Premium.';
            if (purchase.status == PurchaseStatus.purchased && !wasPremium) {
              AdjustTracking.trackStartTrial();
            }
          }
          if (purchase.pendingCompletePurchase) {
            await _iap.completePurchase(purchase);
          }
        case PurchaseStatus.pending:
          message = 'Payment is pending with Apple. Premium unlocks when it completes.';
        case PurchaseStatus.error:
          message = purchase.error?.message ?? 'Purchase failed.';
        case PurchaseStatus.canceled:
          message = null;
      }
    }
    notifyListeners();
  }

  @override
  void dispose() {
    _sub?.cancel();
    super.dispose();
  }
}
