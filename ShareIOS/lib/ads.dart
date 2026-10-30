import 'package:flutter/material.dart';
import 'package:google_mobile_ads/google_mobile_ads.dart';
import 'package:share_ios/adjust_tracking.dart';
import 'package:share_ios/app_config.dart';
import 'package:share_ios/premium_store.dart';

/// AdMob helpers for Share iOS. Hidden while Premium is active.
class ShareAds {
  static InterstitialAd? _interstitial;
  static AppOpenAd? _appOpen;
  static RewardedAd? _rewarded;
  static bool _loadingInterstitial = false;
  static bool _loadingAppOpen = false;
  static bool _loadingRewarded = false;
  static bool _useAltInterstitial = false;
  static DateTime _lastFullScreen = DateTime.fromMillisecondsSinceEpoch(0);
  static DateTime? _backgroundAt;
  static const _cooldown = Duration(seconds: 75);
  static const _resumeAway = Duration(minutes: 3);

  static void onEnteredBackground() {
    _backgroundAt = DateTime.now();
  }

  static Future<void> init() async {
    await MobileAds.instance.initialize();
    preload();
    loadAppOpen();
    loadRewarded();
  }

  static bool _adsOff(PremiumStore? premium) => premium?.isPremium == true;

  static void _reportPaid(String? unitId, double valueMicros, String currencyCode) {
    AdjustTracking.trackAdRevenue(
      valueMicros: valueMicros.round(),
      currencyCode: currencyCode,
      unit: unitId,
    );
  }

  static void preload({PremiumStore? premium}) {
    if (_adsOff(premium) || _interstitial != null || _loadingInterstitial) return;
    _loadingInterstitial = true;
    final unit = _useAltInterstitial ? AppConfig.interstitial2AdUnitId : AppConfig.interstitial1AdUnitId;
    InterstitialAd.load(
      adUnitId: unit,
      request: const AdRequest(),
      adLoadCallback: InterstitialAdLoadCallback(
        onAdLoaded: (ad) {
          _loadingInterstitial = false;
          ad.onPaidEvent = (AdWithoutView ad, double valueMicros, PrecisionType precision, String currencyCode) {
            _reportPaid(ad.adUnitId, valueMicros, currencyCode);
          };
          _interstitial = ad;
        },
        onAdFailedToLoad: (_) {
          _loadingInterstitial = false;
          _interstitial = null;
        },
      ),
    );
  }

  static void loadAppOpen({PremiumStore? premium}) {
    if (_adsOff(premium) || _appOpen != null || _loadingAppOpen) return;
    _loadingAppOpen = true;
    AppOpenAd.load(
      adUnitId: AppConfig.appOpenAdUnitId,
      request: const AdRequest(),
      adLoadCallback: AppOpenAdLoadCallback(
        onAdLoaded: (ad) {
          _loadingAppOpen = false;
          ad.onPaidEvent = (AdWithoutView ad, double valueMicros, PrecisionType precision, String currencyCode) {
            _reportPaid(ad.adUnitId, valueMicros, currencyCode);
          };
          _appOpen = ad;
        },
        onAdFailedToLoad: (_) {
          _loadingAppOpen = false;
          _appOpen = null;
        },
      ),
    );
  }

  static void loadRewarded({PremiumStore? premium}) {
    if (_adsOff(premium) || _rewarded != null || _loadingRewarded) return;
    _loadingRewarded = true;
    RewardedAd.load(
      adUnitId: AppConfig.rewardedAdUnitId,
      request: const AdRequest(),
      rewardedAdLoadCallback: RewardedAdLoadCallback(
        onAdLoaded: (ad) {
          _loadingRewarded = false;
          ad.onPaidEvent = (AdWithoutView ad, double valueMicros, PrecisionType precision, String currencyCode) {
            _reportPaid(ad.adUnitId, valueMicros, currencyCode);
          };
          _rewarded = ad;
        },
        onAdFailedToLoad: (_) {
          _loadingRewarded = false;
          _rewarded = null;
        },
      ),
    );
  }

  static Future<void> showAppOpen({PremiumStore? premium, bool requireAway = false}) async {
    if (_adsOff(premium)) return;
    if (requireAway) {
      final away = _backgroundAt;
      if (away == null || DateTime.now().difference(away) < _resumeAway) return;
    }
    final ad = _appOpen;
    if (ad == null) {
      loadAppOpen(premium: premium);
      return;
    }
    if (DateTime.now().difference(_lastFullScreen) < _cooldown) return;
    _appOpen = null;
    ad.fullScreenContentCallback = FullScreenContentCallback(
      onAdDismissedFullScreenContent: (a) {
        a.dispose();
        loadAppOpen(premium: premium);
      },
      onAdFailedToShowFullScreenContent: (a, _) {
        a.dispose();
        loadAppOpen(premium: premium);
      },
    );
    _lastFullScreen = DateTime.now();
    await ad.show();
  }

  static Future<void> tryShowInterstitial({PremiumStore? premium}) async {
    if (_adsOff(premium)) return;
    if (DateTime.now().difference(_lastFullScreen) < _cooldown) {
      preload(premium: premium);
      return;
    }
    final ad = _interstitial;
    if (ad == null) {
      preload(premium: premium);
      return;
    }
    _interstitial = null;
    _useAltInterstitial = !_useAltInterstitial;
    ad.fullScreenContentCallback = FullScreenContentCallback(
      onAdDismissedFullScreenContent: (a) {
        a.dispose();
        preload(premium: premium);
      },
      onAdFailedToShowFullScreenContent: (a, _) {
        a.dispose();
        preload(premium: premium);
      },
    );
    _lastFullScreen = DateTime.now();
    await ad.show();
  }

  static Future<bool> showRewarded({
    required PremiumStore? premium,
    required VoidCallback onReward,
  }) async {
    if (_adsOff(premium)) {
      onReward();
      return true;
    }
    final ad = _rewarded;
    if (ad == null) {
      loadRewarded(premium: premium);
      return false;
    }
    _rewarded = null;
    ad.fullScreenContentCallback = FullScreenContentCallback(
      onAdDismissedFullScreenContent: (a) {
        a.dispose();
        loadRewarded(premium: premium);
      },
      onAdFailedToShowFullScreenContent: (a, _) {
        a.dispose();
        loadRewarded(premium: premium);
      },
    );
    _lastFullScreen = DateTime.now();
    await ad.show(onUserEarnedReward: (_, __) => onReward());
    return true;
  }

  static Future<void> showRewardedInterstitial({
    required PremiumStore? premium,
    required VoidCallback onReward,
  }) async {
    if (_adsOff(premium)) {
      onReward();
      return;
    }
    await RewardedInterstitialAd.load(
      adUnitId: AppConfig.rewardedInterstitialAdUnitId,
      request: const AdRequest(),
      rewardedInterstitialAdLoadCallback: RewardedInterstitialAdLoadCallback(
        onAdLoaded: (ad) async {
          ad.onPaidEvent = (AdWithoutView paidAd, double valueMicros, PrecisionType precision, String currencyCode) {
            _reportPaid(paidAd.adUnitId, valueMicros, currencyCode);
          };
          ad.fullScreenContentCallback = FullScreenContentCallback(
            onAdDismissedFullScreenContent: (a) => a.dispose(),
            onAdFailedToShowFullScreenContent: (a, _) => a.dispose(),
          );
          _lastFullScreen = DateTime.now();
          await ad.show(onUserEarnedReward: (_, __) => onReward());
        },
        onAdFailedToLoad: (_) {},
      ),
    );
  }
}

/// Anchored banner. [secondary] uses Banner 2 (files / secondary screens).
class ShareBannerAd extends StatefulWidget {
  const ShareBannerAd({super.key, required this.premium, this.secondary = false});
  final PremiumStore premium;
  final bool secondary;

  @override
  State<ShareBannerAd> createState() => _ShareBannerAdState();
}

class _ShareBannerAdState extends State<ShareBannerAd> {
  BannerAd? _banner;
  bool _loaded = false;
  bool _started = false;

  @override
  void didChangeDependencies() {
    super.didChangeDependencies();
    if (_started || widget.premium.isPremium) return;
    _started = true;
    _load();
  }

  Future<void> _load() async {
    final width = MediaQuery.sizeOf(context).width.truncate();
    final size = await AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(width);
    if (!mounted || size == null) return;
    final ad = BannerAd(
      size: size,
      adUnitId: widget.secondary ? AppConfig.banner2AdUnitId : AppConfig.banner1AdUnitId,
      listener: BannerAdListener(
        onAdLoaded: (ad) {
          if (!mounted) return;
          setState(() => _loaded = true);
        },
        onAdFailedToLoad: (ad, _) {
          ad.dispose();
          if (!mounted) return;
          setState(() {
            _banner = null;
            _loaded = false;
          });
        },
      ),
      request: const AdRequest(),
    );
    ad.onPaidEvent = (Ad paid, double valueMicros, PrecisionType precision, String currencyCode) {
      ShareAds._reportPaid(paid.adUnitId, valueMicros, currencyCode);
    };
    _banner = ad;
    await ad.load();
  }

  @override
  void dispose() {
    _banner?.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return ListenableBuilder(
      listenable: widget.premium,
      builder: (context, _) {
        if (widget.premium.isPremium || !_loaded || _banner == null) {
          return const SizedBox.shrink();
        }
        return Align(
          alignment: Alignment.center,
          child: SizedBox(
            width: _banner!.size.width.toDouble(),
            height: _banner!.size.height.toDouble(),
            child: AdWidget(ad: _banner!),
          ),
        );
      },
    );
  }
}
