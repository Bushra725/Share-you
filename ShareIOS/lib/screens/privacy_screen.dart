import 'package:flutter/material.dart';
import 'package:share_ios/premium_store.dart';
import 'package:share_ios/theme.dart';
import 'package:url_launcher/url_launcher.dart';

class PrivacyScreen extends StatelessWidget {
  const PrivacyScreen({super.key});

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text('Privacy')),
      body: ListView(
        padding: const EdgeInsets.all(20),
        children: [
          const PrivacyBody(),
          TextButton(
            onPressed: () => launchUrl(Uri.parse(PremiumStore.privacyPolicyUrl), mode: LaunchMode.externalApplication),
            child: const Text('Open the full privacy policy'),
          ),
          TextButton(
            onPressed: () => launchUrl(Uri.parse(PremiumStore.termsUrl), mode: LaunchMode.externalApplication),
            child: const Text('Apple Terms of Use'),
          ),
        ],
      ),
    );
  }
}

class PrivacyBody extends StatelessWidget {
  const PrivacyBody({super.key});

  @override
  Widget build(BuildContext context) {
    return const Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Text('Privacy on iPhone', style: TextStyle(fontSize: 22, fontWeight: FontWeight.w800, color: navy)),
        SizedBox(height: 12),
        _Block('No account', 'Share does not ask you to create an account.'),
        _Block(
          'Files stay on this iPhone',
          'Files you send, receive, play, or put in Safebox stay in the app unless you choose to share them. Share does not upload your library to a server we run.',
        ),
        _Block(
          'PC page',
          'Connect to PC opens a private page on your Wi-Fi. A computer on the same network can use it. That page is not published on the internet.',
        ),
        _Block(
          'Apple payments',
          'Premium is sold by Apple through In-App Purchase. Share does not receive your card number.',
        ),
        _Block(
          'Ads and measurement',
          'The free version shows Google AdMob ads. If you allow App Tracking, Share and our partners (including Adjust) may use the advertising identifier to measure installs and ad performance. Premium removes ads. See the full privacy policy for details.',
        ),
        _Block('Face ID', 'If you turn on Face ID for Safebox, iOS performs the check. Share only learns whether it succeeded.'),
        _Block('Photos and files', 'Share reads a photo or file only after you pick it.'),
      ],
    );
  }
}

class _Block extends StatelessWidget {
  const _Block(this.title, this.body);
  final String title;
  final String body;

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.only(bottom: 14),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(title, style: const TextStyle(fontWeight: FontWeight.w700, color: navy)),
          const SizedBox(height: 4),
          Text(body, style: const TextStyle(height: 1.35, color: Color(0xFF34515C))),
        ],
      ),
    );
  }
}
