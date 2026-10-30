import 'package:app_tracking_transparency/app_tracking_transparency.dart';
import 'package:firebase_core/firebase_core.dart';
import 'package:flutter/material.dart';
import 'package:share_ios/ads.dart';
import 'package:share_ios/adjust_tracking.dart';
import 'package:share_ios/library.dart';
import 'package:share_ios/pc_server.dart';
import 'package:share_ios/premium_store.dart';
import 'package:share_ios/screens/home_shell.dart';
import 'package:share_ios/screens/privacy_screen.dart';
import 'package:share_ios/theme.dart';
import 'package:shared_preferences/shared_preferences.dart';

Future<void> main() async {
  WidgetsFlutterBinding.ensureInitialized();
  await Firebase.initializeApp();
  await AdjustTracking.init();
  final premium = PremiumStore();
  final library = FileLibrary();
  await Future.wait([premium.start(), library.load()]);
  await ShareAds.init();
  final prefs = await SharedPreferences.getInstance();
  runApp(ShareApp(premium: premium, library: library, seenPrivacy: prefs.getBool('seen_privacy') ?? false));
}

class ShareApp extends StatefulWidget {
  const ShareApp({super.key, required this.premium, required this.library, required this.seenPrivacy});
  final PremiumStore premium;
  final FileLibrary library;
  final bool seenPrivacy;

  @override
  State<ShareApp> createState() => _ShareAppState();
}

class _ShareAppState extends State<ShareApp> with WidgetsBindingObserver {
  late bool seen = widget.seenPrivacy;
  final server = PcShare();
  bool _askedTracking = false;

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addObserver(this);
    if (seen) {
      WidgetsBinding.instance.addPostFrameCallback((_) => _afterConsent());
    }
  }

  @override
  void dispose() {
    WidgetsBinding.instance.removeObserver(this);
    super.dispose();
  }

  @override
  void didChangeAppLifecycleState(AppLifecycleState state) {
    if (!seen) return;
    if (state == AppLifecycleState.paused || state == AppLifecycleState.inactive) {
      ShareAds.onEnteredBackground();
    } else if (state == AppLifecycleState.resumed) {
      ShareAds.showAppOpen(premium: widget.premium, requireAway: true);
    }
  }

  Future<void> _accept() async {
    final prefs = await SharedPreferences.getInstance();
    await prefs.setBool('seen_privacy', true);
    setState(() => seen = true);
    await _afterConsent();
  }

  Future<void> _afterConsent() async {
    if (!_askedTracking) {
      _askedTracking = true;
      await AppTrackingTransparency.requestTrackingAuthorization();
    }
    await ShareAds.showAppOpen(premium: widget.premium);
  }

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      title: 'Share',
      debugShowCheckedModeBanner: false,
      theme: shareTheme(),
      home: seen
          ? HomeShell(library: widget.library, premium: widget.premium, server: server)
          : _Prep(onContinue: _accept),
    );
  }
}

class _Prep extends StatelessWidget {
  const _Prep({required this.onContinue});
  final VoidCallback onContinue;

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      body: SafeArea(
        child: Padding(
          padding: const EdgeInsets.all(24),
          child: Column(
            children: [
              const Spacer(),
              Container(
                width: double.infinity,
                padding: const EdgeInsets.symmetric(horizontal: 24, vertical: 28),
                decoration: BoxDecoration(
                  gradient: const LinearGradient(colors: [Color(0xFF28D4FF), Color(0xFF00C6F8)]),
                  borderRadius: BorderRadius.circular(24),
                ),
                child: const Column(
                  children: [
                    Icon(Icons.wifi_tethering, size: 48, color: navy),
                    SizedBox(height: 8),
                    Text(
                      'A few permissions to share at full speed',
                      textAlign: TextAlign.center,
                      style: TextStyle(color: navy, fontWeight: FontWeight.w600, fontSize: 18),
                    ),
                  ],
                ),
              ),
              const SizedBox(height: 20),
              const Text(
                'Share asks only when you pick a file, unlock Safebox with Face ID, or open the PC page on your Wi-Fi. Payments go through Apple. Ads help keep the free version available — Premium removes them.',
                textAlign: TextAlign.center,
                style: TextStyle(height: 1.4, color: navy),
              ),
              const Spacer(),
              ShareButton(label: 'Continue', onPressed: onContinue),
              TextButton(
                onPressed: () => Navigator.push(context, MaterialPageRoute(builder: (_) => const PrivacyScreen())),
                child: const Text('Read the privacy details'),
              ),
            ],
          ),
        ),
      ),
    );
  }
}
