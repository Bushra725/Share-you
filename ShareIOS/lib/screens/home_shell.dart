import 'package:flutter/material.dart';
import 'package:share_ios/ads.dart';
import 'package:share_ios/app_config.dart';
import 'package:share_ios/library.dart';
import 'package:share_ios/pc_server.dart';
import 'package:share_ios/premium_store.dart';
import 'package:share_ios/screens/pc_screen.dart';
import 'package:share_ios/screens/player_screen.dart';
import 'package:share_ios/screens/premium_screen.dart';
import 'package:share_ios/screens/privacy_screen.dart';
import 'package:share_ios/screens/safebox_screen.dart';
import 'package:share_ios/screens/transfer_screens.dart';
import 'package:share_ios/theme.dart';
import 'package:share_plus/share_plus.dart';
import 'package:url_launcher/url_launcher.dart';

class HomeShell extends StatefulWidget {
  const HomeShell({super.key, required this.library, required this.premium, required this.server});
  final FileLibrary library;
  final PremiumStore premium;
  final PcShare server;

  @override
  State<HomeShell> createState() => _HomeShellState();
}

class _HomeShellState extends State<HomeShell> {
  int tab = 0;

  @override
  Widget build(BuildContext context) {
    final pages = [
      _Home(library: widget.library, premium: widget.premium, server: widget.server),
      _Files(library: widget.library, premium: widget.premium),
      PlayerScreen(library: widget.library),
      _Me(premium: widget.premium, library: widget.library),
    ];
    return Scaffold(
      body: SafeArea(child: pages[tab]),
      bottomNavigationBar: Column(
        mainAxisSize: MainAxisSize.min,
        children: [
          if (tab == 0 || tab == 1) ShareBannerAd(premium: widget.premium, secondary: tab == 1),
          NavigationBar(
            selectedIndex: tab,
            onDestinationSelected: (i) => setState(() => tab = i),
            destinations: const [
              NavigationDestination(icon: Icon(Icons.home_outlined), selectedIcon: Icon(Icons.home), label: 'Home'),
              NavigationDestination(icon: Icon(Icons.folder_outlined), selectedIcon: Icon(Icons.folder), label: 'Files'),
              NavigationDestination(icon: Icon(Icons.play_circle_outline), selectedIcon: Icon(Icons.play_circle), label: 'Player'),
              NavigationDestination(icon: Icon(Icons.person_outline), selectedIcon: Icon(Icons.person), label: 'Me'),
            ],
          ),
        ],
      ),
    );
  }
}

class _Home extends StatelessWidget {
  const _Home({required this.library, required this.premium, required this.server});
  final FileLibrary library;
  final PremiumStore premium;
  final PcShare server;

  @override
  Widget build(BuildContext context) {
    return ListenableBuilder(
      listenable: library,
      builder: (context, _) {
        final recent = library.received.take(5).toList();
        return ListView(
          padding: const EdgeInsets.all(16),
          children: [
            const Text('Share', style: TextStyle(fontSize: 32, fontWeight: FontWeight.w800, color: navy)),
            const SizedBox(height: 4),
            const Text('Send and receive files on this iPhone.', style: TextStyle(color: Color(0xFF5B7380))),
            const SizedBox(height: 16),
            Row(
              children: [
                Expanded(child: _Tile(icon: Icons.upload, label: 'Send', onTap: () => _open(context, SendScreen(premium: premium)))),
                const SizedBox(width: 12),
                Expanded(child: _Tile(icon: Icons.download, label: 'Receive', onTap: () => _open(context, ReceiveScreen(library: library, premium: premium)))),
              ],
            ),
            const SizedBox(height: 12),
            Row(
              children: [
                Expanded(child: _Tile(icon: Icons.computer, label: 'Connect PC', onTap: () => _open(context, PcScreen(library: library, premium: premium, server: server)))),
                const SizedBox(width: 12),
                Expanded(child: _Tile(icon: Icons.workspace_premium, label: 'Premium', onTap: () => _open(context, PremiumScreen(premium: premium)))),
              ],
            ),
            const SizedBox(height: 20),
            const Text('Recently received', style: TextStyle(fontWeight: FontWeight.w700, color: navy)),
            if (recent.isEmpty)
              const Padding(padding: EdgeInsets.only(top: 8), child: Text('Nothing received yet.'))
            else
              for (final file in recent)
                ListTile(
                  contentPadding: EdgeInsets.zero,
                  title: Text(file.name, maxLines: 1, overflow: TextOverflow.ellipsis),
                  subtitle: Text(formatBytes(file.size)),
                ),
          ],
        );
      },
    );
  }

  Future<void> _open(BuildContext context, Widget page) async {
    await Navigator.push(context, MaterialPageRoute(builder: (_) => page));
    await ShareAds.tryShowInterstitial(premium: premium);
  }
}

class _Tile extends StatelessWidget {
  const _Tile({required this.icon, required this.label, required this.onTap});
  final IconData icon;
  final String label;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    return Material(
      color: Colors.white,
      borderRadius: BorderRadius.circular(20),
      child: InkWell(
        borderRadius: BorderRadius.circular(20),
        onTap: onTap,
        child: Padding(
          padding: const EdgeInsets.symmetric(vertical: 22),
          child: Column(
            children: [
              Icon(icon, color: cyanInk, size: 32),
              const SizedBox(height: 8),
              Text(label, style: const TextStyle(fontWeight: FontWeight.w700, color: navy)),
            ],
          ),
        ),
      ),
    );
  }
}

class _Files extends StatelessWidget {
  const _Files({required this.library, required this.premium});
  final FileLibrary library;
  final PremiumStore premium;

  @override
  Widget build(BuildContext context) {
    return ListenableBuilder(
      listenable: library,
      builder: (context, _) {
        final files = library.received;
        return ListView(
          padding: const EdgeInsets.all(16),
          children: [
            const Text('Files', style: TextStyle(fontSize: 28, fontWeight: FontWeight.w800, color: navy)),
            if (files.isEmpty)
              const Padding(padding: EdgeInsets.only(top: 16), child: Text('Imported files show up here.'))
            else
              for (final file in files)
                ListTile(
                  contentPadding: EdgeInsets.zero,
                  title: Text(file.name, maxLines: 1, overflow: TextOverflow.ellipsis),
                  subtitle: Text(formatBytes(file.size)),
                  trailing: IconButton(
                    icon: const Icon(Icons.ios_share),
                    onPressed: () => SharePlus.instance.share(ShareParams(files: [XFile(file.path)])),
                  ),
                ),
          ],
        );
      },
    );
  }
}

class _Me extends StatelessWidget {
  const _Me({required this.premium, required this.library});
  final PremiumStore premium;
  final FileLibrary library;

  @override
  Widget build(BuildContext context) {
    return ListenableBuilder(
      listenable: premium,
      builder: (context, _) {
        return ListView(
          padding: const EdgeInsets.all(16),
          children: [
            const Text('Me', style: TextStyle(fontSize: 28, fontWeight: FontWeight.w800, color: navy)),
            const SizedBox(height: 8),
            ListTile(
              contentPadding: EdgeInsets.zero,
              leading: const Icon(Icons.workspace_premium, color: cyanInk),
              title: Text(premium.isPremium ? "You're Premium" : 'Get Premium'),
              subtitle: const Text('Apple In-App Purchase'),
              onTap: () => Navigator.push(context, MaterialPageRoute(builder: (_) => PremiumScreen(premium: premium))),
            ),
            ListTile(
              contentPadding: EdgeInsets.zero,
              leading: const Icon(Icons.lock, color: cyanInk),
              title: const Text('Safebox'),
              onTap: () => Navigator.push(context, MaterialPageRoute(builder: (_) => SafeboxScreen(library: library, premium: premium))),
            ),
            ListTile(
              contentPadding: EdgeInsets.zero,
              leading: const Icon(Icons.privacy_tip, color: cyanInk),
              title: const Text('Privacy'),
              onTap: () => Navigator.push(context, MaterialPageRoute(builder: (_) => const PrivacyScreen())),
            ),
            ListTile(
              contentPadding: EdgeInsets.zero,
              leading: const Icon(Icons.policy, color: cyanInk),
              title: const Text('Privacy policy'),
              onTap: () => launchUrl(Uri.parse(AppConfig.privacyPolicyUrl), mode: LaunchMode.externalApplication),
            ),
            ListTile(
              contentPadding: EdgeInsets.zero,
              leading: const Icon(Icons.restore, color: cyanInk),
              title: const Text('Restore purchases'),
              onTap: premium.restore,
            ),
            const SizedBox(height: 24),
            const Text('Share 1.0', style: TextStyle(color: Color(0xFF5B7380))),
            Text(AppConfig.bundleId, style: const TextStyle(color: Color(0xFF5B7380), fontSize: 12)),
          ],
        );
      },
    );
  }
}
