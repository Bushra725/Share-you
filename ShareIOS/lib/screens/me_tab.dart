import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';
import 'package:share_ios/app_config.dart';
import 'package:share_ios/navigation/app_router.dart';
import 'package:share_ios/navigation/routes.dart';
import 'package:share_ios/theme.dart';
import 'package:share_ios/widgets/components.dart';
import 'package:url_launcher/url_launcher.dart';

class MeTab extends StatelessWidget {
  const MeTab({super.key, required this.services});
  final AppServices services;

  @override
  Widget build(BuildContext context) {
    return ListenableBuilder(
      listenable: Listenable.merge([services.prefs, services.premium, services.history]),
      builder: (context, _) {
        final prefs = services.prefs;
        final premium = services.premium;
        final history = services.history.entries;
        final sent = history.where((e) => e.sent);
        final received = history.where((e) => !e.sent);
        return ListView(
          children: [
            Container(
              width: double.infinity,
              decoration: const BoxDecoration(gradient: brandGradient),
              padding: EdgeInsets.only(top: MediaQuery.paddingOf(context).top + 24, left: 24, right: 24, bottom: 24),
              child: Row(
                children: [
                  GestureDetector(onTap: prefs.cycleAvatar, child: AvatarBubble(index: prefs.avatar, size: 72, ring: true)),
                  const SizedBox(width: 16),
                  Expanded(
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Text(prefs.deviceName, style: const TextStyle(color: navy, fontWeight: FontWeight.bold, fontSize: 20)),
                        Text(premium.isPremium ? '⭐ Premium' : 'Free', style: TextStyle(color: navy.withOpacity(0.8))),
                      ],
                    ),
                  ),
                  IconButton(
                    onPressed: () async {
                      final ctrl = TextEditingController(text: prefs.deviceName);
                      final name = await showDialog<String>(
                        context: context,
                        builder: (ctx) => AlertDialog(
                          title: const Text('Device name'),
                          content: TextField(controller: ctrl, maxLength: 24, autofocus: true),
                          actions: [
                            TextButton(onPressed: () => Navigator.pop(ctx), child: const Text('Cancel')),
                            TextButton(onPressed: () => Navigator.pop(ctx, ctrl.text), child: const Text('Save')),
                          ],
                        ),
                      );
                      if (name != null) await prefs.setName(name);
                    },
                    icon: const Icon(Icons.edit, color: navy),
                  ),
                ],
              ),
            ),
            Padding(
              padding: const EdgeInsets.all(16),
              child: Column(
                children: [
                  if (!premium.isPremium) ...[
                    PremiumBanner(onTap: () => context.push(Routes.premium)),
                    const SizedBox(height: 16),
                  ],
                  SectionCard(
                    child: Row(
                      children: [
                        Expanded(child: _Stat('Sent', formatBytes(sent.fold<int>(0, (a, b) => a + b.size)), '${sent.length} files')),
                        Expanded(child: _Stat('Received', formatBytes(received.fold<int>(0, (a, b) => a + b.size)), '${received.length} files')),
                      ],
                    ),
                  ),
                  const SizedBox(height: 16),
                  SectionCard(
                    padding: 4,
                    child: Column(
                      children: [
                        MenuRow(
                          icon: Icons.workspace_premium,
                          title: 'Share you Premium',
                          subtitle: premium.isPremium ? "You're Premium ✓" : 'Unlimited file size · No ads · Safebox',
                          onTap: () => context.push(Routes.premium),
                        ),
                        MenuRow(icon: Icons.history, title: 'Transfer history', onTap: () => context.push(Routes.history)),
                        MenuRow(icon: Icons.lock, title: 'Safebox', onTap: () => context.push(Routes.safebox)),
                        MenuRow(icon: Icons.settings, title: 'Settings', onTap: () => context.push(Routes.settings)),
                        MenuRow(icon: Icons.privacy_tip, title: 'Privacy', onTap: () => context.push(Routes.privacy)),
                        MenuRow(
                          icon: Icons.policy,
                          title: 'Privacy policy',
                          onTap: () => launchUrl(Uri.parse(AppConfig.privacyPolicyUrl), mode: LaunchMode.externalApplication),
                        ),
                        MenuRow(icon: Icons.info, title: 'About', subtitle: 'Share you 1.0 · ${AppConfig.bundleId}', onTap: () {}),
                      ],
                    ),
                  ),
                ],
              ),
            ),
          ],
        );
      },
    );
  }
}

class _Stat extends StatelessWidget {
  const _Stat(this.label, this.value, this.sub);
  final String label;
  final String value;
  final String sub;

  @override
  Widget build(BuildContext context) {
    return Column(
      children: [
        Text(value, style: const TextStyle(fontSize: 20, fontWeight: FontWeight.w800, color: cyanInk)),
        Text(label, style: const TextStyle(fontWeight: FontWeight.w700)),
        Text(sub, style: const TextStyle(fontSize: 12, color: Color(0xFF5B7380))),
      ],
    );
  }
}
