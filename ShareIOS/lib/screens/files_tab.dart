import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';
import 'package:share_ios/navigation/app_router.dart';
import 'package:share_ios/navigation/routes.dart';
import 'package:share_ios/theme.dart';
import 'package:share_ios/widgets/components.dart';

class FilesTab extends StatelessWidget {
  const FilesTab({super.key, required this.services});
  final AppServices services;

  @override
  Widget build(BuildContext context) {
    return ListenableBuilder(
      listenable: services.library,
      builder: (context, _) {
        final received = services.library.received;
        return Scaffold(
          backgroundColor: page,
          appBar: AppBar(
            title: const Text('Files'),
            actions: [
              IconButton(onPressed: () => context.push(Routes.browser), icon: const Icon(Icons.search)),
            ],
          ),
          body: ListView(
            padding: const EdgeInsets.all(16),
            children: [
              Material(
                color: const Color(0xFFEDF2F6),
                borderRadius: BorderRadius.circular(14),
                child: ListTile(
                  leading: const Icon(Icons.search),
                  title: const Text('Search for local files'),
                  onTap: () => context.push(Routes.browser),
                ),
              ),
              const SizedBox(height: 16),
              SectionCard(
                child: TileGrid(
                  tiles: [
                    ToolTile(icon: Icons.music_note, label: 'Music', color: catMusic, onTap: () => context.push('${Routes.category}/music')),
                    ToolTile(icon: Icons.apps, label: 'Apps', color: catApps, onTap: () => context.push(Routes.apps)),
                    ToolTile(icon: Icons.movie, label: 'Videos', color: catVideo, onTap: () => context.push('${Routes.category}/videos')),
                    ToolTile(icon: Icons.image, label: 'Photos', color: catPhoto, onTap: () => context.push('${Routes.category}/photos')),
                    ToolTile(icon: Icons.description, label: 'Documents', color: catDocs, onTap: () => context.push('${Routes.category}/docs')),
                    ToolTile(icon: Icons.folder_zip, label: 'ZIP', color: const Color(0xFFD97706), onTap: () => context.push('${Routes.category}/zips')),
                    ToolTile(icon: Icons.transform, label: 'Converter', color: catPdf, onTap: () => context.push(Routes.converter)),
                    ToolTile(icon: Icons.lock, label: 'Safebox', color: catSafe, onTap: () => context.push(Routes.safebox)),
                    ToolTile(
                      icon: Icons.download,
                      label: 'Received',
                      color: catDownload,
                      badge: received.isEmpty ? null : '${received.length}',
                      onTap: () => context.push('${Routes.category}/received'),
                    ),
                  ],
                ),
              ),
              const SizedBox(height: 16),
              SectionCard(
                padding: 4,
                child: Column(
                  children: [
                    MenuRow(icon: Icons.folder_open, title: 'Browse internal storage', onTap: () => context.push(Routes.browser)),
                    MenuRow(
                      icon: Icons.cleaning_services,
                      title: 'Phone Cleaner',
                      subtitle: 'Big files, duplicates, chats, apps & more',
                      onTap: () => context.push(Routes.clean),
                    ),
                  ],
                ),
              ),
              const SizedBox(height: 16),
              SectionCard(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    const Text('Recent files', style: TextStyle(fontWeight: FontWeight.w700, fontSize: 16, color: navy)),
                    if (received.isEmpty)
                      const Padding(
                        padding: EdgeInsets.only(top: 12),
                        child: Text('No recent files', style: TextStyle(color: Color(0xFF5B7380))),
                      )
                    else
                      for (final f in received.take(20))
                        ListTile(
                          contentPadding: EdgeInsets.zero,
                          title: Text(f.name, maxLines: 1, overflow: TextOverflow.ellipsis),
                          subtitle: Text(formatBytes(f.size)),
                        ),
                  ],
                ),
              ),
            ],
          ),
        );
      },
    );
  }
}
