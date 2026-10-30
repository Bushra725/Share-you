import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';
import 'package:share_ios/navigation/app_router.dart';
import 'package:share_ios/navigation/routes.dart';
import 'package:share_ios/theme.dart';
import 'package:share_ios/widgets/components.dart';

class HomeTab extends StatelessWidget {
  const HomeTab({super.key, required this.services});
  final AppServices services;

  @override
  Widget build(BuildContext context) {
    return ListenableBuilder(
      listenable: Listenable.merge([services.prefs, services.premium, services.history, services.library]),
      builder: (context, _) {
        final prefs = services.prefs;
        final premium = services.premium;
        final recent = services.history.entries.where((e) => !e.sent).take(5).toList();
        return CustomScrollView(
          slivers: [
            SliverToBoxAdapter(
              child: Container(
                width: double.infinity,
                decoration: const BoxDecoration(
                  gradient: brandGradient,
                  borderRadius: BorderRadius.vertical(bottom: Radius.circular(32)),
                ),
                padding: EdgeInsets.only(top: MediaQuery.paddingOf(context).top + 16, left: 20, right: 20, bottom: 16),
                child: Column(
                  children: [
                    Row(
                      children: [
                        AvatarBubble(index: prefs.avatar, size: 44, ring: true),
                        const SizedBox(width: 12),
                        Expanded(
                          child: Column(
                            crossAxisAlignment: CrossAxisAlignment.start,
                            children: [
                              const Text('Share you', style: TextStyle(color: navy, fontWeight: FontWeight.w800, fontSize: 22)),
                              Text(prefs.deviceName, style: TextStyle(color: navy.withOpacity(0.8), fontSize: 13)),
                            ],
                          ),
                        ),
                        Material(
                          color: Colors.white.withOpacity(0.35),
                          shape: const CircleBorder(),
                          child: IconButton(
                            onPressed: () => context.push(Routes.receive),
                            icon: const Icon(Icons.qr_code_scanner, color: navy),
                          ),
                        ),
                      ],
                    ),
                    const SizedBox(height: 22),
                    Row(
                      mainAxisAlignment: MainAxisAlignment.spaceEvenly,
                      children: [
                        BigAction(label: 'Send', icon: Icons.upload, onTap: () => context.push(Routes.send)),
                        BigAction(label: 'Receive', icon: Icons.download, onTap: () => context.push(Routes.receive)),
                      ],
                    ),
                    const SizedBox(height: 6),
                  ],
                ),
              ),
            ),
            SliverPadding(
              padding: const EdgeInsets.all(16),
              sliver: SliverList.list(
                children: [
                  if (!premium.isPremium) ...[
                    PremiumBanner(onTap: () => context.push(Routes.premium)),
                    const SizedBox(height: 16),
                  ],
                  SectionCard(
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        const Text('Quick tools', style: TextStyle(fontWeight: FontWeight.w700, fontSize: 16, color: navy)),
                        const SizedBox(height: 8),
                        TileGrid(
                          tiles: [
                            ToolTile(icon: Icons.computer, label: 'Connect PC', color: catApps, onTap: () => context.push(Routes.pc)),
                            ToolTile(icon: Icons.photo_library, label: 'Status saver', color: catPhoto, onTap: () => context.push(Routes.status)),
                            ToolTile(icon: Icons.groups, label: 'Group share', color: catVideo, onTap: () => context.push(Routes.group)),
                            ToolTile(icon: Icons.cloud_download, label: 'Downloader', color: catMusic, onTap: () => context.push(Routes.downloader)),
                            ToolTile(icon: Icons.lock, label: 'Safebox', color: catSafe, onTap: () => context.push(Routes.safebox)),
                            ToolTile(icon: Icons.transform, label: 'Converter', color: catDocs, onTap: () => context.push(Routes.converter)),
                            ToolTile(icon: Icons.cleaning_services, label: 'Clean', color: catPink, onTap: () => context.push(Routes.clean)),
                            ToolTile(icon: Icons.apps, label: 'Apps', color: catDownload, onTap: () => context.push(Routes.apps)),
                          ],
                        ),
                      ],
                    ),
                  ),
                  const SizedBox(height: 16),
                  _StorageCard(onTap: () => context.push(Routes.browser)),
                  const SizedBox(height: 16),
                  SectionCard(
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Row(
                          children: [
                            const Expanded(
                              child: Text('Recently received', style: TextStyle(fontWeight: FontWeight.w700, fontSize: 16, color: navy)),
                            ),
                            TextButton(onPressed: () => context.push(Routes.history), child: const Text('History')),
                          ],
                        ),
                        if (recent.isEmpty)
                          const Padding(
                            padding: EdgeInsets.symmetric(vertical: 12),
                            child: Text('Files you receive will show up here.', style: TextStyle(color: Color(0xFF5B7380))),
                          )
                        else
                          for (final e in recent)
                            ListTile(
                              contentPadding: EdgeInsets.zero,
                              title: Text(e.name, maxLines: 1, overflow: TextOverflow.ellipsis),
                              subtitle: Text('${formatBytes(e.size)} • ${e.peer}'),
                            ),
                      ],
                    ),
                  ),
                  const SizedBox(height: 24),
                ],
              ),
            ),
          ],
        );
      },
    );
  }
}

class _StorageCard extends StatelessWidget {
  const _StorageCard({required this.onTap});
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    return SectionCard(
      child: InkWell(
        onTap: onTap,
        child: Column(
          children: [
            Row(
              children: [
                const BrandIconBox(Icons.storage, size: 44, iconSize: 24),
                const SizedBox(width: 12),
                const Expanded(
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text('Internal storage', style: TextStyle(fontWeight: FontWeight.w700, fontSize: 16, color: navy)),
                      Text('Tap to browse files', style: TextStyle(color: Color(0xFF5B7380), fontSize: 13)),
                    ],
                  ),
                ),
                const Icon(Icons.chevron_right),
              ],
            ),
            const SizedBox(height: 10),
            ClipRRect(
              borderRadius: BorderRadius.circular(4),
              child: const LinearProgressIndicator(value: 0.42, minHeight: 8, color: cyan3, backgroundColor: Color(0xFFEDF2F6)),
            ),
            const SizedBox(height: 6),
            const Align(
              alignment: Alignment.centerLeft,
              child: Text('Available on this device', style: TextStyle(fontSize: 12, color: Color(0xFF5B7380))),
            ),
          ],
        ),
      ),
    );
  }
}
