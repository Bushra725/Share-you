import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';
import 'package:share_ios/navigation/app_router.dart';
import 'package:share_ios/navigation/routes.dart';
import 'package:share_ios/theme.dart';
import 'package:share_ios/widgets/components.dart';

class ToolsTab extends StatelessWidget {
  const ToolsTab({super.key, required this.services});
  final AppServices services;

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: page,
      appBar: AppBar(title: const Text('Tools')),
      body: ListenableBuilder(
        listenable: services.premium,
        builder: (context, _) {
          return ListView(
            padding: const EdgeInsets.all(16),
            children: [
              if (!services.premium.isPremium) ...[
                PremiumBanner(onTap: () => context.push(Routes.premium)),
                const SizedBox(height: 16),
              ],
              _section(context, 'Transfer', [
                ToolTile(icon: Icons.upload, label: 'Send', color: catApps, onTap: () => context.push(Routes.send)),
                ToolTile(icon: Icons.download, label: 'Receive', color: catPhoto, onTap: () => context.push(Routes.receive)),
                ToolTile(icon: Icons.groups, label: 'Group share', color: catVideo, onTap: () => context.push(Routes.group)),
                ToolTile(icon: Icons.computer, label: 'Connect PC', color: catApps, onTap: () => context.push(Routes.pc)),
                ToolTile(icon: Icons.history, label: 'History', color: catDocs, onTap: () => context.push(Routes.history)),
                ToolTile(icon: Icons.photo_library, label: 'Status saver', color: catPhoto, onTap: () => context.push(Routes.status)),
              ]),
              const SizedBox(height: 16),
              _section(context, 'Download', [
                ToolTile(icon: Icons.cloud_download, label: 'Downloader', color: catMusic, onTap: () => context.push(Routes.downloader)),
              ]),
              const SizedBox(height: 16),
              _section(context, 'Media', [
                ToolTile(icon: Icons.music_note, label: 'Music', color: catPink, onTap: () => context.go(Routes.player)),
                ToolTile(icon: Icons.ondemand_video, label: 'Videos', color: catVideo, onTap: () => context.push('${Routes.category}/videos')),
                ToolTile(icon: Icons.transform, label: 'Converter', color: catDocs, onTap: () => context.push(Routes.converter)),
              ]),
              const SizedBox(height: 16),
              _section(context, 'Files', [
                ToolTile(icon: Icons.folder_open, label: 'Browse', color: catCyan, onTap: () => context.push(Routes.browser)),
                ToolTile(icon: Icons.cleaning_services, label: 'Clean', color: catPdf, onTap: () => context.push(Routes.clean)),
                ToolTile(icon: Icons.lock, label: 'Safebox', color: catSafe, onTap: () => context.push(Routes.safebox)),
                ToolTile(icon: Icons.apps, label: 'Apps', color: catDownload, onTap: () => context.push(Routes.apps)),
              ]),
            ],
          );
        },
      ),
    );
  }

  Widget _section(BuildContext context, String title, List<ToolTile> tiles) {
    return SectionCard(
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(title, style: const TextStyle(fontWeight: FontWeight.w700, fontSize: 16, color: navy)),
          const SizedBox(height: 6),
          TileGrid(tiles: tiles),
        ],
      ),
    );
  }
}
