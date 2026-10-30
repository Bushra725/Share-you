import 'package:flutter/material.dart';
import 'package:share_ios/navigation/app_router.dart';
import 'package:share_ios/theme.dart';
import 'package:share_ios/widgets/components.dart';

class CleanScreen extends StatelessWidget {
  const CleanScreen({super.key, required this.services});
  final AppServices services;

  @override
  Widget build(BuildContext context) {
    final items = services.library.received;
    final big = items.where((f) => f.size > 20 * 1024 * 1024).toList();
    return Scaffold(
      appBar: AppBar(title: const Text('Clean')),
      body: ListView(
        padding: const EdgeInsets.all(16),
        children: [
          SectionCard(
            child: TileGrid(
              tiles: [
                ToolTile(icon: Icons.storage, label: 'Big files', color: catPdf, badge: '${big.length}', onTap: () {}),
                ToolTile(icon: Icons.copy_all, label: 'Duplicates', color: catVideo, onTap: () {}),
                ToolTile(icon: Icons.chat, label: 'Chats', color: catPhoto, onTap: () {}),
                ToolTile(icon: Icons.apps, label: 'Apps cache', color: catApps, onTap: () {}),
                ToolTile(icon: Icons.image, label: 'Media', color: catPink, onTap: () {}),
                ToolTile(icon: Icons.notifications_off, label: 'Notify', color: catDocs, onTap: () {}),
              ],
            ),
          ),
          const SizedBox(height: 16),
          SectionCard(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                const Text('Large files in Share', style: TextStyle(fontWeight: FontWeight.w700, fontSize: 16, color: navy)),
                if (big.isEmpty)
                  const Padding(
                    padding: EdgeInsets.only(top: 8),
                    child: Text('No large Share files yet.'),
                  )
                else
                  for (final f in big)
                    ListTile(
                      contentPadding: EdgeInsets.zero,
                      title: Text(f.name, maxLines: 1, overflow: TextOverflow.ellipsis),
                      subtitle: Text(formatBytes(f.size)),
                      trailing: IconButton(
                        icon: const Icon(Icons.delete_outline),
                        onPressed: () => services.library.remove(f),
                      ),
                    ),
              ],
            ),
          ),
        ],
      ),
    );
  }
}
